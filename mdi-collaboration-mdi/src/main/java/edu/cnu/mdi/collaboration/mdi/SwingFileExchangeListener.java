package edu.cnu.mdi.collaboration.mdi;

import java.util.Objects;

import javax.swing.SwingUtilities;

import edu.cnu.mdi.collaboration.transfer.CollaborationFileExchangeEvent;
import edu.cnu.mdi.collaboration.transfer.CollaborationFileExchangeListener;

/** Marshals file-exchange notifications onto Swing's event-dispatch thread. */
public final class SwingFileExchangeListener implements CollaborationFileExchangeListener {
    private final CollaborationFileExchangeListener delegate;

    /** Creates an EDT-marshalling adapter. */
    public SwingFileExchangeListener(CollaborationFileExchangeListener delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override public void onFileExchangeEvent(CollaborationFileExchangeEvent event) {
        Objects.requireNonNull(event, "event");
        if (SwingUtilities.isEventDispatchThread()) delegate.onFileExchangeEvent(event);
        else SwingUtilities.invokeLater(() -> delegate.onFileExchangeEvent(event));
    }
}
