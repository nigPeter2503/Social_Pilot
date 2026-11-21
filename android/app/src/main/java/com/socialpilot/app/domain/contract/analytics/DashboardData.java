package com.socialpilot.app.domain.contract.analytics;

import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.CalculatedValue;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.Coverage;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.DashboardError;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.EvidenceSource;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.ExcludedPost;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.MetricReading;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.SyncState;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.ChannelScope;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.Grouping;
import java.util.List;

import static com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.immutable;

/** Renderable snapshot for one exact query and evidence namespace. */
public record DashboardData(
        DashboardQuery query, EvidenceSource source, ContentState contentState,
        List<ChannelCard> channelCards, List<PostMetrics> postMetrics, List<GrowthPoint> growthPoints,
        List<WeeklyEngagement> weeklyEngagement, List<TopPost> topPosts, List<ExcludedPost> topPostExclusions,
        List<GroupedMetric> groupedMetrics, List<GoalView> goals,
        Coverage coverage, Long lastSuccessAt, Long lastAttemptAt,
        SyncState syncState, boolean offline, List<DashboardError> errors) {

    public enum ContentState { UNAVAILABLE, EMPTY, CONTENT }
    public enum GoalKind { FOLLOWER_LEVEL, ENGAGEMENT_INCREASE }
    public enum GoalCompletion { UNKNOWN, IN_PROGRESS, REACHED, NOT_REACHED }

    public DashboardData {
        channelCards = immutable(channelCards);
        postMetrics = immutable(postMetrics);
        growthPoints = immutable(growthPoints);
        weeklyEngagement = immutable(weeklyEngagement);
        topPosts = immutable(topPosts);
        topPostExclusions = immutable(topPostExclusions);
        groupedMetrics = immutable(groupedMetrics);
        goals = immutable(goals);
        errors = immutable(errors);
    }

    public record ChannelCard(ChannelScope scope, String displayName, MetricReading follower,
                              SyncState syncState, List<DashboardError> errors) {
        public ChannelCard {
            errors = immutable(errors);
        }
    }

    public record PostMetadata(ChannelScope scope, String postId, String title, Long publishedAt,
                               String primaryTopicId, String format) {}

    public record PostMetrics(PostMetadata post, List<MetricReading> readings, List<CalculatedValue> calculated) {
        public PostMetrics {
            readings = immutable(readings);
            calculated = immutable(calculated);
        }
    }

    /** At a gap, observedAt is the expected grid time and the reading has no snapshot. */
    public record GrowthPoint(ChannelScope scope, long observedAt, boolean isGap, MetricReading reading) {}

    public record WeeklyEngagement(ChannelScope scope, CalculatedValue metric) {}

    public record TopPost(PostMetadata post, CalculatedValue selectedMetric, int rank) {}

    public record GroupedMetric(ChannelScope scope, Grouping groupKind, String groupKey,
                                String label, CalculatedValue metric, int postCount, int excludedCount) {}

    /** Read-side shape for a later goal UI. Persistence belongs to SP-048. */
    public record GoalView(String goalId, long revision, ChannelScope scope, GoalKind kind,
                           String metricDefinitionId, long baselineValue, List<String> baselineSnapshotIds,
                           long targetValue, long periodStart, long periodEnd, String zoneId,
                           List<String> cohortPostIds, long createdAt, GoalProgress progress) {
        public GoalView {
            baselineSnapshotIds = immutable(baselineSnapshotIds);
            cohortPostIds = immutable(cohortPostIds);
        }
    }

    public record GoalProgress(CalculatedValue actual, CalculatedValue rawProgress,
                               long calculatedAt, GoalCompletion completionStatus) {}
}
