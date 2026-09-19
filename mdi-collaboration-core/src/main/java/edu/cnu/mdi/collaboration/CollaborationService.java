package edu.cnu.mdi.collaboration;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.cnu.mdi.collaboration.event.CollaborationEvent;
import edu.cnu.mdi.collaboration.event.CollaborationListener;
import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.model.CollaborationMessageType;
import edu.cnu.mdi.collaboration.model.Collaborator;
import edu.cnu.mdi.collaboration.transport.CollaborationTransport;
import edu.cnu.mdi.collaboration.transport.CollaborationTransportEvent;

/** High-level, transport-neutral collaboration API owned by an application. */
public final class CollaborationService implements AutoCloseable {
    private final Collaborator localCollaborator;
    private final CollaborationTransport transport;
    private final CopyOnWriteArrayList<CollaborationListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    /** Creates a service and attaches its transport listener. */
    public CollaborationService(Collaborator localCollaborator, CollaborationTransport transport) {
        this.localCollaborator = Objects.requireNonNull(localCollaborator, "localCollaborator");
        this.transport = Objects.requireNonNull(transport, "transport");
        transport.addListener(this::receive);
    }

    /** Returns the participant represented by this service. */
    public Collaborator localCollaborator() { return localCollaborator; }
    /** Returns whether the underlying transport is connected. */
    public boolean isConnected() { return transport.isConnected(); }

    /** Connects asynchronously. */
    public CompletableFuture<Void> connect() {
        if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("service is closed"));
        return transport.connect();
    }

    /** Disconnects asynchronously and idempotently. */
    public CompletableFuture<Void> disconnect() {
        return transport.disconnect();
    }

    /** Sends an already constructed message after enforcing local sender identity. */
    public CompletableFuture<Void> send(CollaborationMessage message) {
        Objects.requireNonNull(message, "message");
        if (!localCollaborator.id().equals(message.senderId())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("message sender is not local collaborator"));
        }
        return transport.publish(message).whenComplete((unused, error) -> {
            if (error != null) fire(new CollaborationEvent.Error(error));
        });
    }

    /** Sends a small direct text message. */
    public CompletableFuture<Void> sendChat(UUID recipientId, String content) {
        Objects.requireNonNull(recipientId, "recipientId");
        if (content == null || content.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("content must not be blank"));
        }
        return send(CollaborationMessage.create(localCollaborator.id(), recipientId, null,
                CollaborationMessageType.CHAT_MESSAGE, content, Map.of()));
    }

    /** Adds a removable listener. */
    public void addListener(CollaborationListener listener) { listeners.add(Objects.requireNonNull(listener)); }
    /** Removes a listener. */
    public void removeListener(CollaborationListener listener) { listeners.remove(listener); }

    private void receive(CollaborationTransportEvent event) {
        if (event instanceof CollaborationTransportEvent.Connected) {
            fire(new CollaborationEvent.Connected());
        } else if (event instanceof CollaborationTransportEvent.Disconnected) {
            fire(new CollaborationEvent.Disconnected());
        } else if (event instanceof CollaborationTransportEvent.MessageReceived received) {
            fire(new CollaborationEvent.MessageReceived(received.message()));
        } else if (event instanceof CollaborationTransportEvent.Error error) {
            fire(new CollaborationEvent.Error(error.cause()));
        }
    }

    private void fire(CollaborationEvent event) {
        for (CollaborationListener listener : listeners) {
            try { listener.onCollaborationEvent(event); }
            catch (RuntimeException ignored) { /* one faulty client must not stop delivery */ }
        }
    }

    /** Closes this service and its transport; repeated calls are harmless. */
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            transport.close();
            listeners.clear();
        }
    }
}
