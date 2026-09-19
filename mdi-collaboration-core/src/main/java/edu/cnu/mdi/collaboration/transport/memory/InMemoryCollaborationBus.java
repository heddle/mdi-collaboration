package edu.cnu.mdi.collaboration.transport.memory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import edu.cnu.mdi.collaboration.model.CollaborationMessage;

/** An explicit, non-global in-JVM bus shared by in-memory transports. */
public final class InMemoryCollaborationBus implements AutoCloseable {
    private final Set<InMemoryCollaborationTransport> transports = ConcurrentHashMap.newKeySet();

    void attach(InMemoryCollaborationTransport transport) { transports.add(transport); }
    void detach(InMemoryCollaborationTransport transport) { transports.remove(transport); }

    void publish(CollaborationMessage message) {
        for (InMemoryCollaborationTransport transport : transports) {
            if (isAddressedTo(transport.localCollaboratorId(), message)) transport.deliver(message);
        }
    }

    private boolean isAddressedTo(UUID localId, CollaborationMessage message) {
        return message.recipientId() == null || message.recipientId().equals(localId);
    }

    /** Disconnects all attached transports. */
    @Override public void close() {
        for (InMemoryCollaborationTransport transport : Set.copyOf(transports)) transport.disconnect().join();
    }
}
