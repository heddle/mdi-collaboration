package edu.cnu.mdi.collaboration.rabbitmq;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.model.CollaborationMessageType;

class RabbitMqRoutingTest {
    @Test void directProjectAndBroadcastKeysAreStable() {
        UUID sender = UUID.randomUUID(), recipient = UUID.randomUUID(), project = UUID.randomUUID();
        assertEquals("user." + recipient, RabbitMqRouting.forMessage(message(sender, recipient, project)));
        assertEquals("project." + project, RabbitMqRouting.forMessage(message(sender, null, project)));
        assertEquals("broadcast", RabbitMqRouting.forMessage(message(sender, null, null)));
    }

    private static CollaborationMessage message(UUID sender, UUID recipient, UUID project) {
        return CollaborationMessage.create(sender, recipient, project,
                CollaborationMessageType.APPLICATION_EVENT, "test", Map.of());
    }
}
