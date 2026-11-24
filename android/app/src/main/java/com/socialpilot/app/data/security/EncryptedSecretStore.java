package com.socialpilot.app.data.security;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import com.socialpilot.app.domain.contract.secret.SecretStoreException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

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
    /** Stores sharing a namespace file share the lock for both its Keystore alias and its edits. */
    private static final Map<SharedPreferences, Map<String, String>> ROLLBACKS = new WeakHashMap<>();
    private final Map<String, String> pendingRollback;

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
        synchronized (ROLLBACKS) {
            Map<String, String> shared = ROLLBACKS.get(preferences);
            if (shared == null) {
                shared = new HashMap<>();
                ROLLBACKS.put(preferences, shared);
            }
            pendingRollback = shared;
        }
    }

    @Override
    public SecretNamespace namespace() {
        return namespace;
    }

    @Override
    public String write(SecretRef ref, String secret) throws SecretStoreException {
        String name = name(ref);
        if (secret == null || secret.isEmpty()) throw new IllegalArgumentException("Empty secret");
        Record record = new Record(ref.opaque(), secret, UUID.randomUUID().toString());
        synchronized (pendingRollback) {
            final String encrypted;
            try {
                // Serialize key lookup/creation and encryption with commit.
                encrypted = cipher.encrypt(associatedData(name), gson.toJson(record));
            } catch (Exception failure) {
                throw new SecretStoreException(SecretStoreException.Reason.ENCRYPTION_FAILED);
            }
            commitLocked(name, encrypted);
        }
        return record.revision;
    }

    @Override
    public SecretRead read(SecretRef ref) {
        String name = name(ref);
        synchronized (pendingRollback) {
            if (pendingRollback.containsKey(name)) return SecretRead.unreadable();
            return readRecord(ref, name);
        }
    }

    private SecretRead readRecord(SecretRef ref, String name) {
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
    public void delete(SecretRef ref) throws SecretStoreException {
        String name = name(ref);
        synchronized (pendingRollback) {
            commitLocked(name, null);
        }
    }

    private void commitLocked(String name, String value) throws SecretStoreException {
        // Keep the last confirmed ciphertext (or absence) before touching the in-memory map.
        String previous;
        try {
            if (pendingRollback.containsKey(name)) {
                previous = pendingRollback.get(name);
            } else {
                Object stored = preferences.getAll().get(name);
                previous = stored instanceof String ? (String) stored : null;
            }
        } catch (RuntimeException unavailable) {
            throw new SecretStoreException(SecretStoreException.Reason.WRITE_FAILED);
        }

        try {
            SharedPreferences.Editor editor = preferences.edit();
            // A successful commit writes the whole file, including changes by other providers.
            for (Map.Entry<String, String> entry : pendingRollback.entrySet()) {
                putOrRemove(editor, entry.getKey(), entry.getValue());
            }
            putOrRemove(editor, name, value);
            if (editor.commit()) {
                pendingRollback.clear();
                return;
            }
        } catch (RuntimeException failed) {
            // An exception can also leave an unconfirmed in-memory edit.
        }
        pendingRollback.put(name, previous);
        throw new SecretStoreException(SecretStoreException.Reason.WRITE_FAILED);
    }

    private static void putOrRemove(SharedPreferences.Editor editor, String name, String value) {
        if (value == null) editor.remove(name);
        else editor.putString(name, value);
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
