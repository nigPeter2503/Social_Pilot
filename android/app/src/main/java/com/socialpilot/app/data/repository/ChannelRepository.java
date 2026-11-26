package com.socialpilot.app.data.repository;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.local.ChannelDao;
import com.socialpilot.app.data.local.ChannelEntity;
import com.socialpilot.app.data.local.ChannelSecretCleanup;
import com.socialpilot.app.data.remote.instagram.InstagramGraphClient;
import com.socialpilot.app.data.remote.instagram.InstagramGraphClient.AuthorizationExpired;
import com.socialpilot.app.data.remote.instagram.InstagramGraphClient.PermissionDenied;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import com.socialpilot.app.domain.contract.secret.SecretStoreException;
import com.socialpilot.app.domain.contract.social.AccountType;
import com.socialpilot.app.domain.contract.social.CapabilityAvailability;
import com.socialpilot.app.domain.contract.social.ChannelCapability;
import com.socialpilot.app.domain.contract.social.ChannelInfo;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Grant;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Scope;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.domain.contract.social.SocialOperation;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/** The connection registry. Call database and credential operations on a worker. */
public final class ChannelRepository {
    public enum PageCheck { IDLE, CHECKING, VERIFIED, RECONNECT, PERMISSION_REQUIRED, FAILED }

    private final AppDatabase database;
    private final ChannelDao channels;
    private final SecretStore secrets;
    private final InstagramGraphClient graph;
    private final Gson gson = new Gson();
    private final EnumMap<Platform, Snapshot> selected = new EnumMap<>(Platform.class);

    public ChannelRepository(AppDatabase database, SecretStore secrets) {
        this(database, secrets, new InstagramGraphClient());
    }

    ChannelRepository(AppDatabase database, SecretStore secrets, InstagramGraphClient graph) {
        this.database = database;
        channels = database.channels();
        this.secrets = secrets;
        this.graph = graph;
    }

    public synchronized void restore() {
        drainCleanup();
        for (Platform platform : new Platform[] {Platform.FACEBOOK, Platform.INSTAGRAM}) {
            refreshSelected(platform);
        }
    }

    /** Memory-only projection for the current app screen. */
    public synchronized Snapshot selected(Platform platform) {
        return selected.get(platform);
    }

    public synchronized List<Snapshot> list() {
        List<Snapshot> result = new ArrayList<>();
        for (ChannelEntity row : channels.list()) result.add(snapshot(row));
        return Collections.unmodifiableList(result);
    }

    public synchronized Snapshot find(String channelId) {
        ChannelEntity row = channels.find(channelId);
        return row == null ? null : snapshot(row);
    }

    public synchronized boolean isCurrent(Scope scope) {
        ChannelEntity row = current(scope);
        return usable(row) && readCredential(row) != null;
    }

    public PageCheck checkFacebookPage(Scope scope) {
        Snapshot page = selected(Platform.FACEBOOK);
        if (page == null || !scope.matches(page.scope)) return PageCheck.IDLE;
        if (!page.connected()) return PageCheck.RECONNECT;
        OAuthExchangeResponse connection = credential(scope);
        if (connection == null) {
            return scope.matches(selectedScope(Platform.FACEBOOK)) ? PageCheck.RECONNECT : PageCheck.IDLE;
        }
        try {
            graph.validatePageConnection(connection);
            return scope.matches(selectedScope(Platform.FACEBOOK)) ? PageCheck.VERIFIED : PageCheck.IDLE;
        } catch (AuthorizationExpired invalid) {
            return applyFacebookRevocation(scope);
        } catch (PermissionDenied denied) {
            return scope.matches(selectedScope(Platform.FACEBOOK)) ? PageCheck.PERMISSION_REQUIRED : PageCheck.IDLE;
        } catch (IOException unavailable) {
            return scope.matches(selectedScope(Platform.FACEBOOK)) ? PageCheck.FAILED : PageCheck.IDLE;
        }
    }

    private Scope selectedScope(Platform platform) {
        Snapshot snapshot = selected(platform);
        return snapshot == null ? null : snapshot.scope;
    }

    private synchronized PageCheck applyFacebookRevocation(Scope scope) {
        if (!scope.matches(selectedScope(Platform.FACEBOOK))) return PageCheck.IDLE;
        requireReconnect(scope);
        return PageCheck.RECONNECT;
    }

    /**
     * The cleanup row is durable before writing the secret. Promotion removes it and enqueues the
     * previous ref in the same transaction as the Channel update.
     */
    public synchronized Snapshot save(OAuthExchangeResponse response) {
        Grant grant = ConnectionLifecycle.fromExchange(response.sessionId, response.provider, response);
        SecretRef ref = new SecretRef(SecretNamespace.SOCIAL_TOKEN, UUID.randomUUID().toString());
        channels.queueCleanup(new ChannelSecretCleanup(ref.opaque()));
        ChannelEntity row;
        try {
            String secretRevision = secrets.write(ref,
                    gson.toJson(new Credential(response.accessToken, response.refreshToken)));
            SecretRead saved = secrets.read(ref);
            if (saved.status != SecretRead.Status.SUCCESS || !secretRevision.equals(saved.revision)) {
                throw new IllegalStateException("Could not verify saved social credential");
            }
            row = database.runInTransaction(() -> {
                ChannelEntity previous = channels.findIdentity(response.provider, response.accountType,
                        response.externalAccountId);
                ChannelEntity next = new ChannelEntity();
                next.channelId = previous == null ? UUID.randomUUID().toString() : previous.channelId;
                if (previous != null && previous.connectionRevision == Long.MAX_VALUE) {
                    throw new IllegalStateException("Connection revision exhausted");
                }
                next.connectionRevision = previous == null ? 1 : previous.connectionRevision + 1;
                next.platform = response.provider;
                next.accountType = response.accountType;
                next.externalAccountId = response.externalAccountId;
                next.displayName = grant.displayName;
                next.sessionId = response.sessionId;
                next.status = ConnectionStatus.CONNECTED.name();
                next.selected = true;
                next.credentialRef = ref.opaque();
                next.credentialRevision = secretRevision;
                next.tokenType = grant.tokenType;
                next.scopesJson = gson.toJson(grant.grantedScopes);
                next.linkedPageId = grant.linkedPageId;
                next.graphApiVersion = grant.graphApiVersion;
                next.tokenExpiresAtMillis = grant.tokenExpiresAtMillis;
                next.dataAccessExpiresAtMillis = grant.dataAccessExpiresAtMillis;
                next.effectiveExpiresAtMillis = grant.effectiveExpiryMillis();
                channels.clearSelection(next.platform);
                channels.save(next);
                channels.finishCleanup(ref.opaque());
                if (previous != null && previous.credentialRef != null) {
                    channels.queueCleanup(new ChannelSecretCleanup(previous.credentialRef));
                }
                return next;
            });
        } catch (SecretStoreException exception) {
            throw new IllegalStateException("Could not store social credential", exception);
        } finally {
            drainCleanup();
        }
        Snapshot committed = snapshot(row);
        selected.put(committed.info.platform, committed);
        return committed;
    }

    /** Internal adapter entry point. The response contains credentials and stays on the worker. */
    public synchronized OAuthExchangeResponse credential(Scope scope) {
        ChannelEntity row = current(scope);
        if (!usable(row)) return null;
        Credential value = readCredential(row);
        if (value == null) {
            requireReconnect(scope);
            return null;
        }
        return new OAuthExchangeResponse(row.sessionId, row.platform, row.externalAccountId, row.accountType,
                row.displayName, value.accessToken, value.refreshToken, row.tokenType, utc(row.effectiveExpiresAtMillis),
                row.linkedPageId, Arrays.asList(gson.fromJson(row.scopesJson, String[].class)),
                row.graphApiVersion, utc(row.tokenExpiresAtMillis),
                utc(row.dataAccessExpiresAtMillis));
    }

    public synchronized boolean requireReconnect(Scope scope) {
        if (channels.requireReconnect(scope.channelId, scope.platform, scope.connectionRevision) == 0) return false;
        refreshSelected(Platform.valueOf(scope.platform));
        return true;
    }

    /** Apply a result only to the same connected revision, preserving other operation results. */
    public synchronized boolean recordCapability(Scope scope, ChannelCapability capability) {
        boolean changed = database.runInTransaction(() -> {
            ChannelEntity row = current(scope);
            if (!usable(row) || readCredential(row) == null) return false;
            EnumMap<SocialOperation, ChannelCapability> updated = capabilities(row);
            updated.put(capability.operation, capability);
            return channels.saveCapabilities(scope.channelId, scope.platform, scope.connectionRevision,
                    gson.toJson(updated.values())) != 0;
        });
        if (changed) refreshSelected(Platform.valueOf(scope.platform));
        return changed;
    }

    private ChannelEntity current(Scope scope) {
        ChannelEntity row = channels.find(scope.channelId);
        return row != null && scope.platform.equals(row.platform)
                && scope.connectionRevision == row.connectionRevision ? row : null;
    }

    private void refreshSelected(Platform platform) {
        ChannelEntity row = channels.selected(platform.name());
        if (row == null) selected.remove(platform);
        else selected.put(platform, snapshot(row));
    }

    private Snapshot snapshot(ChannelEntity row) {
        ConnectionStatus status = ConnectionStatus.valueOf(row.status);
        SecretRead secret = readSecret(row);
        SecretRead.Status credentialStatus = secret.status;
        if (credentialStatus == SecretRead.Status.SUCCESS && decodeCredential(secret) == null) {
            credentialStatus = SecretRead.Status.UNREADABLE;
        }
        if (status == ConnectionStatus.CONNECTED
                && (!usable(row) || credentialStatus != SecretRead.Status.SUCCESS)) {
            status = ConnectionStatus.REAUTH_REQUIRED;
        }
        ChannelInfo info = new ChannelInfo(row.channelId, Platform.valueOf(row.platform), row.externalAccountId,
                AccountType.valueOf(row.accountType), row.displayName, status,
                row.connectionRevision, row.credentialRef);
        return new Snapshot(info, row, credentialStatus,
                Arrays.asList(gson.fromJson(row.scopesJson, String[].class)), new ArrayList<>(capabilities(row).values()));
    }

    private static boolean usable(ChannelEntity row) {
        return row != null && ConnectionStatus.CONNECTED.name().equals(row.status)
                && (row.effectiveExpiresAtMillis == null || System.currentTimeMillis() < row.effectiveExpiresAtMillis);
    }

    private Credential readCredential(ChannelEntity row) {
        return decodeCredential(readSecret(row));
    }

    private SecretRead readSecret(ChannelEntity row) {
        if (row.credentialRef == null) return SecretRead.missing();
        SecretRead secret = secrets.read(SecretRef.parse(row.credentialRef));
        return secret.status == SecretRead.Status.SUCCESS && !secret.revision.equals(row.credentialRevision)
                ? SecretRead.unreadable() : secret;
    }

    private Credential decodeCredential(SecretRead secret) {
        if (secret.status != SecretRead.Status.SUCCESS) return null;
        try {
            Credential value = gson.fromJson(secret.value, Credential.class);
            return value == null || value.accessToken == null || value.accessToken.trim().isEmpty() ? null : value;
        } catch (JsonParseException damaged) {
            return null;
        }
    }

    private EnumMap<SocialOperation, ChannelCapability> capabilities(ChannelEntity row) {
        EnumMap<SocialOperation, ChannelCapability> result = new EnumMap<>(SocialOperation.class);
        for (ChannelCapability capability : gson.fromJson(row.capabilitiesJson, ChannelCapability[].class)) {
            result.put(capability.operation, capability);
        }
        return result;
    }

    private void drainCleanup() {
        for (String ref : channels.pendingCleanup()) {
            if (channels.references(ref)) continue;
            try {
                secrets.delete(SecretRef.parse(ref));
                channels.finishCleanup(ref);
            } catch (SecretStoreException retryOnRestore) {
                // Keep the journal entry for another attempt.
            }
        }
    }

    private static String utc(Long epochMillis) {
        if (epochMillis == null) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(epochMillis));
    }

    private static final class Credential {
        final String accessToken;
        final String refreshToken;

        Credential(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    public static final class Snapshot {
        public final ChannelInfo info;
        public final Scope scope;
        public final String sessionId;
        public final String linkedPageId;
        public final String graphApiVersion;
        public final String expiresAt;
        public final List<String> grantedScopes;
        public final List<ChannelCapability> capabilities;
        public final SecretRead.Status credentialStatus;
        private final Long expiryMillis;

        private Snapshot(ChannelInfo info, ChannelEntity row, SecretRead.Status credentialStatus, List<String> scopes,
                List<ChannelCapability> capabilities) {
            this.info = info;
            this.credentialStatus = credentialStatus;
            scope = new Scope(row.platform, row.channelId, row.connectionRevision);
            sessionId = row.sessionId;
            linkedPageId = row.linkedPageId;
            graphApiVersion = row.graphApiVersion;
            expiresAt = utc(row.effectiveExpiresAtMillis);
            expiryMillis = row.effectiveExpiresAtMillis;
            grantedScopes = Collections.unmodifiableList(scopes);
            this.capabilities = Collections.unmodifiableList(capabilities);
        }

        public boolean connected() {
            return info.connectionStatus == ConnectionStatus.CONNECTED
                    && (expiryMillis == null || System.currentTimeMillis() < expiryMillis);
        }

        public ChannelCapability capability(SocialOperation operation) {
            for (ChannelCapability capability : capabilities) {
                if (capability.operation == operation) return capability;
            }
            return new ChannelCapability(operation, CapabilityAvailability.UNVERIFIED, "NOT_CHECKED", null);
        }
    }
}
