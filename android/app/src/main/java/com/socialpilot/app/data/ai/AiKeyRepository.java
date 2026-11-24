package com.socialpilot.app.data.ai;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.socialpilot.app.core.NetworkStatus;
import com.socialpilot.app.data.ai.ByokKeyVerifier.Outcome;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.ai.AiRequest.Provider;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import com.socialpilot.app.domain.contract.secret.SecretStoreException;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * BYOK keys per provider. The key lives only in the AI namespace of the encrypted store; the
 * plain status file holds the check result bound to the key revision, never key material.
 * Synchronous storage operations and verification must run off the main thread.
 */
// commit() is deliberate: callers are already on a worker, and a lost status write only falls
// back to UNVERIFIED, which never enables provider calls.
@SuppressLint("ApplySharedPref")
public final class AiKeyRepository {
    public enum KeyState { NOT_SET, UNVERIFIED, VALID, INVALID, PERMISSION_DENIED, UNREADABLE }

    public interface Clock {
        long nowMillis();
    }

    /** UI-safe snapshot: never contains the key, only a masked hint. */
    public static final class KeyStatus {
        public final Provider provider;
        public final KeyState state;
        public final String maskedHint;
        public final Long checkedAtMillis;

        KeyStatus(Provider provider, KeyState state, String maskedHint, Long checkedAtMillis) {
            this.provider = provider;
            this.state = state;
            this.maskedHint = maskedHint;
            this.checkedAtMillis = checkedAtMillis;
        }

        /** Provider calls are allowed only for a key whose last definite check succeeded. */
        public boolean usable() {
            return state == KeyState.VALID;
        }
    }

    public static final class CheckResult {
        public final Outcome outcome;
        public final KeyStatus status;

        CheckResult(Outcome outcome, KeyStatus status) {
            this.outcome = outcome;
            this.status = status;
        }
    }

    private static final int MIN_KEY_LENGTH = 20;
    private static final int MAX_KEY_LENGTH = 512;
    private final SecretStore store;
    private final SharedPreferences statusFile;
    private final ByokKeyVerifier verifier;
    private final NetworkStatus network;
    private final Clock clock;
    private final Gson gson = new Gson();
    private final Map<Provider, KeyStorageQueue> storageQueues = new EnumMap<>(Provider.class);

    public AiKeyRepository(SecretStore store, SharedPreferences statusFile, ByokKeyVerifier verifier,
                           NetworkStatus network, Clock clock) {
        if (store.namespace() != SecretNamespace.AI_PROVIDER) {
            throw new IllegalArgumentException("AI keys must use the AI namespace");
        }
        this.store = store;
        this.statusFile = Objects.requireNonNull(statusFile);
        this.verifier = Objects.requireNonNull(verifier);
        this.network = Objects.requireNonNull(network);
        this.clock = Objects.requireNonNull(clock);
        for (Provider provider : Provider.values()) storageQueues.put(provider, new KeyStorageQueue());
    }

    public static SecretRef reference(Provider provider) {
        return new SecretRef(SecretNamespace.AI_PROVIDER, "llm." + provider.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Trims surrounding whitespace and rejects values that cannot be an HTTP header token. */
    public static String normalize(String raw) {
        String key = raw == null ? "" : raw.trim();
        if (key.length() < MIN_KEY_LENGTH || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Invalid key length");
        }
        for (int i = 0; i < key.length(); i++) {
            char character = key.charAt(i);
            if (character <= ' ' || character > '~') throw new IllegalArgumentException("Invalid key character");
        }
        return key;
    }

    /** Queues UI storage work across screen instances. Loads wait for earlier saves and deletes. */
    public void enqueueStorage(Provider provider, Executor worker, Runnable operation) {
        storageQueues.get(provider).enqueue(worker, operation);
    }

    public synchronized KeyStatus status(Provider provider) {
        SecretRead read = store.read(reference(provider));
        if (read.status == SecretRead.Status.MISSING) return new KeyStatus(provider, KeyState.NOT_SET, null, null);
        if (read.status == SecretRead.Status.UNREADABLE) {
            return new KeyStatus(provider, KeyState.UNREADABLE, null, null);
        }
        StatusRecord record = statusRecord(provider);
        boolean current = record != null && read.revision.equals(record.revision) && record.state != null;
        return new KeyStatus(provider, current ? record.state : KeyState.UNVERIFIED, mask(read.value),
                current ? record.checkedAt : null);
    }

    /** Stores a new key as UNVERIFIED. Replaces any previous key for this provider only. */
    public synchronized KeyStatus save(Provider provider, String rawKey) throws SecretStoreException {
        String key = normalize(rawKey);
        store.write(reference(provider), key);
        // The new revision already invalidates the old result; removing it is only tidy-up.
        statusFile.edit().remove(statusName(provider)).commit();
        return status(provider);
    }

    /** Deletes this provider's key and status only; other providers and social tokens stay. */
    public synchronized KeyStatus delete(Provider provider) throws SecretStoreException {
        store.delete(reference(provider));
        statusFile.edit().remove(statusName(provider)).commit();
        return status(provider);
    }

    /** Credential source for the provider adapters; reads the current key at dispatch time. */
    public AiCredentialSource credentialSource(Provider provider) {
        SecretRef ref = reference(provider);
        return () -> {
            SecretRead read = store.read(ref);
            if (read.status == SecretRead.Status.SUCCESS) return read.value;
            if (read.status == SecretRead.Status.MISSING) return null;
            throw new IllegalStateException("Stored key unreadable");
        };
    }

    /** Prepares a check of the stored key. Run on a background worker; cancel from any thread. */
    public Verification prepareVerification(Provider provider) {
        return new Verification(provider);
    }

    public final class Verification implements Cancelable {
        private final Provider provider;
        private volatile boolean cancelled;
        private volatile ByokKeyVerifier.Check check;

        private Verification(Provider provider) {
            this.provider = provider;
        }

        @Override
        public void cancel() {
            cancelled = true;
            ByokKeyVerifier.Check attached = check;
            if (attached != null) attached.cancel();
        }

        public CheckResult run() {
            SecretRead read;
            synchronized (AiKeyRepository.this) {
                read = store.read(reference(provider));
            }
            if (read.status == SecretRead.Status.MISSING) return new CheckResult(Outcome.NO_KEY, status(provider));
            if (read.status == SecretRead.Status.UNREADABLE) {
                return new CheckResult(Outcome.KEY_UNREADABLE, status(provider));
            }
            if (cancelled) return new CheckResult(Outcome.CANCELLED, status(provider));
            if (!network.isOnline()) return new CheckResult(Outcome.OFFLINE, status(provider));
            check = verifier.prepare(provider, read.value);
            if (cancelled) check.cancel();
            Outcome outcome = check.run();
            if (cancelled) outcome = Outcome.CANCELLED;
            synchronized (AiKeyRepository.this) {
                if (!outcome.describesKey()) return new CheckResult(outcome, status(provider));
                SecretRead now = store.read(reference(provider));
                if (now.status != SecretRead.Status.SUCCESS || !read.revision.equals(now.revision)) {
                    // The user replaced or deleted the key while this check was running.
                    return new CheckResult(Outcome.SUPERSEDED, status(provider));
                }
                KeyState state = outcome == Outcome.VALID ? KeyState.VALID
                        : outcome == Outcome.INVALID_KEY ? KeyState.INVALID : KeyState.PERMISSION_DENIED;
                StatusRecord record = new StatusRecord(read.revision, state, clock.nowMillis());
                statusFile.edit().putString(statusName(provider), gson.toJson(record)).commit();
                return new CheckResult(outcome, status(provider));
            }
        }
    }

    static String mask(String key) {
        return key.length() >= 12 ? "••••" + key.substring(key.length() - 4) : "••••";
    }

    private StatusRecord statusRecord(Provider provider) {
        try {
            String json = statusFile.getString(statusName(provider), null);
            return json == null ? null : gson.fromJson(json, StatusRecord.class);
        } catch (JsonParseException | ClassCastException damaged) {
            return null;
        }
    }

    private static String statusName(Provider provider) {
        return "status." + provider.name();
    }

    /** At most one worker in the shared I/O pool runs this provider's storage work at a time. */
    private static final class KeyStorageQueue {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

        void enqueue(Executor worker, Runnable operation) {
            synchronized (pending) {
                pending.addLast(operation);
                if (pending.size() > 1) return;
            }
            scheduleNext(worker);
        }

        private void scheduleNext(Executor worker) {
            Runnable next;
            synchronized (pending) {
                next = pending.getFirst();
            }
            worker.execute(() -> {
                try {
                    next.run();
                } finally {
                    boolean hasMore;
                    synchronized (pending) {
                        pending.removeFirst();
                        hasMore = !pending.isEmpty();
                    }
                    if (hasMore) scheduleNext(worker);
                }
            });
        }
    }

    private static final class StatusRecord {
        private final String revision;
        private final KeyState state;
        private final Long checkedAt;

        StatusRecord(String revision, KeyState state, Long checkedAt) {
            this.revision = revision;
            this.state = state;
            this.checkedAt = checkedAt;
        }
    }
}
