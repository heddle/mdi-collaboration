package edu.cnu.mdi.collaboration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import edu.cnu.mdi.collaboration.event.CollaborationEvent;
import edu.cnu.mdi.collaboration.event.CollaborationListener;
import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.model.CollaborationMessageType;
import edu.cnu.mdi.collaboration.model.Collaborator;
import edu.cnu.mdi.collaboration.transport.memory.InMemoryCollaborationBus;
import edu.cnu.mdi.collaboration.transport.memory.InMemoryCollaborationTransport;

class InMemoryCollaborationTest {
    private static Collaborator person(String name) {
        return new Collaborator(UUID.randomUUID(), name, "CNU");
    }

    @Test void twoServicesExchangeDirectMessagesAndRemovedListenerStopsSeeingThem() throws Exception {
        Collaborator alice = person("Alice");
        Collaborator bob = person("Bob");
        try (var bus = new InMemoryCollaborationBus();
             var a = service(alice, bus); var b = service(bob, bus)) {
            CountDownLatch received = new CountDownLatch(1);
            AtomicInteger count = new AtomicInteger();
            CollaborationListener listener = event -> {
                if (event instanceof CollaborationEvent.MessageReceived message
                        && "hello".equals(message.message().content())) {
                    count.incrementAndGet(); received.countDown();
                }
            };
            b.addListener(listener);
            a.connect().join(); b.connect().join();
            a.sendChat(bob.id(), "hello").join();
            assertTrue(received.await(2, TimeUnit.SECONDS));
            b.removeListener(listener);
            a.sendChat(bob.id(), "hello").join();
            Thread.sleep(100);
            assertEquals(1, count.get());
        }
    }

    @Test void broadcastAndProjectMessagesReachAllConnectedClients() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob"), cara = person("Cara");
        try (var bus = new InMemoryCollaborationBus(); var a = service(alice, bus);
             var b = service(bob, bus); var c = service(cara, bus)) {
            CountDownLatch received = new CountDownLatch(2);
            b.addListener(e -> { if (e instanceof CollaborationEvent.MessageReceived) received.countDown(); });
            c.addListener(e -> { if (e instanceof CollaborationEvent.MessageReceived) received.countDown(); });
            a.connect().join(); b.connect().join(); c.connect().join();
            a.send(CollaborationMessage.create(alice.id(), null, UUID.randomUUID(),
                    CollaborationMessageType.APPLICATION_EVENT, "result ready", Map.of())).join();
            assertTrue(received.await(2, TimeUnit.SECONDS));
        }
    }

    @Test void disconnectedRecipientGetsNoDeliveryAndCloseIsIdempotent() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob");
        var bus = new InMemoryCollaborationBus();
        var a = service(alice, bus); var b = service(bob, bus);
        AtomicInteger count = new AtomicInteger();
        b.addListener(e -> { if (e instanceof CollaborationEvent.MessageReceived) count.incrementAndGet(); });
        a.connect().join(); b.connect().join(); b.disconnect().join();
        a.sendChat(bob.id(), "missed").join();
        Thread.sleep(100);
        assertEquals(0, count.get());
        a.close(); a.close(); b.close(); b.close(); bus.close(); bus.close();
    }

    @Test void concurrentDeliveryDoesNotLoseMessages() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob");
        int total = 200;
        try (var bus = new InMemoryCollaborationBus(); var a = service(alice, bus); var b = service(bob, bus)) {
            CountDownLatch received = new CountDownLatch(total);
            b.addListener(e -> { if (e instanceof CollaborationEvent.MessageReceived) received.countDown(); });
            a.connect().join(); b.connect().join();
            var futures = java.util.stream.IntStream.range(0, total).parallel()
                    .mapToObj(i -> a.sendChat(bob.id(), "message " + i)).toArray(java.util.concurrent.CompletableFuture[]::new);
            java.util.concurrent.CompletableFuture.allOf(futures).join();
            assertTrue(received.await(3, TimeUnit.SECONDS));
        }
    }

    @Test void oneFailingListenerDoesNotKillReceiver() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob");
        try (var bus = new InMemoryCollaborationBus(); var a = service(alice, bus); var b = service(bob, bus)) {
            CountDownLatch received = new CountDownLatch(1);
            b.addListener(e -> { throw new IllegalStateException("client bug"); });
            b.addListener(e -> { if (e instanceof CollaborationEvent.MessageReceived) received.countDown(); });
            a.connect().join(); b.connect().join(); a.sendChat(bob.id(), "safe").join();
            assertTrue(received.await(2, TimeUnit.SECONDS));
        }
    }

    private static CollaborationService service(Collaborator collaborator, InMemoryCollaborationBus bus) {
        return new CollaborationService(collaborator, new InMemoryCollaborationTransport(collaborator.id(), bus));
    }
}
