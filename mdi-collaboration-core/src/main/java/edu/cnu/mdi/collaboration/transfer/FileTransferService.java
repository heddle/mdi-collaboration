package edu.cnu.mdi.collaboration.transfer;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import edu.cnu.mdi.collaboration.model.Collaborator;

/** Out-of-band data-plane abstraction; implementations must never use the message broker for bytes. */
public interface FileTransferService extends AutoCloseable {
    /** Prepares a file offer asynchronously. */
    TransferHandle send(Path file, Collaborator recipient);
    /** Accepts an offer and transfers it into the supplied directory asynchronously. */
    TransferHandle receive(FileOffer offer, Path destinationDirectory);
    /** Cancels a locally known transfer. */
    CompletableFuture<Boolean> cancel(UUID transferId);
    /** Releases workers and active transfers. */
    @Override void close();
}
