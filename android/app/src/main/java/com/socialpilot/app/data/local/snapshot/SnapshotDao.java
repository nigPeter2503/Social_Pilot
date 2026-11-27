package com.socialpilot.app.data.local.snapshot;

import com.socialpilot.app.data.remote.social.FollowerResult.Source;
import com.socialpilot.app.domain.contract.social.ChannelInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Storage port for SP-045 and SP-046. Room binding awaits Channel, PublishedPost and schema slots. */
public interface SnapshotDao {
    interface Work<T> { T run(Transaction tx); }

    /** Serialize with Channel invalidation and post ownership changes. Roll back on any exception. */
    <T> T transaction(Work<T> work);

    interface Transaction {
        ChannelInfo channel(String channelId);
        /** Resolve the native ID under the exact current scope, returning null when absent. */
        String localPostId(Snapshot.Scope scope, String externalPostId);
        Snapshot snapshot(Snapshot.Key key);
        CollectionAttempt attempt(Snapshot.Key key, String attemptId);
        CollectionAttempt firstAttempt(Snapshot.Key key);
        /** Exact subject/collection key, ascending completion order. */
        List<CollectionAttempt> attemptsForCollection(Snapshot.Key key);
        /** Latest unexpired provider Retry-After for this revision and source, across manual collections. */
        Long rateLimitedUntil(Snapshot.Scope scope, Source source, long now);
        /** Insert or verify exact equality with the existing registry entry. Never REPLACE. */
        void register(MetricDefinition definition);
        /** Insert or update by key, preserving the PK and existing good samples. */
        void saveSnapshot(Snapshot snapshot);
        void insertAttempt(CollectionAttempt attempt);
    }

    /** One scoped offline read for all subjects. Include snapshots having a sample observed in
     * [from, through] (end boundary needed for deltas), and attempts completed in the same range.
     * Return entire matching snapshots so provenance remains intact. For offline cards also return
     * the latest numeric sample per subject/definition/requested period at or before through,
     * and the last completed attempt at or before through, even when they predate from.
     * Select good samples by observedAt, collectedAt, then snapshotId. Throw on stale/missing scope
     * or storage failure. Each store is bound to one source namespace. */
    History read(Snapshot.Scope scope, Source source, long from, long through);

    record SavedSample(String snapshotId, Snapshot.Key key, MetricSample sample) {}

    record History(List<Snapshot> snapshots, List<CollectionAttempt> attempts,
            List<SavedSample> lastGood, CollectionAttempt lastAttempt) {
        public History {
            snapshots = Collections.unmodifiableList(new ArrayList<>(snapshots));
            attempts = Collections.unmodifiableList(new ArrayList<>(attempts));
            lastGood = Collections.unmodifiableList(new ArrayList<>(lastGood));
        }
    }
}
