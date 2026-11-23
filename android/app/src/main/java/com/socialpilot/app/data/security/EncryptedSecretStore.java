package com.socialpilot.app.data.security;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import com.socialpilot.app.domain.contract.secret.SecretStoreException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One namespace per private preferences file and Keystore alias. Each reference is one
 * AES-GCM record whose associated data binds the namespace and record name.
 */
public final class EncryptedSecretStore implements SecretStore {
    private static final int RECORD_VERSION = 1;
    private static final String RECORD_PREFIX = "secret.";
    private final SecretNamespace namespace;
    private final SharedPreferences preferences;
    private final RecordCipher cipher;
    private final Gson gson = new Gson();
    /** Records whose last commit failed; the in-memory map may hold an unconfirmed value. */
    private final Set<String> unconfirmed = new HashSet<>();

    public EncryptedSecretStore(Context context, SecretNamespace namespace) {
        this(namespace, context.getApplicationContext().getSharedPreferences(
                        "secrets_" + namespace.wireName + "_v1", Context.MODE_PRIVATE),
                new KeystoreRecordCipher("socialpilot.secret." + namespace.wireName + ".v1", true));
    }

    // Injection keeps failure tests independent of a device's Keystore and disk availability.
    EncryptedSecretStore(SecretNamespace namespace, SharedPreferences preferences, RecordCipher cipher) {
        this.namespace = Objects.requireNonNull(namespace);
        this.preferences = Objects.requireNonNull(preferences);
        this.cipher = Objects.requireNonNull(cipher);
    }

    @Override
    public SecretNamespace namespace() {
        return namespace;
    }

    @Override
    public synchronized String write(SecretRef ref, String secret) throws SecretStoreException {
        String name = name(ref);
        if (secret == null || secret.isEmpty()) throw new IllegalArgumentException("Empty secret");
        Record record = new Record(ref.opaque(), secret, UUID.randomUUID().toString());
        final String encrypted;
        try {
            // Encrypt fully before touching preferences, so an encryption failure changes nothing.
            encrypted = cipher.encrypt(associatedData(name), gson.toJson(record));
        } catch (Exception failure) {
            throw new SecretStoreException(SecretStoreException.Reason.ENCRYPTION_FAILED);
        }
        boolean committed;
        try {
            committed = preferences.edit().putString(name, encrypted).commit();
        } catch (RuntimeException failure) {
            committed = false;
        }
        if (!committed) {
            // commit(false) may still have changed the in-memory map; never serve that value.
            unconfirmed.add(name);
            throw new SecretStoreException(SecretStoreException.Reason.WRITE_FAILED);
        }
        unconfirmed.remove(name);
        return record.revision;
    }

    @Override
    public synchronized SecretRead read(SecretRef ref) {
        String name = name(ref);
        if (unconfirmed.contains(name)) return SecretRead.unreadable();
        try {
            String encrypted = preferences.getString(name, null);
            if (encrypted == null) return SecretRead.missing();
            Record record = gson.fromJson(cipher.decrypt(associatedData(name), encrypted), Record.class);
            if (record == null || record.version != RECORD_VERSION || !ref.opaque().equals(record.ref)
                    || record.secret == null || record.secret.isEmpty()
                    || record.revision == null || record.revision.isEmpty()) {
                return SecretRead.unreadable();
            }
            return SecretRead.success(record.secret, record.revision);
        } catch (Exception damaged) {
            // Keep the record: the user replaces or deletes it explicitly from the settings screen.
            return SecretRead.unreadable();
        }
    }

    @Override
    public synchronized void delete(SecretRef ref) throws SecretStoreException {
        String name = name(ref);
        boolean committed;
        try {
            committed = preferences.edit().remove(name).commit();
        } catch (RuntimeException failure) {
            committed = false;
        }
        if (!committed) {
            unconfirmed.add(name);
            throw new SecretStoreException(SecretStoreException.Reason.WRITE_FAILED);
        }
        unconfirmed.remove(name);
    }

    private String name(SecretRef ref) {
        if (ref == null || ref.namespace != namespace) {
            throw new IllegalArgumentException("Reference belongs to another namespace");
        }
        return RECORD_PREFIX + ref.id;
    }

    private String associatedData(String name) {
        return namespace.wireName + "/" + name;
    }

    private static final class Record {
        // Not final: a constant final field would be inlined and hide the stored version.
        private int version = RECORD_VERSION;
        private final String ref;
        private final String secret;
        private final String revision;

        Record(String ref, String secret, String revision) {
            this.ref = ref;
            this.secret = secret;
            this.revision = revision;
        }
    }
}
