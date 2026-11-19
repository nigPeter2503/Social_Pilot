package com.socialpilot.app.data.remote.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static com.socialpilot.app.data.remote.social.FollowerRequest.Account.*;
import static com.socialpilot.app.data.remote.social.FollowerResult.*;
import static org.junit.Assert.*;

/** Every HTTP response here is MO_PHONG; live provider operations are NOT_RUN. */
public class FollowerMetricAdapterTest {
    private static final long NOW = 1795060800000L;
    private MockWebServer server;
    private OkHttpClient client;
    private AtomicReference<FollowerMetricAdapter.Binding> connection;
    private AtomicInteger credentialReads;
    private FollowerMetricAdapter adapter;

    @Before public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient();
        connection = new AtomicReference<>();
        credentialReads = new AtomicInteger();
        adapter = new FollowerMetricAdapter(client, ignored -> connection.get(), channel -> {
            credentialReads.incrementAndGet(); return "synthetic-token";
        }, () -> NOW, server.url("/"), server.url("/rest/"), Source.MO_PHONG, "sp045-offline-fixtures");
    }

    @After public void tearDown() throws Exception {
        server.shutdown();
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdownNow();
    }

    private FollowerRequest request(FollowerRequest.Account account) {
        return new FollowerRequest(account, "channel-a", 7,
                account == FACEBOOK_PAGE ? "synthetic-page" : account == LINKEDIN_MEMBER
                        ? "urn:li:person:synthetic-member" : "urn:li:organization:9000000000000000010",
                account == FACEBOOK_PAGE ? "v27.0" : "202610", "synthetic-collection");
    }

    private ChannelInfo channel(FollowerRequest r) {
        return new ChannelInfo(r.channelId, r.platform, r.externalAccountId,
                r.account == FACEBOOK_PAGE ? AccountType.PAGE : r.account == LINKEDIN_MEMBER
                        ? AccountType.PERSON : AccountType.ORGANIZATION,
                "Fixture channel", ConnectionStatus.CONNECTED, r.connectionRevision, "synthetic-secret-ref");
    }

    private ChannelCapability capability(SocialOperation op, CapabilityAvailability availability) {
        return new ChannelCapability(op, availability,
                availability == CapabilityAvailability.AVAILABLE ? null : "synthetic-evidence",
                "2026-11-19T04:00:00Z");
    }

    private void bind(FollowerRequest r) {
        connection.set(new FollowerMetricAdapter.Binding(channel(r), r,
                capability(SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.AVAILABLE), null));
    }

    private FollowerResult read(FollowerRequest r) throws Exception {
        AtomicReference<FollowerResult> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        adapter.read(r, value -> { result.set(value); done.countDown(); });
        assertTrue("background callback", done.await(5, TimeUnit.SECONDS));
        return result.get();
    }

    private FollowerResult response(FollowerRequest r, int status, String body) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(status).setBody(body));
        return read(r);
    }

    private JsonObject fixture(String name) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/followers/" + name + ".json")) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private void missing(FollowerResult result, MissingReason reason) {
        assertNotNull(result.sample);
        assertNull(result.sample.value);
        assertEquals(reason, result.sample.missingReason);
    }

    @Test public void facebookFixtureZeroAndProvenance() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        FollowerResult result = response(r, 200, fixture("facebook-sp018").get("followerResponse").toString());
        assertEquals(Status.SUCCESS, result.status);
        assertEquals(Long.valueOf(0), result.sample.value);
        assertNull(result.sample.missingReason);
        assertEquals(MetricKey.FOLLOWER_COUNT, result.definition.metricKey);
        assertEquals(MeasurementKind.GAUGE, result.definition.measurementKind);
        assertEquals(Unit.COUNT, result.definition.unit);
        assertEquals(Aggregation.LATEST, result.definition.aggregation);
        assertEquals("followers_count", result.definition.providerMetricName);
        assertEquals(Source.MO_PHONG, result.sample.source);
        assertEquals("sp045-offline-fixtures", result.sample.sourceRef);
        assertEquals("v27.0", result.sample.providerApiVersion);
        assertEquals(r.platform, result.sample.scope.platform);
        assertEquals(r.channelId, result.sample.scope.channelId);
        assertEquals(r.connectionRevision, result.sample.scope.connectionRevision);
        assertEquals(r.channelId, result.sample.entityId);
        assertEquals(r.collectionId, result.sample.collectionId);
        assertEquals(NOW, result.sample.collectedAt);
        assertEquals(NOW, result.sample.observedAt);
        assertEquals(TimeBasis.COLLECTION, result.sample.observedAtBasis);
        assertNull(result.sample.providerPeriodStart);
        assertNull(result.sample.providerPeriodEnd);
        RecordedRequest wire = server.takeRequest();
        assertEquals("GET", wire.getMethod());
        assertEquals("/v27.0/synthetic-page?fields=id%2Cfollowers_count", wire.getPath());
        assertEquals("Bearer synthetic-token", wire.getHeader("Authorization"));
        assertFalse(wire.getPath().contains("token"));
    }

    @Test public void memberFixtureZeroUsesMeAndVersionHeaders() throws Exception {
        FollowerRequest r = request(LINKEDIN_MEMBER); bind(r);
        FollowerResult result = response(r, 200, fixture("linkedin-sp019").get("memberFollowers").toString());
        assertEquals(Long.valueOf(0), result.sample.value);
        assertEquals("memberFollowersCount", result.definition.providerMetricName);
        assertEquals(MeasurementKind.GAUGE, result.definition.measurementKind);
        RecordedRequest wire = server.takeRequest();
        assertEquals("/rest/memberFollowersCount?q=me", wire.getPath());
        assertEquals("202610", wire.getHeader("LinkedIn-Version"));
        assertEquals("2.0.0", wire.getHeader("X-Restli-Protocol-Version"));
    }

    @Test public void organizationFixtureUsesFollowerEdge() throws Exception {
        FollowerRequest r = request(LINKEDIN_ORGANIZATION); bind(r);
        FollowerResult result = response(r, 200, fixture("linkedin-sp019").get("organizationFollowers").toString());
        assertEquals(Long.valueOf(150), result.sample.value);
        assertEquals("networkSizes.firstDegreeSize:COMPANY_FOLLOWED_BY_MEMBER", result.definition.providerMetricName);
        assertEquals("/rest/networkSizes/urn:li:organization:9000000000000000010?edgeType=COMPANY_FOLLOWED_BY_MEMBER",
                server.takeRequest().getPath());
    }

    @Test public void zeroIsValidForAllAccounts() throws Exception {
        for (FollowerRequest.Account account : FollowerRequest.Account.values()) {
            FollowerRequest r = request(account); bind(r);
            String body = account == FACEBOOK_PAGE ? "{\"id\":\"synthetic-page\",\"followers_count\":0}"
                    : account == LINKEDIN_MEMBER ? "{\"elements\":[{\"memberFollowersCount\":0}]}" : "{\"firstDegreeSize\":0}";
            FollowerResult result = response(r, 200, body);
            assertEquals(Long.valueOf(0), result.sample.value);
            assertNull(result.sample.missingReason);
        }
    }

    @Test public void missingFieldNeverFallsBackToOtherCounts() throws Exception {
        for (FollowerRequest.Account account : FollowerRequest.Account.values()) {
            FollowerRequest r = request(account); bind(r);
            String body = account == FACEBOOK_PAGE ? "{\"id\":\"synthetic-page\",\"fan_count\":42}"
                    : account == LINKEDIN_MEMBER ? "{\"elements\":[{\"connections\":42}]}" : "{\"connections\":42}";
            FollowerResult result = response(r, 200, body);
            assertEquals(Status.SUCCESS, result.status);
            missing(result, MissingReason.PROVIDER_OMITTED);
        }
    }

    @Test public void nullFieldAndEmptyMemberElementsAreMissing() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        missing(response(r, 200, "{\"id\":\"synthetic-page\",\"followers_count\":null}"), MissingReason.PROVIDER_OMITTED);
        r = request(LINKEDIN_MEMBER); bind(r);
        missing(response(r, 200, "{\"elements\":[]}"), MissingReason.PROVIDER_OMITTED);
    }

    @Test public void invalidCountsAreNotOmittedOrZero() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        for (String value : new String[] {"-1", "1.5", "\"0\"", "true", "9223372036854775808", "{}"}) {
            FollowerResult result = response(r, 200, "{\"id\":\"synthetic-page\",\"followers_count\":" + value + "}");
            assertEquals(Failure.INVALID_RESPONSE, result.failure);
            missing(result, MissingReason.UNVERIFIED);
        }
        assertEquals(Long.valueOf(Long.MAX_VALUE), response(r, 200,
                "{\"id\":\"synthetic-page\",\"followers_count\":9223372036854775807}").sample.value);
    }

    @Test public void memberRejectsPeriodAndAmbiguousTotals() throws Exception {
        FollowerRequest r = request(LINKEDIN_MEMBER); bind(r);
        for (String body : new String[] {"{\"elements\":[{\"dateRange\":{},\"memberFollowersCount\":12}]}",
                "{\"elements\":[{\"memberFollowersCount\":1},{\"memberFollowersCount\":2}]}", "{}", "{\"elements\":[null]}"}) {
            assertEquals(Failure.INVALID_RESPONSE, response(r, 200, body).failure);
        }
    }

    @Test public void wrongPageIdIsDiscarded() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        FollowerResult result = response(r, 200, "{\"id\":\"other-page\",\"followers_count\":99}");
        assertEquals(Status.REJECTED, result.status);
        assertEquals(Failure.SCOPE_MISMATCH, result.failure);
        assertNull(result.sample);
    }

    @Test public void wrongAccountChannelPlatformOrRevisionNeverSends() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE);
        ChannelInfo c = channel(r);
        for (ChannelInfo wrong : new ChannelInfo[] {
                channel(request(LINKEDIN_MEMBER)),
                new ChannelInfo("other-channel", c.platform, c.externalAccountId, c.accountType, c.displayName,
                        c.connectionStatus, c.connectionRevision, c.tokenReference),
                new ChannelInfo(c.channelId, c.platform, "other-page", c.accountType, c.displayName,
                        c.connectionStatus, c.connectionRevision, c.tokenReference),
                new ChannelInfo(c.channelId, c.platform, c.externalAccountId, AccountType.PERSON, c.displayName,
                        c.connectionStatus, c.connectionRevision, c.tokenReference),
                new ChannelInfo(c.channelId, c.platform, c.externalAccountId, c.accountType, c.displayName,
                        c.connectionStatus, 8, c.tokenReference)}) {
            connection.set(new FollowerMetricAdapter.Binding(wrong, r,
                    capability(SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.AVAILABLE), null));
            assertEquals(Failure.SCOPE_MISMATCH, read(r).failure);
        }
        assertEquals(0, credentialReads.get());
        assertEquals(0, server.getRequestCount());
    }

    @Test public void publishCapabilityDoesNotAdmitFollowerRead() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE);
        connection.set(new FollowerMetricAdapter.Binding(channel(r), r,
                capability(SocialOperation.PUBLISH_TEXT, CapabilityAvailability.AVAILABLE), null));
        assertNull(read(r).sample);
        assertEquals(0, server.getRequestCount());
        assertEquals(0, credentialReads.get());
    }

    @Test public void capabilityForOldRevisionOrOtherAccountNeverSends() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE);
        for (FollowerRequest stale : new FollowerRequest[] {
                new FollowerRequest(FACEBOOK_PAGE, r.channelId, 6, r.externalAccountId, r.apiVersion, r.collectionId),
                request(LINKEDIN_MEMBER),
                new FollowerRequest(FACEBOOK_PAGE, r.channelId, 7, r.externalAccountId, "v26.0", r.collectionId)}) {
            connection.set(new FollowerMetricAdapter.Binding(channel(r), stale,
                    capability(SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.AVAILABLE), null));
            FollowerResult result = read(r);
            assertEquals(Failure.CAPABILITY_BLOCKED, result.failure);
            assertNull(result.sample);
        }
        assertEquals(0, server.getRequestCount());
        assertEquals(0, credentialReads.get());
    }

    @Test public void credentialLookupRevisionChangePreventsSend() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        adapter = new FollowerMetricAdapter(client, ignored -> connection.get(), channel -> {
            bind(new FollowerRequest(FACEBOOK_PAGE, r.channelId, 8, r.externalAccountId, r.apiVersion, r.collectionId));
            return "synthetic-token";
        }, () -> NOW, server.url("/"), server.url("/rest/"), Source.MO_PHONG, "synthetic-credential-race");
        assertEquals(Failure.SCOPE_MISMATCH, read(r).failure);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void missingCredentialRequiresReauthentication() throws Exception {
        FollowerRequest r = request(LINKEDIN_MEMBER); bind(r);
        adapter = new FollowerMetricAdapter(client, ignored -> connection.get(), channel -> null,
                () -> NOW, server.url("/"), server.url("/rest/"), Source.MO_PHONG, "synthetic-no-credential");
        FollowerResult result = read(r);
        missing(result, MissingReason.AUTH_REQUIRED);
        assertEquals(SocialError.NextAction.RECONNECT, result.error.nextAction);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void transportFailureIsNullWithNetworkReason() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        server.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START));
        FollowerResult result = read(r);
        missing(result, MissingReason.NETWORK_ERROR);
        assertEquals(SocialError.Code.NETWORK_ERROR, result.error.code);
        assertEquals(SocialError.NextAction.RETRY_WHEN_SAFE, result.error.nextAction);
    }

    @Test public void credentialExceptionIsLocalWithoutProviderInference() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        adapter = new FollowerMetricAdapter(client, ignored -> connection.get(), channel -> {
            throw new IllegalStateException("synthetic-private-detail");
        }, () -> NOW, server.url("/"), server.url("/rest/"), Source.MO_PHONG, "synthetic-local-error");
        FollowerResult result = read(r);
        assertEquals(Failure.LOCAL_ERROR, result.failure);
        assertNull(result.sample);
        assertNull(result.error);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void capabilityBlocksWithoutNetwork() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE);
        CapabilityAvailability[] availability = {CapabilityAvailability.UNVERIFIED,
                CapabilityAvailability.PERMISSION_REQUIRED, CapabilityAvailability.UNSUPPORTED};
        MissingReason[] reasons = {MissingReason.UNVERIFIED, MissingReason.PERMISSION_REQUIRED, MissingReason.UNSUPPORTED};
        for (int i = 0; i < availability.length; i++) {
            connection.set(new FollowerMetricAdapter.Binding(channel(r), r,
                    capability(SocialOperation.READ_FOLLOWER_COUNT, availability[i]), null));
            missing(read(r), reasons[i]);
        }
        assertEquals(0, server.getRequestCount());
        assertEquals(0, credentialReads.get());
    }

    @Test public void disconnectedAndReauthNeverSend() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); ChannelInfo c = channel(r);
        for (ConnectionStatus status : new ConnectionStatus[] {ConnectionStatus.DISCONNECTED, ConnectionStatus.REAUTH_REQUIRED}) {
            ChannelInfo stopped = new ChannelInfo(c.channelId, c.platform, c.externalAccountId, c.accountType,
                    c.displayName, status, c.connectionRevision, status == ConnectionStatus.DISCONNECTED ? null : c.tokenReference);
            connection.set(new FollowerMetricAdapter.Binding(stopped, r,
                    capability(SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.AVAILABLE), null));
            assertEquals(Status.REJECTED, read(r).status);
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test public void revisionChangeWhileReceivingDiscardsResult() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest wire) {
                FollowerRequest newer = new FollowerRequest(FACEBOOK_PAGE, r.channelId, 8, r.externalAccountId, r.apiVersion, r.collectionId);
                bind(newer);
                return new MockResponse().setBody("{\"id\":\"synthetic-page\",\"followers_count\":88}");
            }
        });
        FollowerResult result = read(r);
        assertEquals(Failure.SCOPE_MISMATCH, result.failure);
        assertNull(result.sample);
    }

    @Test public void capabilityRevocationWhileReceivingBlocksValue() throws Exception {
        FollowerRequest r = request(LINKEDIN_MEMBER); bind(r);
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest wire) {
                connection.set(new FollowerMetricAdapter.Binding(channel(r), r,
                        capability(SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.PERMISSION_REQUIRED), null));
                return new MockResponse().setBody("{\"elements\":[{\"memberFollowersCount\":88}]}");
            }
        });
        missing(read(r), MissingReason.PERMISSION_REQUIRED);
    }

    @Test public void linkedinErrorFixturesDoNotInventForbiddenCause() throws Exception {
        FollowerRequest r = request(LINKEDIN_MEMBER); bind(r);
        for (com.google.gson.JsonElement element : fixture("linkedin-sp019").getAsJsonArray("errors")) {
            JsonObject row = element.getAsJsonObject();
            int status = row.get("httpStatus").getAsInt();
            FollowerResult result = response(r, status, row.get("body").toString());
            assertEquals(Status.FAILURE, result.status);
            missing(result, status == 401 ? MissingReason.AUTH_REQUIRED : status == 429 ? MissingReason.RATE_LIMITED : MissingReason.UNVERIFIED);
            if (status == 403 || status == 400 || status == 500) assertNull(result.error);
        }
    }

    @Test public void facebook100NeedsEvidenceForExactScopeAndVersion() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        String body = "{\"error\":{\"code\":100,\"message\":\"Synthetic permission text is not evidence\"}}";
        missing(response(r, 400, body), MissingReason.UNVERIFIED);
        connection.set(new FollowerMetricAdapter.Binding(channel(r), r,
                capability(SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.AVAILABLE),
                new FollowerMetricAdapter.PermissionEvidence(r, 100, "MO_PHONG:fb10-exact-request-evidence")));
        FollowerResult classified = response(r, 400, body);
        missing(classified, MissingReason.PERMISSION_REQUIRED);
        assertEquals(SocialError.Code.PERMISSION_REQUIRED, classified.error.code);
        assertEquals(SocialError.NextAction.RESOLVE_PERMISSION, classified.error.nextAction);
        for (FollowerRequest wrong : new FollowerRequest[] {
                new FollowerRequest(FACEBOOK_PAGE, r.channelId, 6, r.externalAccountId, r.apiVersion, r.collectionId),
                new FollowerRequest(FACEBOOK_PAGE, "other", 7, r.externalAccountId, r.apiVersion, r.collectionId),
                new FollowerRequest(FACEBOOK_PAGE, r.channelId, 7, "other-page", r.apiVersion, r.collectionId),
                new FollowerRequest(FACEBOOK_PAGE, r.channelId, 7, r.externalAccountId, "v26.0", r.collectionId), request(LINKEDIN_MEMBER)}) {
            connection.set(new FollowerMetricAdapter.Binding(channel(r), r,
                    capability(SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.AVAILABLE),
                    new FollowerMetricAdapter.PermissionEvidence(wrong, 100, "MO_PHONG:mismatched-evidence")));
            missing(response(r, 400, body), MissingReason.UNVERIFIED);
        }
    }

    @Test public void facebookErrorFixturesAreConservative() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        for (com.google.gson.JsonElement element : fixture("facebook-sp018").getAsJsonArray("errors")) {
            JsonObject row = element.getAsJsonObject();
            int status = row.get("httpStatus").getAsInt();
            FollowerResult result = response(r, status, row.get("body").toString());
            String caseId = row.get("caseId").getAsString();
            MissingReason expected = caseId.equals("expired") ? MissingReason.AUTH_REQUIRED
                    : caseId.contains("rate") ? MissingReason.RATE_LIMITED : MissingReason.UNVERIFIED;
            missing(result, expected);
        }
    }

    @Test public void retryAfterSecondsDateAndInvalidHeader() throws Exception {
        FollowerRequest r = request(LINKEDIN_ORGANIZATION); bind(r);
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "60"));
        FollowerResult result = read(r);
        missing(result, MissingReason.RATE_LIMITED);
        assertEquals(Long.valueOf(NOW + 60000), result.retryAfterAt);
        assertEquals(SocialError.NextAction.WAIT, result.error.nextAction);
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "Wed, 25 Nov 2026 12:00:00 GMT"));
        assertNotNull(read(r).retryAfterAt);
        for (String header : new String[] {"invalid", "-1", "9223372036854775807"}) {
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", header));
            assertNull(read(r).retryAfterAt);
        }
    }

    @Test public void malformedSuccessfulBodyAndErrorEnvelopeDoNotBecomeZero() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        for (String body : new String[] {"not-json", "[]", "null", "{\"error\":{\"code\":100}}"}) {
            FollowerResult result = response(r, 200, body);
            assertEquals(Status.FAILURE, result.status);
            missing(result, MissingReason.UNVERIFIED);
        }
    }

    @Test public void redirectsAreNotFollowed() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", server.url("/other-account")));
        missing(read(r), MissingReason.UNVERIFIED);
        assertEquals(1, server.getRequestCount());
    }

    @Test public void cancelSuppressesLateCallback() throws Exception {
        FollowerRequest r = request(FACEBOOK_PAGE); bind(r);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest wire) throws InterruptedException {
                entered.countDown(); release.await(5, TimeUnit.SECONDS);
                return new MockResponse().setBody("{\"id\":\"synthetic-page\",\"followers_count\":9}");
            }
        });
        CountDownLatch callback = new CountDownLatch(1);
        Cancelable handle = adapter.read(r, ignored -> callback.countDown());
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        handle.cancel(); release.countDown();
        assertFalse(callback.await(250, TimeUnit.MILLISECONDS));
    }

    @Test public void definitionsSeparateAccountsAndVersions() {
        MetricDefinition fb = new MetricDefinition(request(FACEBOOK_PAGE));
        MetricDefinition member = new MetricDefinition(request(LINKEDIN_MEMBER));
        MetricDefinition org = new MetricDefinition(request(LINKEDIN_ORGANIZATION));
        assertNotEquals(fb.definitionId, member.definitionId);
        assertNotEquals(member.definitionId, org.definitionId);
        FollowerRequest version = new FollowerRequest(FACEBOOK_PAGE, "a", 1, "p", "v28.0", "c");
        assertNotEquals(fb.definitionId, new MetricDefinition(version).definitionId);
    }

    @Test public void invalidTypedRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FollowerRequest(LINKEDIN_MEMBER,
                "a", 1, "urn:li:organization:123", "202610", "c"));
        assertThrows(IllegalArgumentException.class, () -> new FollowerRequest(LINKEDIN_ORGANIZATION,
                "a", 1, "urn:li:person:abc", "202610", "c"));
        assertThrows(IllegalArgumentException.class, () -> new FollowerRequest(FACEBOOK_PAGE,
                "a", 0, "p", "v27.0", "c"));
    }
}
