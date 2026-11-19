package com.socialpilot.app.domain.contract.analytics;

import com.socialpilot.app.domain.contract.social.Platform;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.id;
import static com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.immutable;

/** Exact cache key. UTC epoch milliseconds, plus the IANA zone that defines the local period. */
public record DashboardQuery(
        List<ChannelScope> scopes, String metricDefinitionId,
        long requestedStart, long requestedEnd, String zoneId,
        Cohort cohort, Grouping grouping, int topLimit) {

    public enum Grouping { TOPIC, FORMAT, PUBLISH_HOUR }

    public record ChannelScope(Platform platform, String channelId, long connectionRevision) {
        public ChannelScope {
            Objects.requireNonNull(platform, "platform");
            id(channelId, "channelId");
            if (connectionRevision <= 0) {
                throw new IllegalArgumentException("connectionRevision must be positive");
            }
        }
    }

    /** Fixed cohort of internal PublishedPost IDs. An empty list means no posts. */
    public record Cohort(List<String> cohortPostIds) {
        public Cohort {
            cohortPostIds = immutable(cohortPostIds);
            for (String postId : cohortPostIds) {
                id(postId, "postId");
            }
            if (new HashSet<>(cohortPostIds).size() != cohortPostIds.size()) {
                throw new IllegalArgumentException("Duplicate cohort post ID");
            }
        }
    }

    public DashboardQuery {
        scopes = immutable(scopes);
        HashSet<String> channelIds = new HashSet<>();
        for (ChannelScope scope : scopes) {
            if (!channelIds.add(scope.channelId())) {
                throw new IllegalArgumentException("More than one scope for channel " + scope.channelId());
            }
        }
        id(metricDefinitionId, "metricDefinitionId");
        id(zoneId, "zoneId");
        Objects.requireNonNull(cohort, "cohort");
        Objects.requireNonNull(grouping, "grouping");
        if (requestedStart >= requestedEnd || topLimit <= 0) {
            throw new IllegalArgumentException("Expected start < end and topLimit > 0");
        }
    }
}
