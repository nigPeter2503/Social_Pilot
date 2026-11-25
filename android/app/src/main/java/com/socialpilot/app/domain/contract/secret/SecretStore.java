package com.socialpilot.app.domain.contract.secret;

/**
 * Encrypted secret storage bound to exactly one {@link SecretNamespace}. All methods do disk and
 * Keystore I/O and must run off the main thread. Implementations never log secret values.
 */
public interface SecretStore {
    SecretNamespace namespace();

    /**
     * Replaces the secret atomically. On failure the previously committed value stays the durable
     * value; it is never replaced by a partially written record.
     *
     * @return a new opaque revision for this value, used to discard stale results bound to an
     *     older value (for example a key check that finishes after the user entered a new key)
     */
    String write(SecretRef ref, String secret) throws SecretStoreException;

    /** Never throws for missing or damaged records; see {@link SecretRead.Status}. */
    SecretRead read(SecretRef ref);

    /** Deletes only this reference. Deleting a missing reference succeeds. */
    void delete(SecretRef ref) throws SecretStoreException;
}
