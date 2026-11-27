package com.socialpilot.app.domain.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource.*;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Pure arithmetic. No rounding before the final Double projection, no invented samples. */
public final class AnalyticsCalculator {
    public enum Diagnostic { COUNT_OVERFLOW }
    /** Overflow has a distinct internal diagnostic; the unchanged draft DTO uses INVALID_TARGET. */
    public record Result(Number value, CalculationReason reason, List<MissingDetail> missing,
                         List<MetricSource> provenance, Diagnostic diagnostic) {
        public Result {
            missing = List.copyOf(missing);
            provenance = List.copyOf(provenance);
        }
    }
    private static final Set<String> ZONES;
    static {
        Set<String> zones = new HashSet<>();
        Collections.addAll(zones, TimeZone.getAvailableIDs());
        ZONES = Collections.unmodifiableSet(zones);
    }
    private AnalyticsCalculator() {}

    public static TimeZone zone(String id) {
        if (!ZONES.contains(id)) throw new IllegalArgumentException("Expected IANA timezone");
        return TimeZone.getTimeZone(id);
    }

    /** Local Monday 00:00 through next Monday 00:00, including DST transitions. */
    public static long[] week(long instant, String zoneId) {
        Calendar c = Calendar.getInstance(zone(zoneId), Locale.ROOT);
        c.setTimeInMillis(instant);
        int days = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        c.add(Calendar.DATE, -days);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        c.add(Calendar.DATE, 7);
        return new long[] {start, c.getTimeInMillis()};
    }

    public static boolean eventInWindow(long time, DashboardQuery q) {
        return time >= q.requestedStart() && time < q.requestedEnd();
    }

    public static Freshness freshness(Long collectedAt, long now, long thresholdMillis) {
        if (thresholdMillis <= 0) throw new IllegalArgumentException("Configure freshness policy");
        return new Freshness(collectedAt == null ? null
                : BigDecimal.valueOf(now).subtract(BigDecimal.valueOf(collectedAt))
                    .compareTo(BigDecimal.valueOf(thresholdMillis)) > 0, collectedAt);
    }

    public static Result failure(CalculationReason reason, List<Sample> samples) {
        List<MissingDetail> missing = new ArrayList<>();
        List<MetricSource> sources = new ArrayList<>();
        for (Sample s : samples) {
            if (s == null) continue;
            sources.add(s.provenance());
            if (s.value() == null) missing.add(new MissingDetail(s.definition().id(), s.missingReason()));
        }
        return new Result(null, reason, missing, sources, null);
    }

    private static Result success(Number n, List<Sample> samples) {
        return new Result(n, null, List.of(), failure(CalculationReason.MISSING_COMPONENT, samples).provenance(), null);
    }

    private static Result overflow(List<Sample> samples) {
        return new Result(null, CalculationReason.INVALID_TARGET, List.of(),
                failure(CalculationReason.INVALID_TARGET, samples).provenance(), Diagnostic.COUNT_OVERFLOW);
    }

    /** Compatibility for two observations of the SAME definition and entity. */
    public static CalculationReason pairCompatibility(Sample a, Sample b) {
        MetricSource x = a.provenance(), y = b.provenance();
        if (!x.scope().equals(y.scope()) || a.subject() != b.subject() || !x.entityId().equals(y.entityId()))
            return CalculationReason.INCOMPATIBLE_SCOPE;
        if (!a.definition().equals(b.definition()) || a.definition().kind() == Kind.UNVERIFIED)
            return CalculationReason.INCOMPATIBLE_DEFINITION;
        if (x.source() != y.source() || !java.util.Objects.equals(x.providerApiVersion(), y.providerApiVersion()))
            return CalculationReason.INCOMPATIBLE_SOURCE;
        if (x.observedAtBasis() != y.observedAtBasis()
                || !java.util.Objects.equals(x.providerPeriodStart(), y.providerPeriodStart())
                || !java.util.Objects.equals(x.providerPeriodEnd(), y.providerPeriodEnd()))
            return CalculationReason.INCOMPATIBLE_PERIOD;
        if (a.definition().kind() == Kind.PERIOD && (x.providerPeriodStart() == null
                || x.providerPeriodEnd() == null || x.providerPeriodStart() >= x.providerPeriodEnd()))
            return CalculationReason.INCOMPATIBLE_PERIOD;
        return null;
    }

    /** Cross-component compatibility requires explicit alignment evidence plus exact measured bounds. */
    public static CalculationReason componentCompatibility(Sample a, Sample b) {
        MetricSource x = a.provenance(), y = b.provenance();
        if (!x.scope().equals(y.scope()) || a.subject() != b.subject() || !x.entityId().equals(y.entityId()))
            return CalculationReason.INCOMPATIBLE_SCOPE;
        if (x.source() != y.source() || !java.util.Objects.equals(x.providerApiVersion(), y.providerApiVersion()))
            return CalculationReason.INCOMPATIBLE_SOURCE;
        if (a.definition().kind() != b.definition().kind() || a.definition().kind() == Kind.UNVERIFIED
                || !a.definition().unit().equals(b.definition().unit()))
            return CalculationReason.INCOMPATIBLE_DEFINITION;
        if (a.alignmentId() == null || !a.alignmentId().equals(b.alignmentId())
                || !x.collectionId().equals(y.collectionId()) || x.observedAt() != y.observedAt()
                || x.observedAtBasis() != y.observedAtBasis()
                || !java.util.Objects.equals(x.providerPeriodStart(), y.providerPeriodStart())
                || !java.util.Objects.equals(x.providerPeriodEnd(), y.providerPeriodEnd()))
            return CalculationReason.INCOMPATIBLE_PERIOD;
        if (a.definition().kind() == Kind.PERIOD && (x.providerPeriodStart() == null
                || x.providerPeriodEnd() == null || x.providerPeriodStart() >= x.providerPeriodEnd()))
            return CalculationReason.INCOMPATIBLE_PERIOD;
        return null;
    }

    public static Result engagement(Sample reaction, Sample comment, Sample share) {
        List<Sample> samples = nullableList(reaction, comment, share);
        if (reaction == null || comment == null || share == null
                || reaction.value() == null || comment.value() == null || share.value() == null)
            return failure(CalculationReason.MISSING_COMPONENT, samples);
        if (reaction.definition().key() != Key.REACTION || comment.definition().key() != Key.COMMENT
                || share.definition().key() != Key.SHARE || !"COUNT".equals(reaction.definition().unit()))
            return failure(CalculationReason.INCOMPATIBLE_DEFINITION, samples);
        for (Sample s : samples) {
            CalculationReason reason = componentCompatibility(reaction, s);
            if (reason != null) return failure(reason, samples);
        }
        try { return success(Math.addExact(Math.addExact(reaction.value(), comment.value()), share.value()), samples); }
        catch (ArithmeticException e) { return overflow(samples); }
    }

    public static Result engagementRate(Sample reaction, Sample comment, Sample share, Sample impression) {
        Result e = engagement(reaction, comment, share);
        List<Sample> all = nullableList(reaction, comment, share, impression);
        if (e.value() == null) return new Result(null, e.reason(), failure(e.reason(), all).missing(),
                failure(e.reason(), all).provenance(), e.diagnostic());
        if (impression == null || impression.value() == null) return failure(CalculationReason.MISSING_COMPONENT, all);
        if (impression.definition().key() != Key.IMPRESSION) return failure(CalculationReason.INCOMPATIBLE_DEFINITION, all);
        CalculationReason reason = componentCompatibility(reaction, impression);
        if (reason != null) return failure(reason, all);
        if (impression.value() == 0) return failure(CalculationReason.ZERO_DENOMINATOR, all);
        return success(percent(e.value().longValue(), impression.value()), all);
    }

    public static Result delta(Sample baseline, Sample current, boolean growth) {
        List<Sample> samples = nullableList(baseline, current);
        if (current == null || current.value() == null) return failure(CalculationReason.MISSING_CURRENT, samples);
        if (baseline == null || baseline.value() == null) return failure(CalculationReason.MISSING_BASELINE, samples);
        CalculationReason reason = pairCompatibility(baseline, current);
        if (reason != null) return failure(reason, samples);
        if (baseline.provenance().observedAt() >= current.provenance().observedAt())
            return failure(CalculationReason.INVALID_TIME_ORDER, samples);
        try {
            long d = Math.subtractExact(current.value(), baseline.value());
            if (growth && baseline.value() == 0) return failure(CalculationReason.ZERO_DENOMINATOR, samples);
            return success(growth ? (Number) Double.valueOf(percent(d, baseline.value())) : Long.valueOf(d), samples);
        } catch (ArithmeticException e) { return overflow(samples); }
    }

    public static double percent(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), MathContext.DECIMAL128).doubleValue();
    }

    public static List<Sample> nullableList(Sample... samples) {
        List<Sample> result = new ArrayList<>();
        for (Sample s : samples) if (s != null) result.add(s);
        return result;
    }
}
