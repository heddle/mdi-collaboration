package edu.cnu.mdi.collaboration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.cnu.mdi.collaboration.codec.CollaborationMessageCodec;
import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.model.CollaborationMessageType;

class CollaborationMessageCodecTest {
    private final CollaborationMessageCodec codec = new CollaborationMessageCodec();

    @Test void roundTripUsesSimpleVersionedEnvelope() throws Exception {
        CollaborationMessage original = CollaborationMessage.create(UUID.randomUUID(), UUID.randomUUID(), null,
                CollaborationMessageType.CHAT_MESSAGE, "Hello", Map.of("application", "demo"));
        String json = codec.encode(original);
        assertTrue(json.contains("\"protocolVersion\":1"));
        assertTrue(json.contains("\"type\":\"CHAT_MESSAGE\""));
        assertEquals(original, codec.decode(json));
    }

    @Test void missingRequiredFieldIsRejected() {
        String json = """
                {"protocolVersion":1,"messageId":"%s","type":"CHAT_MESSAGE",
                 "timestamp":"2026-01-01T00:00:00Z","metadata":{}}
                """.formatted(UUID.randomUUID());
        assertThrows(CollaborationException.class, () -> codec.decode(json));
    }

    @Test void unknownTypeAndFutureFieldArePreservedOrIgnored() throws Exception {
        UUID sender = UUID.randomUUID();
        String json = """
                {"protocolVersion":1,"messageId":"%s","senderId":"%s",
                 "type":"FUTURE_RESULT","timestamp":"2026-01-01T00:00:00Z",
                 "metadata":{},"futureField":42}
                """.formatted(UUID.randomUUID(), sender);
        CollaborationMessage message = codec.decode(json);
        assertEquals("FUTURE_RESULT", message.type().value());
        assertFalse(message.type().isKnown());
    }

    @Test void malformedJsonIsRejected() {
        assertThrows(CollaborationException.class, () -> codec.decode("not-json"));
    }
}
