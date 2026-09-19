package edu.cnu.mdi.collaboration.transport.memory;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.transport.CollaborationTransport;
import edu.cnu.mdi.collaboration.transport.CollaborationTransportEvent;
import edu.cnu.mdi.collaboration.transport.CollaborationTransportListener;

/** Deterministic in-JVM transport for tests and two-client demonstrations. */
public final class InMemoryCollaborationTransport implements CollaborationTransport {
    private final UUID localCollaboratorId;
    private final InMemoryCollaborationBus bus;
    private final CopyOnWriteArrayList<CollaborationTransportListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean connected = new AtomicBoolean();
    private final ExecutorService deliveryExecutor;

    /** Creates a transport bound to one local participant and bus. */
    public InMemoryCollaborationTransport(UUID localCollaboratorId, InMemoryCollaborationBus bus) {
        this.localCollaboratorId = Objects.requireNonNull(localCollaboratorId, "localCollaboratorId");
        this.bus = Objects.requireNonNull(bus, "bus");
        deliveryExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mdi-collaboration-memory-" + localCollaboratorId);
            thread.setDaemon(true);
            return thread;
        });
    }

    UUID localCollaboratorId() { return localCollaboratorId; }

    @Override public CompletableFuture<Void> connect() {
        if (deliveryExecutor.isShutdown()) return CompletableFuture.failedFuture(new IllegalStateException("transport is closed"));
        if (connected.compareAndSet(false, true)) {
            bus.attach(this);
            fire(new CollaborationTransportEvent.Connected());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletableFuture<Void> disconnect() {
        if (connected.compareAndSet(true, false)) {
            bus.detach(this);
            fire(new CollaborationTransportEvent.Disconnected());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override public boolean isConnected() { return connected.get(); }

    @Override public CompletableFuture<Void> publish(CollaborationMessage message) {
        Objects.requireNonNull(message, "message");
        if (!connected.get()) return CompletableFuture.failedFuture(new IllegalStateException("transport is disconnected"));
        bus.publish(message);
        return CompletableFuture.completedFuture(null);
    }

    @Override public void addListener(CollaborationTransportListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override public void removeListener(CollaborationTransportListener listener) { listeners.remove(listener); }

    void deliver(CollaborationMessage message) {
        if (!connected.get()) return;
        try {
            deliveryExecutor.execute(() -> {
                if (!connected.get()) return;
                fire(new CollaborationTransportEvent.MessageReceived(message));
            });
        } catch (RejectedExecutionException ignored) {
            // A concurrent close won the race; dropping the message is correct.
        }
    }

    private void fire(CollaborationTransportEvent event) {
        for (CollaborationTransportListener listener : listeners) {
            try { listener.onTransportEvent(event); }
            catch (RuntimeException ignored) { /* isolate listeners */ }
        }
    }

    /** Permanently releases the daemon delivery executor. */
    @Override public void close() {
        disconnect().join();
        deliveryExecutor.shutdownNow();
        listeners.clear();
    }
}
