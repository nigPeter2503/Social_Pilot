package com.socialpilot.app.domain.contract.social;

import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

/** Pure connection policy. The caller commits decisions with a revision check at the point of write. */
public final class ConnectionLifecycle {
    private static final SocialError RECONNECT =
            new SocialError(SocialError.Code.AUTH_REQUIRED, SocialError.NextAction.RECONNECT);
    private static final SocialError RESOLVE_PERMISSION = new SocialError(
            SocialError.Code.PERMISSION_REQUIRED, SocialError.NextAction.RESOLVE_PERMISSION);

    public static final class Identity {
        // Keep the proposed Instagram values independent of the wire enums pending SP-065.
        public final String platform;
        public final String accountType;
        public final String externalAccountId;

        private Identity(String platform, String accountType, String externalAccountId) {
            this.platform = platform;
            this.accountType = accountType;
            this.externalAccountId = ContractChecks.text(externalAccountId, "externalAccountId");
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Identity)) return false;
            Identity identity = (Identity) other;
            return platform.equals(identity.platform) && accountType.equals(identity.accountType)
                    && externalAccountId.equals(identity.externalAccountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(platform, accountType, externalAccountId);
        }
    }

    public static final class Scope {
        public final String platform;
        public final String channelId;
        public final long connectionRevision;

        public Scope(String platform, String channelId, long connectionRevision) {
            this.platform = ContractChecks.text(platform, "platform");
            this.channelId = ContractChecks.text(channelId, "channelId");
            this.connectionRevision = ContractChecks.revision(connectionRevision);
        }

        public boolean matches(Scope current) {
            return current != null && platform.equals(current.platform)
                    && channelId.equals(current.channelId) && connectionRevision == current.connectionRevision;
        }
    }

    /** Validated grant metadata. The exchange layer owns all credential values. */
    public static final class Grant {
        public final Identity identity;
        public final String displayName;
        public final String linkedPageId;
        public final List<String> grantedScopes;
        public final String graphApiVersion;
        public final String tokenType;
        public final Long tokenExpiresAtMillis;
        public final Long dataAccessExpiresAtMillis;

        private Grant(Identity identity, String displayName, String linkedPageId,
                List<String> grantedScopes, String graphApiVersion, String tokenType,
                Long tokenExpiresAtMillis, Long dataAccessExpiresAtMillis) {
            this.identity = identity;
            this.displayName = ContractChecks.text(displayName, "displayName");
            this.linkedPageId = linkedPageId;
            List<String> scopes = new ArrayList<>(Objects.requireNonNull(grantedScopes));
            for (String scope : scopes) ContractChecks.text(scope, "scope");
            this.grantedScopes = Collections.unmodifiableList(scopes);
            this.graphApiVersion = graphApiVersion;
            this.tokenType = ContractChecks.text(tokenType, "tokenType");
            this.tokenExpiresAtMillis = tokenExpiresAtMillis;
            this.dataAccessExpiresAtMillis = dataAccessExpiresAtMillis;
        }

        public Long effectiveExpiryMillis() {
            if (tokenExpiresAtMillis == null) return dataAccessExpiresAtMillis;
            if (dataAccessExpiresAtMillis == null) return tokenExpiresAtMillis;
            return Math.min(tokenExpiresAtMillis, dataAccessExpiresAtMillis);
        }
    }

    public static Grant fromFacebookExchange(String expectedSession, OAuthExchangeResponse response) {
        response.validate();
        Long expiresAt = null;
        if (response.expiresAt != null) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            expiresAt = format.parse(response.expiresAt, new ParsePosition(0)).getTime();
        }
        return validateGrant(expectedSession, "FACEBOOK", response.sessionId, response.provider,
                response.accountType, response.externalAccountId, response.displayName, null,
                Collections.emptyList(), null, response.tokenType, expiresAt, null);
    }

    /** Maps fields supplied by a validated exchange, including the proposed SP-065 metadata. */
    public static Grant validateGrant(String expectedSession, String expectedPlatform, String sessionId,
            String platform, String accountType, String externalAccountId, String displayName,
            String linkedPageId, List<String> grantedScopes, String graphApiVersion, String tokenType,
            Long tokenExpiresAtMillis, Long dataAccessExpiresAtMillis) {
        if (!ContractChecks.text(expectedSession, "expectedSession").equals(sessionId)) {
            throw new IllegalArgumentException("INVALID_SESSION");
        }
        if (!ContractChecks.text(expectedPlatform, "expectedPlatform").equals(platform)) {
            throw new IllegalArgumentException("PROVIDER_ERROR");
        }
        Identity identity = new Identity(platform, accountType, externalAccountId);
        if ("INSTAGRAM".equals(platform)) {
            ContractChecks.text(linkedPageId, "linkedPageId");
            if (!"PROFESSIONAL".equals(accountType) || identity.externalAccountId.equals(linkedPageId)
                    || grantedScopes == null || grantedScopes.isEmpty() || graphApiVersion == null
                    || !graphApiVersion.matches("v[0-9]+\\.[0-9]+")) {
                throw new IllegalArgumentException("PROVIDER_ERROR");
            }
        } else if (!"FACEBOOK".equals(platform) || !"PAGE".equals(accountType) || linkedPageId != null) {
            throw new IllegalArgumentException("PROVIDER_ERROR");
        }
        return new Grant(identity, displayName, linkedPageId, grantedScopes, graphApiVersion,
                tokenType, tokenExpiresAtMillis, dataAccessExpiresAtMillis);
    }

    public static final class Connection {
        public final Scope scope;
        public final Grant grant;
        public final ConnectionStatus status;
        private final Map<SocialOperation, ChannelCapability> capabilities;

        private Connection(Scope scope, Grant grant, ConnectionStatus status,
                Map<SocialOperation, ChannelCapability> capabilities) {
            this.scope = scope;
            this.grant = grant;
            this.status = status;
            this.capabilities = capabilities;
        }

        public ChannelCapability capability(SocialOperation operation) {
            Objects.requireNonNull(operation, "operation");
            ChannelCapability verified = capabilities.get(operation);
            return verified == null
                    ? new ChannelCapability(operation, CapabilityAvailability.UNVERIFIED, "NOT_CHECKED", null)
                    : verified;
        }
    }

    public static final class Decision {
        public final Connection connection;
        public final SocialError error;

        private Decision(Connection connection, SocialError error) {
            this.connection = connection;
            this.error = error;
        }
    }

    private ConnectionLifecycle() {}

    /** Call in the metadata commit after a credential for this grant is ready. */
    public static Connection connected(Grant grant, String newChannelId, Connection previous) {
        String channelId = newChannelId;
        long revision = 1;
        if (previous != null) {
            if (!previous.grant.identity.equals(grant.identity)) {
                throw new IllegalArgumentException("Reconnect requires the same identity");
            }
            channelId = previous.scope.channelId;
            if (previous.scope.connectionRevision == Long.MAX_VALUE) {
                throw new IllegalStateException("Connection revision exhausted");
            }
            revision = previous.scope.connectionRevision + 1;
        }
        return new Connection(new Scope(grant.identity.platform, channelId, revision), grant,
                ConnectionStatus.CONNECTED, Collections.emptyMap());
    }

    public static Decision checkExpiry(Connection current, long nowMillis) {
        Long expiresAt = current.grant.effectiveExpiryMillis();
        return expiresAt != null && nowMillis >= expiresAt
                ? revoked(current)
                : new Decision(current, current.status == ConnectionStatus.REAUTH_REQUIRED ? RECONNECT : null);
    }

    /** Apply only to a connection whose credential was confirmed invalid. */
    public static Decision revoked(Connection current) {
        if (current.status != ConnectionStatus.CONNECTED) return new Decision(current, RECONNECT);
        return new Decision(new Connection(current.scope, current.grant,
                ConnectionStatus.REAUTH_REQUIRED, current.capabilities), RECONNECT);
    }

    /** OAuth scopes alone do not prove availability. Supply verification of the actual operation. */
    public static Decision operationVerified(Connection current, ChannelCapability capability) {
        if (current.status != ConnectionStatus.CONNECTED) return new Decision(current, RECONNECT);
        Map<SocialOperation, ChannelCapability> updated = new EnumMap<>(SocialOperation.class);
        updated.putAll(current.capabilities);
        updated.put(capability.operation, capability);
        SocialError error = capability.availability == CapabilityAvailability.PERMISSION_REQUIRED
                ? RESOLVE_PERMISSION : null;
        return new Decision(new Connection(current.scope, current.grant, current.status, updated), error);
    }

    /** Preserve verified state while returning the adapter's normalized operational failure. */
    public static Decision operationFailed(Connection current, SocialError error) {
        return new Decision(current, Objects.requireNonNull(error, "error"));
    }
}
