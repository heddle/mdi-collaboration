package edu.cnu.mdi.collaboration.mdi;

import java.util.Objects;

import javax.swing.SwingUtilities;

import edu.cnu.mdi.collaboration.event.CollaborationEvent;
import edu.cnu.mdi.collaboration.event.CollaborationListener;

/** Marshals collaboration events onto Swing's event-dispatch thread. */
public final class SwingCollaborationListener implements CollaborationListener {
    private final CollaborationListener delegate;

    /** Creates an EDT-marshalling adapter for a listener. */
    public SwingCollaborationListener(CollaborationListener delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** Dispatches immediately on the EDT or schedules the event otherwise. */
    @Override public void onCollaborationEvent(CollaborationEvent event) {
        Objects.requireNonNull(event, "event");
        if (SwingUtilities.isEventDispatchThread()) delegate.onCollaborationEvent(event);
        else SwingUtilities.invokeLater(() -> delegate.onCollaborationEvent(event));
    }
}
