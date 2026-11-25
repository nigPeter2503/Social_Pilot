package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.data.local.snapshot.*;
import com.socialpilot.app.data.repository.metrics.*;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.*;
import static com.socialpilot.app.data.repository.metrics.MetricCollectionCoordinator.State.*;
import static org.junit.Assert.*;

/** MO_PHONG only: fake UTC clock, scoped DAO, credentials and provider HTTP. */
public class MetricCollectionTest {
    private static final long START = 1_795_060_800_000L;
    private final AtomicLong clock = new AtomicLong(START);
    private Snapshot.Scope scope = new Snapshot.Scope(Platform.FACEBOOK, FollowerRequest.Account.FACEBOOK_PAGE,
            "MO_PHONG-channel", "MO_PHONG-page", 7);
    private final SimulatedSnapshotDao dao = new SimulatedSnapshotDao();
    private OkHttpClient client = new OkHttpClient();
    private final MockWebServer server = new MockWebServer();
    private ChannelInfo channel;
    private MetricProvider provider;
    private MetricCollectionCoordinator coordinator;
    private CapabilityAvailability availability = CapabilityAvailability.AVAILABLE;
    private SocialOperation operation = SocialOperation.READ_FOLLOWER_COUNT;
    private boolean online = true;
    private final MetricJob.PostTarget likes = new MetricJob.PostTarget("post", PostRequest.Query.FB_LIKES, null);
    private final MetricJob.PostTarget shares = new MetricJob.PostTarget("post", PostRequest.Query.FB_SHARES, null);

    @Before public void setup() throws Exception {
        server.start();
        configureCollection();
    }

    private void configureCollection() {
        channel = channel(7);
        dao.bind(channel);
        dao.own(scope, "post", "local-post");
        FollowerRequest binding = request("binding");
        FollowerMetricAdapter follower = new FollowerMetricAdapter(client,
                id -> new FollowerMetricAdapter.Binding(channel, binding, capability(operation), null),
                c -> "mock", clock::get, server.url("/"), server.url("/"), FollowerResult.Source.MO_PHONG, "MO_PHONG");
        provider = MetricProvider.adapters(follower, r -> {
            ChannelCapability cap = capability(SocialOperation.READ_POST_METRICS);
            Map<PostResult.Metric, ChannelCapability> grants = new EnumMap<>(PostResult.Metric.class);
            for (PostResult.MetricDefinition d : PostMetricMapping.definitions(r)) grants.put(d.metricKey, cap);
            return new PostMetricAdapter(client, id -> new PostMetricAdapter.Binding(channel, r, cap, null, grants),
                    c -> "mock", clock::get, server.url("/"), server.url("/"), FollowerResult.Source.MO_PHONG, "MO_PHONG");
        });
        coordinator = coordinator(provider);
    }
    @After public void close() throws Exception {
        server.shutdown();
        client.dispatcher().executorService().shutdownNow();
        client.connectionPool().evictAll();
    }
    private ChannelInfo channel(long revision) {
        return new ChannelInfo(scope.channelId(), scope.platform(), scope.externalAccountId(), scope.account().type,
                "MO_PHONG", ConnectionStatus.CONNECTED, revision, "mock-ref");
    }
    private ChannelCapability capability(SocialOperation op) {
        return new ChannelCapability(op, availability, availability == CapabilityAvailability.AVAILABLE ? null : "fixture",
                "2026-11-19T04:00:00Z");
    }
    private FollowerRequest request(String collection) {
        return new FollowerRequest(scope.account(), scope.channelId(), 7, scope.externalAccountId(), "v27.0", collection);
    }
    private MetricCollectionCoordinator coordinator(MetricProvider p) {
        return new MetricCollectionCoordinator(dao, FollowerResult.Source.MO_PHONG, p, clock::get, () -> online);
    }
    private MetricJob job(boolean follower, MetricJob.PostTarget... posts) {
        return MetricJob.fresh(clock.get(), scope, "v27.0", follower, Arrays.asList(posts));
    }
    private MetricCollectionCoordinator.Result run(MetricJob j, int start) {
        return coordinator.run(j, start, new MetricCollectionCoordinator.Control());
    }
    private SnapshotDao.History history() { return dao.read(scope, FollowerResult.Source.MO_PHONG, START, clock.get()); }
    private void good() { server.enqueue(new MockResponse().setBody("{\"id\":\"MO_PHONG-page\",\"followers_count\":0}")); }

    @Test public void instagramFollowerAndMediaReachPersistAsSeparateSubjectsOnReplay() {
        Snapshot.Scope page = scope;
        scope = new Snapshot.Scope(Platform.INSTAGRAM, FollowerRequest.Account.INSTAGRAM_PROFESSIONAL,
                "MO_PHONG-ig-channel", "MO_PHONG-ig-user", 7);
        configureCollection();
        MetricJob job = job(true, new MetricJob.PostTarget("post", PostRequest.Query.IG_REACH, null));
        server.enqueue(new MockResponse().setBody("{'id':'MO_PHONG-ig-user','followers_count':0}"));
        server.enqueue(new MockResponse().setBody("{'data':[{'id':'post/insights/reach/lifetime',"
                + "'name':'reach','period':'lifetime','values':[{'value':0}]}]}"));
        assertEquals(COMPLETE, run(job, 0).state());
        assertEquals(COMPLETE, run(job, 1).state());
        assertEquals(2, server.getRequestCount());
        assertEquals(2, history().snapshots().size());
        Set<String> keys = new HashSet<>();
        for (Snapshot snapshot : history().snapshots()) {
            assertEquals(scope, snapshot.scope());
            assertEquals(FollowerResult.Source.MO_PHONG, snapshot.source());
            var sample = snapshot.samples().get(0);
            assertEquals(Long.valueOf(0), sample.value());
            assertNull(sample.missingReason());
            keys.add(sample.definition().metricKey());
        }
        assertEquals(new HashSet<>(Arrays.asList("FOLLOWER_COUNT", "REACH_COUNT")), keys);
        assertTrue(dao.read(page, FollowerResult.Source.MO_PHONG, START, clock.get()).snapshots().isEmpty());
    }

    @Test public void newManualCollectionsEvenAtSameInstantAndReplayUsesNoHttp() {
        MetricJob a = job(true), b = job(true);
        assertNotEquals(a.collectionId(), b.collectionId());
        good(); good();
        assertEquals(COMPLETE, run(a, 0).state());
        assertEquals(COMPLETE, run(a, 1).state());
        assertEquals(COMPLETE, run(b, 0).state());
        assertEquals(2, server.getRequestCount());
        assertEquals(2, history().snapshots().size());
        assertEquals(2, history().attempts().size());
        assertNotEquals(history().attempts().get(0).attemptId(), history().attempts().get(1).attemptId());
    }

    @Test public void sp066PayloadCollectionsRetainHistoryAcrossReplayAndOfflineRefresh() throws Exception {
        scope = new Snapshot.Scope(Platform.INSTAGRAM, FollowerRequest.Account.INSTAGRAM_PROFESSIONAL,
                "simulation-sp066", Sp066Evidence.ACCOUNT, 7);
        configureCollection();
        dao.own(scope, Sp066Evidence.MEDIA, "simulation-local-media");
        MetricJob.PostTarget media = new MetricJob.PostTarget(Sp066Evidence.MEDIA, PostRequest.Query.IG_REACH, null);
        MetricJob first = job(true, media);
        server.enqueue(new MockResponse().setBody(Sp066Evidence.body("IG06_followers.json")));
        server.enqueue(new MockResponse().setBody(Sp066Evidence.body("IG07_reach.json")));
        assertEquals(COMPLETE, run(first, 0).state());
        List<Snapshot> original = new ArrayList<>(history().snapshots());
        assertEquals(2, original.size());
        clock.addAndGet(60_000); // Synthetic interval, never copied from Explorer T1/T2.
        coordinator = coordinator(provider);
        MetricJob restored = new MetricJob(first.collectionId(), first.collectionStartedAt(), scope,
                first.apiVersion(), first.follower(), first.posts());
        assertEquals(COMPLETE, run(restored, 1).state());
        assertEquals(original, history().snapshots()); // IDs, clock and provenance unchanged.
        assertEquals(2, server.getRequestCount());
        MetricJob second = job(true, media);
        assertNotEquals(first.collectionId(), second.collectionId());
        server.enqueue(new MockResponse().setBody(Sp066Evidence.body("IG13_followers_recheck.json")));
        server.enqueue(new MockResponse().setBody(Sp066Evidence.body("IG13_reach_recheck.json")));
        assertEquals(COMPLETE, run(second, 0).state());
        SnapshotDao.History measured = history();
        assertEquals(4, measured.snapshots().size());
        assertEquals(4, measured.attempts().size());
        Set<String> ids = new HashSet<>();
        Map<Snapshot.Subject, MetricDefinition> definitions = new EnumMap<>(Snapshot.Subject.class);
        for (Snapshot snapshot : measured.snapshots()) {
            assertTrue(ids.add(snapshot.snapshotId()));
            assertEquals(scope, snapshot.scope());
            assertEquals(FollowerResult.Source.MO_PHONG, snapshot.source());
            MetricSample sample = snapshot.samples().get(0);
            assertEquals("v27.0", sample.providerApiVersion());
            assertEquals("COLLECTION", sample.observedAtBasis());
            assertEquals(snapshot.collectionStartedAt(), sample.observedAt());
            assertEquals(sample.observedAt(), sample.collectedAt());
            MetricDefinition previous = definitions.put(snapshot.key().subjectType(), sample.definition());
            if (previous != null) assertEquals(previous, sample.definition());
            assertNull(sample.providerPeriodStart());
            assertNull(sample.providerPeriodEnd());
            if (snapshot.key().subjectType() == Snapshot.Subject.CHANNEL) {
                assertEquals(scope.channelId(), snapshot.key().subjectId());
                assertEquals(Long.valueOf(1), sample.value());
                assertEquals("GAUGE", sample.definition().measurementKind());
            } else {
                assertEquals("simulation-local-media", snapshot.key().subjectId());
                assertEquals(Long.valueOf(0), sample.value());
                assertEquals("LIFETIME", sample.definition().measurementWindow());
                assertEquals("DISTINCT_TOTAL", sample.definition().aggregation());
            }
        }
        clock.addAndGet(60_000);
        online = false;
        run(job(true, media), 0);
        SnapshotDao.History failed = coordinator.cached(second, START, clock.get()).history();
        assertEquals(measured.snapshots(), failed.snapshots());
        assertEquals(measured.lastGood(), failed.lastGood());
        assertEquals(clock.get(), failed.lastAttempt().completedAt());
        assertEquals(4, server.getRequestCount());
    }

    @Test public void retryAfterSurvivesRecreatedCoordinatorNoEarlyCall() {
        MetricJob first = job(true);
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "95"));
        assertEquals(Long.valueOf(START + 95_000), run(first, 0).retryAt());
        CollectionAttempt failed = history().lastAttempt();
        assertEquals(SocialError.NextAction.WAIT, failed.outcomes().get(0).error().nextAction);
        coordinator = coordinator(provider);
        MetricJob restored = new MetricJob(first.collectionId(), first.collectionStartedAt(), scope, "v27.0", true, Collections.emptyList());
        clock.addAndGet(94_999);
        assertEquals(WAIT, run(restored, 1).state());
        assertEquals(1, history().attempts().size());
        assertEquals(1, server.getRequestCount());
        clock.incrementAndGet();
        good();
        assertEquals(COMPLETE, run(restored, 2).state());
        CollectionAttempt success = history().lastAttempt();
        assertNotEquals(failed.attemptId(), success.attemptId());
        assertEquals(failed.key(), success.key());
        assertEquals(START, success.collectionStartedAt());
        assertEquals(START + 95_000, history().lastGood().get(0).sample().observedAt());
        assertEquals(1, history().snapshots().size());
        assertEquals(2, server.getRequestCount());
    }

    @Test public void throttleWithoutHeaderAndTransientBudgetAreFinite() {
        MetricJob j = job(true);
        for (int i = 0; i < 3; i++) server.enqueue(new MockResponse().setResponseCode(429));
        assertEquals(Long.valueOf(START + 30_000), run(j, 0).retryAt());
        clock.addAndGet(30_000);
        assertEquals(Long.valueOf(START + 90_000), run(j, 1).retryAt());
        clock.addAndGet(60_000);
        assertEquals(EXHAUSTED, run(j, 2).state());
        clock.addAndGet(240_000);
        assertEquals(EXHAUSTED, run(j, 3).state());
        assertEquals(3, server.getRequestCount());
        assertEquals(3, history().attempts().size());
        assertTrue(history().snapshots().isEmpty());
    }

    @Test public void offlineFailurePreservesGoodTimestampSourceAndDailyGap() {
        good();
        MetricJob j = job(true);
        run(j, 0);
        SnapshotDao.SavedSample before = history().lastGood().get(0);
        clock.addAndGet(TimeUnit.DAYS.toMillis(1));
        long slot = clock.get();
        assertNull(coordinator.cached(j, slot, slot).gaps().get(0).latest());
        online = false;
        MetricJob next = job(true);
        MetricCollectionCoordinator.Result result = run(next, 0);
        assertEquals(SocialError.NextAction.RETRY_WHEN_SAFE, result.errors().get(0).nextAction);
        assertEquals(Long.valueOf(slot + 30_000), result.retryAt());
        MetricCollectionCoordinator.Cache cached = coordinator.cached(next, slot, clock.get());
        assertEquals(before, cached.history().lastGood().get(0));
        assertEquals(START, before.sample().collectedAt());
        assertEquals("MO_PHONG", before.sample().sourceRef());
        assertEquals(slot, cached.history().lastAttempt().completedAt());
        assertEquals("OFFLINE", cached.gaps().get(0).latest().failure());
        assertEquals(1, server.getRequestCount());
        assertEquals(1, history().snapshots().size());
    }

    @Test public void permissionAndUnsupportedAreTerminalWithoutHttp() {
        int i = 0;
        for (CapabilityAvailability blocked : Arrays.asList(CapabilityAvailability.PERMISSION_REQUIRED, CapabilityAvailability.UNSUPPORTED)) {
            availability = blocked;
            MetricJob j = job(true);
            MetricCollectionCoordinator.Result result = run(j, 0);
            assertEquals(FAILED, result.state());
            assertEquals(blocked == CapabilityAvailability.PERMISSION_REQUIRED ? SocialError.NextAction.RESOLVE_PERMISSION
                    : SocialError.NextAction.NONE, result.errors().get(0).nextAction);
            assertEquals(FAILED, run(j, 1).state());
            assertEquals(++i, history().attempts().size());
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test public void secondManualCannotBypassProviderRetryAfterOnSameRevision() {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "120"));
        run(job(true), 0);
        MetricJob other = job(true);
        MetricCollectionCoordinator.Result held = run(other, 0);
        assertEquals(WAIT, held.state());
        assertEquals(Long.valueOf(START + 120_000), held.retryAt());
        assertEquals(1, server.getRequestCount());
        assertEquals(1, history().attempts().size());
        clock.addAndGet(120_000);
        good();
        assertEquals(COMPLETE, run(other, 1).state());
        assertEquals(2, server.getRequestCount());
    }

    @Test public void http503FollowerWithRetryAfterRetriesAndCompletes() {
        MetricJob job = job(true);
        server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "120"));
        MetricCollectionCoordinator.Result first = run(job, 0);
        assertEquals(WAIT, first.state());
        assertEquals(Long.valueOf(START + 120_000), first.retryAt());
        assertEquals(1, server.getRequestCount());

        clock.addAndGet(119_999);
        MetricCollectionCoordinator.Result early = run(job, 1);
        assertEquals(WAIT, early.state());
        assertEquals(1, server.getRequestCount());

        clock.incrementAndGet();
        good();
        MetricCollectionCoordinator.Result completed = run(job, 2);
        assertEquals(COMPLETE, completed.state());
        assertEquals(2, server.getRequestCount());
        assertEquals(1, history().snapshots().size());
        Snapshot snapshot = history().snapshots().get(0);
        assertEquals(job.collectionId(), snapshot.key().collectionId());
        assertEquals(job.collectionStartedAt(), snapshot.collectionStartedAt());
        assertEquals(Long.valueOf(0), snapshot.samples().get(0).value());
    }

    @Test public void http503PostMetricsPartialPreservesGoodFollower() {
        MetricJob job = job(true, likes);
        good();
        server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "60"));
        MetricCollectionCoordinator.Result first = run(job, 0);
        assertEquals(WAIT, first.state());
        assertEquals(Long.valueOf(START + 60_000), first.retryAt());
        assertEquals(2, server.getRequestCount());
        assertEquals(1, history().snapshots().size());
        Snapshot followerSnapshot = history().snapshots().get(0);
        assertEquals(Snapshot.Subject.CHANNEL, followerSnapshot.key().subjectType());
        assertEquals(Long.valueOf(0), followerSnapshot.samples().get(0).value());

        clock.addAndGet(60_000);
        server.enqueue(new MockResponse().setBody("{\"summary\":{\"total_count\":5}}"));
        MetricCollectionCoordinator.Result completed = run(job, 1);
        assertEquals(COMPLETE, completed.state());
        assertEquals(3, server.getRequestCount());
        assertEquals(2, history().snapshots().size());
        Snapshot likeSnapshot = history().snapshots().stream()
                .filter(s -> s.key().subjectType() == Snapshot.Subject.POST)
                .findFirst().orElseThrow();
        assertEquals(Long.valueOf(5), likeSnapshot.samples().get(0).value());
        assertEquals(job.collectionId(), likeSnapshot.key().collectionId());
        assertEquals(job.collectionStartedAt(), likeSnapshot.collectionStartedAt());
    }

    @Test public void http503RetryAfterZeroFollowerEnforcesCoordinatorBackoffBeforeQueued200() throws Exception {
        MetricJob job = job(true);
        server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "0").setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"MO_PHONG-page\",\"followers_count\":17}"));

        MetricCollectionCoordinator.Result first = run(job, 0);
        assertEquals(WAIT, first.state());
        assertEquals(Long.valueOf(START + MetricCollectionCoordinator.BACKOFF_MS), first.retryAt());
        assertEquals(SocialError.Code.NETWORK_ERROR, first.errors().get(0).code);
        assertEquals(SocialError.NextAction.WAIT, first.errors().get(0).nextAction);
        assertEquals(1, server.getRequestCount());
        assertTrue(history().snapshots().isEmpty());
        assertEquals(1, history().attempts().size());

        clock.addAndGet(MetricCollectionCoordinator.BACKOFF_MS - 1);
        MetricCollectionCoordinator.Result early = run(job, 1);
        assertEquals(WAIT, early.state());
        assertEquals(1, server.getRequestCount());
        assertTrue(history().snapshots().isEmpty());
        assertEquals(1, history().attempts().size());

        clock.incrementAndGet();
        MetricCollectionCoordinator.Result completed = run(job, 2);
        assertEquals(COMPLETE, completed.state());
        assertEquals(2, server.getRequestCount());
        assertEquals(1, history().snapshots().size());
        Snapshot snapshot = history().snapshots().get(0);
        assertEquals(job.collectionId(), snapshot.key().collectionId());
        assertEquals(job.collectionStartedAt(), snapshot.collectionStartedAt());
        assertEquals(Long.valueOf(17), snapshot.samples().get(0).value());
        assertEquals(2, history().attempts().size());
    }

    @Test public void http503RetryAfterZeroPostMetricsKeepsGoodFollowerAndRetriesOnlyMissingTarget() throws Exception {
        MetricJob job = job(true, likes);
        good();
        server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "0").setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"summary\":{\"total_count\":5}}"));

        MetricCollectionCoordinator.Result first = run(job, 0);
        assertEquals(WAIT, first.state());
        assertEquals(Long.valueOf(START + MetricCollectionCoordinator.BACKOFF_MS), first.retryAt());
        assertEquals(2, server.getRequestCount());
        assertEquals(1, history().snapshots().size());
        Snapshot followerSnapshot = history().snapshots().get(0);
        assertEquals(Snapshot.Subject.CHANNEL, followerSnapshot.key().subjectType());
        assertEquals(Long.valueOf(0), followerSnapshot.samples().get(0).value());

        clock.addAndGet(MetricCollectionCoordinator.BACKOFF_MS - 1);
        assertEquals(WAIT, run(job, 1).state());
        assertEquals(2, server.getRequestCount());
        assertEquals(1, history().snapshots().size());

        clock.incrementAndGet();
        MetricCollectionCoordinator.Result completed = run(job, 2);
        assertEquals(COMPLETE, completed.state());
        assertEquals(3, server.getRequestCount());
        assertEquals(2, history().snapshots().size());
        assertEquals(followerSnapshot, history().snapshots().stream()
                .filter(s -> s.key().subjectType() == Snapshot.Subject.CHANNEL).findFirst().orElseThrow());
        Snapshot likeSnapshot = history().snapshots().stream()
                .filter(s -> s.key().subjectType() == Snapshot.Subject.POST).findFirst().orElseThrow();
        assertEquals(Long.valueOf(5), likeSnapshot.samples().get(0).value());
        assertEquals(job.collectionId(), likeSnapshot.key().collectionId());
        assertEquals(job.collectionStartedAt(), likeSnapshot.collectionStartedAt());

        RecordedRequest req1 = server.takeRequest();
        RecordedRequest req2 = server.takeRequest();
        RecordedRequest req3 = server.takeRequest();
        assertTrue(req1.getPath().contains("followers_count"));
        assertTrue(req2.getPath().contains("/reactions"));
        assertTrue(req3.getPath().contains("/reactions"));
    }

    @Test public void http503RetryAfterZeroRespectsMaxExecutionsAndBackoff() {
        MetricJob job = job(true);
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "0").setBody("{}"));
        }
        assertEquals(Long.valueOf(START + 30_000), run(job, 0).retryAt());
        clock.addAndGet(30_000);
        assertEquals(Long.valueOf(START + 90_000), run(job, 1).retryAt());
        clock.addAndGet(60_000);
        assertEquals(EXHAUSTED, run(job, 2).state());
        clock.addAndGet(240_000);
        assertEquals(EXHAUSTED, run(job, 3).state());
        assertEquals(3, server.getRequestCount());
        assertEquals(3, history().attempts().size());
        assertTrue(history().snapshots().isEmpty());
    }

    @Test public void http503SlowAndIoErrorBodiesPreserveDeadlineAndBackoffForBothAdapters() throws Exception {
        AtomicInteger callbackCount = new AtomicInteger();
        client = client.newBuilder()
                .readTimeout(150, TimeUnit.MILLISECONDS)
                .eventListener(new EventListener() {
                    @Override public void responseFailed(Call call, java.io.IOException ioe) {
                        clock.addAndGet(5_000L);
                    }
                }).build();
        configureCollection();
        // Wrap provider to verify single delivery per read() call.
        MetricProvider inner = provider;
        provider = new MetricProvider() {
            @Override public Cancelable read(FollowerRequest request, FollowerMetricAdapter.ResultCallback callback) {
                return inner.read(request, result -> { callbackCount.incrementAndGet(); callback.complete(result); });
            }
            @Override public Cancelable read(PostRequest request, PostMetricAdapter.ResultCallback callback) {
                return inner.read(request, result -> { callbackCount.incrementAndGet(); callback.complete(result); });
            }
        };
        coordinator = coordinator(provider);

        // Follower: 503 + Retry-After: 120 with body delayed past readTimeout; clock advances +5s during body read.
        MetricJob followerJob = job(true);
        server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "120")
                .setBody("{}").setBodyDelay(450, TimeUnit.MILLISECONDS));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"MO_PHONG-page\",\"followers_count\":21}"));

        MetricCollectionCoordinator.Result waitFollower = run(followerJob, 0);
        assertEquals(1, callbackCount.get());
        assertEquals(WAIT, waitFollower.state());
        // Header arrived at START -> deadline is START + 120_000, not (START + 5_000) + 120_000.
        assertEquals(Long.valueOf(START + 120_000L), waitFollower.retryAt());
        assertEquals(1, server.getRequestCount());

        clock.set(START + 119_999L);
        assertEquals(WAIT, run(followerJob, 1).state());
        assertEquals(1, server.getRequestCount());

        clock.set(START + 120_000L);
        MetricCollectionCoordinator.Result followerDone = run(followerJob, 2);
        assertEquals(COMPLETE, followerDone.state());
        assertEquals(2, server.getRequestCount());
        assertEquals(2, callbackCount.get());
        assertEquals(Long.valueOf(21), history().snapshots().get(0).samples().get(0).value());

        // Post metrics: follower succeeds, post hits 503 + Retry-After: 120 with truncated body I/O failure.
        clock.set(START + 200_000L);
        long postStart = clock.get();
        MetricJob postJob = job(true, likes);
        good();
        server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "120")
                .setBody("{\"truncated_payload_1234567890\":\"abc\"}")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"summary\":{\"total_count\":8}}"));

        callbackCount.set(0);
        MetricCollectionCoordinator.Result waitPost = run(postJob, 0);
        assertEquals(2, callbackCount.get());
        assertEquals(WAIT, waitPost.state());
        assertEquals(Long.valueOf(postStart + 120_000L), waitPost.retryAt());
        assertEquals(4, server.getRequestCount());

        clock.set(postStart + 119_999L);
        assertEquals(WAIT, run(postJob, 1).state());
        assertEquals(4, server.getRequestCount());

        clock.set(postStart + 120_000L);
        MetricCollectionCoordinator.Result postDone = run(postJob, 2);
        assertEquals(COMPLETE, postDone.state());
        assertEquals(5, server.getRequestCount());
        assertEquals(3, callbackCount.get());
        Snapshot postSnap = history().snapshots().stream()
                .filter(s -> s.key().subjectType() == Snapshot.Subject.POST && s.key().collectionId().equals(postJob.collectionId()))
                .findFirst().orElseThrow();
        assertEquals(Long.valueOf(8), postSnap.samples().get(0).value());
    }

    @Test public void unownedPostDoesNotBlockIndependentFollower() {
        good();
        MetricJob j = job(true, new MetricJob.PostTarget("not-owned", shares.query(), null));
        assertEquals(PARTIAL, run(j, 0).state());
        assertEquals(1, server.getRequestCount());
        assertEquals(1, history().snapshots().size());
        assertEquals(Snapshot.Subject.CHANNEL, history().lastAttempt().key().subjectType());
        assertEquals(1, coordinator.cached(j, START, START).gaps().size());
    }

    @Test public void authAndWrongOperationDoNotRetry() {
        server.enqueue(new MockResponse().setResponseCode(401));
        assertEquals(SocialError.NextAction.RECONNECT, run(job(true), 0).errors().get(0).nextAction);
        operation = SocialOperation.READ_POST_METRICS;
        MetricJob j = job(true);
        assertEquals(FAILED, run(j, 0).state());
        assertEquals(FAILED, run(j, 1).state());
        assertEquals(1, server.getRequestCount());
        assertTrue(history().snapshots().isEmpty());
    }

    @Test public void groupsEndpointsBySubjectKeepsGoodAcrossPartialRetry() {
        MetricJob j = job(true, likes, shares);
        good();
        server.enqueue(new MockResponse().setBody("{\"summary\":{\"total_count\":4}}"));
        server.enqueue(new MockResponse().setResponseCode(429));
        assertEquals(WAIT, run(j, 0).state());
        assertEquals(2, history().snapshots().size());
        assertEquals(2, history().attempts().size());
        List<MetricCollectionCoordinator.Gap> gaps = coordinator.cached(j, START, clock.get()).gaps();
        assertEquals(1, gaps.size());
        assertEquals("FB_SHARES", gaps.get(0).operation());
        SnapshotDao.SavedSample like = history().lastGood().stream().filter(s -> s.key().subjectType() == Snapshot.Subject.POST).findFirst().get();
        assertEquals(Long.valueOf(4), like.sample().value());
        clock.addAndGet(30_000);
        server.enqueue(new MockResponse().setBody("{\"id\":\"post\",\"shares\":{\"count\":2}}"));
        assertEquals(COMPLETE, run(j, 1).state());
        assertTrue(history().lastGood().contains(like));
        assertEquals(3, history().attempts().size());
        assertEquals(4, server.getRequestCount());
        assertEquals(2, history().snapshots().size());
        assertTrue(coordinator.cached(j, START, clock.get()).gaps().isEmpty());
    }

    @Test public void providerOmissionIsPartialAndKeepsOlderGoodMetric() {
        server.enqueue(new MockResponse().setBody("{\"id\":\"post\",\"shares\":{\"count\":8}}"));
        assertEquals(COMPLETE, run(job(false, shares), 0).state());
        SnapshotDao.SavedSample old = history().lastGood().get(0);
        clock.incrementAndGet();
        server.enqueue(new MockResponse().setBody("{\"summary\":{\"total_count\":3}}"));
        server.enqueue(new MockResponse().setBody("{\"id\":\"post\"}"));
        MetricJob j = job(false, likes, shares);
        assertEquals(PARTIAL, run(j, 0).state());
        assertEquals(PARTIAL, run(j, 1).state());
        assertTrue(history().lastGood().contains(old));
        assertEquals(2, history().lastAttempt().outcomes().size());
        assertEquals("PROVIDER_OMITTED", coordinator.cached(j, START, clock.get()).gaps().get(0).latest().samples().get(0).missingReason());
        assertEquals(3, server.getRequestCount());
    }

    @Test public void missingPostOwnerAndOversizeJobsAreRejectedBeforeHttp() {
        MetricJob.PostTarget absent = new MetricJob.PostTarget("absent", likes.query(), null);
        assertEquals(FAILED, run(job(false, absent), 0).state());
        List<MetricJob.PostTarget> many = new ArrayList<>();
        for (int i = 0; i < 8; i++) many.add(new MetricJob.PostTarget("p" + i, likes.query(), null));
        assertThrows(IllegalArgumentException.class, () -> MetricJob.fresh(START, scope, "v27.0", true, many));
        assertThrows(IllegalArgumentException.class, () -> job(false, shares, shares));
        assertEquals(0, server.getRequestCount());
        assertTrue(history().attempts().isEmpty());
    }

    /** A deliberately non-cooperative provider for late, crossed and re-delivered callbacks. */
    private final class Held implements MetricProvider {
        final CountDownLatch started = new CountDownLatch(1);
        FollowerMetricAdapter.ResultCallback callback;
        FollowerRequest original;
        int calls;
        volatile boolean cancelled;
        @Override public Cancelable read(FollowerRequest r, FollowerMetricAdapter.ResultCallback cb) {
            calls++;
            original = r;
            callback = cb;
            started.countDown();
            return () -> cancelled = true;
        }
        @Override public Cancelable read(PostRequest r, PostMetricAdapter.ResultCallback cb) { throw new AssertionError(); }
        FollowerResult goodResult(FollowerRequest r) {
            FollowerResult.MetricDefinition def = new FollowerResult.MetricDefinition(r);
            return new FollowerResult(FollowerResult.Status.SUCCESS, def,
                    new FollowerResult.MetricSample(r, def, 7L, null, clock.get(), FollowerResult.Source.MO_PHONG, "MO_PHONG"),
                    null, null, null);
        }
    }

    @Test public void overlappingScopeAndLateAfterCancellation() throws Exception {
        Held held = new Held();
        coordinator = coordinator(held);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MetricCollectionCoordinator.Result> a = executor.submit(() -> run(job(true), 0));
            assertTrue(held.started.await(5, TimeUnit.SECONDS));
            assertEquals(BUSY, run(job(true), 0).state());
            coordinator.cancel(scope.channelId(), 7);
            assertEquals(CANCELLED, a.get(5, TimeUnit.SECONDS).state());
            held.callback.complete(held.goodResult(held.original));
            assertTrue(held.cancelled);
            assertEquals(1, held.calls);
            assertTrue(history().attempts().isEmpty());
            assertTrue(history().snapshots().isEmpty());
        } finally { executor.shutdownNow(); }
    }

    @Test public void cancelStopsOnlyTheMatchingInFlightHttpCall() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MetricCollectionCoordinator.Result> running = executor.submit(() -> run(job(true), 0));
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
            okhttp3.Call http = client.dispatcher().runningCalls().get(0);
            coordinator.cancel("MO_PHONG-B", 7);
            coordinator.cancel(scope.channelId(), 8);
            assertFalse(http.isCanceled());
            coordinator.cancel(scope.channelId(), 7);
            assertEquals(CANCELLED, running.get(5, TimeUnit.SECONDS).state());
            assertTrue(http.isCanceled());
            assertTrue(history().attempts().isEmpty());
            assertTrue(history().snapshots().isEmpty());
        } finally { executor.shutdownNow(); }
    }

    @Test public void revisionChangeBetweenDispatchAndWriteFailsTransaction() throws Exception {
        Held held = new Held();
        coordinator = coordinator(held);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MetricCollectionCoordinator.Result> a = executor.submit(() -> run(job(true), 0));
            assertTrue(held.started.await(5, TimeUnit.SECONDS));
            dao.bind(channel(8));
            held.callback.complete(held.goodResult(held.original));
            assertEquals(CANCELLED, a.get(5, TimeUnit.SECONDS).state());
            assertEquals(0, dao.definitionCount());
            assertEquals(1, held.calls);
        } finally { executor.shutdownNow(); }
    }

    @Test public void crossedCallbackIsRejectedAndRedeliveryKeepsAttemptIdentity() throws Exception {
        Held held = new Held();
        coordinator = coordinator(held);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MetricJob j = job(true);
            Future<?> a = executor.submit(() -> run(j, 0));
            assertTrue(held.started.await(5, TimeUnit.SECONDS));
            held.callback.complete(held.goodResult(request("different-collection")));
            assertTrue(assertThrows(ExecutionException.class, () -> a.get(5, TimeUnit.SECONDS)).getCause() instanceof IllegalArgumentException);
            assertTrue(history().attempts().isEmpty());
            SnapshotReading reading = SnapshotReading.from(held.original, held.goodResult(held.original));
            MetricCollectionCoordinator.Batch batch = new MetricCollectionCoordinator.Batch("MO_PHONG-delivery", START,
                    clock.get(), Collections.singletonList(reading));
            batch.deliver(SnapshotWriter.simulation(dao));
            batch.deliver(SnapshotWriter.simulation(dao));
            assertEquals(COMPLETE, run(j, 1).state());
            assertEquals("MO_PHONG-delivery", history().lastAttempt().attemptId());
            assertEquals(1, history().attempts().size());
            assertEquals(1, history().snapshots().size());
            assertEquals(1, held.calls);
        } finally { executor.shutdownNow(); }
    }
}
