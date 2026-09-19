package edu.cnu.mdi.collaboration.transport;

/** Receives transport events on a transport-defined thread, never implicitly on Swing's EDT. */
@FunctionalInterface
public interface CollaborationTransportListener {
    /** Handles one event and should return promptly. */
    void onTransportEvent(CollaborationTransportEvent event);
}
