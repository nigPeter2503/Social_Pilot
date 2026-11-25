package com.socialpilot.app.domain.contract.secret;

/** Write or delete could not be confirmed on disk. The message never contains secret data. */
public final class SecretStoreException extends Exception {
    public enum Reason {
        /** Encryption or the Keystore key was unavailable; nothing was written. */
        ENCRYPTION_FAILED,
        /** The record could not be committed; the reference is unreadable until the next write. */
        WRITE_FAILED
    }

    public final Reason reason;

    public SecretStoreException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }
}
