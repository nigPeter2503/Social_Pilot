package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.domain.contract.social.SocialError;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.Collections;
import java.util.List;

/** Normalized SP-020 shapes, kept local until shared Java signatures are agreed. */
public final class FollowerResult {
    public enum Source { API_THAT, FIXTURE_TU_API_DA_CHE, MO_PHONG, PROTOTYPE }
    public enum MissingReason {
        PROVIDER_OMITTED, AUTH_REQUIRED, PERMISSION_REQUIRED, RATE_LIMITED,
        NETWORK_ERROR, UNVERIFIED, UNSUPPORTED
    }
    public enum Status { SUCCESS, FAILURE, REJECTED }
    public enum Failure { SCOPE_MISMATCH, CHANNEL_DISCONNECTED, CAPABILITY_BLOCKED,
        PROVIDER_ERROR, INVALID_RESPONSE, NETWORK_ERROR, LOCAL_ERROR }
    public enum EntityType { CHANNEL }
    public enum MetricKey { FOLLOWER_COUNT }
    public enum Unit { COUNT }
    public enum MeasurementKind { GAUGE }
    public enum Aggregation { LATEST }
    public enum TimeBasis { COLLECTION }

    public static final class ChannelScope {
        public final Platform platform;
        public final String channelId;
        public final long connectionRevision;

        ChannelScope(FollowerRequest request) {
            platform = request.platform;
            channelId = request.channelId;
            connectionRevision = request.connectionRevision;
        }
    }

    public static final class MetricDefinition {
        public final String definitionId;
        public final MetricKey metricKey = MetricKey.FOLLOWER_COUNT;
        public final Unit unit = Unit.COUNT;
        public final EntityType entityType = EntityType.CHANNEL;
        public final MeasurementKind measurementKind = MeasurementKind.GAUGE;
        public final Aggregation aggregation = Aggregation.LATEST;
        public final String providerMetricName;
        public final List<String> componentDefinitionIds = Collections.emptyList();

        MetricDefinition(FollowerRequest request) {
            String name;
            switch (request.account) {
                case FACEBOOK_PAGE: name = "followers_count"; break;
                case LINKEDIN_MEMBER: name = "memberFollowersCount"; break;
                default: name = "networkSizes.firstDegreeSize:COMPANY_FOLLOWED_BY_MEMBER";
            }
            providerMetricName = name;
            // Versions stay separate until equivalence has been verified.
            definitionId = "follower-" + request.account.name() + "-" + request.apiVersion + "-v1";
        }
    }

    public static final class MetricSample {
        public final ChannelScope scope;
        public final EntityType entityType = EntityType.CHANNEL;
        public final String entityId;
        public final String definitionId;
        public final Long value;
        public final MissingReason missingReason;
        public final long observedAt;
        public final TimeBasis observedAtBasis = TimeBasis.COLLECTION;
        public final long collectedAt;
        public final Long providerPeriodStart = null;
        public final Long providerPeriodEnd = null;
        public final String providerApiVersion;
        public final Source source;
        public final String sourceRef;
        public final String collectionId;

        MetricSample(FollowerRequest request, MetricDefinition definition, Long value,
                MissingReason reason, long receivedAt, Source source, String sourceRef) {
            if ((value == null) == (reason == null) || value != null && value < 0) {
                throw new IllegalArgumentException("value/missingReason");
            }
            this.scope = new ChannelScope(request);
            this.entityId = request.channelId;
            this.definitionId = definition.definitionId;
            this.value = value;
            this.missingReason = reason;
            this.observedAt = receivedAt;
            this.collectedAt = receivedAt;
            this.providerApiVersion = request.apiVersion;
            this.source = source;
            this.sourceRef = sourceRef;
            this.collectionId = request.collectionId;
        }
    }

    public final Status status;
    public final MetricDefinition definition;
    /** Null for rejected scopes; these results must never enter history. */
    public final MetricSample sample;
    public final Failure failure;
    /** Null when Social v1 has no evidence-backed error classification. */
    public final SocialError error;
    public final Long retryAfterAt;

    FollowerResult(Status status, MetricDefinition definition, MetricSample sample,
            Failure failure, SocialError error, Long retryAfterAt) {
        this.status = status;
        this.definition = definition;
        this.sample = sample;
        this.failure = failure;
        this.error = error;
        this.retryAfterAt = retryAfterAt;
    }
}
