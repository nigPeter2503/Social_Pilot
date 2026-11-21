package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.domain.contract.social.SocialError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.socialpilot.app.data.remote.social.FollowerResult.Source;

/** Adapter-local proposal; rolling organic data cannot be represented as lifetime CUMULATIVE. */
public final class PostResult {
    public enum Status { SUCCESS, PARTIAL, UNAVAILABLE, FAILURE, REJECTED }
    public enum Failure { SCOPE_MISMATCH, CHANNEL_DISCONNECTED, CAPABILITY_BLOCKED,
        PROVIDER_ERROR, INVALID_RESPONSE, NETWORK_ERROR, LOCAL_ERROR }
    public enum MissingReason { PROVIDER_OMITTED, UNVERIFIED, PERMISSION_REQUIRED, UNSUPPORTED,
        AUTH_REQUIRED, RATE_LIMITED, NETWORK_ERROR }
    public enum Metric { REACTION_COUNT, LIKE_COUNT, PROVIDER_LIKE_COUNT, COMMENT_COUNT, SHARE_COUNT,
        IMPRESSION_COUNT, MEDIA_VIEW_COUNT, UNIQUE_MEDIA_VIEWER_COUNT, MEMBERS_REACHED_COUNT, REACH_COUNT }
    public enum MeasurementKind { CUMULATIVE, PERIOD, PROVIDER_ROLLING_12_MONTHS_ORGANIC, UNVERIFIED }
    public enum Aggregation { TOTAL, DISTINCT_TOTAL, UNVERIFIED }
    public enum Unit { COUNT }
    public enum EntityType { POST }
    public enum TimeBasis { COLLECTION }

    public static final class ChannelScope {
        public final Platform platform;
        public final String channelId;
        public final long connectionRevision;
        ChannelScope(PostRequest r) { platform = r.platform; channelId = r.channelId; connectionRevision = r.connectionRevision; }
    }

    public static final class MetricDefinition {
        public final String definitionId;
        public final Metric metricKey;
        public final Unit unit = Unit.COUNT;
        public final EntityType entityType = EntityType.POST;
        public final MeasurementKind measurementKind;
        public final Aggregation aggregation;
        public final String providerMetricName;
        public final String providerFilter;
        public final String measurementWindow;
        public final boolean permitsNegative;
        public final List<String> componentDefinitionIds = Collections.emptyList();

        MetricDefinition(PostRequest r, Metric metric, String field, String filter, String window,
                MeasurementKind kind, Aggregation aggregation, boolean signed) {
            metricKey = metric;
            providerMetricName = field;
            providerFilter = filter;
            measurementWindow = window;
            measurementKind = kind;
            this.aggregation = aggregation;
            permitsNegative = signed;
            definitionId = "post-" + r.account + "-" + r.query + "-" + metric + "-" + r.apiVersion
                    + "-" + kind + (r.period == null ? "" : "-zone-" + r.period.verifiedZoneId) + "-v1";
        }
    }

    public static final class MetricSample {
        public final ChannelScope scope;
        public final EntityType entityType = EntityType.POST;
        public final String entityId;
        public final String externalAccountId;
        public final String definitionId;
        public final Long value;
        public final MissingReason missingReason;
        public final long observedAt;
        public final TimeBasis observedAtBasis = TimeBasis.COLLECTION;
        public final long collectedAt;
        public final Long providerPeriodStart;
        public final Long providerPeriodEnd;
        public final String periodEvidenceRef;
        public final String providerApiVersion;
        public final Source source;
        public final String sourceRef;
        public final String collectionId;

        MetricSample(PostRequest r, MetricDefinition d, Long value, MissingReason reason, long at,
                Source source, String sourceRef, boolean verifiedPeriod) {
            if ((value == null) == (reason == null) || value != null && value < 0 && !d.permitsNegative) {
                throw new IllegalArgumentException("value/reason");
            }
            if (value != null && d.measurementKind == MeasurementKind.PERIOD && !verifiedPeriod) {
                throw new IllegalArgumentException("PERIOD without verified bounds");
            }
            scope = new ChannelScope(r);
            entityId = r.postId;
            externalAccountId = r.externalAccountId;
            definitionId = d.definitionId;
            this.value = value;
            missingReason = reason;
            observedAt = at;
            collectedAt = at;
            providerPeriodStart = verifiedPeriod ? r.period.start : null;
            providerPeriodEnd = verifiedPeriod ? r.period.end : null;
            periodEvidenceRef = verifiedPeriod ? r.period.evidenceRef : null;
            providerApiVersion = r.apiVersion;
            this.source = source;
            this.sourceRef = sourceRef;
            collectionId = r.collectionId;
        }
    }

    public final Status status;
    public final List<MetricDefinition> definitions;
    /** Empty on REJECTED: stale/mismatching results must not enter history. */
    public final List<MetricSample> samples;
    public final Failure failure;
    public final SocialError error;
    public final Long retryAfterAt;

    PostResult(Status status, List<MetricDefinition> definitions, List<MetricSample> samples,
            Failure failure, SocialError error, Long retryAfterAt) {
        this.status = status;
        this.definitions = Collections.unmodifiableList(new ArrayList<>(definitions));
        this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
        this.failure = failure;
        this.error = error;
        this.retryAfterAt = retryAfterAt;
    }
}
