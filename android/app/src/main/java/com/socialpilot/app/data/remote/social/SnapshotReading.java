package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.data.local.snapshot.CollectionAttempt.Outcome;
import com.socialpilot.app.data.local.snapshot.MetricDefinition;
import com.socialpilot.app.data.local.snapshot.MetricSample;
import com.socialpilot.app.data.local.snapshot.Snapshot;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.domain.contract.social.SocialError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Pair each callback with its originating request before passing a complete subject batch to storage. */
public final class SnapshotReading {
    public final Snapshot.Scope scope;
    public final Snapshot.Subject type;
    public final String nativeSubjectId;
    public final String collectionId;
    public final Outcome outcome;

    private SnapshotReading(Snapshot.Scope scope, Snapshot.Subject type, String nativeSubjectId,
            String collectionId, Outcome outcome) {
        this.scope = scope;
        this.type = type;
        this.nativeSubjectId = nativeSubjectId;
        this.collectionId = collectionId;
        this.outcome = outcome;
    }

    /** Transport/admission failed before any usable sample. The caller retains the original target. */
    public static SnapshotReading failure(Snapshot.Scope scope, Snapshot.Subject type, String subjectId,
            String collectionId, String operation, String failure, SocialError error) {
        return new SnapshotReading(scope, type, subjectId, collectionId,
                new Outcome(operation, "FAILURE", failure, error, null, Collections.emptyList()));
    }

    public static SnapshotReading from(FollowerRequest r, FollowerResult result) {
        if (result.status == FollowerResult.Status.REJECTED) throw new IllegalArgumentException("rejected callback");
        FollowerResult.MetricSample s = result.sample;
        if (!r.channelId.equals(s.entityId) || !r.collectionId.equals(s.collectionId) || !r.apiVersion.equals(s.providerApiVersion)
                || !matches(s.scope.platform, s.scope.channelId, s.scope.connectionRevision, r.platform, r.channelId, r.connectionRevision)
                || !new FollowerResult.MetricDefinition(r).definitionId.equals(s.definitionId)
                || !s.definitionId.equals(result.definition.definitionId)) throw new IllegalArgumentException("definition");
        MetricSample sample = new MetricSample(MetricDefinition.from(result.definition), s.value, name(s.missingReason),
                s.observedAt, s.observedAtBasis.name(), s.collectedAt, s.providerPeriodStart, s.providerPeriodEnd,
                null, null, null, s.providerApiVersion, s.source, s.sourceRef);
        Snapshot.Scope scope = new Snapshot.Scope(r.platform, r.account, r.channelId, r.externalAccountId, r.connectionRevision);
        return new SnapshotReading(scope, Snapshot.Subject.CHANNEL, s.entityId, s.collectionId,
                new Outcome("FOLLOWER_COUNT", result.status.name(), name(result.failure), result.error,
                        result.retryAfterAt, Collections.singletonList(sample)));
    }

    public static SnapshotReading from(PostRequest r, PostResult result) {
        if (result.status == PostResult.Status.REJECTED) throw new IllegalArgumentException("rejected callback");
        List<PostResult.MetricDefinition> expected = PostMetricMapping.definitions(r);
        if (expected.size() != result.samples.size() || expected.size() != result.definitions.size()) {
            throw new IllegalArgumentException("incomplete adapter result");
        }
        List<MetricSample> samples = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            PostResult.MetricSample s = result.samples.get(i);
            PostResult.MetricDefinition d = result.definitions.get(i);
            if (!r.postId.equals(s.entityId) || !r.collectionId.equals(s.collectionId) || !r.apiVersion.equals(s.providerApiVersion)
                    || !matches(s.scope.platform, s.scope.channelId, s.scope.connectionRevision, r.platform, r.channelId, r.connectionRevision)
                    || !s.externalAccountId.equals(r.externalAccountId) || !s.definitionId.equals(d.definitionId)
                    || !expected.get(i).definitionId.equals(s.definitionId)) throw new IllegalArgumentException("post binding");
            if (s.providerPeriodStart != null && (r.period == null || s.providerPeriodStart != r.period.start
                    || s.providerPeriodEnd != r.period.end || !Objects.equals(s.periodEvidenceRef, r.period.evidenceRef))) {
                throw new IllegalArgumentException("period binding");
            }
            samples.add(new MetricSample(MetricDefinition.from(d), s.value, name(s.missingReason), s.observedAt,
                    s.observedAtBasis.name(), s.collectedAt, s.providerPeriodStart, s.providerPeriodEnd,
                    s.periodEvidenceRef, r.period == null ? null : r.period.start,
                    r.period == null ? null : r.period.end, s.providerApiVersion, s.source, s.sourceRef));
        }
        Snapshot.Scope scope = new Snapshot.Scope(r.platform, r.account, r.channelId, r.externalAccountId, r.connectionRevision);
        return new SnapshotReading(scope, Snapshot.Subject.POST, r.postId, r.collectionId,
                new Outcome(r.query.name(), result.status.name(), name(result.failure), result.error, result.retryAfterAt, samples));
    }

    private static boolean matches(Platform platform, String channel, long revision,
            Platform expectedPlatform, String expectedChannel, long expectedRevision) {
        return platform == expectedPlatform && channel.equals(expectedChannel) && revision == expectedRevision;
    }

    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
}
