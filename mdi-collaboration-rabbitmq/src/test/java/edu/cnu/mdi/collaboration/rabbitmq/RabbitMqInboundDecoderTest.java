package edu.cnu.mdi.collaboration.rabbitmq;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.cnu.mdi.collaboration.codec.CollaborationMessageCodec;
import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.model.CollaborationMessageType;
import edu.cnu.mdi.collaboration.transport.CollaborationTransportEvent;

class RabbitMqInboundDecoderTest {
    @Test void malformedBodyBecomesErrorAndNextValidBodyStillArrives() throws Exception {
        List<CollaborationTransportEvent> events = new ArrayList<>();
        CollaborationMessageCodec codec = new CollaborationMessageCodec();
        RabbitMqInboundDecoder decoder = new RabbitMqInboundDecoder(codec, events::add);
        decoder.accept("not-json".getBytes(StandardCharsets.UTF_8));
        CollaborationMessage message = CollaborationMessage.create(UUID.randomUUID(), null, null,
                CollaborationMessageType.HELLO, "hi", Map.of());
        decoder.accept(codec.encode(message).getBytes(StandardCharsets.UTF_8));
        assertInstanceOf(CollaborationTransportEvent.Error.class, events.get(0));
        assertEquals(message, ((CollaborationTransportEvent.MessageReceived) events.get(1)).message());
    }
}
