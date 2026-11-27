package com.socialpilot.app.data.repository.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource.*;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.analytics.AnalyticsRepository;
import com.socialpilot.app.domain.contract.analytics.AnalyticsRepository.*;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import com.socialpilot.app.domain.contract.analytics.DashboardData;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.ChannelScope;
import com.socialpilot.app.domain.contract.social.SocialError;
import com.socialpilot.app.domain.contract.social.SocialOperation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.socialpilot.app.data.repository.analytics.AnalyticsFixture.*;
import static org.junit.Assert.*;

/** Real repository -> observer -> queued UI. Source and UI are MO_PHONG, not Room/device. */
public final class AnalyticsDashboardConsumerTest {
    private final Manual io = new Manual(), callbacks = new Manual();
    private final SimulatedAnalyticsSource source = new SimulatedAnalyticsSource();
    private final DashboardQuery query = query(ENGAGEMENT.id(), "p");
    private final DashboardError postError = new DashboardError(SCOPE, SocialOperation.READ_POST_METRICS,
            new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE), null, null);
    private long now = END + 10;
    private DefaultAnalyticsRepository repository;
    private Consumer consumer;

    @Before public void setUp() {
        List<Sample> history = pair("p", 12, 3, 5, 400);
        history.add(sample(FOLLOWER, SCOPE.channelId(), END, 10L));
        source.cached = new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS,
                List.of(new Channel(SCOPE, "MO_PHONG", FOLLOWER.id())),
                List.of(post("p", START, null, null)), history,
                new Attempt(END + 7, END + 7, SyncState.IDLE, false, List.of()));
        repository = newRepository();
        consumer = new Consumer(repository);
        consumer.attach(query);
        drainRepository();
        consumer.ui.all();
    }

    @After public void close() { repository.close(); }

    @Test public void partialRendersNewFollowerAndOldEngagementBeforeTerminalOnUi() {
        CalculatedValue previous = consumer.rendered.weeklyEngagement().get(0).metric();
        preparePartial();
        consumer.refresh();
        drainRepository();
        assertNull(consumer.completed); // Repository callbacks have only queued UI work.
        consumer.ui.all();
        assertPartial(previous, consumer.rendered);
        assertEquals(RefreshOutcome.PARTIAL, consumer.completed.outcome());
        assertEquals(List.of(postError), consumer.completed.errors());
        assertSame(consumer.rendered, consumer.atTerminal);
        for (DashboardData frame : consumer.frames) {
            if (frame.contentState() == DashboardData.ContentState.CONTENT
                    && Long.valueOf(15).equals(frame.channelCards().get(0).follower().value())) {
                assertEquals(Long.valueOf(20), frame.weeklyEngagement().get(0).metric().value());
            }
        }
    }

    @Test public void reattachDuringPartialKeepsOneRefreshAndDropsDetachedUiWork() {
        CalculatedValue previous = consumer.rendered.weeklyEngagement().get(0).metric();
        preparePartial();
        consumer.refresh();
        drainRepository();
        consumer.attach(query); // Old observer work is already queued on the UI thread.
        consumer.ui.all();
        assertNull(consumer.rendered);
        assertEquals(RefreshOutcome.PARTIAL, consumer.completed.outcome());
        drainRepository();
        consumer.ui.all();
        assertPartial(previous, consumer.rendered);
        assertEquals(1, source.refreshes);
    }

    @Test public void cancelAndQueryOrRevisionChangeRejectPartialAlreadyQueuedToUi() {
        preparePartial();
        consumer.refresh();
        drainRepository();
        consumer.cancelRefresh();
        consumer.ui.all();
        assertNull(consumer.completed);
        assertEquals(Long.valueOf(15), consumer.rendered.channelCards().get(0).follower().value());

        consumer.refresh();
        drainRepository();
        DashboardQuery changed = new DashboardQuery(query.scopes(), query.metricDefinitionId(), START, END,
                query.zoneId(), query.cohort(), DashboardQuery.Grouping.FORMAT, 1);
        consumer.attach(changed);
        consumer.ui.all();
        assertNull(consumer.rendered);
        assertNull(consumer.completed);
        drainRepository();
        consumer.ui.all();
        assertEquals(changed, consumer.rendered.query());

        consumer.refresh();
        drainRepository();
        ChannelScope next = new ChannelScope(SCOPE.platform(), SCOPE.channelId(), 2);
        source.revision(next);
        DashboardQuery reconnected = new DashboardQuery(List.of(next), query.metricDefinitionId(), START, END,
                query.zoneId(), query.cohort(), query.grouping(), query.topLimit());
        consumer.attach(reconnected);
        consumer.ui.all();
        assertNull(consumer.rendered);
        assertNull(consumer.completed);
        drainRepository();
        consumer.ui.all();
        assertEquals(reconnected, consumer.rendered.query());
        assertEquals(DashboardData.ContentState.EMPTY, consumer.rendered.contentState());
    }

    @Test public void newRepositoryReadsCommittedPartialHistoryWithoutItsFormerMemoryCache() {
        CalculatedValue previous = consumer.rendered.weeklyEngagement().get(0).metric();
        preparePartial();
        consumer.refresh();
        drainRepository();
        consumer.ui.all();
        repository.close();
        assertTrue(source.listeners.isEmpty());
        repository = newRepository();
        consumer = new Consumer(repository);
        consumer.attach(query);
        drainRepository();
        consumer.ui.all();
        assertPartial(previous, consumer.rendered);
        assertNull(consumer.completed); // Observation never starts or invents a refresh.
        assertEquals(1, source.refreshes);
    }

    private void preparePartial() {
        now = END + STALE_POLICY + 100;
        // New collection, actually collected now; observedAt remains within the query.
        MetricSource provenance = new MetricSource("new-follower-snapshot", "new-follower-collection",
                SCOPE, SCOPE.channelId(), FOLLOWER.id(), EvidenceSource.MO_PHONG, "consumer-partial-fixture",
                END, ObservedAtBasis.PROVIDER, now, null, null, "mock-v1");
        source.refreshed = new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS, List.of(), List.of(),
                List.of(new Sample(FOLLOWER, Subject.CHANNEL, 15L, null, provenance, null)),
                new Attempt(now, now, SyncState.ERROR, false, List.of(postError)));
    }

    private void assertPartial(CalculatedValue previous, DashboardData actual) {
        assertEquals(query, actual.query());
        assertEquals(EvidenceSource.MO_PHONG, actual.source());
        assertEquals(DashboardData.ContentState.CONTENT, actual.contentState());
        assertEquals(SyncState.ERROR, actual.syncState());
        assertEquals(List.of(postError), actual.errors());
        assertFalse(actual.offline());
        assertEquals(Long.valueOf(now), actual.lastAttemptAt());
        MetricReading follower = actual.channelCards().get(0).follower();
        assertEquals(Long.valueOf(15), follower.value());
        assertEquals(Long.valueOf(now), follower.collectedAt());
        assertEquals(Boolean.FALSE, follower.freshness().isStale());
        assertEquals(List.of("new-follower-snapshot"), follower.sourceSnapshotIds());
        assertEquals("new-follower-collection", follower.provenance().get(0).collectionId());
        CalculatedValue engagement = actual.weeklyEngagement().get(0).metric();
        assertEquals(Long.valueOf((12 - 0) + (3 - 0) + (5 - 0)), engagement.value());
        assertEquals(previous.provenance(), engagement.provenance());
        assertEquals(previous.sourceSnapshotIds(), engagement.sourceSnapshotIds());
        assertEquals(previous.bounds(), engagement.bounds());
        assertEquals(previous.coverage(), engagement.coverage());
        assertEquals(CoverageLevel.FULL, engagement.coverage().temporal());
        assertEquals(CoverageLevel.FULL, engagement.coverage().cohort());
        assertEquals(1, engagement.coverage().includedCount());
        assertEquals(1, engagement.coverage().totalCount());
        assertEquals(Long.valueOf(START + 7), engagement.freshness().lastSuccessAt());
        assertEquals(Boolean.TRUE, engagement.freshness().isStale());
        for (MetricSource sample : engagement.provenance()) assertEquals(SCOPE, sample.scope());
    }

    private DefaultAnalyticsRepository newRepository() {
        return new DefaultAnalyticsRepository(source, EvidenceSource.MO_PHONG, STALE_POLICY,
                io, callbacks, () -> now);
    }

    private void drainRepository() { callbacks.all(); io.all(); callbacks.all(); }

    private static final class Manual implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable work) { tasks.add(work); }
        void all() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }

    /** Consumer example only; SP-047 owns the real ViewModel and Android lifecycle. */
    private static final class Consumer {
        final AnalyticsRepository repository;
        final Manual ui = new Manual();
        final List<DashboardData> frames = new ArrayList<>();
        DashboardQuery selected;
        Cancelable observation, request;
        long viewGeneration, refreshGeneration;
        DashboardData rendered, atTerminal;
        RefreshResult completed;
        Consumer(AnalyticsRepository repository) { this.repository = repository; }
        void attach(DashboardQuery query) {
            viewGeneration++;
            if (observation != null) observation.cancel();
            rendered = null;
            if (!query.equals(selected)) { cancelRefresh(); selected = query; }
            long token = viewGeneration;
            observation = repository.observeCachedDashboard(query, data -> ui.execute(() -> {
                if (token == viewGeneration && selected.equals(data.query())) {
                    rendered = data;
                    frames.add(data);
                }
            }));
        }
        void refresh() {
            cancelRefresh();
            long token = refreshGeneration;
            request = repository.refresh(selected, result -> ui.execute(() -> {
                if (token == refreshGeneration && selected.equals(result.query())) {
                    completed = result;
                    atTerminal = rendered;
                }
            }));
        }
        void cancelRefresh() {
            refreshGeneration++;
            if (request != null) request.cancel();
            completed = null;
            atTerminal = null;
        }
    }
}
