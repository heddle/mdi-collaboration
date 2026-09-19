package edu.cnu.mdi.collaboration.transfer.local;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.cnu.mdi.collaboration.transfer.FileOffer;

/** Explicit non-global registry shared by same-JVM local transfer services. */
public final class LocalFileTransferRegistry implements AutoCloseable {
    private final ConcurrentMap<UUID, Entry> entries = new ConcurrentHashMap<>();

    void register(FileOffer offer, Path source, LocalTransferHandle senderHandle) {
        Entry entry = new Entry(offer, source, senderHandle, new AtomicBoolean());
        if (entries.putIfAbsent(offer.transferId(), entry) != null) {
            throw new IllegalStateException("duplicate transfer ID: " + offer.transferId());
        }
    }

    Entry claim(FileOffer offer) {
        Entry entry = entries.get(offer.transferId());
        if (entry == null || !entry.offer().equals(offer)) {
            throw new IllegalArgumentException("unknown or mismatched local file offer");
        }
        if (!entry.claimed().compareAndSet(false, true)) {
            throw new IllegalStateException("file offer has already been accepted");
        }
        return entry;
    }

    boolean cancel(UUID transferId) {
        Entry entry = entries.remove(transferId);
        return entry != null && entry.senderHandle().cancelLocally();
    }

    void complete(UUID transferId) { entries.remove(transferId); }

    /** Cancels every outstanding local offer. */
    @Override public void close() {
        entries.keySet().forEach(this::cancel);
        entries.clear();
    }

    record Entry(FileOffer offer, Path source, LocalTransferHandle senderHandle,
            AtomicBoolean claimed) {
        Entry {
            Objects.requireNonNull(offer);
            Objects.requireNonNull(source);
            Objects.requireNonNull(senderHandle);
        }
    }
}
