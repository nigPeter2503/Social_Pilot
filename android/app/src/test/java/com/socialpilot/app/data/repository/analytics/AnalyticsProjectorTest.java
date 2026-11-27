package com.socialpilot.app.data.repository.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource.*;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import com.socialpilot.app.domain.contract.analytics.DashboardData;
import com.socialpilot.app.domain.contract.analytics.DashboardData.*;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.*;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static com.socialpilot.app.data.repository.analytics.AnalyticsFixture.*;
import static org.junit.Assert.*;

public final class AnalyticsProjectorTest {
    private final AnalyticsProjector projector = new AnalyticsProjector(STALE_POLICY);
    private DashboardData project(DashboardQuery q, Batch batch) {
        return projector.project(q, batch, EvidenceSource.MO_PHONG, END + 10);
    }
    private Batch one(List<Sample> samples) { return batch(List.of(post("local:00A", START, null, null)), samples); }
    @Test public void computesWeeklyEngagementRateAndProvenanceFromRawCounters() {
        DashboardData d = project(query(RATE.id(), "local:00A"), one(pair("local:00A", 12, 3, 5, 400)));
        assertEquals(20L, d.weeklyEngagement().get(0).metric().value());
        assertEquals(5.0, d.topPosts().get(0).selectedMetric().value().doubleValue(), 0);
        assertEquals(8, d.topPosts().get(0).selectedMetric().sourceSnapshotIds().size());
        assertEquals("snapshot:local:00A:reaction-v1:" + START,
                d.topPosts().get(0).selectedMetric().sourceSnapshotIds().get(0));
        assertEquals(CoverageLevel.FULL, d.coverage().temporal());
        assertEquals(CoverageLevel.FULL, d.coverage().cohort());
        assertEquals(Long.valueOf(END), d.topPosts().get(0).selectedMetric().bounds().actualEnd());
    }
    @Test public void partialCohortKeepsSubtotalAndIndependentTemporalCoverage() {
        List<Sample> samples = pair("a", 12, 3, 5, 400);
        samples.addAll(counters("new", END, 9, 2, 1, 100));
        Batch batch = batch(List.of(post("a", START, "t", "text"), post("new", START + 1, "t", "text")), samples);
        DashboardData d = project(query(ENGAGEMENT.id(), "a", "new"), batch);
        CalculatedValue subtotal = d.weeklyEngagement().get(0).metric();
        assertEquals(Long.valueOf(20), subtotal.value());
        assertEquals(CalculationReason.PARTIAL_COVERAGE, subtotal.reason());
        assertEquals(CoverageLevel.FULL, subtotal.coverage().temporal());
        assertEquals(CoverageLevel.PARTIAL, subtotal.coverage().cohort());
        assertEquals(1, subtotal.coverage().includedCount());
        assertEquals(2, subtotal.coverage().totalCount());
        assertEquals(CalculationReason.MISSING_BASELINE, subtotal.coverage().excludedReasons().get(0).reason());
        assertEquals(6, subtotal.sourceSnapshotIds().size());
    }
    @Test public void partialTemporalBoundsAreNotRoundedToMidnight() {
        long first = START + 15 * 60000, last = END - 20 * 60000;
        List<Sample> raw = counters("local:00A", first, 0, 0, 0, 0);
        raw.addAll(counters("local:00A", last, 12, 3, 5, 400));
        DashboardData d = project(query(ENGAGEMENT.id(), "local:00A"), one(raw));
        CalculatedValue value = d.weeklyEngagement().get(0).metric();
        assertEquals(Long.valueOf(first), value.bounds().actualStart());
        assertEquals(Long.valueOf(last), value.bounds().actualEnd());
        assertEquals(CoverageLevel.PARTIAL, value.coverage().temporal());
        assertEquals(CoverageLevel.FULL, value.coverage().cohort());
    }
    @Test public void mismatchedPostPairsCannotBeSummedOrRankedTogether() {
        List<Sample> raw = pair("a", 12, 3, 5, 400);
        raw.addAll(counters("b", START + 1, 0, 0, 0, 0));
        raw.addAll(counters("b", END, 1, 0, 0, 10));
        DashboardData d = project(query(ENGAGEMENT.id(), "a", "b"),
                batch(List.of(post("a", START, "t", null), post("b", START, "t", null)), raw));
        assertTrue(d.topPosts().isEmpty());
        assertEquals(2, d.topPostExclusions().size());
        assertNull(d.weeklyEngagement().get(0).metric().value());
        assertEquals(2, d.weeklyEngagement().get(0).metric().coverage().excludedReasons().size());
    }
    @Test public void groupingUsesWeightedRateAndUnknownMetadata() {
        List<Sample> raw = pair("a", 12, 3, 5, 400);
        raw.addAll(pair("b", 10, 0, 0, 100));
        DashboardData d = project(query(RATE.id(), "a", "b"),
                batch(List.of(post("a", START, null, null), post("b", null, null, null)), raw));
        GroupedMetric group = d.groupedMetrics().get(0);
        assertEquals("UNKNOWN", group.groupKey());
        assertEquals(6.0, group.metric().value().doubleValue(), 0);
        assertEquals(2, group.postCount());
        assertEquals(0, group.excludedCount());
    }
    @Test public void topSortsExactLargeLongTiesNullsAndNegativeDeltas() {
        String[] ids = {"z", "a", "null-time", "negative", "missing", "large-a", "large-b"};
        List<PostMetadata> posts = List.of(post("z", START + 1, null, null), post("a", START + 1, null, null),
                post("null-time", null, null, null), post("negative", START + 2, null, null), post("missing", START, null, null),
                post("large-a", START, null, null), post("large-b", START, null, null));
        List<Sample> raw = new ArrayList<>();
        for (String id : ids) {
            if (id.equals("missing")) continue;
            raw.add(sample(REACTION, id, START, id.equals("negative") ? 10L : 0L));
            raw.add(sample(REACTION, id, END, id.equals("negative") ? 8L
                    : id.equals("large-a") ? 9007199254740992L : id.equals("large-b") ? 9007199254740993L : 20L));
        }
        DashboardData d = project(query(REACTION.id(), ids), batch(posts, raw));
        List<String> actual = new ArrayList<>();
        for (TopPost top : d.topPosts()) actual.add(top.post().postId());
        assertEquals(List.of("large-b", "large-a", "a", "z", "null-time", "negative"), actual);
        assertEquals(Long.valueOf(-2), d.topPosts().get(5).selectedMetric().value());
        assertEquals("missing", d.topPostExclusions().get(0).postId());
    }
    @Test public void sourceScopeRevisionSubjectAndDefinitionFiltersAreExact() {
        List<Sample> raw = pair("local:00A", 12, 3, 5, 400);
        raw.add(sample(REACTION, "local:00A", END, 999L,
                new ChannelScope(Platform.FACEBOOK, SCOPE.channelId(), 2), EvidenceSource.MO_PHONG,
                "wrong", null, null, ObservedAtBasis.COLLECTION));
        raw.add(sample(REACTION, "local:00A", END, 999L,
                new ChannelScope(Platform.LINKEDIN, SCOPE.channelId(), 1), EvidenceSource.MO_PHONG,
                "wrong", null, null, ObservedAtBasis.COLLECTION));
        raw.add(sample(FOLLOWER, "local:00A", END, 999L));
        raw.add(sample(REACTION, "native:00A", END, 999L));
        assertEquals(Long.valueOf(20), project(query(ENGAGEMENT.id(), "local:00A"), one(raw))
                .weeklyEngagement().get(0).metric().value());
        DashboardData wrong = project(query(ENGAGEMENT.id(), "LOCAL:00A"), one(raw));
        assertTrue(wrong.postMetrics().isEmpty());
        assertEquals(0, wrong.coverage().includedCount());
        assertEquals(1, wrong.coverage().excludedReasons().size());
    }
    @Test public void incompatibleRegistryAndNamespaceFailRatherThanBecomeEmpty() {
        Sample simulated = sample(FOLLOWER, SCOPE.channelId(), END, 100L);
        Batch batch = followers(List.of(simulated));
        assertThrows(IllegalArgumentException.class,
                () -> projector.project(query(FOLLOWER.id()), batch, EvidenceSource.API_THAT, END));
        Definition changed = def(REACTION.id(), Key.REACTION, Kind.PERIOD, Subject.POST);
        assertThrows(IllegalArgumentException.class,
                () -> project(query(REACTION.id(), "local:00A"), one(List.of(sample(changed, "local:00A", END, 5L)))));
    }
    @Test public void chartGapsHaveNoSyntheticSnapshotAndEndSampleIsReal() {
        long observed = START + 15 * 60000;
        DashboardData d = project(query(FOLLOWER.id()), followers(List.of(
                sample(FOLLOWER, SCOPE.channelId(), observed, 100L), sample(FOLLOWER, SCOPE.channelId(), END, 95L))));
        assertEquals(8, d.growthPoints().size());
        assertEquals(observed, d.growthPoints().get(0).observedAt());
        assertFalse(d.growthPoints().get(0).isGap());
        assertEquals(END, d.growthPoints().get(7).observedAt());
        GrowthPoint gap = d.growthPoints().get(1);
        assertTrue(gap.isGap());
        assertNull(gap.reading().value());
        assertEquals(MissingReason.NOT_SYNCED, gap.reading().missingReason());
        assertTrue(gap.reading().sourceSnapshotIds().isEmpty());
        assertNull(gap.reading().collectedAt());
    }
    @Test public void hourAndFormatGroupingUseQueryZoneAndUnknown() {
        List<Sample> raw = pair("a", 12, 3, 5, 400);
        raw.addAll(pair("b", 1, 0, 0, 10));
        Batch input = batch(List.of(post("a", START, "topic", "image"), post("b", null, null, null)), raw);
        DashboardQuery q = query(ENGAGEMENT.id(), "a", "b");
        q = new DashboardQuery(q.scopes(), q.metricDefinitionId(), START, END, "Asia/Ho_Chi_Minh", q.cohort(), Grouping.PUBLISH_HOUR, 10);
        assertEquals(List.of("0", "UNKNOWN"), keys(project(q, input)));
        q = new DashboardQuery(q.scopes(), q.metricDefinitionId(), START, END, "UTC", q.cohort(), Grouping.PUBLISH_HOUR, 10);
        assertEquals(List.of("17", "UNKNOWN"), keys(project(q, input)));
        q = new DashboardQuery(q.scopes(), q.metricDefinitionId(), START, END, "UTC", q.cohort(), Grouping.FORMAT, 10);
        assertEquals(List.of("UNKNOWN", "image"), keys(project(q, input)));
    }
    private List<String> keys(DashboardData d) {
        List<String> keys = new ArrayList<>();
        for (GroupedMetric group : d.groupedMetrics()) keys.add(group.groupKey());
        return keys;
    }
    @Test public void aggregateOverflowReturnsNullInsteadOfWrappedCount() {
        List<Sample> raw = pair("a", Long.MAX_VALUE, 0, 0, 1);
        raw.addAll(pair("b", 1, 0, 0, 1));
        DashboardData d = project(query(ENGAGEMENT.id(), "a", "b"),
                batch(List.of(post("a", START, "t", null), post("b", START, "t", null)), raw));
        assertNull(d.weeklyEngagement().get(0).metric().value());
        assertEquals(CalculationReason.INVALID_TARGET, d.weeklyEngagement().get(0).metric().reason());
    }
    @Test public void periodSamplesAreNotSubtractedByQueryProjection() {
        Definition period = def("period", Key.REACTION, Kind.PERIOD, Subject.POST);
        List<Definition> defs = new ArrayList<>(DEFINITIONS);
        defs.add(period);
        Batch b = new Batch(true, EvidenceSource.MO_PHONG, defs, List.of(), List.of(post("p", START, null, null)),
                List.of(sample(period, "p", START, 100L, SCOPE, EvidenceSource.MO_PHONG, "a", START - 1, START, ObservedAtBasis.PROVIDER),
                        sample(period, "p", END, 95L, SCOPE, EvidenceSource.MO_PHONG, "b", START, END, ObservedAtBasis.PROVIDER)),
                new Attempt(null, null, SyncState.IDLE, false, List.of()));
        DashboardData d = project(query(period.id(), "p"), b);
        assertTrue(d.topPosts().isEmpty());
        assertEquals(CalculationReason.INCOMPATIBLE_PERIOD, d.topPostExclusions().get(0).reason());
    }
    @Test public void emptyAndUnavailableAreDistinctSuccessfulReadStates() {
        Batch empty = batch(List.of(), List.of());
        assertEquals(ContentState.EMPTY, project(query(ENGAGEMENT.id()), empty).contentState());
        Batch unavailable = new Batch(false, empty.source(), empty.definitions(), empty.channels(), empty.posts(), empty.samples(), empty.attempt());
        assertEquals(ContentState.UNAVAILABLE, project(query(ENGAGEMENT.id()), unavailable).contentState());
    }
    @Test public void pinnedResourceFlowsThroughRealProjectionAndWeightedCalculation() throws Exception {
        List<PostMetadata> posts = new ArrayList<>();
        List<Sample> raw = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/analytics/mo-phong.json"), StandardCharsets.UTF_8)) {
            var fixture = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals("MO_PHONG", fixture.get("source").getAsString());
            assertEquals("sp-046-input-v1", fixture.get("revision").getAsString());
            for (var element : fixture.getAsJsonArray("posts")) {
                var p = element.getAsJsonObject();
                String id = p.get("localPostId").getAsString();
                ids.add(id);
                posts.add(post(id, START, null, null));
                var before = p.getAsJsonArray("before");
                var after = p.getAsJsonArray("after");
                raw.addAll(counters(id, START, before.get(0).getAsLong(), before.get(1).getAsLong(), before.get(2).getAsLong(), before.get(3).getAsLong()));
                raw.addAll(counters(id, END, after.get(0).getAsLong(), after.get(1).getAsLong(), after.get(2).getAsLong(), after.get(3).getAsLong()));
            }
        }
        SimulatedAnalyticsSource source = new SimulatedAnalyticsSource();
        source.cached = batch(posts, raw);
        source.assertSimulated();
        DashboardData actual = project(query(RATE.id(), ids.toArray(new String[0])), source.readCached(query(RATE.id())));
        assertEquals(30L, actual.weeklyEngagement().get(0).metric().value());
        assertEquals(6.0, actual.groupedMetrics().get(0).metric().value().doubleValue(), 0);
        assertEquals("urn:LOCAL:00B", actual.topPosts().get(0).post().postId());
        assertEquals(EvidenceSource.MO_PHONG, actual.source());
    }
    @Test public void newestFailedSampleKeepsPriorGoodFollowerAndNoSamplesAfterEnd() {
        List<Sample> raw = List.of(sample(FOLLOWER, SCOPE.channelId(), START, 100L),
                sample(FOLLOWER, SCOPE.channelId(), END - 1, null), sample(FOLLOWER, SCOPE.channelId(), END + 1, 999L));
        DashboardData d = project(query(FOLLOWER.id()), followers(raw));
        assertEquals(Long.valueOf(100), d.channelCards().get(0).follower().value());
        assertEquals(Long.valueOf(START + 7), d.channelCards().get(0).follower().collectedAt());
        for (GrowthPoint point : d.growthPoints()) assertTrue(point.observedAt() <= END);
    }
    @Test public void componentNeverCollectedKeepsDefinitionAndNotSyncedReason() {
        List<Sample> raw = pair("local:00A", 12, 3, 5, 400);
        raw.removeIf(s -> s.definition().equals(SHARE));
        DashboardData d = project(query(ENGAGEMENT.id(), "local:00A"), one(raw));
        ExcludedPost excluded = d.topPostExclusions().get(0);
        assertEquals(CalculationReason.MISSING_BASELINE, excluded.reason());
        assertTrue(excluded.missingDetails().contains(new MissingDetail(SHARE.id(), MissingReason.NOT_SYNCED)));
    }
}
