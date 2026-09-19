package edu.cnu.mdi.collaboration.transport;

import java.util.concurrent.CompletableFuture;
import edu.cnu.mdi.collaboration.model.CollaborationMessage;

/** Swing-free asynchronous transport boundary. */
public interface CollaborationTransport extends AutoCloseable {
    /** Connects without blocking the calling thread. */
    CompletableFuture<Void> connect();
    /** Disconnects without blocking the calling thread. */
    CompletableFuture<Void> disconnect();
    /** Returns the current connection state. */
    boolean isConnected();
    /** Publishes a message asynchronously. */
    CompletableFuture<Void> publish(CollaborationMessage message);
    /** Adds a lifecycle, message, and error listener. */
    void addListener(CollaborationTransportListener listener);
    /** Removes a transport listener. */
    void removeListener(CollaborationTransportListener listener);
    /** Releases transport resources. */
    @Override default void close() { disconnect().join(); }
}
