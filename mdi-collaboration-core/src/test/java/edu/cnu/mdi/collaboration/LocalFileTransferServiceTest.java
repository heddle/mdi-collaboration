package edu.cnu.mdi.collaboration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.cnu.mdi.collaboration.model.Collaborator;
import edu.cnu.mdi.collaboration.transfer.FileOffer;
import edu.cnu.mdi.collaboration.transfer.TransferHandle;
import edu.cnu.mdi.collaboration.transfer.TransferState;
import edu.cnu.mdi.collaboration.transfer.local.LocalFileTransferRegistry;
import edu.cnu.mdi.collaboration.transfer.local.LocalFileTransferService;

class LocalFileTransferServiceTest {
    @TempDir Path temporaryDirectory;

    @Test void copiesFileOutOfBandAndVerifiesChecksum() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob");
        Path source = temporaryDirectory.resolve("measurement.dat");
        byte[] content = "scientific result\n".repeat(1000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(source, content);
        Path inbox = temporaryDirectory.resolve("bob-inbox");
        try (var registry = new LocalFileTransferRegistry();
             var sender = new LocalFileTransferService(alice.id(), registry);
             var receiver = new LocalFileTransferService(bob.id(), registry)) {
            TransferHandle outgoing = sender.send(source, bob);
            FileOffer offer = outgoing.offer().get(2, TimeUnit.SECONDS);
            assertEquals(LocalFileTransferService.MECHANISM, offer.mechanism());
            assertFalse(offer.parameters().containsKey("path"));
            TransferHandle incoming = receiver.receive(offer, inbox);
            Path received = incoming.completion().get(2, TimeUnit.SECONDS);
            outgoing.completion().get(2, TimeUnit.SECONDS);
            assertArrayEquals(content, Files.readAllBytes(received));
            assertEquals(TransferState.COMPLETED, outgoing.state());
            assertEquals(TransferState.COMPLETED, incoming.state());
            assertEquals(content.length, incoming.bytesTransferred());
        }
    }

    @Test void cancelledOfferCannotBeReceived() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob");
        Path source = Files.writeString(temporaryDirectory.resolve("cancel.dat"), "cancel me");
        try (var registry = new LocalFileTransferRegistry();
             var sender = new LocalFileTransferService(alice.id(), registry);
             var receiver = new LocalFileTransferService(bob.id(), registry)) {
            TransferHandle outgoing = sender.send(source, bob);
            FileOffer offer = outgoing.offer().get(2, TimeUnit.SECONDS);
            assertTrue(outgoing.cancel());
            TransferHandle incoming = receiver.receive(offer, temporaryDirectory.resolve("inbox"));
            assertThrows(ExecutionException.class,
                    () -> incoming.completion().get(2, TimeUnit.SECONDS));
            assertEquals(TransferState.FAILED, incoming.state());
        }
    }

    @Test void existingDestinationIsNotOverwrittenAndCloseIsIdempotent() throws Exception {
        Collaborator alice = person("Alice"), bob = person("Bob");
        Path source = Files.writeString(temporaryDirectory.resolve("same.dat"), "new");
        Path inbox = Files.createDirectory(temporaryDirectory.resolve("inbox"));
        Path existing = Files.writeString(inbox.resolve("same.dat"), "keep");
        var registry = new LocalFileTransferRegistry();
        var sender = new LocalFileTransferService(alice.id(), registry);
        var receiver = new LocalFileTransferService(bob.id(), registry);
        FileOffer offer = sender.send(source, bob).offer().get(2, TimeUnit.SECONDS);
        TransferHandle incoming = receiver.receive(offer, inbox);
        assertThrows(ExecutionException.class,
                () -> incoming.completion().get(2, TimeUnit.SECONDS));
        assertEquals("keep", Files.readString(existing));
        sender.close(); sender.close(); receiver.close(); receiver.close(); registry.close(); registry.close();
    }

    private static Collaborator person(String name) {
        return new Collaborator(UUID.randomUUID(), name, "CNU");
    }
}
