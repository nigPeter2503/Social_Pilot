package com.socialpilot.app.data.repository.metrics;

import com.socialpilot.app.data.local.snapshot.CollectionAttempt;
import com.socialpilot.app.data.local.snapshot.CollectionAttempt.Outcome;
import com.socialpilot.app.data.local.snapshot.MetricSample;
import com.socialpilot.app.data.local.snapshot.Snapshot;
import com.socialpilot.app.data.local.snapshot.SnapshotDao;
import com.socialpilot.app.data.local.snapshot.SnapshotWriter;
import com.socialpilot.app.data.remote.social.*;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.ChannelInfo;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.SocialError;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Blocking worker-thread entry point. Durable attempts and frozen WorkManager input drive recovery. */
public final class MetricCollectionCoordinator {
    public interface Clock { long now(); }
    public interface Network { boolean connected(); }
    public enum State { COMPLETE, PARTIAL, FAILED, WAIT, EXHAUSTED, CANCELLED, BUSY }
    public record Result(State state, boolean gap, Long retryAt, List<SocialError> errors) {
        public Result { errors = Collections.unmodifiableList(new ArrayList<>(errors)); }
    }

    public static final int MAX_EXECUTIONS = 3;
    public static final int MAX_WORK_STARTS = 8;
    public static final long BACKOFF_MS = 30_000;
    private static final long CALLBACK_SECONDS = 20;

    /** A fresh instance for each Worker. Stopping also interrupts a wait for an adapter callback. */
    public static final class Control {
        private boolean stopped;
        private Thread runner;
        private synchronized void enter() throws InterruptedException {
            check();
            runner = Thread.currentThread();
        }
        private synchronized void leave() { runner = null; }
        public synchronized void cancel() {
            stopped = true;
            if (runner != null) runner.interrupt();
        }
        private synchronized void check() throws InterruptedException {
            if (stopped || Thread.currentThread().isInterrupted()) throw new InterruptedException();
        }
        private synchronized void deliver(SnapshotWriter writer, Batch batch) throws InterruptedException {
            check();
            batch.deliver(writer);
        }
    }

    /** A delivery can be repeated after a storage/acknowledgement failure without changing its identity. */
    public record Batch(String attemptId, long startedAt, long completedAt, List<SnapshotReading> readings) {
        public Batch { readings = Collections.unmodifiableList(new ArrayList<>(readings)); }
        public void deliver(SnapshotWriter writer) { writer.write(attemptId, startedAt, completedAt, readings); }
    }

    private record Subject(Snapshot.Subject type, String nativeId) {}
    private record Target(Subject subject, String operation, MetricJob.PostTarget post) {}

    private final SnapshotDao dao;
    private final SnapshotWriter writer;
    private final FollowerResult.Source source;
    private final MetricProvider provider;
    private final Clock clock;
    private final Network network;
    private final Map<Snapshot.Scope, Control> active = new HashMap<>();

    public MetricCollectionCoordinator(SnapshotDao dao, FollowerResult.Source source, MetricProvider provider,
            Clock clock, Network network) {
        this.dao = Objects.requireNonNull(dao);
        if (source != FollowerResult.Source.MO_PHONG && source != FollowerResult.Source.API_THAT) {
            throw new IllegalArgumentException("snapshot namespace");
        }
        this.source = source;
        writer = source == FollowerResult.Source.MO_PHONG ? SnapshotWriter.simulation(dao) : SnapshotWriter.production(dao);
        this.provider = Objects.requireNonNull(provider);
        this.clock = Objects.requireNonNull(clock);
        this.network = Objects.requireNonNull(network);
    }

    public synchronized void cancel(String channelId, long revision) {
        for (Map.Entry<Snapshot.Scope, Control> entry : active.entrySet()) {
            if (entry.getKey().channelId().equals(channelId) && entry.getKey().connectionRevision() == revision) {
                entry.getValue().cancel();
            }
        }
    }

    public Result run(MetricJob job, int workStart, Control control) {
        synchronized (this) {
            if (active.containsKey(job.scope())) return simple(State.BUSY);
            active.put(job.scope(), control);
        }
        try {
            control.enter();
            return collect(job, workStart, control);
        } catch (IllegalStateException writeFailure) {
            ChannelInfo current = dao.transaction(tx -> tx.channel(job.scope().channelId()));
            if (!job.scope().matches(current) || current.connectionStatus != ConnectionStatus.CONNECTED) {
                return new Result(State.CANCELLED, true, null, Collections.singletonList(
                        new SocialError(SocialError.Code.CHANNEL_DISCONNECTED, SocialError.NextAction.RECONNECT)));
            }
            throw writeFailure;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return simple(State.CANCELLED);
        } finally {
            control.leave();
            synchronized (this) { active.remove(job.scope()); }
        }
    }

    private static Result simple(State state) { return new Result(state, true, null, Collections.emptyList()); }

    private static boolean shouldRetry(Outcome outcome) {
        SocialError e = outcome.error();
        return "CALLBACK_TIMEOUT".equals(outcome.failure()) || e != null && (e.code == SocialError.Code.NETWORK_ERROR
                || e.code == SocialError.Code.RATE_LIMITED);
    }

    /** A gap belongs to the expected daily slot and operation. It is never stored as a measurement. */
    public record Gap(long slot, Snapshot.Subject type, String nativeId, String operation, Outcome latest) {}
    public record Cache(SnapshotDao.History history, List<Gap> gaps) {
        public Cache { gaps = Collections.unmodifiableList(new ArrayList<>(gaps)); }
    }
    public Cache cached(MetricJob expected, long expectedSlot, long now) {
        SnapshotDao.History history = dao.read(expected.scope(), source, expectedSlot, now);
        List<Gap> gaps = new ArrayList<>();
        for (List<Target> group : targets(expected).values()) {
            Subject subject = group.get(0).subject();
            String local = subject.type() == Snapshot.Subject.CHANNEL ? expected.scope().channelId()
                    : dao.transaction(tx -> tx.localPostId(expected.scope(), subject.nativeId()));
            for (Target target : group) {
                Outcome latest = null;
                long latestTime = Long.MIN_VALUE;
                for (CollectionAttempt attempt : history.attempts()) {
                    if (attempt.key().subjectType() != subject.type() || !attempt.key().subjectId().equals(local)
                            || attempt.completedAt() < latestTime) continue;
                    for (Outcome outcome : attempt.outcomes()) {
                        if (matchesExpected(expected, target, outcome)) {
                            latest = outcome;
                            latestTime = attempt.completedAt();
                        }
                    }
                }
                boolean complete = latest != null && "SUCCESS".equals(latest.adapterStatus());
                if (complete) for (MetricSample s : latest.samples()) complete &= s.value() != null && s.collectedAt() >= expectedSlot;
                if (!complete) gaps.add(new Gap(expectedSlot, subject.type(), subject.nativeId(), target.operation(), latest));
            }
        }
        return new Cache(history, gaps);
    }

    private static boolean matchesExpected(MetricJob job, Target target, Outcome outcome) {
        if (!target.operation().equals(outcome.operation())) return false;
        PostRequest.Period period = target.post() == null ? null : target.post().period();
        for (MetricSample s : outcome.samples()) {
            if (!job.apiVersion().equals(s.providerApiVersion()) || !Objects.equals(s.requestedPeriodStart(),
                    period == null ? null : period.start) || !Objects.equals(s.requestedPeriodEnd(),
                    period == null ? null : period.end)) return false;
        }
        return true;
    }

    private static Map<Subject, List<Target>> targets(MetricJob job) {
        Map<Subject, List<Target>> groups = new LinkedHashMap<>();
        if (job.follower()) {
            Subject channel = new Subject(Snapshot.Subject.CHANNEL, job.scope().channelId());
            groups.put(channel, Collections.singletonList(new Target(channel, "FOLLOWER_COUNT", null)));
        }
        for (MetricJob.PostTarget post : job.posts()) {
            Subject subject = new Subject(Snapshot.Subject.POST, post.postId());
            if (!groups.containsKey(subject)) groups.put(subject, new ArrayList<>());
            groups.get(subject).add(new Target(subject, post.query().name(), post));
        }
        return groups;
    }

    private static final class Progress {
        int executions;
        Outcome last;
        long completedAt;
        void record(Outcome outcome, long at) {
            executions++;
            if (last == null || at >= completedAt) {
                last = outcome;
                completedAt = at;
            }
        }
        boolean transientError() {
            if (last == null) return false;
            return shouldRetry(last);
        }
        long retryAt() {
            long delay = BACKOFF_MS << Math.min(executions - 1, MAX_EXECUTIONS - 1);
            long backoff = completedAt > Long.MAX_VALUE - delay ? Long.MAX_VALUE : completedAt + delay;
            return last.retryAfterAt() == null ? backoff : Math.max(backoff, last.retryAfterAt());
        }
    }

    private Result collect(MetricJob job, int workStart, Control control) throws InterruptedException {
        Map<Subject, List<Target>> groups = targets(job);
        Map<Target, Progress> progress = new LinkedHashMap<>();
        SocialError admission = dao.transaction(tx -> restore(job, groups, progress, tx));
        if (admission != null) return new Result(State.FAILED, true, null, Collections.singletonList(admission));
        Result before = summarize(progress.values(), workStart);
        if (before.state() != State.WAIT || before.retryAt() != null && clock.now() < before.retryAt()) return before;

        Long embargo = dao.transaction(tx -> tx.rateLimitedUntil(job.scope(), source, clock.now()));
        if (embargo != null) return new Result(State.WAIT, true, embargo, Collections.singletonList(
                new SocialError(SocialError.Code.RATE_LIMITED, SocialError.NextAction.WAIT)));

        String attemptId = UUID.randomUUID().toString();
        for (List<Target> subject : groups.values()) {
            List<SnapshotReading> readings = new ArrayList<>();
            boolean pause = false;
            List<Progress> dispatched = new ArrayList<>();
            for (Target target : subject) {
                Progress p = progress.get(target);
                if (p.last != null && !p.transientError()) continue;
                control.check();
                SnapshotReading reading = read(job, target);
                readings.add(reading);
                dispatched.add(p);
                pause = shouldRetry(reading.outcome);
                if (pause) break;
            }
            if (!readings.isEmpty()) {
                long completed = clock.now();
                control.deliver(writer, new Batch(attemptId, job.collectionStartedAt(), completed, readings));
                for (int i = 0; i < readings.size(); i++) dispatched.get(i).record(readings.get(i).outcome, completed);
            }
            if (pause) break;
        }
        return summarize(progress.values(), workStart + 1);
    }

    /** Read exact subject keys before any provider IO. The writer checks ownership again at commit. */
    private SocialError restore(MetricJob job, Map<Subject, List<Target>> groups, Map<Target, Progress> progress,
            SnapshotDao.Transaction tx) {
        ChannelInfo channel = tx.channel(job.scope().channelId());
        if (!job.scope().matches(channel) || channel.connectionStatus != ConnectionStatus.CONNECTED) {
            SocialError.Code code = job.scope().matches(channel) && channel.connectionStatus == ConnectionStatus.REAUTH_REQUIRED
                    ? SocialError.Code.AUTH_REQUIRED : SocialError.Code.CHANNEL_DISCONNECTED;
            return new SocialError(code, SocialError.NextAction.RECONNECT);
        }
        for (Map.Entry<Subject, List<Target>> entry : groups.entrySet()) {
            Subject subject = entry.getKey();
            String local = subject.type() == Snapshot.Subject.CHANNEL ? channel.channelId
                    : tx.localPostId(job.scope(), subject.nativeId());
            if (local == null) {
                for (Target target : entry.getValue()) {
                    Progress unowned = new Progress();
                    unowned.record(failure(job, target, "UNOWNED_POST",
                            new SocialError(SocialError.Code.INVALID_CONTENT, SocialError.NextAction.NONE)).outcome, clock.now());
                    progress.put(target, unowned);
                }
                continue;
            }
            Snapshot.Key key = new Snapshot.Key(channel.channelId, subject.type(), local, job.collectionId());
            List<CollectionAttempt> previous = tx.attemptsForCollection(key);
            for (Target target : entry.getValue()) {
                Progress p = new Progress();
                progress.put(target, p);
                for (CollectionAttempt a : previous) {
                    if (!a.scope().equals(job.scope()) || a.source() != source || a.collectionStartedAt() != job.collectionStartedAt()) {
                        throw new IllegalArgumentException("collection identity mismatch");
                    }
                    for (Outcome o : a.outcomes()) {
                        if (!target.operation().equals(o.operation())) continue;
                        if (!matchesExpected(job, target, o)) throw new IllegalArgumentException("collection target changed");
                        p.record(o, a.completedAt());
                    }
                }
            }
        }
        return null;
    }

    private static Result summarize(Collection<Progress> all, int startsUsed) {
        boolean good = false, gap = false, pending = false, exhausted = false;
        Long retry = null;
        List<SocialError> errors = new ArrayList<>();
        for (Progress p : all) {
            Outcome o = p.last;
            if (o == null) { pending = true; gap = true; continue; }
            if (o.error() != null) errors.add(o.error());
            gap |= !"SUCCESS".equals(o.adapterStatus());
            for (MetricSample s : o.samples()) {
                good |= s.value() != null;
                gap |= s.value() == null;
                if (o.error() == null && "PERMISSION_REQUIRED".equals(s.missingReason())) {
                    errors.add(new SocialError(SocialError.Code.PERMISSION_REQUIRED, SocialError.NextAction.RESOLVE_PERMISSION));
                } else if (o.error() == null && "UNSUPPORTED".equals(s.missingReason())) {
                    errors.add(new SocialError(SocialError.Code.UNSUPPORTED_OPERATION, SocialError.NextAction.NONE));
                }
            }
            if (p.transientError()) {
                pending = true;
                exhausted |= p.executions >= MAX_EXECUTIONS;
                retry = retry == null ? p.retryAt() : Math.max(retry, p.retryAt());
            }
        }
        State state;
        if (pending) state = exhausted || startsUsed >= MAX_WORK_STARTS ? State.EXHAUSTED : State.WAIT;
        else state = !gap ? State.COMPLETE : good ? State.PARTIAL : State.FAILED;
        return new Result(state, gap, retry, errors);
    }

    private SnapshotReading read(MetricJob job, Target target) throws InterruptedException {
        if (!network.connected()) return networkFailure(job, target, "OFFLINE");
        if (target.post() == null) {
            FollowerRequest request = job.followerRequest();
            ArrayBlockingQueue<FollowerResult> callback = new ArrayBlockingQueue<>(1);
            FollowerResult r = await(callback, provider.read(request, callback::offer));
            if (r == null) return failure(job, target, "CALLBACK_TIMEOUT", null);
            if (r.status == FollowerResult.Status.REJECTED) return failure(job, target, r.failure.name(), r.error);
            return SnapshotReading.from(request, r);
        }
        PostRequest request = job.postRequest(target.post());
        ArrayBlockingQueue<PostResult> callback = new ArrayBlockingQueue<>(1);
        PostResult r = await(callback, provider.read(request, callback::offer));
        if (r == null) return failure(job, target, "CALLBACK_TIMEOUT", null);
        if (r.status == PostResult.Status.REJECTED) return failure(job, target, r.failure.name(), r.error);
        return SnapshotReading.from(request, r);
    }

    private static <T> T await(ArrayBlockingQueue<T> queue, Cancelable call) throws InterruptedException {
        try { return queue.poll(CALLBACK_SECONDS, TimeUnit.SECONDS); }
        finally { call.cancel(); }
    }
    private static SnapshotReading networkFailure(MetricJob job, Target t, String reason) {
        return failure(job, t, reason, new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE));
    }
    private static SnapshotReading failure(MetricJob job, Target t, String reason, SocialError error) {
        return SnapshotReading.failure(job.scope(), t.subject().type(), t.subject().nativeId(), job.collectionId(),
                t.operation(), reason, error);
    }
}
