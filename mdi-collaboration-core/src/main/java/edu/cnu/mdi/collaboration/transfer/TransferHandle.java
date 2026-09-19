package edu.cnu.mdi.collaboration.transfer;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Read-only application handle for observing and cancelling a transfer. */
public interface TransferHandle {
    /** Returns the stable transfer identifier. */
    UUID transferId();
    /** Completes when validated offer metadata is ready. */
    CompletableFuture<FileOffer> offer();
    /** Returns the current lifecycle state. */
    TransferState state();
    /** Returns bytes successfully processed so far. */
    long bytesTransferred();
    /** Completes with the local source or destination path. */
    CompletableFuture<Path> completion();
    /** Requests cancellation and returns whether it changed active state. */
    boolean cancel();
}
