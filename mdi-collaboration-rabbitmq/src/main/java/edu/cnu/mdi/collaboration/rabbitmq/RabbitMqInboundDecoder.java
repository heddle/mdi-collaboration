package edu.cnu.mdi.collaboration.rabbitmq;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

import edu.cnu.mdi.collaboration.codec.CollaborationMessageCodec;
import edu.cnu.mdi.collaboration.transport.CollaborationTransportEvent;

/** Converts untrusted broker bytes into transport events without throwing. */
final class RabbitMqInboundDecoder {
    private final CollaborationMessageCodec codec;
    private final Consumer<CollaborationTransportEvent> eventSink;

    RabbitMqInboundDecoder(CollaborationMessageCodec codec,
            Consumer<CollaborationTransportEvent> eventSink) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
    }

    void accept(byte[] body) {
        try {
            String json = new String(Objects.requireNonNull(body, "body"), StandardCharsets.UTF_8);
            eventSink.accept(new CollaborationTransportEvent.MessageReceived(codec.decode(json)));
        } catch (RuntimeException | edu.cnu.mdi.collaboration.CollaborationException error) {
            eventSink.accept(new CollaborationTransportEvent.Error(error));
        }
    }
}
