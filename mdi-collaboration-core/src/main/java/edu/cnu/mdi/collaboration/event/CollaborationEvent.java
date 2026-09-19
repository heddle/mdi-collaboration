package edu.cnu.mdi.collaboration.event;

import edu.cnu.mdi.collaboration.model.CollaborationMessage;

/** A service lifecycle, message, or error notification. */
public sealed interface CollaborationEvent {
    /** Successful connection. */
    record Connected() implements CollaborationEvent {}
    /** Completed disconnection. */
    record Disconnected() implements CollaborationEvent {}
    /** Incoming message. */
    record MessageReceived(CollaborationMessage message) implements CollaborationEvent {}
    /** Recoverable asynchronous failure. */
    record Error(Throwable cause) implements CollaborationEvent {}
}
