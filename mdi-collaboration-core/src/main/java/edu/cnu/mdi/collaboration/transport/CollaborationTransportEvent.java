package edu.cnu.mdi.collaboration.transport;

import java.util.Objects;

import edu.cnu.mdi.collaboration.model.CollaborationMessage;

/** A transport lifecycle, message, or asynchronous-error notification. */
public sealed interface CollaborationTransportEvent {
    /** The transport established its connection and subscriptions. */
    record Connected() implements CollaborationTransportEvent {}
    /** The transport disconnected and will deliver no further messages. */
    record Disconnected() implements CollaborationTransportEvent {}
    /** A validated incoming message. */
    record MessageReceived(CollaborationMessage message) implements CollaborationTransportEvent {
        /** Validates the event. */
        public MessageReceived { Objects.requireNonNull(message, "message"); }
    }
    /** A recoverable asynchronous transport or wire-format failure. */
    record Error(Throwable cause) implements CollaborationTransportEvent {
        /** Validates the event. */
        public Error { Objects.requireNonNull(cause, "cause"); }
    }
}
