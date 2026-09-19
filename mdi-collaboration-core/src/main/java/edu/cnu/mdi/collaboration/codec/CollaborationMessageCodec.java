package edu.cnu.mdi.collaboration.codec;

import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import edu.cnu.mdi.collaboration.CollaborationException;
import edu.cnu.mdi.collaboration.model.CollaborationMessage;

/** Encodes and validates the stable JSON message envelope. */
public final class CollaborationMessageCodec {
    private final ObjectMapper mapper;

    /** Creates a codec that accepts unknown future JSON fields. */
    public CollaborationMessageCodec() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** Serializes a message to JSON. */
    public String encode(CollaborationMessage message) throws CollaborationException {
        Objects.requireNonNull(message, "message");
        try {
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new CollaborationException("Could not encode collaboration message", e);
        }
    }

    /** Deserializes untrusted JSON, applying the model's required-field validation. */
    public CollaborationMessage decode(String json) throws CollaborationException {
        Objects.requireNonNull(json, "json");
        try {
            return mapper.readValue(json, CollaborationMessage.class);
        } catch (IOException | RuntimeException e) {
            throw new CollaborationException("Invalid collaboration message", e);
        }
    }
}
