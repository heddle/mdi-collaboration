package edu.cnu.mdi.collaboration.event;

/** Receives service events on a transport worker thread, never implicitly on the EDT. */
@FunctionalInterface
public interface CollaborationListener {
    /** Handles one event. Implementations should return promptly. */
    void onCollaborationEvent(CollaborationEvent event);
}
