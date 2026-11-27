package com.socialpilot.app.data.local.snapshot;

import com.socialpilot.app.data.remote.social.FollowerResult.Source;
import com.socialpilot.app.domain.contract.social.SocialError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One terminal execution on one subject. Re-delivery uses the same attemptId.
 * A new execution after failure uses a fresh attemptId within the original collection. */
public record CollectionAttempt(String attemptId, Snapshot.Key key, Snapshot.Scope scope, Source source,
        long collectionStartedAt, long completedAt, Status status, List<Outcome> outcomes) {
    public enum Status { SUCCESS, PARTIAL, FAILURE }

    /** Keep per-endpoint failures and missing samples even when a collection has no snapshot. */
    public record Outcome(String operation, String adapterStatus, String failure, SocialError error,
            Long retryAfterAt, List<MetricSample> samples) {
        public Outcome {
            samples = Collections.unmodifiableList(new ArrayList<>(samples));
        }
    }

    public CollectionAttempt {
        outcomes = Collections.unmodifiableList(new ArrayList<>(outcomes));
    }
}
