package com.socialpilot.app.domain.contract.social;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Connection;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Decision;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Grant;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Identity;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Scope;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.junit.Test;

public final class ConnectionLifecycleTest {
    private static final String SESSION = "session-demo";
    private static final String VERIFIED_AT = "2026-11-21T02:00:00Z";

    @Test public void mapsFacebookDtoAndInstagramFixtureToSeparateOpaqueIdentities() {
        Grant page = facebook("000Page");
        Grant profile = instagram("000Ig", "000Page", null, null);
        assertEquals("FACEBOOK", page.identity.platform);
        assertEquals("PAGE", page.identity.accountType);
        assertEquals("000Page", page.identity.externalAccountId);
        assertEquals("INSTAGRAM", profile.identity.platform);
        assertEquals("PROFESSIONAL", profile.identity.accountType);
        assertEquals("000Ig", profile.identity.externalAccountId);
        assertEquals(page.identity.externalAccountId, profile.linkedPageId);
        assertEquals("v27.0", profile.graphApiVersion);
        assertEquals(Collections.singletonList("instagram_basic"), profile.grantedScopes);
        assertNotEquals(page.identity, instagram("000Page", "another-page", null, null).identity);
    }

    @Test public void rejectsInvalidExchangeFieldsBeforeAnyWrite() {
        assertThrows(IllegalArgumentException.class, () -> instagram("page", "page", null, null));
        assertThrows(IllegalArgumentException.class, () -> instagram("ig", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> instagram("", "page", null, null));
        assertThrows(IllegalArgumentException.class, () ->
                ConnectionLifecycle.fromExchange("new-session", "FACEBOOK", facebookResponse("page", "PAGE")));
        assertThrows(IllegalArgumentException.class, () ->
                ConnectionLifecycle.fromExchange(SESSION, "FACEBOOK", facebookResponse("page", "PERSON")));
        assertThrows(IllegalArgumentException.class, () -> ConnectionLifecycle.validateGrant(
                SESSION, "FACEBOOK", SESSION, "INSTAGRAM", "PROFESSIONAL", "ig", "Demo", "page",
                Collections.singletonList("instagram_basic"), "v27.0", "bearer", null, null));
        assertThrows(IllegalArgumentException.class, () -> ConnectionLifecycle.validateGrant(
                SESSION, "INSTAGRAM", SESSION, "INSTAGRAM", "PAGE", "ig", "Demo", "page",
                Collections.singletonList("instagram_basic"), "v27.0", "bearer", null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new OAuthExchangeResponse(SESSION, "FACEBOOK", "page", "PAGE", "Demo", null,
                        null, "bearer", null));
    }

    @Test public void mapsActualInstagramExchangeAndRetainsDeadlineProvenance() {
        String dataExpiry = "2033-05-18T03:33:20Z";
        String tokenExpiry = "2034-05-18T03:33:20Z";
        OAuthExchangeResponse response = new OAuthExchangeResponse(SESSION, "INSTAGRAM",
                "17841459976957663", "PROFESSIONAL", "Demo", "fake-token", null, "bearer",
                dataExpiry, "1234567890", Collections.singletonList("instagram_basic"),
                "v27.0", tokenExpiry, dataExpiry);
        Grant grant = ConnectionLifecycle.fromExchange(SESSION, "INSTAGRAM", response);
        assertEquals("17841459976957663", grant.identity.externalAccountId);
        assertEquals("1234567890", grant.linkedPageId);
        assertEquals(Long.valueOf(Instant.parse(tokenExpiry).toEpochMilli()), grant.tokenExpiresAtMillis);
        assertEquals(Long.valueOf(Instant.parse(dataExpiry).toEpochMilli()), grant.dataAccessExpiresAtMillis);
        assertEquals(grant.dataAccessExpiresAtMillis, grant.effectiveExpiryMillis());
        assertThrows(IllegalArgumentException.class, () ->
                ConnectionLifecycle.fromExchange(SESSION, "FACEBOOK", response));

        Grant legacy = ConnectionLifecycle.fromExchange(SESSION, "INSTAGRAM", new OAuthExchangeResponse(
                SESSION, "INSTAGRAM", "17841459976957663", "PROFESSIONAL", "Demo", "fake-token",
                null, "bearer", dataExpiry, "1234567890",
                Collections.singletonList("instagram_basic"), "v27.0"));
        assertNull(legacy.tokenExpiresAtMillis);
        assertNull(legacy.dataAccessExpiresAtMillis);
        assertEquals(grant.dataAccessExpiresAtMillis, legacy.effectiveExpiryMillis());
    }

    @Test public void expiryUsesEarlierKnownDeadlineAndUnknownDoesNotExpire() {
        Grant grant = instagram("ig", "page", 2000L, 1000L);
        Connection current = ConnectionLifecycle.connected(grant, "ig-demo", null);
        assertEquals(Long.valueOf(2000), grant.tokenExpiresAtMillis);
        assertEquals(Long.valueOf(1000), grant.dataAccessExpiresAtMillis);
        assertSame(current, ConnectionLifecycle.checkExpiry(current, 999).connection);
        assertReauth(ConnectionLifecycle.checkExpiry(current, 1000));
        assertReauth(ConnectionLifecycle.checkExpiry(current, 1001));
        assertEquals(Long.valueOf(1000), instagram("ig", "page", 1000L, 2000L).effectiveExpiryMillis());
        assertEquals(Long.valueOf(1000), instagram("ig", "page", 1000L, null).effectiveExpiryMillis());
        assertEquals(Long.valueOf(1000), instagram("ig", "page", null, 1000L).effectiveExpiryMillis());
        Connection unknown = ConnectionLifecycle.connected(facebook("page"), "fb-demo", null);
        assertNull(unknown.grant.effectiveExpiryMillis());
        assertSame(unknown, ConnectionLifecycle.checkExpiry(unknown, Long.MAX_VALUE).connection);
    }

    @Test public void callbackAndReconnectPreserveOtherIdentitiesAndRejectLateWrites() {
        FixtureRepository repo = new FixtureRepository();
        Grant page = facebook("page");
        Grant profile = instagram("ig", "page", null, null);
        assertTrue(repo.commit(page, "fb-demo", null, "fake-ref-fb"));
        assertTrue(repo.commit(profile, "ig-demo", null, "fake-ref-ig"));
        assertSame(repo.find(profile.identity), repo.find("ig-demo"));
        ChannelInfo pageInfo = repo.facebookInfo("fb-demo").channel;
        assertEquals("fake-ref-fb", pageInfo.tokenReference);
        assertEquals(Platform.FACEBOOK, pageInfo.platform);
        assertEquals(SocialError.Code.AUTH_REQUIRED, repo.facebookInfo("missing-channel").error.code);
        assertFalse(repo.commit(profile, "duplicate", null, "fake-ref-duplicate"));
        Scope oldScope = repo.find(profile.identity).scope;
        assertNotNull(repo.update(oldScope, current -> ConnectionLifecycle.operationVerified(current,
                new ChannelCapability(SocialOperation.READ_COMMENTS, CapabilityAvailability.AVAILABLE,
                        null, VERIFIED_AT))));

        assertFalse(repo.commit(profile, "unused", oldScope, null));
        assertEquals("fake-ref-ig", repo.references.get("ig-demo"));
        assertEquals(CapabilityAvailability.AVAILABLE,
                repo.find(profile.identity).capability(SocialOperation.READ_COMMENTS).availability);
        assertTrue(repo.commit(profile, "unused", oldScope, "fake-ref-ig-reconnect"));
        assertFalse(repo.commit(profile, "unused", oldScope, "fake-ref-late-callback"));
        assertNull(repo.update(oldScope, ConnectionLifecycle::revoked));
        assertEquals("fake-ref-ig-reconnect", repo.references.get("ig-demo"));
        Connection reconnected = repo.find(profile.identity);
        assertEquals("ig-demo", reconnected.scope.channelId);
        assertEquals(2, reconnected.scope.connectionRevision);
        assertEquals(CapabilityAvailability.UNVERIFIED,
                reconnected.capability(SocialOperation.READ_COMMENTS).availability);

        assertReauth(repo.update(reconnected.scope, ConnectionLifecycle::revoked));
        assertEquals(ConnectionStatus.CONNECTED, repo.find(page.identity).status);
        assertReauth(repo.update(repo.find(page.identity).scope, ConnectionLifecycle::revoked));
        assertEquals(ConnectionStatus.REAUTH_REQUIRED, repo.facebookInfo("fb-demo").channel.connectionStatus);
        assertEquals("fake-ref-fb", repo.facebookInfo("fb-demo").channel.tokenReference);
        repo.references.remove("fb-demo");
        assertNull(repo.facebookInfo("fb-demo").channel);
        assertEquals(SocialError.NextAction.RECONNECT, repo.facebookInfo("fb-demo").error.nextAction);
        Grant differentProfile = instagram("other-ig", "page", null, null);
        assertFalse(repo.commit(differentProfile, "other", reconnected.scope, "fake-ref-other"));
        assertTrue(repo.commit(differentProfile, "other", null, "fake-ref-other"));
        assertEquals(ConnectionStatus.REAUTH_REQUIRED, repo.find(profile.identity).status);
        assertEquals(ConnectionStatus.CONNECTED, repo.find(differentProfile.identity).status);
        assertEquals(3, repo.connections.size());
    }

    @Test public void operationalFailuresPreserveStatusAndVerifiedCapabilities() {
        Connection current = ConnectionLifecycle.connected(facebook("page"), "fb-demo", null);
        current = ConnectionLifecycle.operationVerified(current, new ChannelCapability(
                SocialOperation.READ_COMMENTS, CapabilityAvailability.AVAILABLE, null, VERIFIED_AT)).connection;
        Decision permission = ConnectionLifecycle.operationVerified(current, new ChannelCapability(
                SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.PERMISSION_REQUIRED,
                "MISSING_FOLLOWER_PERMISSION", VERIFIED_AT));
        assertEquals(ConnectionStatus.CONNECTED, permission.connection.status);
        assertEquals(SocialError.Code.PERMISSION_REQUIRED, permission.error.code);
        assertEquals(SocialError.NextAction.RESOLVE_PERMISSION, permission.error.nextAction);
        assertEquals(CapabilityAvailability.AVAILABLE,
                permission.connection.capability(SocialOperation.READ_COMMENTS).availability);
        for (SocialError failure : new SocialError[] {
                new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE),
                new SocialError(SocialError.Code.RATE_LIMITED, SocialError.NextAction.WAIT)}) {
            Decision result = ConnectionLifecycle.operationFailed(permission.connection, failure);
            assertSame(permission.connection, result.connection);
            assertSame(failure, result.error);
        }
    }

    @Test public void consumerFixtureUsesCompatibleScopesAndRevisionWriteGuard() throws Exception {
        Gson gson = new Gson();
        JsonObject fixture;
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/social/sp-022-consumer-fixtures.json")), StandardCharsets.UTF_8)) {
            fixture = gson.fromJson(reader, JsonObject.class);
        }
        assertEquals("MO_PHONG", fixture.get("source").getAsString());
        JsonObject events = fixture.getAsJsonObject("events");
        JsonObject comments = events.getAsJsonObject("comments");
        JsonObject reauth = events.getAsJsonObject("reauth");
        JsonObject permission = events.getAsJsonObject("followerPermission");
        assertEquals("CONNECTED", comments.get("status").getAsString());
        assertEquals("REAUTH_REQUIRED", reauth.get("status").getAsString());
        assertEquals("CONNECTED", permission.get("status").getAsString());
        assertEquals("AVAILABLE", comments.getAsJsonObject("capability").get("availability").getAsString());
        assertEquals("RECONNECT", reauth.getAsJsonObject("error").get("nextAction").getAsString());
        assertEquals("PERMISSION_REQUIRED", permission.getAsJsonObject("capability").get("availability").getAsString());
        Scope old = gson.fromJson(comments.get("scope"), Scope.class);
        Scope current = gson.fromJson(permission.get("scope"), Scope.class);
        assertTrue(old.matches(gson.fromJson(reauth.get("scope"), Scope.class)));
        assertFalse(old.matches(current));
        assertFalse(current.matches(new Scope("FACEBOOK", current.channelId, current.connectionRevision)));
        JsonObject write = fixture.getAsJsonObject("rejectedWrite");
        assertTrue(old.matches(gson.fromJson(write.get("responseScope"), Scope.class)));
        assertTrue(current.matches(gson.fromJson(write.get("currentScope"), Scope.class)));
        assertFalse(write.get("applied").getAsBoolean());
    }

    private static Grant facebook(String id) {
        return ConnectionLifecycle.fromExchange(SESSION, "FACEBOOK", facebookResponse(id, "PAGE"));
    }

    private static OAuthExchangeResponse facebookResponse(String id, String type) {
        return new OAuthExchangeResponse(SESSION, "FACEBOOK", id, type, "Demo Page",
                "fake-test-credential", null, "bearer", null);
    }

    private static Grant instagram(String id, String pageId, Long tokenExpiry, Long dataAccessExpiry) {
        return ConnectionLifecycle.validateGrant(SESSION, "INSTAGRAM", SESSION, "INSTAGRAM",
                "PROFESSIONAL", id, "Demo Profile", pageId, Collections.singletonList("instagram_basic"),
                "v27.0", "bearer", tokenExpiry, dataAccessExpiry);
    }

    private static void assertReauth(Decision decision) {
        assertEquals(ConnectionStatus.REAUTH_REQUIRED, decision.connection.status);
        assertEquals(SocialError.Code.AUTH_REQUIRED, decision.error.code);
        assertEquals(SocialError.NextAction.RECONNECT, decision.error.nextAction);
    }

    private static final class FixtureLookup {
        final ChannelInfo channel;
        final SocialError error;

        FixtureLookup(ChannelInfo channel, SocialError error) {
            this.channel = channel;
            this.error = error;
        }
    }

    /** Models the atomic metadata write boundary pending the Room and SP-027 handoffs. */
    private static final class FixtureRepository {
        private final Map<Identity, Connection> connections = new HashMap<>();
        private final Map<String, String> references = new HashMap<>();

        synchronized Connection find(Identity identity) {
            return connections.get(identity);
        }

        synchronized Connection find(String channelId) {
            for (Connection connection : connections.values()) {
                if (channelId.equals(connection.scope.channelId)) return connection;
            }
            return null;
        }

        synchronized FixtureLookup facebookInfo(String channelId) {
            Connection connection = find(channelId);
            String reference = references.get(channelId);
            if (connection == null || reference == null || !"FACEBOOK".equals(connection.scope.platform)) {
                return new FixtureLookup(null,
                        new SocialError(SocialError.Code.AUTH_REQUIRED, SocialError.NextAction.RECONNECT));
            }
            ChannelInfo info = new ChannelInfo(channelId, Platform.FACEBOOK, connection.grant.identity.externalAccountId,
                    AccountType.PAGE, connection.grant.displayName, connection.status,
                    connection.scope.connectionRevision, reference);
            return new FixtureLookup(info, null);
        }

        synchronized boolean commit(Grant grant, String channelId, Scope expected, String storedReference) {
            Connection previous = find(grant.identity);
            boolean canWrite = previous == null ? expected == null : previous.scope.matches(expected);
            if (!canWrite || storedReference == null || (previous == null && find(channelId) != null)) return false;
            Connection committed = ConnectionLifecycle.connected(grant, channelId, previous);
            references.put(committed.scope.channelId, storedReference);
            connections.put(grant.identity, committed);
            return true;
        }

        synchronized Decision update(Scope requested, Function<Connection, Decision> decide) {
            for (Map.Entry<Identity, Connection> entry : connections.entrySet()) {
                if (!requested.matches(entry.getValue().scope)) continue;
                Decision decision = decide.apply(entry.getValue());
                entry.setValue(decision.connection);
                return decision;
            }
            return null;
        }
    }
}
