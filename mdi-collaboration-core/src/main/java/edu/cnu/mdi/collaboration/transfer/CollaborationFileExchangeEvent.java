package edu.cnu.mdi.collaboration.transfer;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** A control-plane or data-plane file exchange notification. */
public sealed interface CollaborationFileExchangeEvent {
    /** A remote participant offered a file to the local participant. */
    record OfferReceived(FileOffer offer) implements CollaborationFileExchangeEvent {
        public OfferReceived { Objects.requireNonNull(offer, "offer"); }
    }
    /** A local offer was prepared and its control message was published. */
    record OfferSent(FileOffer offer, TransferHandle handle) implements CollaborationFileExchangeEvent {
        public OfferSent { Objects.requireNonNull(offer); Objects.requireNonNull(handle); }
    }
    /** The remote recipient accepted an outgoing offer. */
    record OfferAccepted(UUID transferId, UUID collaboratorId) implements CollaborationFileExchangeEvent {
        public OfferAccepted { Objects.requireNonNull(transferId); Objects.requireNonNull(collaboratorId); }
    }
    /** The remote recipient rejected an outgoing offer. */
    record OfferRejected(UUID transferId, UUID collaboratorId, String reason)
            implements CollaborationFileExchangeEvent {
        public OfferRejected { Objects.requireNonNull(transferId); Objects.requireNonNull(collaboratorId); }
    }
    /** A transfer completed at a local source or destination path. */
    record TransferCompleted(UUID transferId, Path path) implements CollaborationFileExchangeEvent {
        public TransferCompleted { Objects.requireNonNull(transferId); Objects.requireNonNull(path); }
    }
    /** A control or transfer operation failed without stopping the exchange. */
    record Error(UUID transferId, Throwable cause) implements CollaborationFileExchangeEvent {
        public Error { Objects.requireNonNull(cause, "cause"); }
    }
}
