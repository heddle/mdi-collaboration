package edu.cnu.mdi.collaboration.transfer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.model.CollaborationMessageType;

/** Creates and validates file-transfer control-plane collaboration messages. */
public final class FileTransferMessages {
    private static final String TRANSFER_ID = "transferId";
    private FileTransferMessages() {}

    /** Creates a FILE_OFFER control message containing metadata but no file bytes. */
    public static CollaborationMessage offer(FileOffer offer) {
        Objects.requireNonNull(offer, "offer");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(TRANSFER_ID, offer.transferId().toString());
        metadata.put("fileName", offer.fileName());
        metadata.put("size", offer.size());
        if (offer.sha256() != null) metadata.put("sha256", offer.sha256());
        metadata.put("mechanism", offer.mechanism());
        metadata.put("parameters", offer.parameters());
        return CollaborationMessage.create(offer.senderId(), offer.recipientId(), null,
                CollaborationMessageType.FILE_OFFER, null, metadata);
    }

    /** Reconstructs and validates a file offer from an untrusted control message. */
    public static FileOffer decodeOffer(CollaborationMessage message) {
        requireType(message, CollaborationMessageType.FILE_OFFER);
        Map<String, Object> metadata = message.metadata();
        Object size = metadata.get("size");
        if (!(size instanceof Number number)) throw new IllegalArgumentException("file offer size is missing");
        return new FileOffer(uuid(metadata, TRANSFER_ID), message.senderId(),
                Objects.requireNonNull(message.recipientId(), "file offer recipientId"),
                string(metadata, "fileName"), number.longValue(), optionalString(metadata, "sha256"),
                string(metadata, "mechanism"), stringMap(metadata.get("parameters")));
    }

    /** Creates FILE_ACCEPT for a prior offer. */
    public static CollaborationMessage accept(FileOffer offer, UUID acceptingCollaboratorId) {
        return response(offer, acceptingCollaboratorId, CollaborationMessageType.FILE_ACCEPT, null);
    }

    /** Creates FILE_REJECT for a prior offer with an optional non-sensitive reason. */
    public static CollaborationMessage reject(FileOffer offer, UUID rejectingCollaboratorId, String reason) {
        return response(offer, rejectingCollaboratorId, CollaborationMessageType.FILE_REJECT, reason);
    }

    /** Extracts a transfer identifier from accept, reject, or offer metadata. */
    public static UUID transferId(CollaborationMessage message) {
        Objects.requireNonNull(message, "message");
        return uuid(message.metadata(), TRANSFER_ID);
    }

    private static CollaborationMessage response(FileOffer offer, UUID responder,
            CollaborationMessageType type, String reason) {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(responder, "responder");
        if (!offer.recipientId().equals(responder)) {
            throw new IllegalArgumentException("only the offered recipient may respond");
        }
        return CollaborationMessage.create(responder, offer.senderId(), null, type, reason,
                Map.of(TRANSFER_ID, offer.transferId().toString()));
    }

    private static void requireType(CollaborationMessage message, CollaborationMessageType type) {
        Objects.requireNonNull(message, "message");
        if (!type.equals(message.type())) throw new IllegalArgumentException("expected " + type.value());
    }

    private static UUID uuid(Map<String, Object> metadata, String key) {
        try { return UUID.fromString(string(metadata, key)); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException(key + " is invalid", error); }
    }

    private static String string(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " is missing");
        }
        return text;
    }

    private static String optionalString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    private static Map<String, String> stringMap(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("parameters must be an object");
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (!(key instanceof String textKey) || !(item instanceof String textValue)) {
                throw new IllegalArgumentException("parameters must contain strings");
            }
            result.put(textKey, textValue);
        });
        return result;
    }
}
