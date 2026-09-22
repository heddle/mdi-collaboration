package edu.cnu.mdi.collaboration.transfer;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.cnu.mdi.collaboration.CollaborationService;
import edu.cnu.mdi.collaboration.event.CollaborationEvent;
import edu.cnu.mdi.collaboration.event.CollaborationListener;
import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.model.CollaborationMessageType;
import edu.cnu.mdi.collaboration.model.Collaborator;

/** Coordinates file-transfer control messages with an out-of-band data-plane service. */
public final class CollaborationFileExchange implements AutoCloseable {
    private final CollaborationService collaboration;
    private final FileTransferService transfers;
    private final ConcurrentMap<UUID, FileOffer> incomingOffers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TransferHandle> outgoingTransfers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, FileOffer> outgoingOffers = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> acceptedTransfers = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<CollaborationFileExchangeListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CollaborationListener collaborationListener = this::onCollaborationEvent;

    /** Creates an exchange that owns the supplied file-transfer service. */
    public CollaborationFileExchange(CollaborationService collaboration, FileTransferService transfers) {
        this.collaboration = Objects.requireNonNull(collaboration, "collaboration");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        collaboration.addListener(collaborationListener);
    }

    /** Prepares and publishes a file offer without putting file bytes in the message. */
    public TransferHandle offer(Path file, Collaborator recipient) {
        requireOpen();
        TransferHandle handle = transfers.send(file, recipient);
        outgoingTransfers.put(handle.transferId(), handle);
        handle.offer().whenComplete((offer, error) -> {
            if (error != null) {
                outgoingTransfers.remove(handle.transferId());
                fire(new CollaborationFileExchangeEvent.Error(handle.transferId(), unwrap(error)));
                return;
            }
            outgoingOffers.put(offer.transferId(), offer);
            collaboration.send(FileTransferMessages.offer(offer)).whenComplete((unused, sendError) -> {
                if (sendError != null) {
                    outgoingTransfers.remove(handle.transferId());
                    outgoingOffers.remove(handle.transferId());
                    handle.cancel();
                    fire(new CollaborationFileExchangeEvent.Error(handle.transferId(), unwrap(sendError)));
                } else {
                    fire(new CollaborationFileExchangeEvent.OfferSent(offer, handle));
                }
            });
        });
        monitor(handle);
        return handle;
    }

    /** Accepts a pending offer, publishes acceptance, and starts its data-plane receive. */
    public TransferHandle accept(UUID transferId, Path destinationDirectory) {
        requireOpen();
        FileOffer offer = removeIncoming(transferId);
        TransferHandle handle = transfers.receive(offer, destinationDirectory);
        monitor(handle);
        collaboration.send(FileTransferMessages.accept(offer, collaboration.localCollaborator().id()))
                .whenComplete((unused, error) -> {
                    if (error != null) {
                        handle.cancel();
                        fire(new CollaborationFileExchangeEvent.Error(transferId, unwrap(error)));
                    }
                });
        return handle;
    }

    /** Rejects a pending offer and publishes the response. */
    public void reject(UUID transferId, String reason) {
        requireOpen();
        FileOffer offer = removeIncoming(transferId);
        collaboration.send(FileTransferMessages.reject(offer, collaboration.localCollaborator().id(), reason))
                .whenComplete((unused, error) -> {
                    if (error != null) fire(new CollaborationFileExchangeEvent.Error(transferId, unwrap(error)));
                });
    }

    /** Returns a snapshot of currently pending incoming offers. */
    public java.util.List<FileOffer> pendingOffers() { return java.util.List.copyOf(incomingOffers.values()); }
    /** Adds a removable exchange listener. */
    public void addListener(CollaborationFileExchangeListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }
    /** Removes an exchange listener. */
    public void removeListener(CollaborationFileExchangeListener listener) { listeners.remove(listener); }

    private FileOffer removeIncoming(UUID transferId) {
        Objects.requireNonNull(transferId, "transferId");
        FileOffer offer = incomingOffers.remove(transferId);
        if (offer == null) throw new IllegalArgumentException("no pending offer: " + transferId);
        return offer;
    }

    private void onCollaborationEvent(CollaborationEvent event) {
        if (!(event instanceof CollaborationEvent.MessageReceived received)) return;
        CollaborationMessage message = received.message();
        try {
            if (message.type().equals(CollaborationMessageType.FILE_OFFER)) {
                receiveOffer(message);
            } else if (message.type().equals(CollaborationMessageType.FILE_ACCEPT)) {
                UUID transferId = FileTransferMessages.transferId(message);
                validateResponse(message, transferId);
                if (!acceptedTransfers.add(transferId)) {
                    throw new IllegalArgumentException("duplicate file acceptance: " + transferId);
                }
                outgoingOffers.remove(transferId);
                fire(new CollaborationFileExchangeEvent.OfferAccepted(transferId, message.senderId()));
            } else if (message.type().equals(CollaborationMessageType.FILE_REJECT)) {
                UUID transferId = FileTransferMessages.transferId(message);
                validateResponse(message, transferId);
                TransferHandle handle = outgoingTransfers.remove(transferId);
                if (handle != null) {
                    outgoingOffers.remove(transferId);
                    transfers.cancel(transferId);
                    fire(new CollaborationFileExchangeEvent.OfferRejected(
                            transferId, message.senderId(), message.content()));
                }
            }
        } catch (RuntimeException error) {
            fire(new CollaborationFileExchangeEvent.Error(safeTransferId(message), error));
        }
    }

    private void validateResponse(CollaborationMessage message, UUID transferId) {
        if (!collaboration.localCollaborator().id().equals(message.recipientId())) {
            throw new IllegalArgumentException("file response is addressed to another collaborator");
        }
        FileOffer offer = outgoingOffers.get(transferId);
        if (offer == null || !offer.recipientId().equals(message.senderId())) {
            throw new IllegalArgumentException("file response is from an unauthorized collaborator");
        }
    }

    private void receiveOffer(CollaborationMessage message) {
        FileOffer offer = FileTransferMessages.decodeOffer(message);
        if (!collaboration.localCollaborator().id().equals(offer.recipientId())) {
            throw new IllegalArgumentException("file offer is addressed to another collaborator");
        }
        if (incomingOffers.putIfAbsent(offer.transferId(), offer) != null) {
            throw new IllegalArgumentException("duplicate file offer: " + offer.transferId());
        }
        fire(new CollaborationFileExchangeEvent.OfferReceived(offer));
    }

    private void monitor(TransferHandle handle) {
        handle.completion().whenComplete((path, error) -> {
            outgoingTransfers.remove(handle.transferId(), handle);
            acceptedTransfers.remove(handle.transferId());
            if (error == null) fire(new CollaborationFileExchangeEvent.TransferCompleted(handle.transferId(), path));
            else if (!(unwrap(error) instanceof java.util.concurrent.CancellationException)) {
                fire(new CollaborationFileExchangeEvent.Error(handle.transferId(), unwrap(error)));
            }
        });
    }

    private UUID safeTransferId(CollaborationMessage message) {
        try { return FileTransferMessages.transferId(message); }
        catch (RuntimeException ignored) { return null; }
    }

    private void fire(CollaborationFileExchangeEvent event) {
        for (CollaborationFileExchangeListener listener : listeners) {
            try { listener.onFileExchangeEvent(event); }
            catch (RuntimeException ignored) { }
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void requireOpen() {
        if (closed.get()) throw new IllegalStateException("file exchange is closed");
    }

    /** Removes listeners, cancels pending offers, and closes the owned transfer service. */
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            collaboration.removeListener(collaborationListener);
            incomingOffers.clear();
            outgoingTransfers.values().forEach(TransferHandle::cancel);
            outgoingTransfers.clear();
            outgoingOffers.clear();
            acceptedTransfers.clear();
            transfers.close();
            listeners.clear();
        }
    }
}
