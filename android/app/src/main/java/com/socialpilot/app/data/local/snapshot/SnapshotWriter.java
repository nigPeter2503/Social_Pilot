package com.socialpilot.app.data.local.snapshot;

import com.socialpilot.app.data.remote.social.FollowerResult.Source;
import com.socialpilot.app.data.remote.social.SnapshotReading;
import com.socialpilot.app.domain.contract.social.ChannelInfo;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Blocking IO. SP-045.04 supplies the durable collection ID, its start and a per-execution attempt ID. */
public final class SnapshotWriter {
    private final SnapshotDao dao;
    private final Source namespace;

    private SnapshotWriter(SnapshotDao dao, Source source) {
        this.dao = Objects.requireNonNull(dao);
        this.namespace = source;
    }

    public static SnapshotWriter production(SnapshotDao dao) { return new SnapshotWriter(dao, Source.API_THAT); }
    public static SnapshotWriter simulation(SnapshotDao dao) { return new SnapshotWriter(dao, Source.MO_PHONG); }

    /** One terminal batch for one subject, possibly spanning several endpoints.
     * Returns the retained snapshot (null before any successful response). */
    public Snapshot write(String attemptId, long collectionStartedAt, long completedAt, List<SnapshotReading> readings) {
        if (attemptId == null || attemptId.trim().isEmpty() || collectionStartedAt > completedAt || readings.isEmpty()) {
            throw new IllegalArgumentException("invalid collection attempt");
        }
        SnapshotReading first = readings.get(0);
        List<CollectionAttempt.Outcome> outcomes = new ArrayList<>();
        Map<SampleKey, MetricSample> incoming = new LinkedHashMap<>();
        boolean hasResponse = false;
        boolean complete = true;
        for (SnapshotReading reading : readings) {
            if (!first.scope.equals(reading.scope) || first.type != reading.type
                    || !first.nativeSubjectId.equals(reading.nativeSubjectId) || !first.collectionId.equals(reading.collectionId)) {
                throw new IllegalArgumentException("mixed collection targets");
            }
            CollectionAttempt.Outcome outcome = reading.outcome;
            outcomes.add(outcome);
            hasResponse |= "SUCCESS".equals(outcome.adapterStatus()) || "PARTIAL".equals(outcome.adapterStatus());
            complete &= "SUCCESS".equals(outcome.adapterStatus());
            for (MetricSample sample : outcome.samples()) {
                if (sample.source() != namespace || sample.collectedAt() < collectionStartedAt || sample.collectedAt() > completedAt) {
                    throw new IllegalArgumentException("source or collection time");
                }
                complete &= sample.value() != null;
                if (incoming.put(new SampleKey(sample), sample) != null) throw new IllegalArgumentException("duplicate sample");
            }
        }
        CollectionAttempt.Status status = !hasResponse ? CollectionAttempt.Status.FAILURE
                : complete ? CollectionAttempt.Status.SUCCESS : CollectionAttempt.Status.PARTIAL;
        return dao.transaction(tx -> {
            ChannelInfo channel = tx.channel(first.scope.channelId());
            if (!first.scope.matches(channel) || channel.connectionStatus != ConnectionStatus.CONNECTED) {
                throw new IllegalStateException("inactive channel scope");
            }
            String subjectId = first.type == Snapshot.Subject.CHANNEL ? channel.channelId
                    : tx.localPostId(first.scope, first.nativeSubjectId);
            if (subjectId == null) throw new IllegalStateException("unowned post");
            Snapshot.Key key = new Snapshot.Key(channel.channelId, first.type, subjectId, first.collectionId);
            CollectionAttempt firstAttempt = tx.firstAttempt(key);
            if (firstAttempt != null && (!first.scope.equals(firstAttempt.scope()) || namespace != firstAttempt.source()
                    || collectionStartedAt != firstAttempt.collectionStartedAt())) {
                throw new IllegalArgumentException("collection ID already bound");
            }
            Snapshot previous = tx.snapshot(key);
            if (tx.attempt(key, attemptId) != null) return previous;
            for (MetricSample sample : incoming.values()) tx.register(sample.definition());
            Snapshot retained = previous;
            if (status != CollectionAttempt.Status.FAILURE) {
                retained = merge(previous, key, first.scope, collectionStartedAt, completedAt, incoming);
                if (retained != previous) tx.saveSnapshot(retained);
            }
            tx.insertAttempt(new CollectionAttempt(attemptId, key, first.scope, namespace,
                    collectionStartedAt, completedAt, status, outcomes));
            return retained;
        });
    }

    /** A retry may fill a missing metric. An already measured value retains its original clock and provenance. */
    private Snapshot merge(Snapshot previous, Snapshot.Key key, Snapshot.Scope scope, long startedAt,
            long completedAt, Map<SampleKey, MetricSample> incoming) {
        Map<SampleKey, MetricSample> retained = new LinkedHashMap<>();
        if (previous != null) {
            for (MetricSample sample : previous.samples()) retained.put(new SampleKey(sample), sample);
        }
        boolean changed = false;
        for (Map.Entry<SampleKey, MetricSample> entry : incoming.entrySet()) {
            MetricSample old = retained.get(entry.getKey());
            if (old == null || (old.value() == null && entry.getValue().value() != null)) {
                retained.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (!changed) return previous;
        return new Snapshot(previous == null ? UUID.randomUUID().toString() : previous.snapshotId(), key, scope,
                namespace, startedAt, previous == null ? completedAt : Math.max(previous.collectedAt(), completedAt),
                new ArrayList<>(retained.values()));
    }

    private record SampleKey(String definitionId, Long start, Long end) {
        SampleKey(MetricSample sample) {
            this(sample.definition().definitionId(), sample.requestedPeriodStart(), sample.requestedPeriodEnd());
        }
    }
}
