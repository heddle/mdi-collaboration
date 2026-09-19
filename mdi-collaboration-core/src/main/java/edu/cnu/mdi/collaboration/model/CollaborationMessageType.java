package edu.cnu.mdi.collaboration.model;

import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A version-tolerant message type. Unknown wire values are retained rather
 * than rejected, allowing older clients to ignore or route future messages.
 */
public record CollaborationMessageType(String value) {
    public static final CollaborationMessageType HELLO = of("HELLO");
    public static final CollaborationMessageType GOODBYE = of("GOODBYE");
    public static final CollaborationMessageType CHAT_MESSAGE = of("CHAT_MESSAGE");
    public static final CollaborationMessageType FILE_OFFER = of("FILE_OFFER");
    public static final CollaborationMessageType FILE_ACCEPT = of("FILE_ACCEPT");
    public static final CollaborationMessageType FILE_REJECT = of("FILE_REJECT");
    public static final CollaborationMessageType APPLICATION_EVENT = of("APPLICATION_EVENT");

    /** Creates a normalized type token. */
    @JsonCreator
    public static CollaborationMessageType of(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("Invalid message type: " + value);
        }
        return new CollaborationMessageType(normalized);
    }

    /** Returns the wire representation. */
    @JsonValue
    public String wireValue() { return value; }

    /** Validates direct construction used by JSON binding. */
    public CollaborationMessageType {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[A-Z][A-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("Invalid message type: " + value);
        }
    }

    /** Returns whether this is one of the protocol's currently defined types. */
    public boolean isKnown() {
        return this.equals(HELLO) || this.equals(GOODBYE) || this.equals(CHAT_MESSAGE)
                || this.equals(FILE_OFFER) || this.equals(FILE_ACCEPT)
                || this.equals(FILE_REJECT) || this.equals(APPLICATION_EVENT);
    }
}
