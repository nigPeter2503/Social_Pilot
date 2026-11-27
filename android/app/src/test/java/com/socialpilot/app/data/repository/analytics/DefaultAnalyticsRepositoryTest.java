package com.socialpilot.app.data.repository.analytics;

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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;
import static com.socialpilot.app.data.repository.analytics.AnalyticsFixture.*;
import static org.junit.Assert.*;

public final class DefaultAnalyticsRepositoryTest {
    private final Manual io = new Manual(), callbacks = new Manual();
    private final SimulatedAnalyticsSource source = new SimulatedAnalyticsSource();
    private long now = END + 10;
    private final DefaultAnalyticsRepository repo = new DefaultAnalyticsRepository(source, EvidenceSource.MO_PHONG,
            STALE_POLICY, io, callbacks, () -> now);
    private final DashboardQuery q = query(ENGAGEMENT.id(), "a");
    private final List<DashboardData> states = new ArrayList<>();
    private final List<RefreshResult> results = new ArrayList<>();
    @After public void close() { repo.close(); }
    private void drain() {
        int guard = 0;
        while (!io.queue.isEmpty() || !callbacks.queue.isEmpty()) {
            callbacks.all();
            io.all();
            if (++guard > 100) throw new AssertionError("Unbounded work");
        }
    }
    private Batch content() { return batch(List.of(post("a", START, "topic", "text")), pair("a", 12, 3, 5, 400)); }
    private DashboardData latest() { return states.get(states.size() - 1); }
    private SourceFailure network() {
        return new SourceFailure(List.of(new DashboardError(SCOPE, SocialOperation.READ_POST_METRICS,
                new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE), null, null)), true);
    }
    @Test public void observeReturnsImmediatelyUnavailableThenEmptyWithoutRefresh() {
        Cancelable handle = repo.observeCachedDashboard(q, states::add);
        assertNotNull(handle);
        assertTrue(states.isEmpty());
        callbacks.all();
        assertEquals(ContentState.UNAVAILABLE, latest().contentState());
        drain();
        assertEquals(ContentState.EMPTY, latest().contentState());
        assertEquals(0, source.refreshes);
    }
    @Test public void refreshPublishesLoadingAndDataBeforeSingleTerminalCallback() {
        repo.observeCachedDashboard(q, states::add);
        drain();
        source.refreshed = content();
        repo.refresh(q, result -> {
            assertEquals(ContentState.CONTENT, latest().contentState());
            assertEquals(SyncState.IDLE, latest().syncState());
            results.add(result);
        });
        assertTrue(results.isEmpty());
        callbacks.all();
        assertEquals(SyncState.LOADING, latest().syncState());
        drain();
        assertEquals(1, results.size());
        assertEquals(RefreshOutcome.SUCCESS, results.get(0).outcome());
        assertEquals(Long.valueOf(now), latest().lastAttemptAt());
        assertEquals(Long.valueOf(END + 7), latest().lastSuccessAt());
        assertEquals(Long.valueOf(20), latest().weeklyEngagement().get(0).metric().value());
    }
    @Test public void offlineRefreshPreservesLastGoodDataTimeAndProvenanceAndUpdatesFreshness() {
        source.cached = followers(List.of(sample(FOLLOWER, SCOPE.channelId(), END, 100L)));
        DashboardQuery follower = query(FOLLOWER.id());
        repo.observeCachedDashboard(follower, states::add);
        drain();
        var ids = latest().channelCards().get(0).follower().sourceSnapshotIds();
        now = END + STALE_POLICY + 100;
        source.refreshFailure = network();
        repo.refresh(follower, results::add);
        drain();
        DashboardData actual = latest();
        assertEquals(Long.valueOf(100), actual.channelCards().get(0).follower().value());
        assertEquals(ids, actual.channelCards().get(0).follower().sourceSnapshotIds());
        assertTrue(actual.channelCards().get(0).follower().freshness().isStale());
        assertEquals(Long.valueOf(END + 7), actual.lastSuccessAt());
        assertEquals(Long.valueOf(now), actual.lastAttemptAt());
        assertEquals(SyncState.ERROR, actual.syncState());
        assertTrue(actual.offline());
        assertEquals(RefreshOutcome.FAILED, results.get(0).outcome());
    }
    @Test public void errorWithoutHistoryIsUnavailableRatherThanEmptySuccess() {
        source.readFailure = network();
        repo.observeCachedDashboard(q, states::add);
        drain();
        assertEquals(ContentState.UNAVAILABLE, latest().contentState());
        assertEquals(SyncState.ERROR, latest().syncState());
        assertTrue(latest().offline());
        assertEquals(1, latest().errors().size());
    }
    @Test public void storageExceptionIsSanitizedAndNotAnEmptySuccess() {
        source.storageFailure = new IllegalStateException("raw body and credential URL must never escape");
        repo.observeCachedDashboard(q, states::add);
        drain();
        assertEquals(ContentState.UNAVAILABLE, latest().contentState());
        assertEquals(LocalFailure.CACHE_READ_FAILED, latest().errors().get(0).localFailure());
    }
    @Test public void canceledObserveSuppressesQueuedCallbacksAndUnregistersIdempotently() {
        Cancelable handle = repo.observeCachedDashboard(q, states::add);
        io.all();
        handle.cancel();
        handle.cancel();
        drain();
        assertTrue(states.isEmpty());
        source.changed();
        drain();
        assertTrue(states.isEmpty());
    }
    @Test public void canceledRefreshSuppressesAlreadyQueuedResult() {
        source.refreshed = content();
        Cancelable handle = repo.refresh(q, results::add);
        io.all();
        handle.cancel();
        handle.cancel();
        drain();
        assertTrue(results.isEmpty());
    }
    @Test public void newestAttemptWinsEvenWhenOlderCompletionIsAlreadyQueued() {
        source.refreshed = content();
        repo.observeCachedDashboard(q, states::add);
        drain();
        repo.refresh(q, results::add);
        io.all();
        source.refreshed = batch(List.of(post("a", START, "topic", "text")), pair("a", 1, 0, 0, 10));
        repo.refresh(q, results::add);
        io.all();
        callbacks.all();
        assertEquals(1, results.size());
        assertEquals(Long.valueOf(1), latest().weeklyEngagement().get(0).metric().value());
    }
    @Test public void revisionChangeDropsQueuedCacheAndRefreshCallbacks() {
        source.cached = content();
        repo.observeCachedDashboard(q, states::add);
        repo.refresh(q, results::add);
        io.all();
        source.revision(new ChannelScope(SCOPE.platform(), SCOPE.channelId(), 2));
        drain();
        assertTrue(states.isEmpty());
        assertTrue(results.isEmpty());
    }
    @Test public void queryChangeWithCanceledOldHandlesDropsOldResults() {
        source.cached = content();
        Cancelable oldObserver = repo.observeCachedDashboard(q, states::add);
        Cancelable oldRefresh = repo.refresh(q, results::add);
        io.all();
        oldObserver.cancel();
        oldRefresh.cancel();
        DashboardQuery next = query(RATE.id(), "a");
        repo.observeCachedDashboard(next, states::add);
        drain();
        for (DashboardData state : states) assertEquals(next, state.query());
        assertTrue(results.isEmpty());
    }
    @Test public void invalidationReloadsAndReattachmentNeverRefreshes() {
        Cancelable first = repo.observeCachedDashboard(q, states::add);
        drain();
        source.cached = content();
        source.changed();
        drain();
        assertEquals(ContentState.CONTENT, latest().contentState());
        first.cancel();
        repo.observeCachedDashboard(q, states::add);
        drain();
        assertEquals(ContentState.CONTENT, latest().contentState());
        assertEquals(0, source.refreshes);
    }
    @Test public void productionNamespaceRejectsSimulatedHistory() {
        source.cached = content();
        source.assertSimulated();
        try (DefaultAnalyticsRepository production = DefaultAnalyticsRepository.production(source, 1000, io, callbacks, () -> now)) {
            production.observeCachedDashboard(q, states::add);
            drain();
            assertEquals(EvidenceSource.API_THAT, latest().source());
            assertEquals(ContentState.UNAVAILABLE, latest().contentState());
            assertEquals(SyncState.ERROR, latest().syncState());
            assertTrue(latest().postMetrics().isEmpty());
        }
    }
    @Test public void closeCancelsWatchAndAllPendingCallbacks() {
        repo.observeCachedDashboard(q, states::add);
        repo.refresh(q, results::add);
        repo.close();
        repo.close();
        drain();
        assertTrue(states.isEmpty());
        assertTrue(results.isEmpty());
        assertTrue(source.listeners.isEmpty());
    }
    @Test public void callbacksAndIoActuallyRunOffCallerThread() throws Exception {
        var ioWorker = Executors.newSingleThreadExecutor();
        var deliveryWorker = Executors.newSingleThreadExecutor();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        Thread caller = Thread.currentThread();
        try (DefaultAnalyticsRepository async = new DefaultAnalyticsRepository(source, EvidenceSource.MO_PHONG,
                STALE_POLICY, ioWorker, deliveryWorker, () -> END)) {
            async.refresh(q, r -> { callbackThread.set(Thread.currentThread()); complete.countDown(); });
            assertTrue(complete.await(5, TimeUnit.SECONDS));
            assertNotEquals(caller, callbackThread.get());
        } finally {
            ioWorker.shutdownNow();
            deliveryWorker.shutdownNow();
        }
    }
    @Test public void cancelAfterLoadingRestoresReadableCacheWithoutTerminalCallback() {
        source.cached = content();
        repo.observeCachedDashboard(q, states::add);
        drain();
        Cancelable refresh = repo.refresh(q, results::add);
        callbacks.all();
        assertEquals(SyncState.LOADING, latest().syncState());
        refresh.cancel();
        drain();
        assertEquals(SyncState.IDLE, latest().syncState());
        assertEquals(Long.valueOf(20), latest().weeklyEngagement().get(0).metric().value());
        assertTrue(results.isEmpty());
    }
    @Test public void observerFailureCannotBlockOthersOrTerminalRefresh() {
        repo.observeCachedDashboard(q, d -> { throw new IllegalStateException("Broken view"); });
        repo.observeCachedDashboard(q, states::add);
        drain();
        source.refreshed = content();
        repo.refresh(q, results::add);
        drain();
        assertEquals(ContentState.CONTENT, latest().contentState());
        assertEquals(1, results.size());
    }
    @Test public void reattachDoesNotEraseNewerRefreshFailureState() {
        source.cached = content();
        Cancelable observe = repo.observeCachedDashboard(q, states::add);
        drain();
        source.refreshFailure = network();
        now = END + STALE_POLICY;
        repo.refresh(q, results::add);
        drain();
        observe.cancel();
        repo.observeCachedDashboard(q, states::add);
        drain();
        assertEquals(SyncState.ERROR, latest().syncState());
        assertTrue(latest().offline());
        assertEquals(Long.valueOf(now), latest().lastAttemptAt());
        assertEquals(1, source.refreshes);
    }
    @Test public void partialRefreshPreservesDataAndNormalizedErrors() {
        Batch good = content();
        source.refreshed = new Batch(true, good.source(), good.definitions(), good.channels(), good.posts(), good.samples(),
                new Attempt(END + 7, END + 8, SyncState.ERROR, true, network().errors));
        repo.observeCachedDashboard(q, states::add);
        drain();
        repo.refresh(q, results::add);
        drain();
        assertEquals(RefreshOutcome.PARTIAL, results.get(0).outcome());
        assertEquals(ContentState.CONTENT, latest().contentState());
        assertEquals(SyncState.ERROR, latest().syncState());
        assertTrue(latest().offline());
        assertEquals(1, latest().errors().size());
    }
    @Test public void revisionInvalidatedDuringCachePublicationSuppressesTerminalAndOtherObservers() {
        repo.observeCachedDashboard(q, data -> {
            if (data.contentState() == ContentState.CONTENT)
                source.revision(new ChannelScope(SCOPE.platform(), SCOPE.channelId(), 2));
        });
        repo.observeCachedDashboard(q, states::add);
        drain();
        source.refreshed = content();
        repo.refresh(q, results::add);
        drain();
        assertTrue(results.isEmpty());
        assertNotEquals(ContentState.CONTENT, latest().contentState());
    }
    private static final class Manual implements Executor {
        final Queue<Runnable> queue = new ArrayDeque<>();
        @Override public void execute(Runnable task) { queue.add(task); }
        void all() { while (!queue.isEmpty()) queue.remove().run(); }
    }
}
