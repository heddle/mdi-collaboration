package edu.cnu.mdi.collaboration.rabbitmq;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import edu.cnu.mdi.collaboration.CollaborationService;
import edu.cnu.mdi.collaboration.event.CollaborationEvent;
import edu.cnu.mdi.collaboration.model.Collaborator;

class RabbitMqTransportIT {
    @Test void twoClientsExchangeMessageThroughConfiguredBroker() throws Exception {
        Assumptions.assumeTrue(System.getenv(RabbitMqConfiguration.URI_ENV) != null,
                RabbitMqConfiguration.URI_ENV + " is required for broker integration test");
        RabbitMqConfiguration configuration = RabbitMqConfiguration.fromEnvironment();
        Collaborator alice = new Collaborator(UUID.randomUUID(), "Alice IT", "Test");
        Collaborator bob = new Collaborator(UUID.randomUUID(), "Bob IT", "Test");
        try (var a = service(alice, configuration); var b = service(bob, configuration)) {
            CountDownLatch received = new CountDownLatch(1);
            b.addListener(event -> {
                if (event instanceof CollaborationEvent.MessageReceived message
                        && "broker integration".equals(message.message().content())) received.countDown();
            });
            a.connect().get(10, TimeUnit.SECONDS);
            b.connect().get(10, TimeUnit.SECONDS);
            a.sendChat(bob.id(), "broker integration").get(10, TimeUnit.SECONDS);
            assertTrue(received.await(10, TimeUnit.SECONDS));
        }
    }

    private static CollaborationService service(Collaborator collaborator,
            RabbitMqConfiguration configuration) {
        return new CollaborationService(collaborator,
                new RabbitMqCollaborationTransport(collaborator.id(), configuration));
    }
}
