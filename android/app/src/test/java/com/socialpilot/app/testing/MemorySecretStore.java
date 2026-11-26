package com.socialpilot.app.testing;

import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import com.socialpilot.app.domain.contract.secret.SecretStoreException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Labelled in-memory fake with injectable failures; production uses EncryptedSecretStore. */
public final class MemorySecretStore implements SecretStore {
    private final SecretNamespace namespace;
    private final Map<SecretRef, String[]> values = new HashMap<>();
    public final Set<SecretRef> unreadable = new HashSet<>();
    public boolean failWrites;
    public boolean failDeletes;

    public MemorySecretStore(SecretNamespace namespace) {
        this.namespace = namespace;
    }

    @Override public SecretNamespace namespace() { return namespace; }

    @Override public synchronized String write(SecretRef ref, String secret) throws SecretStoreException {
        check(ref);
        if (failWrites) throw new SecretStoreException(SecretStoreException.Reason.ENCRYPTION_FAILED);
        String revision = UUID.randomUUID().toString();
        values.put(ref, new String[] {secret, revision});
        unreadable.remove(ref);
        return revision;
    }

    @Override public synchronized SecretRead read(SecretRef ref) {
        check(ref);
        if (unreadable.contains(ref)) return SecretRead.unreadable();
        String[] value = values.get(ref);
        return value == null ? SecretRead.missing() : SecretRead.success(value[0], value[1]);
    }

    @Override public synchronized void delete(SecretRef ref) throws SecretStoreException {
        check(ref);
        if (failDeletes) throw new SecretStoreException(SecretStoreException.Reason.WRITE_FAILED);
        values.remove(ref);
        unreadable.remove(ref);
    }

    private void check(SecretRef ref) {
        if (ref.namespace != namespace) throw new IllegalArgumentException("Foreign namespace");
    }
}
