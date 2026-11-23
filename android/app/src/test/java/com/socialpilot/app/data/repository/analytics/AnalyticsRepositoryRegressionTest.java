package com.socialpilot.app.data.repository.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource;
import com.socialpilot.app.domain.analytics.AnalyticsSource.*;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.analytics.AnalyticsRepository.RefreshResult;
import com.socialpilot.app.domain.contract.analytics.AnalyticsRepository.RefreshOutcome;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import com.socialpilot.app.domain.contract.analytics.DashboardData;
import com.socialpilot.app.domain.contract.analytics.DashboardData.ContentState;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.ChannelScope;
import com.socialpilot.app.domain.contract.social.SocialError;
import com.socialpilot.app.domain.contract.social.SocialOperation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;
import static com.socialpilot.app.data.repository.analytics.AnalyticsFixture.*;
import static org.junit.Assert.*;

public final class AnalyticsRepositoryRegressionTest {
    private final Manual io = new Manual(), delivery = new Manual();
    private final Probe source = new Probe();
    private long now = END + 10;
    private final DefaultAnalyticsRepository repo = new DefaultAnalyticsRepository(source, EvidenceSource.MO_PHONG,
            STALE_POLICY, io, delivery, () -> now);
    private final DashboardQuery q = query(ENGAGEMENT.id(), "p");
    private final List<DashboardData> states = new ArrayList<>();
    private final List<RefreshResult> results = new ArrayList<>();
    private Batch content(long n) { return batch(List.of(post("p", START, null, null)), pair("p", n, 0, 0, 100)); }
    private Batch contentWithFollower(long n, long follower) {
        List<Sample> samples = pair("p", n, 0, 0, 100);
        samples.add(sample(FOLLOWER, SCOPE.channelId(), END, follower));
        return new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS,
                List.of(new Channel(SCOPE, "MO_PHONG", FOLLOWER.id())),
                List.of(post("p", START, null, null)),
                samples,
                new Attempt(END + 7, END + 7, SyncState.IDLE, false, List.of()));
    }
    private DashboardData last() { return states.get(states.size() - 1); }
    private void drain() {
        for (int n = 0; !io.tasks.isEmpty() || !delivery.tasks.isEmpty(); n++) {
            assertTrue("Bounded deterministic executor", n < 100);
            delivery.all(); io.all();
        }
    }
    @After public void close() { repo.close(); }
    @Test public void cancelBeforeIoNeverCallsSourceOrTerminal() {
        Cancelable handle = repo.refresh(q, results::add);
        handle.cancel(); handle.cancel();
        drain();
        assertEquals(0, source.raw.refreshes);
        assertTrue(results.isEmpty());
    }
    private int activeHandles() throws Exception {
        // Resource invariant: terminal attempts must no longer be retained by the repository.
        var field = DefaultAnalyticsRepository.class.getDeclaredField("tickets");
        field.setAccessible(true);
        return ((List<?>) field.get(repo)).size();
    }
    @Test public void supersededTerminalAttemptsReleaseTheirHandles() throws Exception {
        source.raw.refreshed = content(1);
        repo.refresh(q, results::add); io.all();
        repo.refresh(q, results::add); io.all(); drain();
        assertEquals(1, results.size());
        assertEquals(0, activeHandles());
    }
    @Test public void rejectedRevisionTerminalAttemptsReleaseTheirHandles() throws Exception {
        repo.refresh(q, results::add); io.all();
        source.raw.revision(new ChannelScope(SCOPE.platform(), SCOPE.channelId(), 2));
        drain();
        assertTrue(results.isEmpty());
        assertEquals(0, activeHandles());
    }
    @Test public void throwingTerminalConsumerStillReleasesHandleAndAllowsNextAttempt() throws Exception {
        repo.refresh(q, r -> { throw new IllegalStateException("consumer failure"); }); io.all();
        assertThrows(IllegalStateException.class, delivery::all);
        assertEquals(0, activeHandles());
        repo.refresh(q, results::add); drain();
        assertEquals(1, results.size());
    }
    @Test public void reentrantNewAttemptSuppressesOldDataForRemainingObservers() {
        AtomicBoolean switched = new AtomicBoolean();
        repo.observeCachedDashboard(q, d -> {
            if (d.contentState() == ContentState.CONTENT && switched.compareAndSet(false, true)) {
                source.raw.refreshed = content(2);
                repo.refresh(q, results::add);
            }
        });
        repo.observeCachedDashboard(q, states::add);
        drain();
        source.raw.refreshed = content(1);
        repo.refresh(q, results::add);
        drain();
        for (DashboardData d : states) if (d.contentState() == ContentState.CONTENT && d.syncState() == SyncState.IDLE)
            assertEquals(Long.valueOf(2), d.weeklyEngagement().get(0).metric().value());
        assertEquals(1, results.size());
        assertEquals(Long.valueOf(2), last().weeklyEngagement().get(0).metric().value());
    }
    @Test public void oldIoCompletionCannotReplaceNewAttemptEvenIfRunLast() {
        repo.observeCachedDashboard(q, states::add); drain();
        repo.refresh(q, results::add);
        Runnable old = io.tasks.remove();
        repo.refresh(q, results::add);
        source.raw.refreshed = content(2);
        io.all();
        source.raw.refreshed = content(1);
        old.run();
        drain();
        assertEquals(Long.valueOf(2), last().weeklyEngagement().get(0).metric().value());
        assertEquals(1, results.size());
    }
    @Test public void storageRefreshFailurePreservesValueTimestampsAndProvenance() {
        source.raw.cached = content(20);
        repo.observeCachedDashboard(q, states::add); drain();
        DashboardData good = last();
        source.storageFailure = true;
        now = END + STALE_POLICY + 100;
        repo.refresh(q, results::add); drain();
        assertEquals(ContentState.CONTENT, last().contentState());
        assertEquals(SyncState.ERROR, last().syncState());
        assertEquals(good.lastSuccessAt(), last().lastSuccessAt());
        assertEquals(Long.valueOf(now), last().lastAttemptAt());
        var value = last().weeklyEngagement().get(0).metric();
        assertEquals(Long.valueOf(20), value.value());
        assertEquals(good.weeklyEngagement().get(0).metric().provenance(), value.provenance());
        assertTrue(value.freshness().isStale());
        assertEquals(LocalFailure.CACHE_READ_FAILED, last().errors().get(0).localFailure());
        assertEquals(RefreshOutcome.FAILED, results.get(0).outcome());
    }
    @Test public void unavailableRefreshIsFailureAndKeepsLastGoodContent() {
        source.raw.cached = content(20);
        repo.observeCachedDashboard(q, states::add); drain();
        Batch b = content(0);
        source.raw.refreshed = new Batch(false, b.source(), b.definitions(), List.of(), List.of(), List.of(), b.attempt());
        repo.refresh(q, results::add); drain();
        assertEquals(ContentState.CONTENT, last().contentState());
        assertEquals(Long.valueOf(20), last().weeklyEngagement().get(0).metric().value());
        assertEquals(SyncState.ERROR, last().syncState());
        assertEquals(RefreshOutcome.FAILED, results.get(0).outcome());
    }
    @Test public void reentrantCloseSuppressesRemainingObserversAndTerminalAndRemovesWatch() {
        repo.observeCachedDashboard(q, d -> { if (d.contentState() == ContentState.CONTENT) repo.close(); });
        repo.observeCachedDashboard(q, states::add); drain();
        source.raw.refreshed = content(1);
        repo.refresh(q, results::add); drain();
        assertTrue(results.isEmpty());
        assertNotEquals(ContentState.CONTENT, last().contentState());
        assertTrue(source.raw.listeners.isEmpty());
        assertThrows(IllegalStateException.class, () -> repo.refresh(q, results::add));
        assertThrows(IllegalStateException.class, () -> repo.observeCachedDashboard(q, states::add));
    }
    @Test public void cancelOneObserverPreservesOtherAndReattachDoesNotRefresh() {
        List<DashboardData> removed = new ArrayList<>();
        Cancelable h = repo.observeCachedDashboard(q, removed::add);
        repo.observeCachedDashboard(q, states::add); drain();
        h.cancel(); h.cancel();
        int count = removed.size();
        source.raw.cached = content(7); source.raw.changed(); drain();
        assertEquals(count, removed.size());
        assertEquals(Long.valueOf(7), last().weeklyEngagement().get(0).metric().value());
        repo.observeCachedDashboard(q, removed::add); drain();
        assertEquals(ContentState.CONTENT, removed.get(removed.size() - 1).contentState());
        assertEquals(0, source.raw.refreshes);
    }
    @Test public void revisionChangedByLoadingObserverPreventsQueuedCompletion() {
        repo.observeCachedDashboard(q, d -> {
            if (d.syncState() == SyncState.LOADING)
                source.raw.revision(new ChannelScope(SCOPE.platform(), SCOPE.channelId(), 2));
        });
        repo.observeCachedDashboard(q, states::add); drain();
        source.raw.refreshed = content(9);
        repo.refresh(q, results::add); io.all(); drain();
        assertTrue(results.isEmpty());
        assertEquals(ContentState.EMPTY, last().contentState());
    }
    @Test public void completionQueuedThenCloseCannotEmitOrReRegisterWatch() {
        source.raw.refreshed = content(1);
        repo.observeCachedDashboard(q, states::add);
        repo.refresh(q, results::add);
        io.all(); repo.close(); drain();
        source.raw.changed(); drain();
        assertTrue(states.isEmpty()); assertTrue(results.isEmpty());
        assertTrue(source.raw.listeners.isEmpty());
    }
    @Test public void cancelDuringIoSuppressesUncooperativeCompletion() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), returned = new CountDownLatch(1);
        Probe blocking = new Probe();
        blocking.beforeRefresh = () -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timeout"); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { returned.countDown(); }
        };
        blocking.raw.refreshed = content(10);
        try (DefaultAnalyticsRepository async = new DefaultAnalyticsRepository(blocking, EvidenceSource.MO_PHONG,
                STALE_POLICY, worker, delivery, () -> END + 10)) {
            Cancelable h = async.refresh(q, results::add);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            h.cancel(); h.cancel(); release.countDown();
            assertTrue(returned.await(5, TimeUnit.SECONDS));
            worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
            delivery.all();
            assertTrue(results.isEmpty());
        } finally {
            release.countDown(); worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertTrue(blocking.raw.listeners.isEmpty());
    }
    @Test public void partialRefreshPreservesPriorGoodPostMetricsValueTimestampAndProvenance() {
        source.raw.cached = contentWithFollower(20, 10);
        repo.observeCachedDashboard(q, states::add);
        drain();
        assertEquals(ContentState.CONTENT, last().contentState());
        assertEquals(Long.valueOf(20), last().weeklyEngagement().get(0).metric().value());
        assertEquals(Long.valueOf(10), last().channelCards().get(0).follower().value());
        var goodProvenance = last().weeklyEngagement().get(0).metric().provenance();
        var goodSnapshotIds = last().weeklyEngagement().get(0).metric().sourceSnapshotIds();

        now = END + STALE_POLICY + 100;
        DashboardError postMetricError = new DashboardError(SCOPE, SocialOperation.READ_POST_METRICS,
                new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE), null, null);
        source.raw.refreshed = new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS,
                List.of(new Channel(SCOPE, "MO_PHONG", FOLLOWER.id())),
                List.of(post("p", START, null, null)),
                List.of(sample(FOLLOWER, SCOPE.channelId(), END, 15L)),
                new Attempt(END + 7, now, SyncState.ERROR, false, List.of(postMetricError)));

        AtomicReference<DashboardData> dataBeforeCallback = new AtomicReference<>();
        repo.refresh(q, r -> {
            dataBeforeCallback.set(last());
            results.add(r);
        });
        drain();

        assertEquals(1, results.size());
        assertEquals(RefreshOutcome.PARTIAL, results.get(0).outcome());
        assertEquals(1, results.get(0).errors().size());

        assertNotNull(dataBeforeCallback.get());
        assertEquals(Long.valueOf(15), dataBeforeCallback.get().channelCards().get(0).follower().value());
        assertEquals(Long.valueOf(20), dataBeforeCallback.get().weeklyEngagement().get(0).metric().value());

        DashboardData current = last();
        assertEquals(ContentState.CONTENT, current.contentState());
        assertEquals(SyncState.ERROR, current.syncState());
        assertEquals(Long.valueOf(now), current.lastAttemptAt());
        assertEquals(Long.valueOf(15), current.channelCards().get(0).follower().value());

        var engagement = current.weeklyEngagement().get(0).metric();
        assertEquals(Long.valueOf(20), engagement.value());
        assertEquals(goodProvenance, engagement.provenance());
        assertEquals(goodSnapshotIds, engagement.sourceSnapshotIds());
        assertTrue(engagement.freshness().isStale());

        for (DashboardData d : states) {
            if (d.contentState() == ContentState.CONTENT && d.channelCards().get(0).follower().value() != null
                    && d.channelCards().get(0).follower().value().equals(15L)) {
                assertEquals(Long.valueOf(20), d.weeklyEngagement().get(0).metric().value());
            }
        }
    }
    @Test public void reattachCacheAndSubsequentFailedRefreshPreservesGoodMetrics() {
        source.raw.cached = contentWithFollower(20, 10);
        repo.observeCachedDashboard(q, states::add);
        drain();

        now = END + 10;
        DashboardError postMetricError = new DashboardError(SCOPE, SocialOperation.READ_POST_METRICS,
                new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE), null, null);
        source.raw.refreshed = new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS,
                List.of(new Channel(SCOPE, "MO_PHONG", FOLLOWER.id())),
                List.of(post("p", START, null, null)),
                List.of(sample(FOLLOWER, SCOPE.channelId(), END, 15L)),
                new Attempt(END + 7, now, SyncState.ERROR, false, List.of(postMetricError)));

        repo.refresh(q, results::add);
        drain();
        assertEquals(Long.valueOf(15), last().channelCards().get(0).follower().value());
        assertEquals(Long.valueOf(20), last().weeklyEngagement().get(0).metric().value());

        List<DashboardData> reattachedStates = new ArrayList<>();
        repo.observeCachedDashboard(q, reattachedStates::add);
        drain();
        DashboardData reattached = reattachedStates.get(reattachedStates.size() - 1);
        assertEquals(Long.valueOf(15), reattached.channelCards().get(0).follower().value());
        assertEquals(Long.valueOf(20), reattached.weeklyEngagement().get(0).metric().value());

        now = END + STALE_POLICY + 200;
        source.storageFailure = true;
        repo.refresh(q, results::add);
        drain();
        assertEquals(RefreshOutcome.FAILED, results.get(results.size() - 1).outcome());
        assertEquals(Long.valueOf(15), last().channelCards().get(0).follower().value());
        assertEquals(Long.valueOf(20), last().weeklyEngagement().get(0).metric().value());
    }
    @Test public void subsequentSuccessfulRefreshUpdatesMetricAndRevisionChangeDiscardsOld() {
        source.raw.cached = contentWithFollower(20, 10);
        repo.observeCachedDashboard(q, states::add);
        drain();

        DashboardError postMetricError = new DashboardError(SCOPE, SocialOperation.READ_POST_METRICS,
                new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE), null, null);
        source.raw.refreshed = new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS,
                List.of(new Channel(SCOPE, "MO_PHONG", FOLLOWER.id())),
                List.of(post("p", START, null, null)),
                List.of(sample(FOLLOWER, SCOPE.channelId(), END, 15L)),
                new Attempt(END + 7, now, SyncState.ERROR, false, List.of(postMetricError)));
        repo.refresh(q, results::add);
        drain();
        assertEquals(Long.valueOf(20), last().weeklyEngagement().get(0).metric().value());

        source.raw.refreshed = contentWithFollower(35, 15);
        repo.refresh(q, results::add);
        drain();
        assertEquals(RefreshOutcome.SUCCESS, results.get(results.size() - 1).outcome());
        assertEquals(Long.valueOf(35), last().weeklyEngagement().get(0).metric().value());

        ChannelScope rev2Scope = new ChannelScope(SCOPE.platform(), SCOPE.channelId(), 2);
        DashboardQuery rev2Query = new DashboardQuery(List.of(rev2Scope), ENGAGEMENT.id(), START, END,
                "Asia/Ho_Chi_Minh", new DashboardQuery.Cohort(List.of("p")), DashboardQuery.Grouping.TOPIC, 10);
        source.raw.revision(rev2Scope);
        List<DashboardData> rev2States = new ArrayList<>();
        repo.observeCachedDashboard(rev2Query, rev2States::add);
        drain();
        DashboardData rev2Last = rev2States.get(rev2States.size() - 1);
        assertEquals(ContentState.EMPTY, rev2Last.contentState());
    }
    @Test public void successfulPostReadRetainsBaselineWhenFollowerReadFails() {
        source.raw.cached = contentWithFollower(20, 10);
        repo.observeCachedDashboard(q, states::add); drain();
        DashboardError followerError = new DashboardError(SCOPE, SocialOperation.READ_FOLLOWER_COUNT,
                new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE), null, null);
        source.raw.refreshed = new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS, List.of(),
                List.of(post("p", START, null, null)), List.of(sample(REACTION, "p", END, 35L)),
                new Attempt(END + 7, now, SyncState.ERROR, false, List.of(followerError)));
        repo.refresh(q, results::add); drain();
        assertEquals(RefreshOutcome.PARTIAL, results.get(0).outcome());
        assertEquals(Long.valueOf(10), last().channelCards().get(0).follower().value());
        assertEquals(Long.valueOf(35), last().weeklyEngagement().get(0).metric().value());
    }
    @Test public void failedPostDoesNotDiscardAnotherPostsSuccessfulSamples() {
        DashboardQuery twoPosts = query(ENGAGEMENT.id(), "p", "other");
        List<Sample> history = pair("p", 20, 0, 0, 100);
        history.addAll(pair("other", 30, 0, 0, 100));
        source.raw.cached = batch(List.of(post("p", START, null, null), post("other", START, null, null)), history);
        List<DashboardData> observed = new ArrayList<>();
        repo.observeCachedDashboard(twoPosts, observed::add); drain();
        DashboardError failedPost = new DashboardError(SCOPE, SocialOperation.READ_POST_METRICS,
                new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE), null, null);
        source.raw.refreshed = new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS, List.of(),
                List.of(post("p", START, null, null)), List.of(sample(REACTION, "p", END, 25L)),
                new Attempt(END + 7, now, SyncState.ERROR, false, List.of(failedPost)));
        repo.refresh(twoPosts, results::add); drain();
        DashboardData latest = observed.get(observed.size() - 1);
        assertEquals(RefreshOutcome.PARTIAL, results.get(0).outcome());
        assertEquals(Long.valueOf(55), latest.weeklyEngagement().get(0).metric().value());
        assertEquals(2, latest.coverage().includedCount());
    }
    @Test public void firstRefreshAfterRepositoryCreationReadsPersistedGoodHistory() {
        source.raw.cached = contentWithFollower(20, 10);
        DashboardError postError = new DashboardError(SCOPE, SocialOperation.READ_POST_METRICS,
                new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE), null, null);
        source.raw.refreshed = new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS,
                List.of(new Channel(SCOPE, "MO_PHONG", FOLLOWER.id())), List.of(),
                List.of(sample(FOLLOWER, SCOPE.channelId(), END, 15L)),
                new Attempt(END + 7, now, SyncState.ERROR, false, List.of(postError)));
        repo.refresh(q, results::add); drain();
        repo.observeCachedDashboard(q, states::add); drain();
        assertEquals(RefreshOutcome.PARTIAL, results.get(0).outcome());
        assertEquals(Long.valueOf(15), last().channelCards().get(0).follower().value());
        assertEquals(Long.valueOf(20), last().weeklyEngagement().get(0).metric().value());
    }
    @Test public void staleReadbackCannotPublishAnAttemptBatchAsComplete() {
        source.raw.cached = contentWithFollower(20, 10);
        repo.observeCachedDashboard(q, states::add); drain();
        source.cacheOverride = source.raw.cached;
        DashboardError postError = new DashboardError(SCOPE, SocialOperation.READ_POST_METRICS,
                new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE), null, null);
        source.raw.refreshed = new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS,
                List.of(new Channel(SCOPE, "MO_PHONG", FOLLOWER.id())), List.of(),
                List.of(sample(FOLLOWER, SCOPE.channelId(), END, 15L)),
                new Attempt(END + 7, now, SyncState.ERROR, false, List.of(postError)));
        repo.refresh(q, results::add); drain();
        assertEquals(RefreshOutcome.FAILED, results.get(0).outcome());
        assertEquals(Long.valueOf(10), last().channelCards().get(0).follower().value());
        assertEquals(Long.valueOf(20), last().weeklyEngagement().get(0).metric().value());
        assertEquals(LocalFailure.CACHE_READ_FAILED, last().errors().get(0).localFailure());
    }
    private static final class Manual implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { tasks.add(task); }
        void all() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }
    private static final class Probe implements AnalyticsSource {
        final SimulatedAnalyticsSource raw = new SimulatedAnalyticsSource();
        boolean storageFailure;
        Batch cacheOverride;
        Runnable beforeRefresh = () -> {};
        @Override public Batch readCached(DashboardQuery q) throws SourceFailure {
            return cacheOverride == null ? raw.readCached(q) : cacheOverride;
        }
        @Override public Batch refreshProjection(DashboardQuery q) throws SourceFailure {
            beforeRefresh.run();
            if (storageFailure) throw new IllegalStateException("private storage context");
            return raw.refreshProjection(q);
        }
        @Override public Cancelable watch(Runnable r) { return raw.watch(r); }
        @Override public boolean withCurrentScopes(List<ChannelScope> scopes, Runnable r) { return raw.withCurrentScopes(scopes, r); }
    }
}
