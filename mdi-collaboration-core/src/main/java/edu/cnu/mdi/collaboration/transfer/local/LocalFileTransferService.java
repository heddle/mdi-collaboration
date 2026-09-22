package edu.cnu.mdi.collaboration.transfer.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.cnu.mdi.collaboration.model.Collaborator;
import edu.cnu.mdi.collaboration.transfer.FileOffer;
import edu.cnu.mdi.collaboration.transfer.FileTransferService;
import edu.cnu.mdi.collaboration.transfer.TransferHandle;
import edu.cnu.mdi.collaboration.transfer.TransferState;

/** Same-machine transfer implementation using opaque registry entries rather than remote paths. */
public final class LocalFileTransferService implements FileTransferService {
    /** Mechanism token used in local file offers. */
    public static final String MECHANISM = "local-registry";

    private final UUID localCollaboratorId;
    private final LocalFileTransferRegistry registry;
    private final ConcurrentMap<UUID, LocalTransferHandle> handles = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService executor;

    /** Creates a service for one participant on an explicitly shared registry. */
    public LocalFileTransferService(UUID localCollaboratorId, LocalFileTransferRegistry registry) {
        this.localCollaboratorId = Objects.requireNonNull(localCollaboratorId, "localCollaboratorId");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mdi-local-file-transfer-" + localCollaboratorId);
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override public TransferHandle send(Path file, Collaborator recipient) {
        requireOpen();
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(recipient, "recipient");
        UUID transferId = UUID.randomUUID();
        LocalTransferHandle handle = new LocalTransferHandle(transferId);
        handles.put(transferId, handle);
        handle.completion().whenComplete((path, error) -> handles.remove(transferId, handle));
        handle.cancelWith(() -> cancelNow(transferId));
        executor.execute(() -> prepare(file, recipient, handle));
        return handle;
    }

    private void prepare(Path file, Collaborator recipient, LocalTransferHandle handle) {
        try {
            Path source = file.toAbsolutePath().normalize();
            if (!Files.isRegularFile(source)) throw new IllegalArgumentException("source must be a regular file");
            String fileName = FileOffer.validateFileName(source.getFileName().toString());
            FileOffer offer = new FileOffer(handle.transferId(), localCollaboratorId, recipient.id(),
                    fileName, Files.size(source), sha256(source), MECHANISM, Map.of());
            if (handle.state() == TransferState.CANCELLED) return;
            registry.register(offer, source, handle);
            handle.offered(offer);
        } catch (Throwable error) {
            handles.remove(handle.transferId());
            handle.failed(error);
        }
    }

    @Override public TransferHandle receive(FileOffer offer, Path destinationDirectory) {
        requireOpen();
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(destinationDirectory, "destinationDirectory");
        if (!localCollaboratorId.equals(offer.recipientId())) {
            throw new IllegalArgumentException("offer is addressed to another collaborator");
        }
        if (!MECHANISM.equals(offer.mechanism())) {
            throw new IllegalArgumentException("unsupported transfer mechanism: " + offer.mechanism());
        }
        LocalTransferHandle handle = new LocalTransferHandle(offer.transferId());
        handle.offered(offer);
        handles.put(offer.transferId(), handle);
        handle.completion().whenComplete((path, error) -> handles.remove(offer.transferId(), handle));
        handle.cancelWith(() -> cancelNow(offer.transferId()));
        executor.execute(() -> copy(offer, destinationDirectory, handle));
        return handle;
    }

    private void copy(FileOffer offer, Path destinationDirectory, LocalTransferHandle receiver) {
        LocalFileTransferRegistry.Entry entry = null;
        try {
            entry = registry.claim(offer);
            LocalTransferHandle sender = entry.senderHandle();
            sender.transferring();
            receiver.transferring();
            Path directory = destinationDirectory.toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path destination = directory.resolve(offer.fileName()).normalize();
            if (!destination.startsWith(directory)) throw new SecurityException("unsafe destination filename");
            MessageDigest digest = digest();
            long copied = copyBytes(entry.source(), destination, digest, sender, receiver);
            if (copied != offer.size()) throw new IOException("transferred size does not match offer");
            String checksum = HexFormat.of().formatHex(digest.digest());
            if (offer.sha256() != null && !offer.sha256().equals(checksum)) {
                Files.deleteIfExists(destination);
                throw new IOException("transferred checksum does not match offer");
            }
            sender.completed(entry.source());
            receiver.completed(destination);
        } catch (Throwable error) {
            receiver.failed(error);
            if (entry != null) entry.senderHandle().failed(error);
        } finally {
            registry.complete(offer.transferId());
            handles.remove(offer.transferId());
        }
    }

    private static long copyBytes(Path source, Path destination, MessageDigest digest,
            LocalTransferHandle sender, LocalTransferHandle receiver) throws IOException {
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(source)) {
            boolean destinationCreated = false;
            try (OutputStream output = Files.newOutputStream(destination,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                destinationCreated = true;
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (sender.state() == TransferState.CANCELLED || receiver.state() == TransferState.CANCELLED) {
                        throw new java.util.concurrent.CancellationException("transfer cancelled");
                    }
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    total += count;
                    sender.addBytes(count);
                    receiver.addBytes(count);
                }
            } catch (IOException | RuntimeException | Error error) {
                if (destinationCreated) Files.deleteIfExists(destination);
                throw error;
            }
        }
        return total;
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    @Override public CompletableFuture<Boolean> cancel(UUID transferId) {
        Objects.requireNonNull(transferId, "transferId");
        return CompletableFuture.completedFuture(cancelNow(transferId));
    }

    private boolean cancelNow(UUID transferId) {
        LocalTransferHandle handle = handles.remove(transferId);
        boolean local = handle != null && handle.cancelLocally();
        boolean registered = registry.cancel(transferId);
        return local || registered;
    }

    private void requireOpen() {
        if (closed.get()) throw new IllegalStateException("file transfer service is closed");
    }

    /** Cancels outstanding work and stops the daemon worker. */
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            handles.keySet().forEach(this::cancelNow);
            executor.shutdownNow();
        }
    }
}
