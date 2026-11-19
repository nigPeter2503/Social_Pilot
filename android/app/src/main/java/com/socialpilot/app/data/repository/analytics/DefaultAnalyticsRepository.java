package com.socialpilot.app.data.repository.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource;
import com.socialpilot.app.domain.analytics.AnalyticsSource.*;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.analytics.AnalyticsRepository;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import com.socialpilot.app.domain.contract.analytics.DashboardData;
import com.socialpilot.app.domain.contract.analytics.DashboardData.ContentState;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

/** Local repository only. Inject background IO and background callback executors; never direct/UI. */
public final class DefaultAnalyticsRepository implements AnalyticsRepository, AutoCloseable {
    public interface Clock { long nowMillis(); }
    private final AnalyticsSource source;
    private final AnalyticsProjector projector;
    private final EvidenceSource namespace;
    private final Executor io;
    private final SerialExecutor delivery;
    private final Clock clock;
    private final Map<DashboardQuery, DashboardData> cache = new HashMap<>();
    private final Map<DashboardQuery, Long> generations = new HashMap<>();
    private final Map<DashboardQuery, Long> publications = new HashMap<>();
    private final Map<DashboardQuery, Batch> goodBatches = new HashMap<>();
    private final List<Subscription> observers = new ArrayList<>();
    private final List<Ticket> tickets = new ArrayList<>();
    private final Cancelable watch;
    private volatile boolean closed;

    public DefaultAnalyticsRepository(AnalyticsSource source, EvidenceSource namespace, long staleAfterMillis,
                                      Executor io, Executor callbackWorker, Clock clock) {
        this.source = Objects.requireNonNull(source);
        this.namespace = Objects.requireNonNull(namespace);
        this.projector = new AnalyticsProjector(staleAfterMillis);
        this.io = Objects.requireNonNull(io);
        this.delivery = new SerialExecutor(Objects.requireNonNull(callbackWorker));
        this.clock = Objects.requireNonNull(clock);
        watch = source.watch(this::reloadObserved);
    }

    /** Production construction enforces the API_THAT namespace; no fixture source is installed here. */
    public static DefaultAnalyticsRepository production(AnalyticsSource source, long staleAfterMillis,
                                                        Executor io, Executor callbacks, Clock clock) {
        return new DefaultAnalyticsRepository(source, EvidenceSource.API_THAT, staleAfterMillis, io, callbacks, clock);
    }

    @Override public Cancelable observeCachedDashboard(DashboardQuery query, DashboardObserver observer) {
        Objects.requireNonNull(observer);
        com.socialpilot.app.domain.analytics.AnalyticsCalculator.zone(query.zoneId());
        Ticket ticket = ticket();
        Subscription subscription = new Subscription(query, observer, ticket);
        synchronized (this) { observers.add(subscription); }
        delivery.execute(() -> ticket.deliver(() -> guarded(query, () -> notifyObserver(observer, current(query)))));
        read(query, ticket);
        return () -> {
            ticket.cancel();
            synchronized (this) { observers.remove(subscription); tickets.remove(ticket); }
        };
    }

    @Override public Cancelable refresh(DashboardQuery query, RefreshCallback callback) {
        Objects.requireNonNull(callback);
        com.socialpilot.app.domain.analytics.AnalyticsCalculator.zone(query.zoneId());
        Ticket ticket = ticket();
        long attemptAt = clock.nowMillis();
        long generation;
        synchronized (this) {
            generation = counter(generations, query) + 1;
            generations.put(query, generation);
        }
        delivery.execute(() -> ticket.deliver(() -> guarded(query, () -> {
            if (!isCurrent(query, generation)) return;
            publish(state(current(query), SyncState.LOADING, attemptAt, current(query).offline(), List.of()));
        })));
        FutureTask<Void> work = new FutureTask<>(() -> {
            if (ticket.canceled()) return null;
            try {
                Batch batch = source.refreshProjection(query);
                DashboardData result = projector.project(query, batch, namespace, clock.nowMillis());
                if (!batch.available()) throw localFailure();
                deliverTerminal(ticket, () -> guarded(query, () -> {
                    if (!isCurrent(query, generation)) return;
                    synchronized (this) { goodBatches.put(query, batch); }
                    DashboardData finalData = state(result, batch.attempt().errors().isEmpty() ? SyncState.IDLE : SyncState.ERROR,
                            attemptAt, batch.attempt().offline(), batch.attempt().errors());
                    publish(finalData);
                    ticket.deliver(() -> guarded(query, () -> {
                        if (isCurrent(query, generation)) callback.onComplete(new RefreshResult(query,
                                batch.attempt().errors().isEmpty() ? RefreshOutcome.SUCCESS : RefreshOutcome.PARTIAL,
                                batch.attempt().errors()));
                    }));
                }));
            } catch (SourceFailure failure) {
                refreshFailure(query, callback, ticket, generation, attemptAt, failure);
            } catch (RuntimeException failure) {
                // Deliberately discard raw exceptions: storage messages may contain private data.
                refreshFailure(query, callback, ticket, generation, attemptAt, localFailure());
            }
            return null;
        });
        ticket.attach(work);
        io.execute(work);
        return () -> {
            ticket.cancel();
            retire(ticket);
            delivery.execute(() -> guarded(query, () -> {
                if (isCurrent(query, generation) && current(query).syncState() == SyncState.LOADING) {
                    synchronized (this) { generations.put(query, generation + 1); }
                    publish(state(freshCached(query), SyncState.IDLE, attemptAt, current(query).offline(), current(query).errors()));
                }
            }));
        };
    }

    private void refreshFailure(DashboardQuery q, RefreshCallback callback, Ticket ticket, long generation,
                                long at, SourceFailure failure) {
        deliverTerminal(ticket, () -> guarded(q, () -> {
            if (!isCurrent(q, generation)) return;
            publish(state(freshCached(q), SyncState.ERROR, at, failure.offline, failure.errors));
            ticket.deliver(() -> guarded(q, () -> {
                if (isCurrent(q, generation)) callback.onComplete(new RefreshResult(q, RefreshOutcome.FAILED, failure.errors));
            }));
        }));
    }

    private void deliverTerminal(Ticket ticket, Runnable action) {
        delivery.execute(() -> {
            try { ticket.deliver(action); }
            finally { retire(ticket); }
        });
    }

    private void read(DashboardQuery q, Ticket ticket) {
        long generation;
        long publication;
        synchronized (this) {
            generation = counter(generations, q);
            publication = counter(publications, q);
        }
        io.execute(() -> {
            if (ticket.canceled()) return;
            try {
                Batch batch = source.readCached(q);
                DashboardData result = projector.project(q, batch, namespace, clock.nowMillis());
                delivery.execute(() -> ticket.deliver(() -> guarded(q, () -> {
                    if (!isCurrent(q, generation) || !samePublication(q, publication)) return;
                    synchronized (this) { if (batch.available()) goodBatches.put(q, batch); }
                    DashboardData previous = current(q);
                    DashboardData merged = previous.lastAttemptAt() != null
                            && (result.lastAttemptAt() == null || previous.lastAttemptAt() > result.lastAttemptAt())
                            ? state(result, previous.syncState(), previous.lastAttemptAt(), previous.offline(), previous.errors()) : result;
                    publish(merged);
                })));
            } catch (SourceFailure failure) {
                readFailure(q, ticket, generation, publication, failure);
            } catch (RuntimeException failure) {
                readFailure(q, ticket, generation, publication, localFailure());
            }
        });
    }

    private void readFailure(DashboardQuery q, Ticket ticket, long generation, long publication, SourceFailure failure) {
        delivery.execute(() -> ticket.deliver(() -> guarded(q, () -> {
            if (!isCurrent(q, generation) || !samePublication(q, publication)) return;
            DashboardData result = state(freshCached(q), SyncState.ERROR, current(q).lastAttemptAt(), failure.offline, failure.errors);
            publish(result);
        })));
    }

    private void reloadObserved() {
        List<Subscription> subscriptions;
        synchronized (this) { subscriptions = new ArrayList<>(observers); }
        // Each active observer reads its exact cache key. No refresh is triggered by reattachment.
        for (Subscription s : subscriptions) read(s.query(), s.ticket());
    }

    private void guarded(DashboardQuery q, Runnable action) {
        if (!closed) source.withCurrentScopes(q.scopes(), () -> { if (!closed) action.run(); });
    }
    private synchronized boolean isCurrent(DashboardQuery q, long generation) {
        return !closed && counter(generations, q) == generation;
    }
    private synchronized boolean samePublication(DashboardQuery q, long publication) {
        return counter(publications, q) == publication;
    }
    private DashboardData freshCached(DashboardQuery q) {
        Batch batch;
        synchronized (this) { batch = goodBatches.get(q); }
        return batch == null ? current(q) : projector.project(q, batch, namespace, clock.nowMillis());
    }
    private synchronized Ticket ticket() {
        if (closed) throw new IllegalStateException("Repository closed");
        Ticket ticket = new Ticket();
        tickets.add(ticket);
        return ticket;
    }
    private synchronized void retire(Ticket ticket) { tickets.remove(ticket); }
    private synchronized DashboardData current(DashboardQuery q) { return cache.containsKey(q) ? cache.get(q) : unavailable(q); }
    private static long counter(Map<DashboardQuery, Long> map, DashboardQuery q) {
        Long value = map.get(q);
        return value == null ? 0 : value;
    }
    private DashboardData unavailable(DashboardQuery q) {
        return new DashboardData(q, namespace, ContentState.UNAVAILABLE, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), new Coverage(CoverageLevel.NONE, CoverageLevel.NONE,
                0, q.cohort().cohortPostIds().size(), List.of()), null, null, SyncState.IDLE, false, List.of());
    }
    private static DashboardData state(DashboardData d, SyncState sync, Long attemptAt,
                                       boolean offline, List<DashboardError> errors) {
        List<DashboardData.ChannelCard> cards = new ArrayList<>();
        for (DashboardData.ChannelCard card : d.channelCards()) {
            List<DashboardError> scopedErrors = new ArrayList<>();
            for (DashboardError error : errors) if (error.scope() == null || error.scope().equals(card.scope())) scopedErrors.add(error);
            cards.add(new DashboardData.ChannelCard(card.scope(), card.displayName(), card.follower(), sync, scopedErrors));
        }
        return new DashboardData(d.query(), d.source(), d.contentState(), cards, d.postMetrics(), d.growthPoints(),
                d.weeklyEngagement(), d.topPosts(), d.topPostExclusions(), d.groupedMetrics(), d.goals(), d.coverage(),
                d.lastSuccessAt(), attemptAt, sync, offline, errors);
    }
    private void publish(DashboardData data) {
        synchronized (this) {
            cache.put(data.query(), data);
            publications.put(data.query(), counter(publications, data.query()) + 1);
        }
        notifyObservers(data.query(), data);
    }
    private void notifyObservers(DashboardQuery q, DashboardData data) {
        List<Subscription> copy;
        long generation;
        long publication;
        synchronized (this) {
            copy = new ArrayList<>(observers);
            generation = counter(generations, q);
            publication = counter(publications, q);
        }
        for (Subscription s : copy) if (s.query().equals(q))
            s.ticket().deliver(() -> guarded(q, () -> {
                // An earlier observer can synchronously start another attempt or publish new data.
                if (isCurrent(q, generation) && samePublication(q, publication)) notifyObserver(s.observer(), data);
            }));
    }
    private static void notifyObserver(DashboardObserver observer, DashboardData data) {
        try { observer.onChanged(data); }
        catch (RuntimeException ignored) { /* One consumer must not break another or terminal delivery. */ }
    }
    private static SourceFailure localFailure() {
        return new SourceFailure(List.of(new DashboardError(null, null, null, LocalFailure.CACHE_READ_FAILED, null)), false);
    }
    @Override public void close() {
        List<Ticket> copy;
        synchronized (this) {
            if (closed) return;
            closed = true;
            copy = new ArrayList<>(tickets);
            observers.clear();
            tickets.clear();
            cache.clear();
            goodBatches.clear();
        }
        for (Ticket ticket : copy) ticket.cancel();
        watch.cancel();
    }
    private record Subscription(DashboardQuery query, DashboardObserver observer, Ticket ticket) {}
    private static final class Ticket implements Cancelable {
        private boolean canceled;
        private FutureTask<?> task;
        synchronized boolean canceled() { return canceled; }
        synchronized void attach(FutureTask<?> task) {
            this.task = task;
            if (canceled) task.cancel(true);
        }
        synchronized void deliver(Runnable callback) { if (!canceled) callback.run(); }
        @Override public synchronized void cancel() {
            canceled = true;
            if (task != null) task.cancel(true);
        }
    }
    private static final class SerialExecutor implements Executor {
        private final Executor executor;
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private Runnable active;
        SerialExecutor(Executor executor) { this.executor = executor; }
        @Override public synchronized void execute(Runnable task) {
            tasks.add(() -> { try { task.run(); } finally { next(); } });
            if (active == null) next();
        }
        private synchronized void next() {
            active = tasks.poll();
            if (active != null) executor.execute(active);
        }
    }
}
