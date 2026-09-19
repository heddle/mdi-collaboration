package edu.cnu.mdi.collaboration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.cnu.mdi.collaboration.codec.CollaborationMessageCodec;
import edu.cnu.mdi.collaboration.model.CollaborationMessage;
import edu.cnu.mdi.collaboration.model.CollaborationMessageType;
import edu.cnu.mdi.collaboration.transfer.FileOffer;
import edu.cnu.mdi.collaboration.transfer.FileTransferMessages;

class FileTransferMessagesTest {
    @Test void offerSurvivesJsonControlPlaneRoundTripWithoutFileBytes() throws Exception {
        FileOffer offer = offer();
        CollaborationMessageCodec codec = new CollaborationMessageCodec();
        CollaborationMessage decodedMessage = codec.decode(codec.encode(FileTransferMessages.offer(offer)));
        assertEquals(offer, FileTransferMessages.decodeOffer(decodedMessage));
        assertNull(decodedMessage.content());
    }

    @Test void onlyIntendedRecipientMayAcceptOrReject() {
        FileOffer offer = offer();
        CollaborationMessage accepted = FileTransferMessages.accept(offer, offer.recipientId());
        assertEquals(CollaborationMessageType.FILE_ACCEPT, accepted.type());
        assertEquals(offer.transferId(), FileTransferMessages.transferId(accepted));
        assertThrows(IllegalArgumentException.class,
                () -> FileTransferMessages.reject(offer, UUID.randomUUID(), "no"));
    }

    @Test void unsafeFilenamesAndChecksumsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> offerWithName("../secret"));
        assertThrows(IllegalArgumentException.class, () -> offerWithName("folder/file.dat"));
        assertThrows(IllegalArgumentException.class, () -> new FileOffer(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "safe.dat", 1, "not-a-checksum",
                "local-registry", Map.of()));
    }

    private static FileOffer offer() { return offerWithName("result.dat"); }

    private static FileOffer offerWithName(String name) {
        return new FileOffer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), name,
                42, "a".repeat(64), "local-registry", Map.of("hint", "test"));
    }
}
