package edu.cnu.mdi.collaboration.transfer;

/** Observable lifecycle state of a file transfer. */
public enum TransferState {
    /** Preparing metadata such as size and checksum. */
    PREPARING,
    /** Offered and waiting for recipient action. */
    OFFERED,
    /** Copying or otherwise moving file bytes. */
    TRANSFERRING,
    /** Bytes arrived and integrity checks passed. */
    COMPLETED,
    /** Cancelled by either participant. */
    CANCELLED,
    /** Failed before completion. */
    FAILED
}
