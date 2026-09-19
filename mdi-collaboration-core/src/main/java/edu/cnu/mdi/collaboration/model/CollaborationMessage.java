package edu.cnu.mdi.collaboration.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A transport-neutral, versioned message envelope for small control payloads. */
public record CollaborationMessage(
        int protocolVersion,
        UUID messageId,
        UUID senderId,
        UUID recipientId,
        UUID projectId,
        Instant timestamp,
        CollaborationMessageType type,
        Map<String, Object> metadata,
        String content) {

    /** Current protocol version emitted by this library. */
    public static final int CURRENT_PROTOCOL_VERSION = 1;

    /** Creates and validates a message. */
    public CollaborationMessage {
        if (protocolVersion < 1) throw new IllegalArgumentException("protocolVersion must be positive");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(type, "type");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Creates a new message with generated identity and timestamp. */
    public static CollaborationMessage create(UUID senderId, UUID recipientId, UUID projectId,
            CollaborationMessageType type, String content, Map<String, Object> metadata) {
        return new CollaborationMessage(CURRENT_PROTOCOL_VERSION, UUID.randomUUID(), senderId,
                recipientId, projectId, Instant.now(), type, metadata, content);
    }
}
