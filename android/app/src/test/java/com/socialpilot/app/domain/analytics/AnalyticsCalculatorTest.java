package com.socialpilot.app.domain.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource.*;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.ChannelScope;
import com.socialpilot.app.domain.contract.social.Platform;
import java.time.Instant;
import org.junit.Test;
import static com.socialpilot.app.data.repository.analytics.AnalyticsFixture.*;
import static org.junit.Assert.*;

public final class AnalyticsCalculatorTest {
    private Sample r(long n) { return sample(REACTION, "Local:00A", END, n); }
    private Sample c(long n) { return sample(COMMENT, "Local:00A", END, n); }
    private Sample s(Long n) { return sample(SHARE, "Local:00A", END, n); }
    private Sample i(long n) { return sample(IMPRESSION, "Local:00A", END, n); }
    @Test public void completeCountsAndRateAreCalculatedFromInputs() {
        assertEquals(Long.valueOf(20), AnalyticsCalculator.engagement(r(12), c(3), s(5L)).value());
        assertEquals(5.0, AnalyticsCalculator.engagementRate(r(12), c(3), s(5L), i(400)).value().doubleValue(), 0);
    }
    @Test public void zeroIsAValueAndZeroDenominatorHasReason() {
        assertEquals(0.0, AnalyticsCalculator.engagementRate(r(0), c(0), s(0L), i(400)).value().doubleValue(), 0);
        assertEquals(CalculationReason.ZERO_DENOMINATOR,
                AnalyticsCalculator.engagementRate(r(12), c(3), s(5L), i(0)).reason());
    }
    @Test public void missingShareRetainsOriginalReasonAndInputSources() {
        var actual = AnalyticsCalculator.engagementRate(r(12), c(3), s(null), i(400));
        assertNull(actual.value());
        assertEquals(CalculationReason.MISSING_COMPONENT, actual.reason());
        assertEquals(new MissingDetail(SHARE.id(), MissingReason.PROVIDER_OMITTED), actual.missing().get(0));
        assertEquals(4, actual.provenance().size());
    }
    @Test public void negativeDeltaAndGrowthArePreserved() {
        Sample before = sample(FOLLOWER, SCOPE.channelId(), START, 100L), after = sample(FOLLOWER, SCOPE.channelId(), END, 95L);
        assertEquals(Long.valueOf(-5), AnalyticsCalculator.delta(before, after, false).value());
        assertEquals(-5.0, AnalyticsCalculator.delta(before, after, true).value().doubleValue(), 0);
    }
    @Test public void baselineZeroAndMissingEndpointsAreNotFilled() {
        Sample zero = sample(FOLLOWER, SCOPE.channelId(), START, 0L), current = sample(FOLLOWER, SCOPE.channelId(), END, 95L);
        assertEquals(CalculationReason.ZERO_DENOMINATOR, AnalyticsCalculator.delta(zero, current, true).reason());
        assertEquals(CalculationReason.MISSING_BASELINE, AnalyticsCalculator.delta(null, current, false).reason());
        assertEquals(CalculationReason.MISSING_CURRENT, AnalyticsCalculator.delta(zero, null, false).reason());
    }
    @Test public void equalAndReverseTimesAreRejected() {
        Sample before = sample(FOLLOWER, SCOPE.channelId(), START, 100L), after = sample(FOLLOWER, SCOPE.channelId(), END, 95L);
        assertEquals(CalculationReason.INVALID_TIME_ORDER, AnalyticsCalculator.delta(before, before, false).reason());
        assertEquals(CalculationReason.INVALID_TIME_ORDER, AnalyticsCalculator.delta(after, before, false).reason());
    }
    @Test public void detectsCountSumAndDeltaOverflow() {
        var sum = AnalyticsCalculator.engagement(r(Long.MAX_VALUE), c(1), s(0L));
        assertNull(sum.value());
        assertEquals(AnalyticsCalculator.Diagnostic.COUNT_OVERFLOW, sum.diagnostic());
        var delta = AnalyticsCalculator.delta(sample(REACTION, "p", START, -1L), sample(REACTION, "p", END, Long.MAX_VALUE), false);
        assertNull(delta.value());
        assertEquals(AnalyticsCalculator.Diagnostic.COUNT_OVERFLOW, delta.diagnostic());
    }
    @Test public void scopePlatformChannelRevisionAndEntityAreExact() {
        Sample before = sample(FOLLOWER, SCOPE.channelId(), START, 100L);
        ChannelScope[] scopes = {new ChannelScope(Platform.LINKEDIN, SCOPE.channelId(), 1),
                new ChannelScope(Platform.FACEBOOK, "channel:00a", 1), new ChannelScope(Platform.FACEBOOK, SCOPE.channelId(), 2)};
        for (ChannelScope scope : scopes) {
            Sample after = sample(FOLLOWER, SCOPE.channelId(), END, 95L, scope, EvidenceSource.MO_PHONG,
                    "window", null, null, ObservedAtBasis.COLLECTION);
            assertEquals(CalculationReason.INCOMPATIBLE_SCOPE, AnalyticsCalculator.delta(before, after, false).reason());
        }
        assertEquals(CalculationReason.INCOMPATIBLE_SCOPE,
                AnalyticsCalculator.delta(before, sample(FOLLOWER, "native-provider-id", END, 95L), false).reason());
    }
    @Test public void differentDefinitionAndKindAreRejected() {
        Sample before = sample(FOLLOWER, SCOPE.channelId(), START, 100L);
        Definition other = def("follower-v2", Key.FOLLOWER, Kind.GAUGE, Subject.CHANNEL);
        assertEquals(CalculationReason.INCOMPATIBLE_DEFINITION,
                AnalyticsCalculator.delta(before, sample(other, SCOPE.channelId(), END, 95L), false).reason());
        Definition kind = def(FOLLOWER.id(), Key.FOLLOWER, Kind.CUMULATIVE, Subject.CHANNEL);
        assertEquals(CalculationReason.INCOMPATIBLE_DEFINITION,
                AnalyticsCalculator.delta(before, sample(kind, SCOPE.channelId(), END, 95L), false).reason());
    }
    @Test public void sourceAndObservationBasisAreNotMixed() {
        Sample before = sample(FOLLOWER, SCOPE.channelId(), START, 100L);
        Sample source = sample(FOLLOWER, SCOPE.channelId(), END, 95L, SCOPE, EvidenceSource.API_THAT, "w", null, null, ObservedAtBasis.COLLECTION);
        Sample basis = sample(FOLLOWER, SCOPE.channelId(), END, 95L, SCOPE, EvidenceSource.MO_PHONG, "w", null, null, ObservedAtBasis.PROVIDER);
        assertEquals(CalculationReason.INCOMPATIBLE_SOURCE, AnalyticsCalculator.delta(before, source, false).reason());
        assertEquals(CalculationReason.INCOMPATIBLE_PERIOD, AnalyticsCalculator.delta(before, basis, false).reason());
    }
    @Test public void periodDifferentWindowsCannotBecomeCumulative() {
        Definition d = def("period-v1", Key.REACTION, Kind.PERIOD, Subject.POST);
        Sample before = sample(d, "p", START, 100L, SCOPE, EvidenceSource.MO_PHONG, "w", START - 100, START, ObservedAtBasis.PROVIDER);
        Sample after = sample(d, "p", END, 95L, SCOPE, EvidenceSource.MO_PHONG, "w", START, END, ObservedAtBasis.PROVIDER);
        assertEquals(CalculationReason.INCOMPATIBLE_PERIOD, AnalyticsCalculator.delta(before, after, false).reason());
    }
    @Test public void collectionIdAloneDoesNotProveComponentAlignment() {
        Sample different = sample(SHARE, "Local:00A", END, 5L, SCOPE, EvidenceSource.MO_PHONG, null, null, null, ObservedAtBasis.COLLECTION);
        assertEquals(CalculationReason.INCOMPATIBLE_PERIOD, AnalyticsCalculator.engagement(r(12), c(3), different).reason());
        assertEquals(CalculationReason.INCOMPATIBLE_PERIOD,
                AnalyticsCalculator.engagement(r(12), c(3), sample(SHARE, "Local:00A", END - 1, 5L)).reason());
    }
    @Test public void noEarlyRoundingOrLongPrecisionLoss() {
        double actual = AnalyticsCalculator.engagementRate(r(1), c(0), s(0L), i(3)).value().doubleValue();
        assertEquals(100.0 / 3, actual, 1e-14);
        assertNotEquals(33.33, actual, 1e-10);
        long n = 9007199254740993L;
        assertEquals(Long.valueOf(1), AnalyticsCalculator.delta(sample(REACTION, "p", START, n),
                sample(REACTION, "p", END, n + 1), false).value());
    }
    @Test public void mondaySundayWeekAndEndExclusiveEvents() {
        long sunday = Instant.parse("2026-11-08T16:59:59Z").toEpochMilli();
        assertArrayEquals(new long[]{START, END}, AnalyticsCalculator.week(sunday, "Asia/Ho_Chi_Minh"));
        assertTrue(AnalyticsCalculator.eventInWindow(START, query(REACTION.id())));
        assertFalse(AnalyticsCalculator.eventInWindow(END, query(REACTION.id())));
    }
    @Test public void dstAndZoneChangesUseRealCalendarBoundaries() {
        long sunday = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli();
        long[] ny = AnalyticsCalculator.week(sunday, "America/New_York");
        assertEquals(169L * 3600000, ny[1] - ny[0]);
        long[] spring = AnalyticsCalculator.week(Instant.parse("2026-03-08T12:00:00Z").toEpochMilli(), "America/New_York");
        assertEquals(167L * 3600000, spring[1] - spring[0]);
        assertNotEquals(ny[0], AnalyticsCalculator.week(sunday, "Asia/Ho_Chi_Minh")[0]);
    }
    @Test public void freshnessThresholdIsInjectedStrictAndNullable() {
        assertFalse(AnalyticsCalculator.freshness(START, START + STALE_POLICY, STALE_POLICY).isStale());
        assertTrue(AnalyticsCalculator.freshness(START, START + STALE_POLICY + 1, STALE_POLICY).isStale());
        assertNull(AnalyticsCalculator.freshness(null, END, 1).isStale());
        assertThrows(IllegalArgumentException.class, () -> AnalyticsCalculator.zone("Unknown/Zone"));
        assertThrows(IllegalArgumentException.class, () -> AnalyticsCalculator.freshness(START, END, 0));
    }
}
