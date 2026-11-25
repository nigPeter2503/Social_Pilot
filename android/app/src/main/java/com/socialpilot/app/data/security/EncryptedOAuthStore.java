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
    private static final String INSTAGRAM_CONNECTED = "instagram_connected_v2";
    private static final String[] NO_LEGACY_KEYS = {};
    private static final String[] LEGACY_PENDING = {"pending_session", "pending_verifier"};
    private static final String[] LEGACY_CONNECTED = {"page_id", "page_name", "access_token", "token_type", "expires_at"};
    private final SharedPreferences preferences;
    private final RecordCipher cipher;
    private final Gson gson = new Gson();
    private final Set<String> unavailable = new HashSet<>();
    private long instagramGeneration;

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
        savePending(sessionId, verifier, "FACEBOOK");
    }

    public synchronized void savePending(String sessionId, String verifier, String provider) {
        PendingRecord record = new PendingRecord(sessionId, verifier, provider);
        record.validate();
        write(PENDING, record, LEGACY_PENDING);
    }

    public synchronized Pending getPending() {
        PendingRecord record = read(PENDING, PendingRecord.class, LEGACY_PENDING);
        if (record == null) return null;
        try {
            record.validate();
            return new Pending(record.sessionId, record.verifier, record.provider == null ? "FACEBOOK" : record.provider);
        } catch (RuntimeException exception) {
            invalidate(PENDING, LEGACY_PENDING);
            return null;
        }
    }

    public synchronized void clearPending() { invalidate(PENDING, LEGACY_PENDING); }

    public synchronized void saveConnected(OAuthExchangeResponse response) {
        if (response == null) throw storageUnavailable();
        ConnectedRecord record = new ConnectedRecord(response);
        String provider = response.provider;
        boolean instagram = "INSTAGRAM".equals(provider);
        record.validate(instagram ? "INSTAGRAM" : "FACEBOOK");
        write(instagram ? INSTAGRAM_CONNECTED : CONNECTED, record,
                instagram ? NO_LEGACY_KEYS : LEGACY_CONNECTED);
        if (instagram) instagramGeneration++;
    }

    public synchronized String pageSummary() {
        ConnectedRecord record = connected("FACEBOOK");
        return record == null ? null : record.connection.displayName + " (" + record.connection.externalAccountId + ")";
    }

    public synchronized PageMetadata pageMetadata() {
        ConnectedRecord record = connected("FACEBOOK");
        if (unavailable.contains(CONNECTED)) throw storageUnavailable();
        return record == null ? null : new PageMetadata(record.connection);
    }

    /** Token access is kept out of Room and should only be consumed by a provider adapter. */
    public synchronized String accessToken() {
        ConnectedRecord record = connected("FACEBOOK");
        return record == null ? null : record.connection.accessToken;
    }

    public synchronized OAuthExchangeResponse instagramConnection() {
        ConnectedRecord record = connected("INSTAGRAM");
        return record == null ? null : record.connection;
    }

    public synchronized InstagramSession instagramSession() {
        OAuthExchangeResponse current = instagramConnection();
        return new InstagramSession(current, instagramGeneration);
    }

    public synchronized boolean isCurrentInstagramSession(InstagramSession session) {
        OAuthExchangeResponse current = instagramConnection();
        return session.matches(current) && session.generation == instagramGeneration;
    }

    public synchronized OAuthExchangeResponse instagramConnection(InstagramSession session) {
        OAuthExchangeResponse current = instagramConnection();
        return session.matches(current) && session.generation == instagramGeneration ? current : null;
    }

    public synchronized void invalidateInstagram(String sessionId) {
        OAuthExchangeResponse current = instagramConnection();
        if (current != null && current.sessionId.equals(sessionId)) {
            invalidate(INSTAGRAM_CONNECTED, NO_LEGACY_KEYS);
        }
    }

    private ConnectedRecord connected(String provider) {
        boolean instagram = "INSTAGRAM".equals(provider);
        String key = instagram ? INSTAGRAM_CONNECTED : CONNECTED;
        String[] legacy = instagram ? NO_LEGACY_KEYS : LEGACY_CONNECTED;
        ConnectedRecord record = read(key, ConnectedRecord.class, legacy);
        if (record == null) return null;
        try {
            record.validate(provider);
            return record;
        } catch (RuntimeException exception) {
            invalidate(key, legacy);
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
        if (INSTAGRAM_CONNECTED.equals(name)) instagramGeneration++;
        try {
            SharedPreferences.Editor editor = preferences.edit().remove(name);
            for (String key : legacy) editor.remove(key);
            // Remain unavailable even if cleanup cannot reach disk; a successful save restores access.
            editor.commit();
        } catch (RuntimeException ignored) { }
    }

    /** In-process identity for a connected or disconnected state, without OAuth credentials. */
    public static final class InstagramSession {
        public final String sessionId;
        public final String accountId;
        private final long generation;

        private InstagramSession(OAuthExchangeResponse connection, long generation) {
            sessionId = connection == null ? null : connection.sessionId;
            accountId = connection == null ? null : connection.externalAccountId;
            this.generation = generation;
        }

        public boolean matches(OAuthExchangeResponse connection) {
            return connection == null ? sessionId == null : matches(connection.sessionId, connection.externalAccountId);
        }

        public boolean matches(String expectedSessionId, String expectedAccountId) {
            return sessionId != null && sessionId.equals(expectedSessionId) && accountId.equals(expectedAccountId);
        }
    }

    private static IllegalStateException storageUnavailable() {
        return new IllegalStateException("Secure storage unavailable");
    }

    private static final class PendingRecord {
        private int version = 2;
        private final String sessionId, verifier;
        private final String provider;
        PendingRecord(String sessionId, String verifier, String provider) {
            this.sessionId = sessionId; this.verifier = verifier; this.provider = provider;
        }
        void validate() {
            if (version != 2 || sessionId == null || sessionId.trim().isEmpty() || verifier == null
                    || !verifier.matches("[A-Za-z0-9._~-]{43,128}")
                    || (provider != null && !"FACEBOOK".equals(provider) && !"INSTAGRAM".equals(provider))) {
                throw storageUnavailable();
            }
        }
    }

    private static final class ConnectedRecord {
        private int version = 2;
        private final OAuthExchangeResponse connection;
        ConnectedRecord(OAuthExchangeResponse connection) { this.connection = connection; }
        void validate(String provider) {
            if (version != 2 || connection == null) throw storageUnavailable();
            connection.validate();
            if (!provider.equals(connection.provider)
                    || ("FACEBOOK".equals(provider) && !"PAGE".equals(connection.accountType))) {
                throw storageUnavailable();
            }
        }
    }

    public static final class PageMetadata {
        public final String displayName;
        public final String externalAccountId;
        public final String expiresAt;

        private PageMetadata(OAuthExchangeResponse connection) {
            displayName = connection.displayName;
            externalAccountId = connection.externalAccountId;
            expiresAt = connection.expiresAt;
        }
    }

    public static final class Pending {
        public final String sessionId;
        public final String verifier;
        public final String provider;
        private Pending(String sessionId, String verifier, String provider) {
            this.sessionId = sessionId; this.verifier = verifier; this.provider = provider;
        }
    }
}
