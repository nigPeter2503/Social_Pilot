package com.socialpilot.app.domain.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource.*;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import java.util.List;
import org.junit.Test;
import static com.socialpilot.app.data.repository.analytics.AnalyticsFixture.*;
import static org.junit.Assert.*;

/** SP-046.03: raw-input gaps beyond the pinned SP-046.02 suite. */
public final class AnalyticsCalculatorRegressionTest {
    private Sample version(Sample s, String api) {
        MetricSource p = s.provenance();
        return new Sample(s.definition(), s.subject(), s.value(), s.missingReason(),
                new MetricSource(p.snapshotId(), p.collectionId(), p.scope(), p.entityId(), p.definitionId(),
                        p.source(), p.sourceRef(), p.observedAt(), p.observedAtBasis(), p.collectedAt(),
                        p.providerPeriodStart(), p.providerPeriodEnd(), api), s.alignmentId());
    }
    @Test public void rateRetainsEveryMissingComponentIncludingDenominator() {
        var r = AnalyticsCalculator.engagementRate(sample(REACTION, "p", END, 12L),
                sample(COMMENT, "p", END, 3L), sample(SHARE, "p", END, null), sample(IMPRESSION, "p", END, null));
        assertNull(r.value());
        assertEquals(CalculationReason.MISSING_COMPONENT, r.reason());
        assertEquals(List.of(new MissingDetail(SHARE.id(), MissingReason.PROVIDER_OMITTED),
                new MissingDetail(IMPRESSION.id(), MissingReason.PROVIDER_OMITTED)), r.missing());
        assertEquals(4, r.provenance().size());
    }
    @Test public void apiVersionMismatchRejectsPairAndComponents() {
        Sample a = sample(REACTION, "p", START, 1L), b = version(sample(REACTION, "p", END, 2L), "mock-v2");
        assertEquals(CalculationReason.INCOMPATIBLE_SOURCE, AnalyticsCalculator.delta(a, b, false).reason());
        assertEquals(CalculationReason.INCOMPATIBLE_SOURCE,
                AnalyticsCalculator.componentCompatibility(sample(COMMENT, "p", END, 2L), b));
    }
    @Test public void unitMismatchWithSameDefinitionIdIsNotCompatible() {
        Definition percent = new Definition(REACTION.id(), Key.REACTION, Kind.CUMULATIVE, Subject.POST, "PERCENT", List.of());
        assertEquals(CalculationReason.INCOMPATIBLE_DEFINITION, AnalyticsCalculator.pairCompatibility(
                sample(REACTION, "p", START, 1L), sample(percent, "p", END, 2L)));
        assertEquals(CalculationReason.INCOMPATIBLE_DEFINITION, AnalyticsCalculator.componentCompatibility(
                sample(COMMENT, "p", END, 1L), sample(percent, "p", END, 2L)));
    }
    @Test public void alignedPeriodComponentsStillRequireValidProviderBounds() {
        Definition r = def("period-r", Key.REACTION, Kind.PERIOD, Subject.POST);
        Definition c = def("period-c", Key.COMMENT, Kind.PERIOD, Subject.POST);
        Definition s = def("period-s", Key.SHARE, Kind.PERIOD, Subject.POST);
        for (Long[] bounds : new Long[][] {{null, null}, {END, START}, {END, END}}) {
            var result = AnalyticsCalculator.engagement(
                    sample(r, "p", END, 12L, SCOPE, EvidenceSource.MO_PHONG, "aligned", bounds[0], bounds[1], ObservedAtBasis.PROVIDER),
                    sample(c, "p", END, 3L, SCOPE, EvidenceSource.MO_PHONG, "aligned", bounds[0], bounds[1], ObservedAtBasis.PROVIDER),
                    sample(s, "p", END, 5L, SCOPE, EvidenceSource.MO_PHONG, "aligned", bounds[0], bounds[1], ObservedAtBasis.PROVIDER));
            assertNull(result.value());
            assertEquals(CalculationReason.INCOMPATIBLE_PERIOD, result.reason());
        }
    }
    @Test public void periodEndMismatchIsRejectedEvenWithMatchingAlignment() {
        Sample a = sample(REACTION, "p", END, 1L, SCOPE, EvidenceSource.MO_PHONG, "w", START, END, ObservedAtBasis.PROVIDER);
        Sample b = sample(COMMENT, "p", END, 1L, SCOPE, EvidenceSource.MO_PHONG, "w", START, END - 1, ObservedAtBasis.PROVIDER);
        assertEquals(CalculationReason.INCOMPATIBLE_PERIOD, AnalyticsCalculator.componentCompatibility(a, b));
    }
    @Test public void exactEngagementCountAboveDoubleIntegerRange() {
        var e = AnalyticsCalculator.engagement(sample(REACTION, "p", END, 9007199254740993L),
                sample(COMMENT, "p", END, 1L), sample(SHARE, "p", END, 1L));
        assertEquals(Long.valueOf(9007199254740995L), e.value());
        assertNull(e.reason());
    }
    @Test public void missingCurrentRetainsItsReasonAndBaselineProvenance() {
        var d = AnalyticsCalculator.delta(sample(FOLLOWER, SCOPE.channelId(), START, 0L),
                sample(FOLLOWER, SCOPE.channelId(), END, null), true);
        assertEquals(CalculationReason.MISSING_CURRENT, d.reason());
        assertEquals(List.of(new MissingDetail(FOLLOWER.id(), MissingReason.PROVIDER_OMITTED)), d.missing());
        assertEquals(2, d.provenance().size());
    }
    @Test public void freshnessArithmeticDoesNotOverflowAcrossLongBounds() {
        assertTrue(AnalyticsCalculator.freshness(Long.MIN_VALUE, Long.MAX_VALUE, 1).isStale());
        assertFalse(AnalyticsCalculator.freshness(Long.MAX_VALUE, Long.MIN_VALUE, 1).isStale());
    }
}
