package edu.cnu.mdi.collaboration.transfer;

/** Receives file-exchange events on collaboration or transfer worker threads. */
@FunctionalInterface
public interface CollaborationFileExchangeListener {
    /** Handles one event and should return promptly. */
    void onFileExchangeEvent(CollaborationFileExchangeEvent event);
}
