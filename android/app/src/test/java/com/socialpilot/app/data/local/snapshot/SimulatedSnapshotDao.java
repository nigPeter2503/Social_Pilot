package com.socialpilot.app.data.local.snapshot;

import com.socialpilot.app.data.remote.social.FollowerResult.Source;
import com.socialpilot.app.domain.contract.social.ChannelInfo;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MO_PHONG only. Writes and root changes share one lock; only a completed staging copy is published. */
public final class SimulatedSnapshotDao implements SnapshotDao {
    private record Post(Snapshot.Scope scope, String nativeId) {}
    private record AttemptKey(Snapshot.Key key, String id) {}
    private record Series(Snapshot.Subject type, String id, String definitionId, Long start, Long end) {}
    private static final Comparator<SavedSample> BY_OBSERVATION = Comparator
            .comparingLong((SavedSample row) -> row.sample().observedAt())
            .thenComparingLong(row -> row.sample().collectedAt()).thenComparing(SavedSample::snapshotId);
    private static final Comparator<CollectionAttempt> BY_COMPLETION = Comparator
            .comparingLong(CollectionAttempt::completedAt).thenComparing(CollectionAttempt::attemptId)
            .thenComparing(a -> a.key().subjectType()).thenComparing(a -> a.key().subjectId())
            .thenComparing(a -> a.key().collectionId());

    private final Map<String, ChannelInfo> channels = new HashMap<>();
    private final Map<Post, String> posts = new HashMap<>();
    private State committed = new State();
    public boolean failAtAttempt;

    public synchronized void bind(ChannelInfo channel) { channels.put(channel.channelId, channel); }
    public synchronized void own(Snapshot.Scope scope, String nativeId, String localId) {
        posts.put(new Post(scope, nativeId), localId);
    }
    public synchronized int definitionCount() { return committed.definitions.size(); }

    @Override public synchronized <T> T transaction(Work<T> work) {
        State staged = new State();
        staged.snapshots.putAll(committed.snapshots);
        staged.attempts.putAll(committed.attempts);
        staged.definitions.putAll(committed.definitions);
        T result = work.run(staged);
        committed = staged;
        return result;
    }

    private final class State implements Transaction {
        final Map<Snapshot.Key, Snapshot> snapshots = new LinkedHashMap<>();
        final Map<AttemptKey, CollectionAttempt> attempts = new LinkedHashMap<>();
        final Map<String, MetricDefinition> definitions = new HashMap<>();
        @Override public ChannelInfo channel(String id) { return channels.get(id); }
        @Override public String localPostId(Snapshot.Scope scope, String id) { return posts.get(new Post(scope, id)); }
        @Override public Snapshot snapshot(Snapshot.Key key) { return snapshots.get(key); }
        @Override public CollectionAttempt attempt(Snapshot.Key key, String id) { return attempts.get(new AttemptKey(key, id)); }
        @Override public Long rateLimitedUntil(Snapshot.Scope scope, Source source, long now) {
            Long until = null;
            for (CollectionAttempt a : attempts.values()) {
                if (!a.scope().equals(scope) || a.source() != source) continue;
                for (CollectionAttempt.Outcome outcome : a.outcomes()) {
                    Long time = outcome.retryAfterAt();
                    if (time != null && time > now && (until == null || time > until)) until = time;
                }
            }
            return until;
        }
        @Override public List<CollectionAttempt> attemptsForCollection(Snapshot.Key key) {
            List<CollectionAttempt> rows = new ArrayList<>();
            for (CollectionAttempt attempt : attempts.values()) if (key.equals(attempt.key())) rows.add(attempt);
            rows.sort(BY_COMPLETION);
            return rows;
        }
        @Override public CollectionAttempt firstAttempt(Snapshot.Key key) {
            for (CollectionAttempt a : attempts.values()) if (key.equals(a.key())) return a;
            return null;
        }
        @Override public void register(MetricDefinition definition) {
            MetricDefinition old = definitions.put(definition.definitionId(), definition);
            if (old != null && !old.equals(definition)) throw new IllegalArgumentException("changed definition");
        }
        @Override public void saveSnapshot(Snapshot snapshot) { snapshots.put(snapshot.key(), snapshot); }
        @Override public void insertAttempt(CollectionAttempt attempt) {
            if (failAtAttempt) throw new IllegalStateException("MO_PHONG injected rollback");
            if (attempts.put(new AttemptKey(attempt.key(), attempt.attemptId()), attempt) != null) {
                throw new IllegalStateException("duplicate attempt");
            }
        }
    }

    @Override public synchronized History read(Snapshot.Scope scope, Source source, long from, long through) {
        ChannelInfo current = channels.get(scope.channelId());
        if (source != Source.MO_PHONG || !scope.matches(current) || current.connectionStatus == ConnectionStatus.DISCONNECTED) {
            throw new IllegalStateException("inactive read scope");
        }
        if (from > through) throw new IllegalArgumentException("reversed time range");
        List<Snapshot> snapshots = new ArrayList<>();
        Map<Series, SavedSample> good = new LinkedHashMap<>();
        for (Snapshot s : committed.snapshots.values()) {
            if (!scope.equals(s.scope()) || source != s.source()) continue;
            boolean inWindow = false;
            for (MetricSample m : s.samples()) {
                if (m.observedAt() > through) continue;
                inWindow |= m.observedAt() >= from;
                if (m.value() != null) {
                    Series series = new Series(s.key().subjectType(), s.key().subjectId(), m.definition().definitionId(),
                            m.requestedPeriodStart(), m.requestedPeriodEnd());
                    SavedSample candidate = new SavedSample(s.snapshotId(), s.key(), m);
                    SavedSample previous = good.get(series);
                    if (previous == null || BY_OBSERVATION.compare(candidate, previous) > 0) good.put(series, candidate);
                }
            }
            if (inWindow) snapshots.add(s);
        }
        List<CollectionAttempt> attempts = new ArrayList<>();
        CollectionAttempt latest = null;
        for (CollectionAttempt a : committed.attempts.values()) {
            if (!scope.equals(a.scope()) || source != a.source() || a.completedAt() > through) continue;
            if (a.completedAt() >= from) attempts.add(a);
            if (latest == null || BY_COMPLETION.compare(a, latest) > 0) latest = a;
        }
        snapshots.sort(Comparator.comparingLong(Snapshot::collectedAt).thenComparing(Snapshot::snapshotId));
        attempts.sort(BY_COMPLETION);
        return new History(snapshots, attempts, new ArrayList<>(good.values()), latest);
    }
}
