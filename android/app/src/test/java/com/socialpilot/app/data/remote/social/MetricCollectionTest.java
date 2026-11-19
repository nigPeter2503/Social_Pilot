package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.data.local.snapshot.*;
import com.socialpilot.app.data.repository.metrics.*;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.*;
import static com.socialpilot.app.data.repository.metrics.MetricCollectionCoordinator.State.*;
import static org.junit.Assert.*;

/** MO_PHONG only: fake UTC clock, scoped DAO, credentials and provider HTTP. */
public class MetricCollectionTest {
    private static final long START = 1_795_060_800_000L;
    private final AtomicLong clock = new AtomicLong(START);
    private final Snapshot.Scope scope = new Snapshot.Scope(Platform.FACEBOOK, FollowerRequest.Account.FACEBOOK_PAGE,
            "MO_PHONG-channel", "MO_PHONG-page", 7);
    private final SimulatedSnapshotDao dao = new SimulatedSnapshotDao();
    private final OkHttpClient client = new OkHttpClient();
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
        return new ChannelInfo(scope.channelId(), scope.platform(), scope.externalAccountId(), AccountType.PAGE,
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
