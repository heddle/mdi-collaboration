package edu.cnu.mdi.collaboration.transfer.local;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import edu.cnu.mdi.collaboration.transfer.FileOffer;
import edu.cnu.mdi.collaboration.transfer.TransferHandle;
import edu.cnu.mdi.collaboration.transfer.TransferState;

final class LocalTransferHandle implements TransferHandle {
    private final UUID transferId;
    private final CompletableFuture<FileOffer> offer = new CompletableFuture<>();
    private final CompletableFuture<Path> completion = new CompletableFuture<>();
    private final AtomicReference<TransferState> state = new AtomicReference<>(TransferState.PREPARING);
    private final AtomicLong bytesTransferred = new AtomicLong();
    private volatile BooleanSupplier cancelAction = this::cancelLocally;

    LocalTransferHandle(UUID transferId) { this.transferId = transferId; }

    @Override public UUID transferId() { return transferId; }
    @Override public CompletableFuture<FileOffer> offer() { return offer; }
    @Override public TransferState state() { return state.get(); }
    @Override public long bytesTransferred() { return bytesTransferred.get(); }
    @Override public CompletableFuture<Path> completion() { return completion; }
    @Override public boolean cancel() { return cancelAction.getAsBoolean(); }

    void cancelWith(BooleanSupplier action) { cancelAction = action; }
    void offered(FileOffer value) { offer.complete(value); state.set(TransferState.OFFERED); }
    void transferring() { state.set(TransferState.TRANSFERRING); }
    void addBytes(long count) { bytesTransferred.addAndGet(count); }

    void completed(Path path) {
        state.set(TransferState.COMPLETED);
        completion.complete(path);
    }

    void failed(Throwable error) {
        if (state.get() == TransferState.CANCELLED) return;
        state.set(TransferState.FAILED);
        offer.completeExceptionally(error);
        completion.completeExceptionally(error);
    }

    boolean cancelLocally() {
        while (true) {
            TransferState current = state.get();
            if (current == TransferState.COMPLETED || current == TransferState.CANCELLED
                    || current == TransferState.FAILED) return false;
            if (state.compareAndSet(current, TransferState.CANCELLED)) {
                CancellationException error = new CancellationException("transfer cancelled");
                offer.completeExceptionally(error);
                completion.completeExceptionally(error);
                return true;
            }
        }
    }
}
