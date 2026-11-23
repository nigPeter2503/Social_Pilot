package com.socialpilot.app.data.remote.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static com.socialpilot.app.data.remote.social.PostRequest.Query.*;
import static com.socialpilot.app.data.remote.social.PostResult.*;
import static org.junit.Assert.*;

/** All raw JSON, permission evidence, dates, tokens and HTTP replies are MO_PHONG. Live NOT_RUN. */
public class PostMetricAdapterTest {
    private static final long NOW = 1795060800000L;
    private MockWebServer server;
    private OkHttpClient client;
    private AtomicReference<PostMetricAdapter.Binding> connection;
    private AtomicInteger credentialReads;
    private PostMetricAdapter adapter;

    @Before public void setUp() throws Exception {
        server = new MockWebServer(); server.start();
        client = new OkHttpClient();
        connection = new AtomicReference<>();
        credentialReads = new AtomicInteger();
        adapter = makeAdapter(ignored -> connection.get(), channel -> {
            credentialReads.incrementAndGet(); return "MO_PHONG-token";
        });
    }
    private PostMetricAdapter makeAdapter(PostMetricAdapter.ConnectionSource source, PostMetricAdapter.CredentialSource credentials) {
        return new PostMetricAdapter(client, source, credentials, () -> NOW, server.url("/"), server.url("/rest/"),
                FollowerResult.Source.MO_PHONG, "sp045-MO_PHONG");
    }
    @After public void tearDown() throws Exception {
        server.shutdown(); client.connectionPool().evictAll(); client.dispatcher().executorService().shutdownNow();
    }
    private PostRequest request(PostRequest.Query query) { return request(query, null, null); }
    private PostRequest request(PostRequest.Query query, PostRequest.Period period, String post) {
        FollowerRequest.Account account = query.name().startsWith("FB_") ? FollowerRequest.Account.FACEBOOK_PAGE
                : query == ORGANIZATION_ORGANIC ? FollowerRequest.Account.LINKEDIN_ORGANIZATION : FollowerRequest.Account.LINKEDIN_MEMBER;
        return new PostRequest(account, "channel-a", 7, account == FollowerRequest.Account.FACEBOOK_PAGE ? "synthetic-page"
                : account == FollowerRequest.Account.LINKEDIN_MEMBER ? "urn:li:person:synthetic-member"
                : "urn:li:organization:9000000000000000010", post != null ? post : account == FollowerRequest.Account.FACEBOOK_PAGE
                ? "synthetic-page_synthetic-post" : "urn:li:share:9000000000000000001",
                account == FollowerRequest.Account.FACEBOOK_PAGE ? "v27.0" : "202610", "collection-MO_PHONG", query, period);
    }
    private PostRequest.Period period() {
        return new PostRequest.Period("2026-11-01", "2026-11-08", "UTC", "MO_PHONG-date-zone-evidence");
    }
    private ChannelInfo channel(PostRequest r) {
        return new ChannelInfo(r.channelId, r.platform, r.externalAccountId, r.account.type,
                "MO_PHONG", ConnectionStatus.CONNECTED, r.connectionRevision, "MO_PHONG-secret-ref");
    }
    private ChannelCapability capability(SocialOperation operation, CapabilityAvailability availability) {
        return new ChannelCapability(operation, availability, availability == CapabilityAvailability.AVAILABLE ? null : "MO_PHONG-evidence",
                "2026-11-19T04:00:00Z");
    }
    private Map<Metric, ChannelCapability> grants(PostRequest r) {
        Map<Metric, ChannelCapability> grants = new EnumMap<>(Metric.class);
        for (MetricDefinition d : PostMetricMapping.definitions(r)) grants.put(d.metricKey,
                capability(SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        return grants;
    }
    private void bind(PostRequest r) {
        connection.set(binding(r, r, grants(r), null, SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
    }
    private PostMetricAdapter.Binding binding(PostRequest r, PostRequest capScope, Map<Metric, ChannelCapability> grants,
            PostMetricAdapter.PermissionEvidence evidence, SocialOperation op, CapabilityAvailability availability) {
        return new PostMetricAdapter.Binding(channel(r), capScope, capability(op, availability), evidence, grants);
    }
    private PostResult read(PostRequest r) throws Exception {
        AtomicReference<PostResult> result = new AtomicReference<>(); CountDownLatch done = new CountDownLatch(1);
        adapter.read(r, value -> { result.set(value); done.countDown(); });
        assertTrue("terminal background callback", done.await(5, TimeUnit.SECONDS));
        return result.get();
    }
    private PostResult response(PostRequest r, int status, String body) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(status).setBody(body)); return read(r);
    }
    private JsonObject fixture(String path) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private String extra(String key) throws Exception { return fixture("/posts/sp045-02.json").get(key).toString(); }
    private JsonObject org() throws Exception { return fixture("/followers/linkedin-sp019.json").getAsJsonObject("organizationPostStatistics"); }
    private MetricSample sample(PostResult result, Metric key) {
        for (int i = 0; i < result.definitions.size(); i++) if (result.definitions.get(i).metricKey == key) return result.samples.get(i);
        throw new AssertionError(key);
    }
    private void missing(PostResult result, Metric key, MissingReason reason) {
        assertNull(sample(result, key).value); assertEquals(reason, sample(result, key).missingReason);
    }
    private void rejected(PostResult result) {
        assertEquals(Status.REJECTED, result.status); assertEquals(Failure.SCOPE_MISMATCH, result.failure); assertTrue(result.samples.isEmpty());
    }

    @Test public void instagramReachKeepsMediaLifetimeAndZero() throws Exception {
        PostRequest r = new PostRequest(FollowerRequest.Account.INSTAGRAM_PROFESSIONAL, "ch-ig", 7,
                "ig-user", "ig-media", "v27.0", "collection", IG_REACH, null);
        bind(r);
        String body = "{'data':[{'id':'ig-media/insights/reach/lifetime','period':'lifetime',"
                + "'name':'reach','values':[{'value':0}]}]}";
        PostResult result = response(r, 200, body);
        assertEquals(Long.valueOf(0), sample(result, Metric.REACH_COUNT).value);
        assertEquals("/v27.0/ig-media/insights?metric=reach", server.takeRequest().getPath());
        assertEquals("reach", result.definitions.get(0).providerMetricName);
        rejected(response(r, 200, body.replace("ig-media", "other-media")));
        missing(response(r, 200, "{'data':[]}"), Metric.REACH_COUNT, MissingReason.PROVIDER_OMITTED);
        assertNotEquals(Status.SUCCESS, response(r, 200, body.replace("lifetime", "day")).status);
    }

    @Test public void instagramImpressionStaysUnverifiedWithoutProviderMapping() throws Exception {
        PostRequest r = new PostRequest(FollowerRequest.Account.INSTAGRAM_PROFESSIONAL, "ch-ig", 7,
                "ig-user", "ig-media", "v27.0", "collection", IG_IMPRESSIONS, null);
        bind(r);
        missing(read(r), Metric.IMPRESSION_COUNT, MissingReason.UNVERIFIED);
        assertEquals(0, server.getRequestCount());
    }

    private PostRequest sp066Request(PostRequest.Query query) {
        return new PostRequest(FollowerRequest.Account.INSTAGRAM_PROFESSIONAL, "simulation-sp066", 7,
                Sp066Evidence.ACCOUNT, Sp066Evidence.MEDIA, "v27.0", "simulation-collection", query, null);
    }

    @Test public void sp066ReachPermissionErrorNeedsExactMetricScope() throws Exception {
        PostRequest reach = sp066Request(IG_REACH);
        bind(reach);
        PostResult good = response(reach, 200, Sp066Evidence.body("IG12_token_A_success.json"));
        assertEquals(Status.SUCCESS, good.status);
        assertEquals(Long.valueOf(0), sample(good, Metric.REACH_COUNT).value);
        String denied = Sp066Evidence.body("IG12_token_B_denied.json");
        // HTTP status was not captured. Synthetic 200 exercises error-envelope classification.
        connection.set(binding(reach, reach, grants(reach), new PostMetricAdapter.PermissionEvidence(
                sp066Request(IG_IMPRESSIONS), 10, "simulation-IG12-wrong-query"),
                SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        missing(response(reach, 200, denied), Metric.REACH_COUNT, MissingReason.UNVERIFIED);
        connection.set(binding(reach, reach, grants(reach), new PostMetricAdapter.PermissionEvidence(
                reach, 10, "simulation-IG12-scoped-permission"),
                SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        PostResult blocked = response(reach, 200, denied);
        assertEquals(Status.FAILURE, blocked.status);
        missing(blocked, Metric.REACH_COUNT, MissingReason.PERMISSION_REQUIRED);
        assertEquals(SocialError.NextAction.RESOLVE_PERMISSION, blocked.error.nextAction);
    }

    @Test public void sp066UnsupportedImpressionDoesNotDisableReachOrReadCredentials() throws Exception {
        PostRequest impression = sp066Request(IG_IMPRESSIONS);
        // IG-11 documents unsupported on this media/version. The production Channel
        // owner must supply this scoped capability; code 100 alone is not sufficient.
        assertEquals(100, JsonParser.parseString(Sp066Evidence.body("IG11_impressions_error.json"))
                .getAsJsonObject().getAsJsonObject("error").get("code").getAsInt());
        Map<Metric, ChannelCapability> metrics = grants(impression);
        metrics.put(Metric.IMPRESSION_COUNT, new ChannelCapability(SocialOperation.READ_POST_METRICS,
                CapabilityAvailability.UNSUPPORTED, "SP066-IG11-scoped-simulation", "2026-11-21T10:00:00Z"));
        connection.set(binding(impression, impression, metrics, null,
                SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        PostResult unavailable = read(impression);
        assertEquals(Status.UNAVAILABLE, unavailable.status);
        missing(unavailable, Metric.IMPRESSION_COUNT, MissingReason.UNSUPPORTED);
        assertEquals(0, credentialReads.get());
        assertEquals(0, server.getRequestCount());
        PostRequest reach = sp066Request(IG_REACH); bind(reach);
        PostResult available = response(reach, 200, Sp066Evidence.body("IG07_reach.json"));
        assertEquals(Long.valueOf(0), sample(available, Metric.REACH_COUNT).value);
        assertNotEquals(unavailable.definitions.get(0).definitionId, available.definitions.get(0).definitionId);
        assertEquals("reach", server.takeRequest().getRequestUrl().queryParameter("metric"));
    }

    @Test public void existingFacebookFixtureUsesSummaryNotArrayLength() throws Exception {
        PostRequest r = request(FB_REACTIONS); bind(r);
        JsonObject raw = fixture("/followers/facebook-sp018.json").getAsJsonObject("postResponse").getAsJsonObject("reactions");
        PostResult result = response(r, 200, raw.toString());
        assertEquals(Status.SUCCESS, result.status); assertEquals(Long.valueOf(5), result.samples.get(0).value);
        assertEquals("/v27.0/synthetic-page_synthetic-post/reactions?summary=true&limit=1", server.takeRequest().getPath());
    }
    @Test public void reactionsAndLikeHaveSeparateFieldsFiltersAndDefinitions() throws Exception {
        PostRequest r = request(FB_REACTIONS); bind(r);
        PostResult all = response(r, 200, extra("facebookReactions")); server.takeRequest();
        r = request(FB_LIKES); bind(r); PostResult likes = response(r, 200, extra("facebookLikes"));
        assertEquals(Long.valueOf(5), sample(all, Metric.REACTION_COUNT).value);
        assertEquals(Long.valueOf(1), sample(likes, Metric.LIKE_COUNT).value);
        assertNotEquals(all.definitions.get(0).definitionId, likes.definitions.get(0).definitionId);
        assertEquals("type=LIKE", likes.definitions.get(0).providerFilter);
        assertEquals("LIKE", server.takeRequest().getRequestUrl().queryParameter("type"));
    }
    @Test public void commentFiltersHaveSeparateDefinitionsAndCounts() throws Exception {
        PostRequest r = request(FB_COMMENTS_TOPLEVEL); bind(r);
        PostResult top = response(r, 200, extra("facebookToplevel"));
        assertEquals("toplevel", server.takeRequest().getRequestUrl().queryParameter("filter"));
        r = request(FB_COMMENTS_STREAM); bind(r); PostResult stream = response(r, 200, extra("facebookStream"));
        assertEquals("stream", server.takeRequest().getRequestUrl().queryParameter("filter"));
        assertEquals(Long.valueOf(4), top.samples.get(0).value); assertEquals(Long.valueOf(5), stream.samples.get(0).value);
        assertNotEquals(top.definitions.get(0).definitionId, stream.definitions.get(0).definitionId);
    }
    @Test public void facebookSharesOmittedIsPartialSuccessfulRequest() throws Exception {
        PostRequest r = request(FB_SHARES); bind(r);
        PostResult result = response(r, 200, extra("facebookSharesOmitted"));
        assertEquals(Status.PARTIAL, result.status); assertNull(result.failure); assertNull(result.error);
        missing(result, Metric.SHARE_COUNT, MissingReason.PROVIDER_OMITTED);
    }
    @Test public void viewsAndUniqueViewersAreNotImpressions() throws Exception {
        PostRequest r = request(FB_MEDIA_VIEWS); bind(r); PostResult views = response(r, 200, extra("facebookViews"));
        assertEquals(Long.valueOf(17), sample(views, Metric.MEDIA_VIEW_COUNT).value);
        r = request(FB_UNIQUE_VIEWERS); bind(r); PostResult viewers = response(r, 200, extra("facebookViewers"));
        assertEquals(Long.valueOf(3), sample(viewers, Metric.UNIQUE_MEDIA_VIEWER_COUNT).value);
        assertEquals(Aggregation.DISTINCT_TOTAL, viewers.definitions.get(0).aggregation);
        r = request(FB_IMPRESSIONS); bind(r); PostResult impression = read(r);
        missing(impression, Metric.IMPRESSION_COUNT, MissingReason.UNVERIFIED);
        assertEquals(MeasurementKind.UNVERIFIED, impression.definitions.get(0).measurementKind);
        assertEquals(2, server.getRequestCount());
    }
    @Test public void insightsWrongMetricPeriodBreakdownAndMultipleValuesAreInvalid() throws Exception {
        PostRequest r = request(FB_MEDIA_VIEWS); bind(r);
        for (String body : new String[] {
                extra("facebookViewers"), extra("facebookViews").replace("lifetime", "day"),
                extra("facebookViews").replace("17", "{\"is_from_ads\":17}"),
                extra("facebookViews").replace("{\"value\":17}", "{\"value\":17},{\"value\":2}")}) {
            assertEquals(Failure.INVALID_RESPONSE, response(r, 200, body).failure);
        }
    }
    @Test public void existingOrganizationFixturePreservesSignedLikeWithoutReactionFallback() throws Exception {
        PostRequest r = request(ORGANIZATION_ORGANIC); bind(r); PostResult result = response(r, 200, org().toString());
        assertEquals(Status.PARTIAL, result.status);
        assertEquals(Long.valueOf(-1), sample(result, Metric.PROVIDER_LIKE_COUNT).value);
        missing(result, Metric.REACTION_COUNT, MissingReason.UNVERIFIED);
        assertEquals(Long.valueOf(100), sample(result, Metric.IMPRESSION_COUNT).value);
        assertEquals(Long.valueOf(0), sample(result, Metric.COMMENT_COUNT).value);
        assertEquals(Long.valueOf(2), sample(result, Metric.SHARE_COUNT).value);
        for (MetricDefinition d : result.definitions) assertEquals(MeasurementKind.PROVIDER_ROLLING_12_MONTHS_ORGANIC, d.measurementKind);
        for (MetricSample s : result.samples) { assertNull(s.providerPeriodStart); assertNull(s.providerPeriodEnd); }
        RecordedRequest wire = server.takeRequest();
        assertEquals("List(urn:li:share:9000000000000000001)", wire.getRequestUrl().queryParameter("shares"));
        assertFalse(wire.getPath().contains("timeIntervals")); assertFalse(wire.getPath().contains("%253A"));
    }
    @Test public void memberTotalZeroHasVerifiedProvenanceAndUnionRequest() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r); PostResult result = response(r, 200, extra("memberTotal"));
        assertEquals(Status.SUCCESS, result.status); MetricSample s = result.samples.get(0);
        assertEquals(Long.valueOf(0), s.value); assertNull(s.missingReason);
        assertEquals(r.postId, s.entityId); assertEquals(r.externalAccountId, s.externalAccountId);
        assertEquals(r.channelId, s.scope.channelId); assertEquals(r.platform, s.scope.platform);
        assertEquals(7, s.scope.connectionRevision); assertEquals(r.collectionId, s.collectionId);
        assertEquals(FollowerResult.Source.MO_PHONG, s.source); assertEquals("sp045-MO_PHONG", s.sourceRef);
        assertEquals("202610", s.providerApiVersion); assertEquals(NOW, s.observedAt); assertEquals(NOW, s.collectedAt);
        assertEquals(TimeBasis.COLLECTION, s.observedAtBasis); assertNull(s.providerPeriodStart);
        assertEquals(Unit.COUNT, result.definitions.get(0).unit); assertEquals(MeasurementKind.CUMULATIVE, result.definitions.get(0).measurementKind);
        RecordedRequest wire = server.takeRequest();
        assertEquals("202610", wire.getHeader("LinkedIn-Version")); assertEquals("2.0.0", wire.getHeader("X-Restli-Protocol-Version"));
        assertEquals("Bearer MO_PHONG-token", wire.getHeader("Authorization"));
        assertEquals("(share:urn:li:share:9000000000000000001)", wire.getRequestUrl().queryParameter("entity"));
        assertTrue(wire.getPath().contains("entity=(share:urn%3Ali%3Ashare%3A9000000000000000001)"));
        assertEquals("TOTAL", wire.getRequestUrl().queryParameter("aggregation"));
    }
    @Test public void memberEachMetricMapsIndependently() throws Exception {
        for (PostRequest.Query q : new PostRequest.Query[] {MEMBER_IMPRESSION, MEMBER_REACTION, MEMBER_COMMENT, MEMBER_RESHARE, MEMBER_REACHED}) {
            PostRequest r = request(q); bind(r);
            PostResult result = response(r, 200, extra("memberTotal").replace("REACTION", PostMetricMapping.memberMetric(r)));
            assertEquals(Long.valueOf(0), result.samples.get(0).value);
            assertEquals(PostMetricMapping.memberMetric(r), server.takeRequest().getRequestUrl().queryParameter("queryType"));
        }
    }
    @Test public void periodTotalPreservesBoundsWithoutCallingThemObservationTime() throws Exception {
        PostRequest r = request(MEMBER_REACTION, period(), null); bind(r);
        PostResult result = response(r, 200, extra("memberPeriod")); MetricSample s = result.samples.get(0);
        assertEquals(Status.SUCCESS, result.status); assertEquals(Long.valueOf(2), s.value);
        assertEquals(MeasurementKind.PERIOD, result.definitions.get(0).measurementKind);
        assertEquals(Long.valueOf(1793491200000L), s.providerPeriodStart); assertEquals(Long.valueOf(1794096000000L), s.providerPeriodEnd);
        assertEquals("MO_PHONG-date-zone-evidence", s.periodEvidenceRef); assertEquals(NOW, s.observedAt);
        assertTrue(server.takeRequest().getRequestUrl().queryParameter("dateRange").contains("year:2026"));
        assertNotEquals(PostMetricMapping.definitions(request(MEMBER_REACTION)).get(0).definitionId, result.definitions.get(0).definitionId);
    }
    @Test public void periodMissingOrMismatchingBoundsIsNotLifetimeOrZero() throws Exception {
        PostRequest r = request(MEMBER_REACTION, period(), null); bind(r);
        for (String raw : new String[] {extra("memberTotal"), extra("memberPeriod").replace("\"end\"", "\"omittedEnd\""),
                extra("memberPeriod").replace("\"day\":8", "\"day\":9")}) {
            PostResult result = response(r, 200, raw); assertEquals(Status.FAILURE, result.status);
            missing(result, Metric.REACTION_COUNT, MissingReason.UNVERIFIED); assertNull(result.samples.get(0).providerPeriodEnd);
        }
    }
    @Test public void totalRejectsPeriodPayloadRatherThanRelabelingIt() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        assertEquals(Failure.INVALID_RESPONSE, response(r, 200, extra("memberPeriod")).failure);
    }
    @Test public void orgPeriodAggregateCannotBecomeSinglePostMeasurement() throws Exception {
        PostRequest r = request(ORGANIZATION_ORGANIC); bind(r);
        JsonObject body = org(); body.getAsJsonArray("elements").get(0).getAsJsonObject().add("timeRange", new JsonObject());
        assertEquals(Failure.INVALID_RESPONSE, response(r, 200, body.toString()).failure);
        body.getAsJsonArray("elements").get(0).getAsJsonObject().remove("share");
        rejected(response(r, 200, body.toString()));
        assertThrows(IllegalArgumentException.class, () -> request(ORGANIZATION_ORGANIC, period(), null));
    }
    @Test public void missingAndNullAreNotZeroAcrossAccounts() throws Exception {
        for (PostRequest.Query q : new PostRequest.Query[] {FB_REACTIONS, MEMBER_REACTION, ORGANIZATION_ORGANIC}) {
            PostRequest r = request(q); bind(r);
            for (String replacement : new String[] {"null", "MISSING"}) {
                String raw = q == FB_REACTIONS ? "{\"summary\":{\"total_count\":0}}" : q == MEMBER_REACTION
                        ? extra("memberTotal") : org().toString();
                String field = q == FB_REACTIONS ? "total_count" : q == MEMBER_REACTION ? "count" : "commentCount";
                JsonObject body = JsonParser.parseString(raw).getAsJsonObject();
                JsonObject container = q == FB_REACTIONS ? body.getAsJsonObject("summary")
                        : q == MEMBER_REACTION ? body.getAsJsonArray("elements").get(0).getAsJsonObject()
                        : body.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("totalShareStatistics");
                if (replacement.equals("null")) container.add(field, null); else container.remove(field);
                missing(response(r, 200, body.toString()), q == ORGANIZATION_ORGANIC ? Metric.COMMENT_COUNT : Metric.REACTION_COUNT, MissingReason.PROVIDER_OMITTED);
            }
        }
    }
    @Test public void zeroCountsValidForEveryFacebookEndpoint() throws Exception {
        for (PostRequest.Query q : new PostRequest.Query[] {FB_REACTIONS, FB_LIKES, FB_COMMENTS_TOPLEVEL, FB_COMMENTS_STREAM, FB_SHARES, FB_MEDIA_VIEWS, FB_UNIQUE_VIEWERS}) {
            PostRequest r = request(q); bind(r);
            String body = q == FB_SHARES ? "{\"id\":\"synthetic-page_synthetic-post\",\"shares\":{\"count\":0}}"
                    : q == FB_MEDIA_VIEWS ? extra("facebookViews").replace("17", "0")
                    : q == FB_UNIQUE_VIEWERS ? extra("facebookViewers").replace("3", "0") : "{\"data\":[],\"summary\":{\"total_count\":0}}";
            assertEquals(Long.valueOf(0), response(r, 200, body).samples.get(0).value);
        }
    }
    @Test public void wrongTypesNegativeFractionAndOverflowNeverBecomeZero() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        for (String value : new String[] {"\"0\"", "false", "{}", "[]", "-1", "0.5", "9223372036854775808"}) {
            PostResult result = response(r, 200, extra("memberTotal").replace("\"count\":0", "\"count\":" + value));
            assertEquals(Failure.INVALID_RESPONSE, result.failure); missing(result, Metric.REACTION_COUNT, MissingReason.UNVERIFIED);
        }
    }
    @Test public void invalidOrgFieldPreservesOtherGoodFieldsIncludingFollowingFields() throws Exception {
        PostRequest r = request(ORGANIZATION_ORGANIC); bind(r);
        PostResult result = response(r, 200, org().toString().replace("\"impressionCount\":100", "\"impressionCount\":\"bad\""));
        assertEquals(Status.PARTIAL, result.status); assertEquals(Failure.INVALID_RESPONSE, result.failure);
        missing(result, Metric.IMPRESSION_COUNT, MissingReason.UNVERIFIED);
        assertEquals(Long.valueOf(0), sample(result, Metric.COMMENT_COUNT).value);
        assertEquals(Long.valueOf(-1), sample(result, Metric.PROVIDER_LIKE_COUNT).value);
    }
    @Test public void orgOnlyProviderLikeMayBeNegative() throws Exception {
        PostRequest r = request(ORGANIZATION_ORGANIC); bind(r);
        for (String field : new String[] {"impressionCount", "commentCount", "shareCount"}) {
            JsonObject raw = org(); raw.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("totalShareStatistics").addProperty(field, -1);
            assertEquals(Failure.INVALID_RESPONSE, response(r, 200, raw.toString()).failure);
        }
    }
    @Test public void emptyRowsAndMissingSummaryDoNotInferZero() throws Exception {
        for (PostRequest.Query q : new PostRequest.Query[] {FB_REACTIONS, MEMBER_REACTION, ORGANIZATION_ORGANIC}) {
            PostRequest r = request(q); bind(r); PostResult result = response(r, 200,
                    q == FB_REACTIONS ? "{\"data\":[{\"id\":\"one\"}]}" : "{\"elements\":[]}");
            assertEquals(Status.PARTIAL, result.status);
            missing(result, Metric.REACTION_COUNT, q == ORGANIZATION_ORGANIC ? MissingReason.UNVERIFIED : MissingReason.PROVIDER_OMITTED);
        }
    }
    @Test public void wrongPostAndOrganizationAreRejected() throws Exception {
        PostRequest r = request(ORGANIZATION_ORGANIC); bind(r);
        rejected(response(r, 200, org().toString().replace(r.postId, "urn:li:share:1")));
        rejected(response(r, 200, org().toString().replace(r.externalAccountId, "urn:li:organization:1")));
        r = request(MEMBER_REACTION); bind(r);
        rejected(response(r, 200, extra("memberTotal").replace(r.postId, "urn:li:share:1")));
        r = request(FB_SHARES); bind(r); rejected(response(r, 200, "{\"id\":\"wrong\",\"shares\":{\"count\":99}}"));
    }
    @Test public void optionalEchoedAccountAndPostMustMatch() throws Exception {
        PostRequest r = request(FB_REACTIONS); bind(r);
        rejected(response(r, 200, "{\"id\":\"wrong\",\"summary\":{\"total_count\":1}}"));
        rejected(response(r, 200, "{\"from\":{\"id\":\"other-page\"},\"summary\":{\"total_count\":1}}"));
        r = request(MEMBER_REACTION); bind(r);
        rejected(response(r, 200, extra("memberTotal").replace("\"count\":0", "\"author\":\"urn:li:person:other\",\"count\":0")));
    }
    @Test public void wrongChannelAccountPlatformAndRevisionRejectBeforeCredentials() throws Exception {
        PostRequest r = request(MEMBER_REACTION);
        for (PostRequest wrong : new PostRequest[] {new PostRequest(r.account, "other", 7, r.externalAccountId, r.postId, r.apiVersion, r.collectionId, r.query, null),
                new PostRequest(r.account, r.channelId, 8, r.externalAccountId, r.postId, r.apiVersion, r.collectionId, r.query, null),
                new PostRequest(r.account, r.channelId, 7, "urn:li:person:other", r.postId, r.apiVersion, r.collectionId, r.query, null), request(FB_REACTIONS), request(ORGANIZATION_ORGANIC)}) {
            bind(wrong); rejected(read(r));
        }
        assertEquals(0, credentialReads.get()); assertEquals(0, server.getRequestCount());
    }
    @Test public void wrongCapabilityOperationRevisionPostOrVersionRejectsWithoutNetwork() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        connection.set(binding(r, r, grants(r), null, SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.AVAILABLE));
        assertEquals(Failure.CAPABILITY_BLOCKED, read(r).failure);
        for (PostRequest cap : new PostRequest[] {new PostRequest(r.account, r.channelId, 8, r.externalAccountId, r.postId, r.apiVersion, r.collectionId, r.query, null),
                request(MEMBER_COMMENT), new PostRequest(r.account, r.channelId, 7, r.externalAccountId, "urn:li:share:1", r.apiVersion, r.collectionId, r.query, null),
                new PostRequest(r.account, r.channelId, 7, r.externalAccountId, r.postId, "202609", r.collectionId, r.query, null)}) {
            connection.set(binding(r, cap, grants(r), null, SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
            assertEquals(Failure.CAPABILITY_BLOCKED, read(r).failure);
        }
        assertEquals(0, credentialReads.get()); assertEquals(0, server.getRequestCount());
    }
    @Test public void operationAvailableDoesNotGrantEverySubmetric() throws Exception {
        PostRequest r = request(ORGANIZATION_ORGANIC); Map<Metric, ChannelCapability> grants = grants(r);
        grants.remove(Metric.IMPRESSION_COUNT);
        grants.put(Metric.SHARE_COUNT, capability(SocialOperation.READ_POST_METRICS, CapabilityAvailability.PERMISSION_REQUIRED));
        grants.put(Metric.COMMENT_COUNT, capability(SocialOperation.READ_POST_METRICS, CapabilityAvailability.UNSUPPORTED));
        connection.set(binding(r, r, grants, null, SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        PostResult result = response(r, 200, org().toString());
        missing(result, Metric.IMPRESSION_COUNT, MissingReason.UNVERIFIED);
        missing(result, Metric.SHARE_COUNT, MissingReason.PERMISSION_REQUIRED); missing(result, Metric.COMMENT_COUNT, MissingReason.UNSUPPORTED);
        assertEquals(Long.valueOf(-1), sample(result, Metric.PROVIDER_LIKE_COUNT).value);
        assertEquals(Status.PARTIAL, result.status);
    }
    @Test public void noMetricGrantSkipsNetworkAndCredentialLookup() throws Exception {
        PostRequest r = request(MEMBER_REACTION);
        connection.set(binding(r, r, Collections.emptyMap(), null, SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        PostResult unavailable = read(r);
        assertEquals(Status.UNAVAILABLE, unavailable.status);
        missing(unavailable, Metric.REACTION_COUNT, MissingReason.UNVERIFIED);
        Map<Metric, ChannelCapability> grants = grants(r); grants.put(Metric.REACTION_COUNT,
                capability(SocialOperation.PUBLISH_TEXT, CapabilityAvailability.AVAILABLE));
        connection.set(binding(r, r, grants, null, SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        missing(read(r), Metric.REACTION_COUNT, MissingReason.UNVERIFIED);
        assertEquals(0, credentialReads.get()); assertEquals(0, server.getRequestCount());
    }
    @Test public void blockedOperationKeepsItsReason() throws Exception {
        PostRequest r = request(MEMBER_REACTION);
        for (CapabilityAvailability availability : new CapabilityAvailability[] {CapabilityAvailability.UNVERIFIED, CapabilityAvailability.PERMISSION_REQUIRED, CapabilityAvailability.UNSUPPORTED}) {
            connection.set(binding(r, r, grants(r), null, SocialOperation.READ_POST_METRICS, availability));
            PostResult result = read(r); assertEquals(Status.FAILURE, result.status);
            missing(result, Metric.REACTION_COUNT, MissingReason.valueOf(availability.name()));
        }
        assertEquals(0, server.getRequestCount());
    }
    @Test public void revisionChangesWhileReceivingRejectsSamples() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                bind(new PostRequest(r.account, r.channelId, 8, r.externalAccountId, r.postId, r.apiVersion, r.collectionId, r.query, null));
                return new MockResponse().setBody("{\"elements\":[]}");
            }
        });
        rejected(read(r));
    }
    @Test public void metricPermissionRevokedDuringResponseDoesNotLeakCount() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest wire) {
                Map<Metric, ChannelCapability> grants = grants(r); grants.put(Metric.REACTION_COUNT,
                        capability(SocialOperation.READ_POST_METRICS, CapabilityAvailability.PERMISSION_REQUIRED));
                connection.set(binding(r, r, grants, null, SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
                return new MockResponse().setBody("{\"elements\":[{\"targetEntity\":{\"share\":\"" + r.postId + "\"},\"metricType\":\"REACTION\",\"count\":99}]}");
            }
        });
        missing(read(r), Metric.REACTION_COUNT, MissingReason.PERMISSION_REQUIRED);
    }
    @Test public void revisionChangeDuringCredentialsPreventsSending() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        adapter = makeAdapter(ignored -> connection.get(), channel -> {
            bind(new PostRequest(r.account, r.channelId, 8, r.externalAccountId, r.postId, r.apiVersion, r.collectionId, r.query, null));
            return "MO_PHONG";
        });
        rejected(read(r)); assertEquals(0, server.getRequestCount());
    }
    @Test public void authErrorsAreNotOmittedOrZero() throws Exception {
        for (PostRequest.Query q : new PostRequest.Query[] {FB_REACTIONS, MEMBER_REACTION, ORGANIZATION_ORGANIC}) {
            PostRequest r = request(q); bind(r); PostResult result = response(r, 401, "{}");
            assertEquals(Status.FAILURE, result.status); assertEquals(SocialError.Code.AUTH_REQUIRED, result.error.code);
            for (MetricSample s : result.samples) { assertNull(s.value); assertEquals(MissingReason.AUTH_REQUIRED, s.missingReason); }
        }
    }
    @Test public void forbiddenAndFacebook100AloneDoNotProvePermissionOrUnsupported() throws Exception {
        for (PostRequest.Query q : new PostRequest.Query[] {FB_REACTIONS, MEMBER_REACTION}) {
            PostRequest r = request(q); bind(r); PostResult result = response(r, 403, "{\"error\":{\"code\":100}}");
            assertEquals(Status.FAILURE, result.status); assertNull(result.error); missing(result, Metric.REACTION_COUNT, MissingReason.UNVERIFIED);
        }
    }
    @Test public void facebook100RequiresEvidenceForExactPostMetricScopeVersionAndRevision() throws Exception {
        PostRequest r = request(FB_REACTIONS); PostMetricAdapter.PermissionEvidence evidence = new PostMetricAdapter.PermissionEvidence(r, 100, "MO_PHONG-permission-proof");
        connection.set(binding(r, r, grants(r), evidence, SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        PostResult result = response(r, 400, "{\"error\":{\"code\":100}}");
        missing(result, Metric.REACTION_COUNT, MissingReason.PERMISSION_REQUIRED); assertEquals(SocialError.NextAction.RESOLVE_PERMISSION, result.error.nextAction);
        PostRequest wrong = request(FB_LIKES);
        connection.set(binding(wrong, wrong, grants(wrong), evidence, SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        missing(response(wrong, 400, "{\"error\":{\"code\":100}}"), Metric.LIKE_COUNT, MissingReason.UNVERIFIED);
        PostRequest old = new PostRequest(r.account, r.channelId, 6, r.externalAccountId, r.postId, r.apiVersion, r.collectionId, r.query, null);
        connection.set(binding(r, r, grants(r), new PostMetricAdapter.PermissionEvidence(old, 100, "MO_PHONG-old"), SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        missing(response(r, 400, "{\"error\":{\"code\":100}}"), Metric.REACTION_COUNT, MissingReason.UNVERIFIED);
    }
    @Test public void retryAfterSecondsDateInvalidAndOverflow() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        for (String header : new String[] {"12", "Thu, 19 Nov 2026 09:20:00 GMT", "bad", "-1", "9223372036854775807"}) {
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", header).setBody("{}"));
            PostResult result = read(r); missing(result, Metric.REACTION_COUNT, MissingReason.RATE_LIMITED);
            assertEquals(SocialError.NextAction.WAIT, result.error.nextAction);
            if (header.equals("12")) assertEquals(Long.valueOf(NOW + 12000), result.retryAfterAt);
            else if (header.startsWith("Thu")) assertEquals(Long.valueOf(1795080000000L), result.retryAfterAt);
            else assertNull(result.retryAfterAt);
        }
    }
    @Test public void graphRateAndAuthEnvelopeOn200AreFailures() throws Exception {
        PostRequest r = request(FB_REACTIONS); bind(r);
        missing(response(r, 200, "{\"error\":{\"code\":190}}"), Metric.REACTION_COUNT, MissingReason.AUTH_REQUIRED);
        for (int code : new int[] {4, 17, 32, 613}) missing(response(r, 400, "{\"error\":{\"code\":" + code + "}}"), Metric.REACTION_COUNT, MissingReason.RATE_LIMITED);
        r = request(MEMBER_REACTION); bind(r); assertEquals(Status.FAILURE, response(r, 200, "{\"status\":403,\"message\":\"MO_PHONG\"}").status);
    }
    @Test public void malformedEnvelopeAndMultipleMemberRowsFail() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        for (String raw : new String[] {"bad-json", "[]", "{}", "{\"elements\":[null]}", extra("memberTotal").replace("\"REACTION\"", "\"IMPRESSION\"")}) {
            assertEquals(Failure.INVALID_RESPONSE, response(r, 200, raw).failure);
        }
        JsonObject raw = JsonParser.parseString(extra("memberTotal")).getAsJsonObject(); raw.getAsJsonArray("elements").add(raw.getAsJsonArray("elements").get(0).deepCopy());
        assertEquals(Failure.INVALID_RESPONSE, response(r, 200, raw.toString()).failure);
    }
    @Test public void opaqueUgcPostIsKeptAndEncodedOnce() throws Exception {
        for (PostRequest.Query q : new PostRequest.Query[] {MEMBER_REACTION, ORGANIZATION_ORGANIC}) {
            PostRequest r = request(q, null, "urn:li:ugcPost:0009000000000000000001"); bind(r);
            String raw = q == MEMBER_REACTION ? extra("memberTotal").replace("\"share\"", "\"ugc\"") : org().toString().replace("\"share\"", "\"ugcPost\"");
            raw = raw.replace("urn:li:share:9000000000000000001", r.postId);
            assertEquals(r.postId, response(r, 200, raw).samples.get(0).entityId);
            RecordedRequest wire = server.takeRequest(); assertFalse(wire.getPath().contains("%253A"));
            assertTrue(wire.getPath().contains("0009000000000000000001"));
        }
    }
    @Test public void invalidRequestAndPeriodDoNotSilentlyNormalize() {
        assertThrows(IllegalArgumentException.class, () -> request(MEMBER_REACTION, null, "urn:li:activity:1"));
        assertThrows(IllegalArgumentException.class, () -> new PostRequest(FollowerRequest.Account.FACEBOOK_PAGE, "c", 1, "p", "post", "v27.0", "col", MEMBER_REACTION, null));
        assertThrows(IllegalArgumentException.class, () -> new PostRequest.Period("2026-02-30", "2026-03-02", "UTC", "MO_PHONG"));
        assertThrows(IllegalArgumentException.class, () -> new PostRequest.Period("2026-11-01", "2026-11-08", "invalid", "MO_PHONG"));
        assertThrows(IllegalArgumentException.class, () -> new PostRequest.Period("2026-11-08", "2026-11-01", "UTC", "MO_PHONG"));
    }
    @Test public void credentialFailureIsLocalAndRedirectNotFollowed() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        adapter = makeAdapter(ignored -> connection.get(), channel -> { throw new IllegalStateException("secret-not-retained"); });
        assertEquals(Failure.LOCAL_ERROR, read(r).failure); assertEquals(0, server.getRequestCount());
        adapter = makeAdapter(ignored -> connection.get(), channel -> "MO_PHONG");
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", server.url("/unapproved")));
        assertEquals(Status.FAILURE, read(r).status); assertEquals(1, server.getRequestCount());
    }
    @Test public void cancelSuppressesCallback() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), callback = new CountDownLatch(1);
        adapter = makeAdapter(ignored -> connection.get(), channel -> {
            entered.countDown(); try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "MO_PHONG";
        });
        Cancelable call = adapter.read(r, ignored -> callback.countDown()); assertTrue(entered.await(3, TimeUnit.SECONDS));
        call.cancel(); call.cancel(); release.countDown(); assertFalse(callback.await(150, TimeUnit.MILLISECONDS));
        assertEquals(0, server.getRequestCount());
    }
    @Test public void wrongFacebookFieldTypesDoNotBecomeOmittedOrZero() throws Exception {
        for (PostRequest.Query q : new PostRequest.Query[] {FB_REACTIONS, FB_SHARES, FB_MEDIA_VIEWS}) {
            PostRequest r = request(q); bind(r);
            for (String value : new String[] {"\"0\"", "true", "{}", "-1", "0.5", "9223372036854775808"}) {
                String raw = q == FB_REACTIONS ? "{\"summary\":{\"total_count\":" + value + "}}"
                        : q == FB_SHARES ? "{\"id\":\"" + r.postId + "\",\"shares\":{\"count\":" + value + "}}"
                        : extra("facebookViews").replace("\"value\":17", "\"value\":" + value);
                PostResult result = response(r, 200, raw);
                assertEquals(Status.FAILURE, result.status); assertEquals(Failure.INVALID_RESPONSE, result.failure);
                assertNull(result.samples.get(0).value); assertEquals(MissingReason.UNVERIFIED, result.samples.get(0).missingReason);
            }
        }
    }
    @Test public void foreignInsightSubjectIsRejectedAndUnexpectedPeriodIsInvalid() throws Exception {
        PostRequest r = request(FB_MEDIA_VIEWS); bind(r);
        rejected(response(r, 200, extra("facebookViews").replace("\"name\":", "\"id\":\"other-post/insights/post_media_view/lifetime\",\"name\":")));
        r = request(FB_REACTIONS); bind(r);
        assertEquals(Failure.INVALID_RESPONSE, response(r, 200, "{\"dateRange\":{},\"summary\":{\"total_count\":0}}").failure);
    }
    @Test public void facebook100EvidenceCannotBeReusedForAnotherPostAccountOrVersion() throws Exception {
        PostRequest r = request(FB_REACTIONS);
        for (PostRequest wrong : new PostRequest[] {
                new PostRequest(r.account, r.channelId, 7, r.externalAccountId, "other-post", r.apiVersion, r.collectionId, r.query, null),
                new PostRequest(r.account, "other-channel", 7, r.externalAccountId, r.postId, r.apiVersion, r.collectionId, r.query, null),
                new PostRequest(r.account, r.channelId, 7, "other-page", r.postId, r.apiVersion, r.collectionId, r.query, null),
                new PostRequest(r.account, r.channelId, 7, r.externalAccountId, r.postId, "v26.0", r.collectionId, r.query, null)}) {
            connection.set(binding(r, r, grants(r), new PostMetricAdapter.PermissionEvidence(wrong, 100, "MO_PHONG-wrong"),
                    SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
            missing(response(r, 400, "{\"error\":{\"code\":100}}"), Metric.REACTION_COUNT, MissingReason.UNVERIFIED);
        }
    }
    @Test public void malformedProviderErrorCodeCannotMatchEvidenceByCoercion() throws Exception {
        PostRequest r = request(FB_REACTIONS);
        connection.set(binding(r, r, grants(r), new PostMetricAdapter.PermissionEvidence(r, 100, "MO_PHONG-proof"),
                SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
        for (String value : new String[] {"\"100\"", "100.5", "null", "true"}) {
            missing(response(r, 400, "{\"error\":{\"code\":" + value + "}}"), Metric.REACTION_COUNT, MissingReason.UNVERIFIED);
        }
    }
    @Test public void metricRevokedWhileReadingCredentialsPreventsRequest() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        adapter = makeAdapter(ignored -> connection.get(), channel -> {
            connection.set(binding(r, r, Collections.emptyMap(), null, SocialOperation.READ_POST_METRICS, CapabilityAvailability.AVAILABLE));
            return "MO_PHONG";
        });
        assertEquals(Status.UNAVAILABLE, read(r).status); assertEquals(0, server.getRequestCount());
    }
    @Test public void networkBodyFailureIsSeparateFromSuccessfulEmptyResponse() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        server.enqueue(new MockResponse().setBody("{\"elements\":[{\"count\":1234567890}]}")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        PostResult result = read(r); assertEquals(Status.FAILURE, result.status); assertEquals(Failure.NETWORK_ERROR, result.failure);
        missing(result, Metric.REACTION_COUNT, MissingReason.NETWORK_ERROR);
        assertEquals(SocialError.NextAction.RETRY_WHEN_SAFE, result.error.nextAction);
    }
    @Test public void disconnectAndTokenReferenceChangesDiscardLateResponses() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r);
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest wire) {
                ChannelInfo old = channel(r);
                ChannelInfo changed = new ChannelInfo(old.channelId, old.platform, old.externalAccountId, old.accountType,
                        old.displayName, ConnectionStatus.CONNECTED, old.connectionRevision, "MO_PHONG-replaced-secret");
                connection.set(new PostMetricAdapter.Binding(changed, r, capability(SocialOperation.READ_POST_METRICS,
                        CapabilityAvailability.AVAILABLE), null, grants(r)));
                return new MockResponse().setBody("{\"elements\":[]}");
            }
        });
        rejected(read(r));
        ChannelInfo old = channel(r);
        ChannelInfo disconnected = new ChannelInfo(old.channelId, old.platform, old.externalAccountId, old.accountType,
                old.displayName, ConnectionStatus.DISCONNECTED, old.connectionRevision, null);
        connection.set(new PostMetricAdapter.Binding(disconnected, r, capability(SocialOperation.READ_POST_METRICS,
                CapabilityAvailability.AVAILABLE), null, grants(r)));
        PostResult result = read(r); assertEquals(Failure.CHANNEL_DISCONNECTED, result.failure); assertTrue(result.samples.isEmpty());
    }
    @Test public void failedConnectionLookupInHttpCallbackIsTerminalLocalFailure() throws Exception {
        PostRequest r = request(MEMBER_REACTION); bind(r); AtomicInteger reads = new AtomicInteger();
        adapter = makeAdapter(ignored -> {
            if (reads.incrementAndGet() >= 3) throw new IllegalStateException("MO_PHONG-private-error");
            return connection.get();
        }, channel -> "MO_PHONG");
        assertEquals(Failure.LOCAL_ERROR, response(r, 200, extra("memberTotal")).failure);
    }
}
