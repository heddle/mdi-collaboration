package edu.cnu.mdi.collaboration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.cnu.mdi.collaboration.model.Collaborator;
import edu.cnu.mdi.collaboration.transfer.CollaborationFileExchange;
import edu.cnu.mdi.collaboration.transfer.CollaborationFileExchangeEvent;
import edu.cnu.mdi.collaboration.transfer.FileOffer;
import edu.cnu.mdi.collaboration.transfer.TransferHandle;
import edu.cnu.mdi.collaboration.transfer.TransferState;
import edu.cnu.mdi.collaboration.transfer.local.LocalFileTransferRegistry;
import edu.cnu.mdi.collaboration.transfer.local.LocalFileTransferService;
import edu.cnu.mdi.collaboration.transport.memory.InMemoryCollaborationBus;
import edu.cnu.mdi.collaboration.transport.memory.InMemoryCollaborationTransport;

class CollaborationFileExchangeTest {
    @TempDir Path temporaryDirectory;

    @Test void offerAcceptAndTransferCompleteEndToEnd() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob");
        try (Fixture fixture = new Fixture(alice, bob)) {
            Path source = Files.writeString(temporaryDirectory.resolve("result.txt"), "important result");
            AtomicReference<FileOffer> receivedOffer = new AtomicReference<>();
            CountDownLatch offered = new CountDownLatch(1);
            fixture.bobExchange.addListener(event -> {
                if (event instanceof CollaborationFileExchangeEvent.OfferReceived received) {
                    receivedOffer.set(received.offer()); offered.countDown();
                }
            });
            TransferHandle outgoing = fixture.aliceExchange.offer(source, bob);
            assertTrue(offered.await(2, TimeUnit.SECONDS));
            TransferHandle incoming = fixture.bobExchange.accept(receivedOffer.get().transferId(),
                    temporaryDirectory.resolve("bob-inbox"));
            Path destination = incoming.completion().get(2, TimeUnit.SECONDS);
            outgoing.completion().get(2, TimeUnit.SECONDS);
            assertEquals("important result", Files.readString(destination));
            assertEquals(TransferState.COMPLETED, outgoing.state());
            assertTrue(fixture.bobExchange.pendingOffers().isEmpty());
        }
    }

    @Test void rejectionCancelsOutgoingTransfer() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob");
        try (Fixture fixture = new Fixture(alice, bob)) {
            Path source = Files.writeString(temporaryDirectory.resolve("reject.txt"), "no thanks");
            AtomicReference<FileOffer> receivedOffer = new AtomicReference<>();
            CountDownLatch offered = new CountDownLatch(1);
            CountDownLatch rejected = new CountDownLatch(1);
            fixture.bobExchange.addListener(event -> {
                if (event instanceof CollaborationFileExchangeEvent.OfferReceived received) {
                    receivedOffer.set(received.offer()); offered.countDown();
                }
            });
            fixture.aliceExchange.addListener(event -> {
                if (event instanceof CollaborationFileExchangeEvent.OfferRejected) rejected.countDown();
            });
            TransferHandle outgoing = fixture.aliceExchange.offer(source, bob);
            assertTrue(offered.await(2, TimeUnit.SECONDS));
            fixture.bobExchange.reject(receivedOffer.get().transferId(), "not needed");
            assertTrue(rejected.await(2, TimeUnit.SECONDS));
            assertEquals(TransferState.CANCELLED, outgoing.state());
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.bobExchange.reject(receivedOffer.get().transferId(), "again"));
        }
    }

    private static Collaborator person(String name) {
        return new Collaborator(UUID.randomUUID(), name, "CNU");
    }

    private static final class Fixture implements AutoCloseable {
        final InMemoryCollaborationBus bus = new InMemoryCollaborationBus();
        final LocalFileTransferRegistry registry = new LocalFileTransferRegistry();
        final CollaborationService aliceService;
        final CollaborationService bobService;
        final CollaborationFileExchange aliceExchange;
        final CollaborationFileExchange bobExchange;

        Fixture(Collaborator alice, Collaborator bob) {
            aliceService = service(alice, bus);
            bobService = service(bob, bus);
            aliceExchange = new CollaborationFileExchange(aliceService,
                    new LocalFileTransferService(alice.id(), registry));
            bobExchange = new CollaborationFileExchange(bobService,
                    new LocalFileTransferService(bob.id(), registry));
            aliceService.connect().join();
            bobService.connect().join();
        }

        @Override public void close() {
            aliceExchange.close(); bobExchange.close();
            aliceService.close(); bobService.close();
            registry.close(); bus.close();
        }

        private static CollaborationService service(Collaborator collaborator,
                InMemoryCollaborationBus bus) {
            return new CollaborationService(collaborator,
                    new InMemoryCollaborationTransport(collaborator.id(), bus));
        }
    }
}
