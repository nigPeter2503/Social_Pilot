package com.socialpilot.app.data.security;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import java.util.HashSet;
import java.util.Set;

/** Versioned, authenticated OAuth records. A Page and its credentials are read/written together. */
public final class EncryptedOAuthStore {
    private static final String KEY_ALIAS = "socialpilot.oauth.v1";
    private static final String PENDING = "pending_v2";
    private static final String CONNECTED = "connected_v2";
    private static final String[] LEGACY_PENDING = {"pending_session", "pending_verifier"};
    private static final String[] LEGACY_CONNECTED = {"page_id", "page_name", "access_token", "token_type", "expires_at"};
    private final SharedPreferences preferences;
    private final RecordCipher cipher;
    private final Gson gson = new Gson();
    private final Set<String> unavailable = new HashSet<>();

    public EncryptedOAuthStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences("oauth_secure_v1", Context.MODE_PRIVATE),
                new KeystoreRecordCipher(KEY_ALIAS, false));
    }

    // Injection keeps failure tests independent of a device's Keystore and disk availability.
    EncryptedOAuthStore(SharedPreferences preferences, RecordCipher cipher) {
        this.preferences = preferences;
        this.cipher = cipher;
    }

    public synchronized void savePending(String sessionId, String verifier) {
        PendingRecord record = new PendingRecord(sessionId, verifier);
        record.validate();
        write(PENDING, record, LEGACY_PENDING);
    }

    public synchronized Pending getPending() {
        PendingRecord record = read(PENDING, PendingRecord.class, LEGACY_PENDING);
        if (record == null) return null;
        try {
            record.validate();
            return new Pending(record.sessionId, record.verifier);
        } catch (RuntimeException exception) {
            invalidate(PENDING, LEGACY_PENDING);
            return null;
        }
    }

    public synchronized void clearPending() { invalidate(PENDING, LEGACY_PENDING); }

    public synchronized void saveConnected(OAuthExchangeResponse response) {
        ConnectedRecord record = new ConnectedRecord(response);
        record.validate();
        write(CONNECTED, record, LEGACY_CONNECTED);
    }

    public synchronized String pageSummary() {
        ConnectedRecord record = connected();
        return record == null ? null : record.connection.displayName + " (" + record.connection.externalAccountId + ")";
    }

    /** Token access is kept out of Room and should only be consumed by a provider adapter. */
    public synchronized String accessToken() {
        ConnectedRecord record = connected();
        return record == null ? null : record.connection.accessToken;
    }

    private ConnectedRecord connected() {
        ConnectedRecord record = read(CONNECTED, ConnectedRecord.class, LEGACY_CONNECTED);
        if (record == null) return null;
        try {
            record.validate();
            return record;
        } catch (RuntimeException exception) {
            invalidate(CONNECTED, LEGACY_CONNECTED);
            return null;
        }
    }

    private void write(String name, Object record, String[] legacy) {
        final String encrypted;
        try {
            // Encryption/serialization must succeed before touching preferences.
            encrypted = cipher.encrypt(name, gson.toJson(record));
        } catch (Exception exception) {
            throw storageUnavailable();
        }
        try {
            SharedPreferences.Editor editor = preferences.edit().putString(name, encrypted);
            for (String key : legacy) editor.remove(key);
            if (!editor.commit()) throw storageUnavailable();
            unavailable.remove(name);
        } catch (RuntimeException exception) {
            // commit(false) can still change SharedPreferences' in-memory map. Do not trust it.
            invalidate(name, legacy);
            throw storageUnavailable();
        }
    }

    private <T> T read(String name, Class<T> type, String[] legacy) {
        if (unavailable.contains(name)) return null;
        try {
            String encrypted = preferences.getString(name, null);
            if (encrypted == null) {
                // v1 fields may already contain mixed credentials. Require reconnect rather than migrate them.
                for (String key : legacy) {
                    if (preferences.contains(key)) { invalidate(name, legacy); break; }
                }
                return null;
            }
            T record = gson.fromJson(cipher.decrypt(name, encrypted), type);
            if (record == null) throw storageUnavailable();
            return record;
        } catch (Exception exception) {
            invalidate(name, legacy);
            return null;
        }
    }

    private void invalidate(String name, String[] legacy) {
        unavailable.add(name);
        try {
            SharedPreferences.Editor editor = preferences.edit().remove(name);
            for (String key : legacy) editor.remove(key);
            // Remain unavailable even if cleanup cannot reach disk; a successful save restores access.
            editor.commit();
        } catch (RuntimeException ignored) { }
    }

    private static IllegalStateException storageUnavailable() {
        return new IllegalStateException("Secure storage unavailable");
    }

    private static final class PendingRecord {
        private int version = 2;
        private final String sessionId, verifier;
        PendingRecord(String sessionId, String verifier) { this.sessionId = sessionId; this.verifier = verifier; }
        void validate() {
            if (version != 2 || sessionId == null || sessionId.trim().isEmpty() || verifier == null
                    || !verifier.matches("[A-Za-z0-9._~-]{43,128}")) throw storageUnavailable();
        }
    }

    private static final class ConnectedRecord {
        private int version = 2;
        private final OAuthExchangeResponse connection;
        ConnectedRecord(OAuthExchangeResponse connection) { this.connection = connection; }
        void validate() {
            if (version != 2 || connection == null) throw storageUnavailable();
            connection.validate();
            if (!"FACEBOOK".equals(connection.provider) || !"PAGE".equals(connection.accountType)) throw storageUnavailable();
        }
    }

    public static final class Pending {
        public final String sessionId;
        public final String verifier;
        private Pending(String sessionId, String verifier) { this.sessionId = sessionId; this.verifier = verifier; }
    }
}
