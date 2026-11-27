package com.socialpilot.app.data.security;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.socialpilot.app.data.repository.ChannelRepository;
import com.socialpilot.app.data.repository.ChannelRepository.DisconnectResult;
import com.socialpilot.app.data.repository.ChannelRepository.Snapshot;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Scope;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Versioned, authenticated OAuth records. A Page and its credentials are read/written together. */
public final class EncryptedOAuthStore {
    private static final String KEY_ALIAS = "socialpilot.oauth.v1";
    private static final String LEGACY_PENDING_RECORD = "pending_v2";
    private static final String FACEBOOK_PENDING = "facebook_pending_v2";
    private static final String INSTAGRAM_PENDING = "instagram_pending_v2";
    private static final String CONNECTED = "connected_v2";
    private static final String INSTAGRAM_CONNECTED = "instagram_connected_v2";
    private static final String[] NO_LEGACY_KEYS = {};
    private static final String[] LEGACY_PENDING = {"pending_session", "pending_verifier"};
    private static final String[] LEGACY_CONNECTED = {"page_id", "page_name", "access_token", "token_type", "expires_at"};
    private final SharedPreferences preferences;
    private final RecordCipher cipher;
    private final ChannelRepository channels;
    private final Gson gson = new Gson();
    private final Set<String> unavailable = new HashSet<>();
    private final EnumMap<Platform, Long> authorizationGenerations = new EnumMap<>(Platform.class);
    private long instagramGeneration;

    public EncryptedOAuthStore(Context context) {
        this(context, null);
    }

    public EncryptedOAuthStore(Context context, ChannelRepository channels) {
        this(context.getApplicationContext().getSharedPreferences("oauth_secure_v1", Context.MODE_PRIVATE),
                new KeystoreRecordCipher(KEY_ALIAS, false), channels);
    }

    // Injection keeps failure tests independent of a device's Keystore and disk availability.
    EncryptedOAuthStore(SharedPreferences preferences, RecordCipher cipher) {
        this(preferences, cipher, null);
    }

    private EncryptedOAuthStore(SharedPreferences preferences, RecordCipher cipher, ChannelRepository channels) {
        this.preferences = preferences;
        this.cipher = cipher;
        this.channels = channels;
    }

    /** Imports authenticated v2 records once, after opening the shared Room database. */
    public synchronized Set<Platform> restoreChannels() {
        if (channels == null) return Collections.emptySet();
        channels.restore();
        EnumSet<Platform> failed = EnumSet.noneOf(Platform.class);
        if (!importLegacy(Platform.FACEBOOK, CONNECTED, LEGACY_CONNECTED)) failed.add(Platform.FACEBOOK);
        if (!importLegacy(Platform.INSTAGRAM, INSTAGRAM_CONNECTED, NO_LEGACY_KEYS)) failed.add(Platform.INSTAGRAM);
        return Collections.unmodifiableSet(failed);
    }

    private boolean importLegacy(Platform platform, String key, String[] fields) {
        ConnectedRecord legacy = connected(platform.name());
        if (legacy == null) return true;
        try {
            Snapshot current = channels.importLegacy(legacy.connection);
            // A selected connection can supersede the legacy record. Retire only a proven matching
            // Room/Keystore pair here. An explicit reconnect can retire a superseded record.
            if (current.info.connectionStatus == ConnectionStatus.DISCONNECTED
                    || (current.info.externalAccountId.equals(legacy.connection.externalAccountId)
                            && current.credentialStatus == SecretRead.Status.SUCCESS)) {
                invalidate(key, fields);
            }
            return true;
        } catch (RuntimeException promotionFailed) {
            return false;
        }
    }

    public synchronized void savePending(String sessionId, String verifier) {
        savePending(sessionId, verifier, "FACEBOOK");
    }

    public synchronized void savePending(String sessionId, String verifier, String provider) {
        PendingRecord record = new PendingRecord(sessionId, verifier, provider);
        record.validate();
        PendingRecord older = readPending(LEGACY_PENDING_RECORD, LEGACY_PENDING);
        String[] retired = older != null && provider.equals(older.provider())
                ? new String[] {LEGACY_PENDING_RECORD} : NO_LEGACY_KEYS;
        write(pendingKey(provider), record, retired);
    }

    public synchronized Pending getPending() {
        PendingRecord record = readPending(FACEBOOK_PENDING, NO_LEGACY_KEYS);
        if (record == null) record = readPending(INSTAGRAM_PENDING, NO_LEGACY_KEYS);
        if (record == null) record = readPending(LEGACY_PENDING_RECORD, LEGACY_PENDING);
        return record == null ? null : record.pending();
    }

    /** Consume one session without touching the other provider's pending authorization. */
    public synchronized Pending takePending(String sessionId) {
        if (sessionId == null) return null;
        for (String key : new String[] {FACEBOOK_PENDING, INSTAGRAM_PENDING, LEGACY_PENDING_RECORD}) {
            String[] legacy = LEGACY_PENDING_RECORD.equals(key) ? LEGACY_PENDING : NO_LEGACY_KEYS;
            PendingRecord record = readPending(key, legacy);
            if (record == null || !sessionId.equals(record.sessionId)) continue;
            // Never exchange a handoff until its verifier removal is durable.
            unavailable.add(key);
            if (!preferences.edit().remove(key).commit()) throw storageUnavailable();
            return new Pending(record.sessionId, record.verifier, record.provider(),
                    authorizationGeneration(record.provider()));
        }
        return null;
    }

    private PendingRecord readPending(String key, String[] legacy) {
        PendingRecord record = read(key, PendingRecord.class, legacy);
        if (record == null) return null;
        try {
            record.validate();
            if (!LEGACY_PENDING_RECORD.equals(key) && !key.equals(pendingKey(record.provider()))) {
                throw storageUnavailable();
            }
            return record;
        } catch (RuntimeException exception) {
            invalidate(key, legacy);
            return null;
        }
    }

    private static String pendingKey(String provider) {
        return "INSTAGRAM".equals(provider) ? INSTAGRAM_PENDING : FACEBOOK_PENDING;
    }

    public synchronized long authorizationGeneration(String provider) {
        Long generation = authorizationGenerations.get(Platform.valueOf(provider));
        return generation == null ? 0L : generation;
    }

    public synchronized void savePending(String sessionId, String verifier, String provider, long generation) {
        if (generation != authorizationGeneration(provider)) throw storageUnavailable();
        savePending(sessionId, verifier, provider);
    }

    public synchronized void saveConnected(Pending pending, OAuthExchangeResponse response) {
        if (pending.generation != authorizationGeneration(pending.provider)) throw storageUnavailable();
        saveConnected(response);
    }

    public synchronized DisconnectResult disconnect(Scope scope) {
        if (channels == null) return DisconnectResult.STALE;
        Platform platform = Platform.valueOf(scope.platform);
        Snapshot selection = channels.selected(platform);
        if (selection == null || !scope.matches(selection.scope)) return DisconnectResult.STALE;

        String pendingKey = pendingKey(scope.platform);
        PendingRecord legacy = readPending(LEGACY_PENDING_RECORD, LEGACY_PENDING);
        SharedPreferences.Editor edit = preferences.edit().remove(pendingKey);
        if (legacy != null && scope.platform.equals(legacy.provider())) edit.remove(LEGACY_PENDING_RECORD);
        if (!edit.commit()) throw storageUnavailable();
        authorizationGenerations.put(platform, authorizationGeneration(scope.platform) + 1);
        return channels.disconnect(scope);
    }

    public synchronized void saveConnected(OAuthExchangeResponse response) {
        if (response == null) throw storageUnavailable();
        ConnectedRecord record = new ConnectedRecord(response);
        String provider = response.provider;
        boolean instagram = "INSTAGRAM".equals(provider);
        record.validate(instagram ? "INSTAGRAM" : "FACEBOOK");
        if (channels != null) {
            channels.save(response);
            invalidate(instagram ? INSTAGRAM_CONNECTED : CONNECTED,
                    instagram ? NO_LEGACY_KEYS : LEGACY_CONNECTED);
            return;
        }
        write(instagram ? INSTAGRAM_CONNECTED : CONNECTED, record,
                instagram ? NO_LEGACY_KEYS : LEGACY_CONNECTED);
        if (instagram) instagramGeneration++;
    }

    public String pageSummary() {
        if (channels != null) {
            Snapshot page = channels.selected(Platform.FACEBOOK);
            return page == null || !page.connected() ? null
                    : page.info.displayName + " (" + page.info.externalAccountId + ")";
        }
        synchronized (this) {
            ConnectedRecord record = connected("FACEBOOK");
            return record == null ? null : record.connection.displayName + " (" + record.connection.externalAccountId + ")";
        }
    }

    /** Token access is kept out of Room and should only be consumed by a provider adapter. */
    public synchronized String accessToken() {
        if (channels != null) {
            Snapshot page = channels.selected(Platform.FACEBOOK);
            OAuthExchangeResponse credential = page == null ? null : channels.credential(page.scope);
            return credential == null ? null : credential.accessToken;
        }
        ConnectedRecord record = connected("FACEBOOK");
        return record == null ? null : record.connection.accessToken;
    }

    public synchronized OAuthExchangeResponse instagramConnection() {
        if (channels != null) {
            Snapshot profile = channels.selected(Platform.INSTAGRAM);
            return profile == null ? null : channels.credential(profile.scope);
        }
        ConnectedRecord record = connected("INSTAGRAM");
        return record == null ? null : record.connection;
    }

    public InstagramSession instagramSession() {
        if (channels != null) {
            InstagramMetadata selected = selectedInstagramMetadata();
            return new InstagramSession(selected, 0);
        }
        synchronized (this) {
            InstagramMetadata current = instagramMetadata();
            return new InstagramSession(current, instagramGeneration);
        }
    }

    public boolean isCurrentInstagramSession(InstagramSession session) {
        InstagramSession current = instagramSession();
        return session.generation == current.generation
                && Objects.equals(session.sessionId, current.sessionId)
                && Objects.equals(session.accountId, current.accountId)
                && (session.scope == null ? current.scope == null : session.scope.matches(current.scope));
    }

    public synchronized OAuthExchangeResponse instagramConnection(InstagramSession session) {
        if (!isCurrentInstagramSession(session)) return null;
        if (channels == null) return instagramConnection();
        return session.sessionId == null ? null : channels.credential(session.scope);
    }

    public synchronized void invalidateInstagram(String sessionId) {
        if (channels != null) {
            Snapshot profile = channels.selected(Platform.INSTAGRAM);
            if (profile != null && profile.sessionId.equals(sessionId)) channels.requireReconnect(profile.scope);
            return;
        }
        OAuthExchangeResponse current = instagramConnection();
        if (current != null && current.sessionId.equals(sessionId)) {
            invalidate(INSTAGRAM_CONNECTED, NO_LEGACY_KEYS);
        }
    }

    public InstagramMetadata instagramMetadata() {
        InstagramMetadata selected = selectedInstagramMetadata();
        return selected != null && selected.connected ? selected : null;
    }

    /** Display identity survives credential expiry. Session admission still uses instagramMetadata(). */
    public InstagramMetadata selectedInstagramMetadata() {
        if (channels != null) {
            Snapshot profile = channels.selected(Platform.INSTAGRAM);
            return profile == null ? null : new InstagramMetadata(profile.sessionId,
                    profile.info.externalAccountId, profile.info.displayName, profile.linkedPageId,
                    profile.grantedScopes, profile.expiresAt, profile.graphApiVersion, profile.scope, profile.connected());
        }
        OAuthExchangeResponse response = instagramConnection();
        return response == null ? null : new InstagramMetadata(response.sessionId, response.externalAccountId,
                response.displayName, response.linkedPageId, response.grantedScopes, response.expiresAt,
                response.graphApiVersion, null, true);
    }

    public static final class InstagramMetadata {
        public final String sessionId, externalAccountId, displayName, linkedPageId, expiresAt, graphApiVersion;
        public final List<String> grantedScopes;
        public final Scope scope;
        public final boolean connected;

        private InstagramMetadata(String sessionId, String externalAccountId, String displayName,
                String linkedPageId, List<String> grantedScopes, String expiresAt, String graphApiVersion,
                Scope scope, boolean connected) {
            this.sessionId = sessionId;
            this.externalAccountId = externalAccountId;
            this.displayName = displayName;
            this.linkedPageId = linkedPageId;
            this.grantedScopes = grantedScopes;
            this.expiresAt = expiresAt;
            this.graphApiVersion = graphApiVersion;
            this.scope = scope;
            this.connected = connected;
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
        private final Scope scope;

        public Scope scope() { return scope; }

        private InstagramSession(InstagramMetadata connection, long generation) {
            boolean connected = connection != null && connection.connected;
            sessionId = connected ? connection.sessionId : null;
            accountId = connected ? connection.externalAccountId : null;
            scope = connection == null ? null : connection.scope;
            this.generation = generation;
        }

        public boolean matches(InstagramMetadata connection) {
            return connection == null ? sessionId == null
                    : matches(connection.sessionId, connection.externalAccountId)
                            && (scope == null ? connection.scope == null : scope.matches(connection.scope));
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
        String provider() { return provider == null ? "FACEBOOK" : provider; }
        Pending pending() { return new Pending(sessionId, verifier, provider()); }
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

    public static final class Pending {
        public final String sessionId;
        public final String verifier;
        public final String provider;
        private final long generation;
        private Pending(String sessionId, String verifier, String provider) {
            this(sessionId, verifier, provider, 0);
        }
        private Pending(String sessionId, String verifier, String provider, long generation) {
            this.sessionId = sessionId; this.verifier = verifier; this.provider = provider;
            this.generation = generation;
        }
    }
}
