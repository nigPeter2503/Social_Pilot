package com.socialpilot.app.data.repository.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource.*;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import com.socialpilot.app.domain.contract.analytics.DashboardData;
import com.socialpilot.app.domain.contract.analytics.DashboardData.*;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.*;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static com.socialpilot.app.data.repository.analytics.AnalyticsFixture.*;
import static org.junit.Assert.*;

public final class AnalyticsProjectorRegressionTest {
    private DashboardData project(DashboardQuery q, Batch b) {
        return new AnalyticsProjector(STALE_POLICY).project(q, b, EvidenceSource.MO_PHONG, END + 10);
    }
    @Test public void shuffledOutOfWindowSamplesCannotChangeExactBoundaryDelta() {
        List<Sample> raw = pair("p", 12, 3, 5, 400);
        raw.addAll(counters("p", START - 1, 999, 999, 999, 999));
        raw.addAll(counters("p", END + 1, 999, 999, 999, 999));
        Collections.reverse(raw);
        var d = project(query(RATE.id(), "p"), batch(List.of(post("p", START, null, null)), raw));
        var v = d.topPosts().get(0).selectedMetric();
        assertEquals(5.0, v.value().doubleValue(), 0);
        assertEquals(Long.valueOf(START), v.bounds().actualStart());
        assertEquals(Long.valueOf(END), v.bounds().actualEnd());
        assertEquals(8, v.provenance().size());
        for (MetricSource p : v.provenance()) assertTrue(p.observedAt() == START || p.observedAt() == END);
    }
    @Test public void fixedCohortExcludesUnrequestedPostAndItsProvenance() {
        List<Sample> raw = pair("p", 1, 0, 0, 10);
        raw.addAll(pair("unrequested", 100, 0, 0, 10));
        var d = project(query(ENGAGEMENT.id(), "p"), batch(List.of(post("p", START, null, null),
                post("unrequested", START, null, null)), raw));
        assertEquals(1, d.postMetrics().size());
        assertEquals(Long.valueOf(1), d.weeklyEngagement().get(0).metric().value());
        for (MetricSource p : d.weeklyEngagement().get(0).metric().provenance()) assertEquals("p", p.entityId());
    }
    @Test public void partialSubtotalFreshnessUsesOnlyIncludedInput() {
        List<Sample> raw = pair("good", 12, 3, 5, 400);
        raw.addAll(counters("missing", END, 900, 0, 0, 100));
        var d = project(query(ENGAGEMENT.id(), "good", "missing"), batch(List.of(
                post("good", START, null, null), post("missing", START, null, null)), raw));
        var v = d.weeklyEngagement().get(0).metric();
        assertEquals(Long.valueOf(20), v.value());
        assertEquals(CalculationReason.PARTIAL_COVERAGE, v.reason());
        assertEquals(Long.valueOf(START + 7), v.freshness().lastSuccessAt());
        assertEquals(6, v.provenance().size());
        for (MetricSource p : v.provenance()) assertEquals("good", p.entityId());
        assertEquals("missing", v.coverage().excludedReasons().get(0).postId());
    }
    @Test public void fullCohortAndPartialTemporalCoverageAreIndependent() {
        List<Sample> raw = counters("p", START + 1234, 10, 2, 3, 100);
        raw.addAll(counters("p", END - 5678, 8, 2, 3, 100));
        var d = project(query(ENGAGEMENT.id(), "p"), batch(List.of(post("p", START, null, null)), raw));
        var v = d.weeklyEngagement().get(0).metric();
        assertEquals(Long.valueOf(-2), v.value());
        assertEquals(CoverageLevel.FULL, v.coverage().cohort());
        assertEquals(CoverageLevel.PARTIAL, v.coverage().temporal());
        assertEquals(Long.valueOf(START + 1234), v.bounds().actualStart());
        assertEquals(Long.valueOf(END - 5678), v.bounds().actualEnd());
    }
    @Test public void weightedRateCancellingDenominatorsReturnsReason() {
        List<Sample> raw = pair("a", 10, 0, 0, 100);
        raw.addAll(counters("b", START, 0, 0, 0, 100));
        raw.addAll(counters("b", END, 5, 0, 0, 0));
        var d = project(query(RATE.id(), "a", "b"), batch(List.of(post("a", START, null, null),
                post("b", START, null, null)), raw));
        var v = d.groupedMetrics().get(0).metric();
        assertNull(v.value());
        assertEquals(CalculationReason.ZERO_DENOMINATOR, v.reason());
        assertEquals(2, v.coverage().includedCount());
        assertEquals(16, v.provenance().size());
    }
    @Test public void topLimitAndRanksAreIndependentForEachScope() {
        ChannelScope other = new ChannelScope(Platform.LINKEDIN, "Channel:B", 2);
        List<PostMetadata> posts = List.of(post("a", START, null, null), post("b", START, null, null),
                new PostMetadata(other, "c", null, START, null, null));
        List<Sample> raw = pair("a", 20, 0, 0, 100);
        raw.addAll(pair("b", 10, 0, 0, 100));
        for (Sample s : pair("c", 30, 0, 0, 100)) raw.add(sample(s.definition(), "c", s.provenance().observedAt(),
                s.value(), other, EvidenceSource.MO_PHONG, s.alignmentId(), null, null, ObservedAtBasis.COLLECTION));
        var q = new DashboardQuery(List.of(SCOPE, other), ENGAGEMENT.id(), START, END, "UTC",
                new Cohort(List.of("a", "b", "c")), Grouping.TOPIC, 1);
        var d = project(q, batch(posts, raw));
        assertEquals(2, d.topPosts().size());
        assertEquals("a", d.topPosts().get(0).post().postId());
        assertEquals("c", d.topPosts().get(1).post().postId());
        for (TopPost p : d.topPosts()) assertEquals(1, p.rank());
        assertEquals(3, d.coverage().includedCount());
    }
    @Test public void duplicateOrAmbiguousLocalIdentityNeverSubstitutesScope() {
        ChannelScope other = new ChannelScope(Platform.LINKEDIN, "other", 1);
        var q = new DashboardQuery(List.of(SCOPE, other), ENGAGEMENT.id(), START, END, "UTC",
                new Cohort(List.of("same")), Grouping.TOPIC, 2);
        var d = project(q, batch(List.of(post("same", START, null, null),
                new PostMetadata(other, "same", null, START, null, null)), pair("same", 1, 0, 0, 10)));
        assertTrue(d.topPosts().isEmpty());
        assertEquals(0, d.coverage().includedCount());
        assertEquals(CalculationReason.INCOMPATIBLE_SCOPE, d.coverage().excludedReasons().get(0).reason());
        assertThrows(IllegalArgumentException.class, () -> project(query(ENGAGEMENT.id(), "same"),
                batch(List.of(post("same", START, null, null), post("same", START, null, null)), List.of())));
    }
    @Test public void mixedSampleNamespaceIsRejectedInsideProductionBatch() {
        Batch mock = followers(List.of(sample(FOLLOWER, SCOPE.channelId(), END, 100L)));
        Batch disguised = new Batch(true, EvidenceSource.API_THAT, mock.definitions(), mock.channels(),
                mock.posts(), mock.samples(), mock.attempt());
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsProjector(STALE_POLICY)
                .project(query(FOLLOWER.id()), disguised, EvidenceSource.API_THAT, END));
    }
}
