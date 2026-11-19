package com.socialpilot.app.data.repository.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource.*;
import com.socialpilot.app.domain.analytics.AnalyticsCalculator;
import com.socialpilot.app.domain.analytics.AnalyticsCalculator.Result;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import com.socialpilot.app.domain.contract.analytics.DashboardData;
import com.socialpilot.app.domain.contract.analytics.DashboardData.*;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Conservative query projection: unknown compatibility is excluded, never inferred. */
public final class AnalyticsProjector {
    private final long staleAfterMillis;
    public AnalyticsProjector(long staleAfterMillis) {
        if (staleAfterMillis <= 0) throw new IllegalArgumentException("Configure freshness threshold");
        this.staleAfterMillis = staleAfterMillis;
    }
    private record Evaluation(Result result, Bounds bounds, Long numerator, Long denominator) {}
    private record Row(PostMetadata post, Evaluation selected) {}
    private record PostKey(ChannelScope scope, String postId) {}

    public DashboardData project(DashboardQuery q, Batch batch, EvidenceSource namespace, long now) {
        AnalyticsCalculator.zone(q.zoneId());
        if (batch.source() != namespace) throw new IllegalArgumentException("Wrong evidence namespace");
        Map<String, Definition> defs = new HashMap<>();
        for (Definition d : batch.definitions()) {
            if (defs.put(d.id(), d) != null) throw new IllegalArgumentException("Duplicate definition");
        }
        Map<PostKey, PostMetadata> posts = new HashMap<>();
        for (PostMetadata p : batch.posts()) {
            if (q.scopes().contains(p.scope()) && q.cohort().cohortPostIds().contains(p.postId())) {
                if (posts.put(new PostKey(p.scope(), p.postId()), p) != null)
                    throw new IllegalArgumentException("Duplicate local post");
            }
        }
        List<Sample> samples = new ArrayList<>();
        for (Sample s : batch.samples()) {
            MetricSource p = s.provenance();
            if (p.source() != namespace) throw new IllegalArgumentException("Mixed evidence namespace");
            if (!q.scopes().contains(p.scope())) continue;
            if (!s.definition().equals(defs.get(s.definition().id())))
                throw new IllegalArgumentException("Sample does not match pinned registry");
            if (s.subject() == Subject.CHANNEL && !p.entityId().equals(p.scope().channelId())) continue;
            if (s.subject() == Subject.POST && !posts.containsKey(new PostKey(p.scope(), p.entityId()))) continue;
            if (p.observedAt() <= q.requestedEnd()) samples.add(s);
        }
        Collections.sort(samples, (a, b) -> {
            int c = Long.compare(a.provenance().observedAt(), b.provenance().observedAt());
            if (c == 0) c = Long.compare(a.provenance().collectedAt(), b.provenance().collectedAt());
            return c != 0 ? c : a.provenance().snapshotId().compareTo(b.provenance().snapshotId());
        });
        List<ChannelCard> cards = new ArrayList<>();
        List<GrowthPoint> growth = new ArrayList<>();
        for (Channel c : batch.channels()) {
            if (!q.scopes().contains(c.scope())) continue;
            Definition def = defs.get(c.followerDefinitionId());
            if (def == null || def.key() != Key.FOLLOWER || def.subject() != Subject.CHANNEL) continue;
            List<Sample> history = history(samples, c.scope(), c.scope().channelId(), def.id(), Subject.CHANNEL);
            Sample latest = history.isEmpty() ? null : history.get(history.size() - 1);
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i).value() != null) { latest = history.get(i); break; }
            }
            cards.add(new ChannelCard(c.scope(), c.displayName(), reading(def.id(), latest, now),
                    batch.attempt().state(), batch.attempt().errors()));
            chart(q, c.scope(), def.id(), history, now, growth);
        }
        List<PostMetrics> metrics = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        List<ExcludedPost> unknownPosts = new ArrayList<>();
        Definition selected = defs.get(q.metricDefinitionId());
        for (String id : q.cohort().cohortPostIds()) {
            List<PostMetadata> matches = new ArrayList<>();
            for (PostMetadata p : posts.values()) if (p.postId().equals(id)) matches.add(p);
            Collections.sort(matches, (a, b) -> a.scope().channelId().compareTo(b.scope().channelId()));
            if (matches.size() != 1) {
                // Local IDs must be globally unambiguous; do not substitute a provider ID.
                unknownPosts.add(new ExcludedPost(null, id, CalculationReason.INCOMPATIBLE_SCOPE, List.of()));
                continue;
            }
            PostMetadata p = matches.get(0);
            List<MetricReading> readings = new ArrayList<>();
            List<CalculatedValue> calculated = new ArrayList<>();
            List<Definition> sorted = new ArrayList<>(defs.values());
            Collections.sort(sorted, (a, b) -> a.id().compareTo(b.id()));
            for (Definition d : sorted) {
                if (d.subject() != Subject.POST) continue;
                if (d.components().isEmpty()) {
                    List<Sample> h = history(samples, p.scope(), p.postId(), d.id(), Subject.POST);
                    if (!h.isEmpty()) readings.add(reading(d.id(), h.get(h.size() - 1), now));
                } else if (!d.id().equals(q.metricDefinitionId())) {
                    calculated.add(value(d.id(), evaluate(q, p, d, defs, samples, false), now));
                }
            }
            Evaluation evaluation = evaluate(q, p, selected, defs, samples, true);
            calculated.add(value(q.metricDefinitionId(), evaluation, now));
            rows.add(new Row(p, evaluation));
            metrics.add(new PostMetrics(p, readings, calculated));
        }
        List<WeeklyEngagement> weekly = new ArrayList<>();
        List<GroupedMetric> groups = new ArrayList<>();
        List<TopPost> top = new ArrayList<>();
        List<ExcludedPost> topExcluded = new ArrayList<>(unknownPosts);
        List<ExcludedPost> allExcluded = new ArrayList<>(unknownPosts);
        int included = 0;
        CoverageLevel temporal = CoverageLevel.FULL;
        for (ChannelScope scope : q.scopes()) {
            List<Row> scoped = new ArrayList<>();
            for (Row row : rows) if (row.post().scope().equals(scope)) scoped.add(row);
            // Comparisons cannot combine distinct actual windows or measurement semantics.
            List<Row> eligible = comparable(scoped, topExcluded);
            Collections.sort(eligible, topOrder());
            for (int i = 0; i < Math.min(q.topLimit(), eligible.size()); i++) {
                Row row = eligible.get(i);
                top.add(new TopPost(row.post(), value(q.metricDefinitionId(), row.selected(), now), i + 1));
            }
            Map<String, List<Row>> buckets = new LinkedHashMap<>();
            for (Row row : scoped) {
                String key = groupKey(q, row.post());
                if (!buckets.containsKey(key)) buckets.put(key, new ArrayList<>());
                buckets.get(key).add(row);
            }
            List<String> keys = new ArrayList<>(buckets.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                List<Row> bucket = buckets.get(key);
                CalculatedValue sum = aggregate(q, q.metricDefinitionId(), bucket, List.of(), now);
                groups.add(new GroupedMetric(scope, q.grouping(), key, key, sum,
                        sum.coverage().includedCount(), sum.coverage().totalCount() - sum.coverage().includedCount()));
            }
            List<Definition> engagementDefs = new ArrayList<>();
            for (Definition d : defs.values()) if (d.key() == Key.ENGAGEMENT) engagementDefs.add(d);
            Collections.sort(engagementDefs, (a, b) -> a.id().compareTo(b.id()));
            for (Definition d : engagementDefs) {
                List<Row> engagementRows = new ArrayList<>();
                for (Row row : scoped) engagementRows.add(new Row(row.post(),
                        evaluate(q, row.post(), d, defs, samples, true)));
                weekly.add(new WeeklyEngagement(scope, aggregate(q, d.id(), engagementRows, unknownPosts, now)));
            }
            CalculatedValue subtotal = aggregate(q, q.metricDefinitionId(), scoped, List.of(), now);
            included += subtotal.coverage().includedCount();
            allExcluded.addAll(subtotal.coverage().excludedReasons());
            if (!scoped.isEmpty() && subtotal.coverage().temporal() != CoverageLevel.FULL)
                temporal = temporal == CoverageLevel.NONE || subtotal.coverage().temporal() == CoverageLevel.NONE
                        ? CoverageLevel.NONE : CoverageLevel.PARTIAL;
        }
        int total = q.cohort().cohortPostIds().size();
        if (rows.isEmpty() || included == 0) temporal = CoverageLevel.NONE;
        Coverage coverage = new Coverage(temporal, level(included, total), included, total, allExcluded);
        Attempt a = batch.attempt();
        ContentState state = !batch.available() ? ContentState.UNAVAILABLE
                : cards.isEmpty() && rows.isEmpty() ? ContentState.EMPTY : ContentState.CONTENT;
        return new DashboardData(q, namespace, state, cards, metrics, growth, weekly, top, topExcluded,
                groups, List.of(), coverage, a.lastSuccessAt(), a.lastAttemptAt(), a.state(), a.offline(), a.errors());
    }

    private Evaluation evaluate(DashboardQuery q, PostMetadata p, Definition d, Map<String, Definition> defs,
                                List<Sample> samples, boolean interval) {
        return evaluate(q, p, d, defs, samples, interval, new java.util.HashSet<>());
    }

    private Evaluation evaluate(DashboardQuery q, PostMetadata p, Definition d, Map<String, Definition> defs,
                                List<Sample> samples, boolean interval, java.util.Set<String> visiting) {
        Bounds empty = new Bounds(q.requestedStart(), q.requestedEnd(), null, null);
        if (d == null || d.subject() != Subject.POST || !visiting.add(d.id()))
            return failed(CalculationReason.INCOMPATIBLE_DEFINITION, List.of(), empty);
        if (d.components().isEmpty()) {
            List<Sample> h = history(samples, p.scope(), p.postId(), d.id(), Subject.POST);
            removeBefore(h, q.requestedStart());
            Sample first = h.size() < 2 ? null : h.get(0);
            Sample last = h.isEmpty() ? null : h.get(h.size() - 1);
            if (!interval) {
                Result r = last == null || last.value() == null
                        ? AnalyticsCalculator.failure(CalculationReason.MISSING_COMPONENT, h)
                        : new Result(last.value(), null, List.of(), List.of(last.provenance()), null);
                return new Evaluation(r, empty, null, null);
            }
            // PERIOD values require a direct period query policy, not cumulative subtraction.
            if (d.kind() == Kind.PERIOD || d.kind() == Kind.UNVERIFIED)
                return failed(CalculationReason.INCOMPATIBLE_PERIOD, h, empty);
            Result r = AnalyticsCalculator.delta(first, last, false);
            Bounds bounds = first == null || last == null || first.provenance().observedAt() >= last.provenance().observedAt()
                    ? empty : new Bounds(q.requestedStart(), q.requestedEnd(), first.provenance().observedAt(), last.provenance().observedAt());
            return new Evaluation(r, bounds, null, null);
        }
        // Registry explicitly identifies components. No search by generic metric name.
        List<Key> required = d.key() == Key.ENGAGEMENT ? List.of(Key.REACTION, Key.COMMENT, Key.SHARE)
                : d.key() == Key.ENGAGEMENT_RATE ? List.of(Key.REACTION, Key.COMMENT, Key.SHARE, Key.IMPRESSION) : List.of();
        if (required.isEmpty() || d.components().size() != required.size()
                || !(d.key() == Key.ENGAGEMENT ? "COUNT" : "PERCENT").equals(d.unit()))
            return failed(CalculationReason.INCOMPATIBLE_DEFINITION, List.of(), empty);
        List<Sample> starts = new ArrayList<>(), ends = new ArrayList<>();
        for (int i = 0; i < required.size(); i++) {
            Definition component = defs.get(d.components().get(i));
            if (component == null || component.key() != required.get(i) || !component.components().isEmpty()
                    || component.kind() != d.kind())
                return failed(CalculationReason.INCOMPATIBLE_DEFINITION, List.of(), empty);
            List<Sample> h = history(samples, p.scope(), p.postId(), component.id(), Subject.POST);
            removeBefore(h, q.requestedStart());
            starts.add(h.size() < 2 ? null : h.get(0));
            ends.add(h.isEmpty() ? null : h.get(h.size() - 1));
        }
        List<Sample> provenance = new ArrayList<>();
        if (interval) for (Sample s : starts) if (s != null) provenance.add(s);
        for (Sample s : ends) if (s != null) provenance.add(s);
        Result endE = AnalyticsCalculator.engagement(ends.get(0), ends.get(1), ends.get(2));
        if (!interval) return new Evaluation(d.key() == Key.ENGAGEMENT ? endE
                : AnalyticsCalculator.engagementRate(ends.get(0), ends.get(1), ends.get(2), ends.get(3)), empty, null, null);
        if (starts.contains(null)) return missingComponent(d, starts, CalculationReason.MISSING_BASELINE, provenance, empty);
        if (ends.contains(null)) return missingComponent(d, ends, CalculationReason.MISSING_CURRENT, provenance, empty);
        Result startE = AnalyticsCalculator.engagement(starts.get(0), starts.get(1), starts.get(2));
        for (int i = 0; i < starts.size(); i++) {
            if (starts.get(i).definition().kind() != Kind.CUMULATIVE)
                return failed(CalculationReason.INCOMPATIBLE_PERIOD, provenance, empty);
            Result delta = AnalyticsCalculator.delta(starts.get(i), ends.get(i), false);
            if (delta.value() == null) return failed(delta.reason(), provenance, empty);
        }
        if (startE.value() == null) return failed(startE.reason(), provenance, empty);
        if (endE.value() == null) return failed(endE.reason(), provenance, empty);
        Bounds bounds = new Bounds(q.requestedStart(), q.requestedEnd(), starts.get(0).provenance().observedAt(),
                ends.get(0).provenance().observedAt());
        try {
            long e = Math.subtractExact(endE.value().longValue(), startE.value().longValue());
            Long impression = null;
            Number n = Long.valueOf(e);
            if (d.key() == Key.ENGAGEMENT_RATE) {
                CalculationReason a = AnalyticsCalculator.componentCompatibility(starts.get(0), starts.get(3));
                CalculationReason b = AnalyticsCalculator.componentCompatibility(ends.get(0), ends.get(3));
                if (a != null || b != null) return failed(a != null ? a : b, provenance, bounds);
                impression = Math.subtractExact(ends.get(3).value(), starts.get(3).value());
                if (impression == 0) return failed(CalculationReason.ZERO_DENOMINATOR, provenance, bounds);
                n = AnalyticsCalculator.percent(e, impression);
            }
            return new Evaluation(new Result(n, null, List.of(), sources(provenance), null), bounds,
                    d.key() == Key.ENGAGEMENT_RATE ? e : null, impression);
        } catch (ArithmeticException ex) {
            return new Evaluation(new Result(null, CalculationReason.INVALID_TARGET, List.of(), sources(provenance),
                    AnalyticsCalculator.Diagnostic.COUNT_OVERFLOW), bounds, null, null);
        }
    }

    private static List<MetricSource> sources(List<Sample> samples) {
        List<MetricSource> out = new ArrayList<>();
        for (Sample s : samples) out.add(s.provenance());
        return out;
    }
    private static void removeBefore(List<Sample> samples, long start) {
        java.util.Iterator<Sample> iterator = samples.iterator();
        while (iterator.hasNext()) if (iterator.next().provenance().observedAt() < start) iterator.remove();
    }
    private static Evaluation failed(CalculationReason reason, List<Sample> samples, Bounds bounds) {
        return new Evaluation(AnalyticsCalculator.failure(reason, samples), bounds, null, null);
    }
    private static Evaluation missingComponent(Definition def, List<Sample> components, CalculationReason reason,
                                                List<Sample> samples, Bounds bounds) {
        Result failure = AnalyticsCalculator.failure(reason, samples);
        List<MissingDetail> details = new ArrayList<>(failure.missing());
        for (int i = 0; i < components.size(); i++) if (components.get(i) == null)
            details.add(new MissingDetail(def.components().get(i), MissingReason.NOT_SYNCED));
        return new Evaluation(new Result(null, reason, details, failure.provenance(), null), bounds, null, null);
    }
    private static List<Sample> history(List<Sample> samples, ChannelScope scope, String id, String def, Subject subject) {
        List<Sample> h = new ArrayList<>();
        for (Sample s : samples) if (s.provenance().scope().equals(scope) && s.subject() == subject
                && s.provenance().entityId().equals(id) && s.definition().id().equals(def)) h.add(s);
        return h;
    }
    private MetricReading reading(String definition, Sample sample, long now) {
        return new MetricReading(definition, sample == null ? null : sample.value(),
                sample == null ? MissingReason.NOT_SYNCED : sample.missingReason(),
                sample == null ? null : sample.provenance().collectedAt(),
                AnalyticsCalculator.freshness(sample == null ? null : sample.provenance().collectedAt(), now, staleAfterMillis),
                sample == null ? List.of() : List.of(sample.provenance()));
    }
    private void chart(DashboardQuery q, ChannelScope scope, String definition, List<Sample> history,
                       long now, List<GrowthPoint> output) {
        Calendar day = Calendar.getInstance(AnalyticsCalculator.zone(q.zoneId()), Locale.ROOT);
        day.setTimeInMillis(q.requestedStart());
        while (day.getTimeInMillis() < q.requestedEnd()) {
            long start = day.getTimeInMillis();
            day.add(Calendar.DATE, 1);
            long end = Math.min(day.getTimeInMillis(), q.requestedEnd());
            boolean found = false;
            for (Sample s : history) if (s.provenance().observedAt() >= start && s.provenance().observedAt() < end) {
                output.add(new GrowthPoint(scope, s.provenance().observedAt(), false, reading(definition, s, now)));
                found = true;
            }
            if (!found) output.add(new GrowthPoint(scope, start, true, reading(definition, null, now)));
        }
        for (Sample s : history) if (s.provenance().observedAt() == q.requestedEnd())
            output.add(new GrowthPoint(scope, q.requestedEnd(), false, reading(definition, s, now)));
    }
    private CalculatedValue value(String id, Evaluation e, long now) {
        Result r = e.result();
        int included = r.value() == null ? 0 : 1;
        Coverage coverage = new Coverage(temporal(e.bounds()), level(included, 1), included, 1, List.of());
        return new CalculatedValue(id, r.value(), r.reason(), r.missing(), e.bounds(), coverage,
                freshness(r.provenance(), now), r.provenance());
    }
    private Freshness freshness(List<MetricSource> sources, long now) {
        Long oldest = null;
        for (MetricSource s : sources) oldest = oldest == null ? s.collectedAt() : Math.min(oldest, s.collectedAt());
        return AnalyticsCalculator.freshness(oldest, now, staleAfterMillis);
    }
    private static CoverageLevel temporal(Bounds b) {
        return b.actualStart() == null ? CoverageLevel.NONE
                : b.actualStart() == b.requestedStart() && b.actualEnd() == b.requestedEnd()
                    ? CoverageLevel.FULL : CoverageLevel.PARTIAL;
    }
    private static CoverageLevel level(int included, int total) {
        return included == 0 ? CoverageLevel.NONE : included == total ? CoverageLevel.FULL : CoverageLevel.PARTIAL;
    }
    private static String signature(Evaluation e) {
        StringBuilder key = new StringBuilder();
        // Internal tuple representation uses list equality below, not concatenated opaque IDs.
        key.append(e.bounds().actualStart()).append(':').append(e.bounds().actualEnd());
        return key.toString();
    }
    private static boolean compatible(Evaluation a, Evaluation b) {
        if (!signature(a).equals(signature(b))) return false;
        List<MetricSource> x = a.result().provenance(), y = b.result().provenance();
        if (x.size() != y.size()) return false;
        for (int i = 0; i < x.size(); i++) {
            MetricSource s = x.get(i), t = y.get(i);
            if (!s.scope().equals(t.scope()) || !s.definitionId().equals(t.definitionId())
                    || s.source() != t.source() || s.observedAt() != t.observedAt()
                    || s.observedAtBasis() != t.observedAtBasis()
                    || !Objects.equals(s.providerApiVersion(), t.providerApiVersion())
                    || !Objects.equals(s.providerPeriodStart(), t.providerPeriodStart())
                    || !Objects.equals(s.providerPeriodEnd(), t.providerPeriodEnd())) return false;
        }
        return true;
    }
    private static List<Row> comparable(List<Row> rows, List<ExcludedPost> excluded) {
        List<Row> candidates = new ArrayList<>();
        for (Row row : rows) {
            if (row.selected().result().value() == null) excluded.add(exclusion(row, row.selected().result().reason()));
            else candidates.add(row);
        }
        boolean mismatch = false;
        for (int i = 1; i < candidates.size(); i++) if (!compatible(candidates.get(0).selected(), candidates.get(i).selected())) mismatch = true;
        if (mismatch) {
            for (Row row : candidates) excluded.add(exclusion(row, CalculationReason.INCOMPATIBLE_PERIOD));
            return new ArrayList<>();
        }
        return candidates;
    }
    private static ExcludedPost exclusion(Row row, CalculationReason reason) {
        return new ExcludedPost(row.post().scope(), row.post().postId(), reason, row.selected().result().missing());
    }
    private CalculatedValue aggregate(DashboardQuery q, String id, List<Row> rows, List<ExcludedPost> initial, long now) {
        List<ExcludedPost> excluded = new ArrayList<>(initial);
        List<Row> valid = comparable(rows, excluded);
        List<MetricSource> sources = new ArrayList<>();
        List<MissingDetail> missing = new ArrayList<>();
        for (Row row : valid) sources.addAll(row.selected().result().provenance());
        for (Row row : rows) missing.addAll(row.selected().result().missing());
        Bounds bounds = valid.isEmpty() ? new Bounds(q.requestedStart(), q.requestedEnd(), null, null) : valid.get(0).selected().bounds();
        Number n = null;
        CalculationReason reason = valid.isEmpty() ? CalculationReason.MISSING_COMPONENT : null;
        try {
            long sum = 0, numerator = 0, denominator = 0;
            for (Row row : valid) {
                Evaluation e = row.selected();
                if (e.denominator() == null) sum = Math.addExact(sum, e.result().value().longValue());
                else {
                    numerator = Math.addExact(numerator, e.numerator());
                    denominator = Math.addExact(denominator, e.denominator());
                }
            }
            if (!valid.isEmpty()) {
                boolean rate = valid.get(0).selected().denominator() != null;
                if (rate && denominator == 0) reason = CalculationReason.ZERO_DENOMINATOR;
                else {
                    n = rate ? (Number) Double.valueOf(AnalyticsCalculator.percent(numerator, denominator)) : Long.valueOf(sum);
                    if (!excluded.isEmpty()) reason = CalculationReason.PARTIAL_COVERAGE;
                }
            }
        } catch (ArithmeticException overflow) { reason = CalculationReason.INVALID_TARGET; }
        Coverage coverage = new Coverage(temporal(bounds), level(valid.size(), rows.size() + initial.size()),
                valid.size(), rows.size() + initial.size(), excluded);
        return new CalculatedValue(id, n, reason, missing, bounds, coverage, freshness(sources, now), sources);
    }
    private static Comparator<Row> topOrder() {
        return (a, b) -> {
            int c = new java.math.BigDecimal(b.selected().result().value().toString())
                    .compareTo(new java.math.BigDecimal(a.selected().result().value().toString()));
            if (c != 0) return c;
            Long x = a.post().publishedAt(), y = b.post().publishedAt();
            c = x == null ? y == null ? 0 : 1 : y == null ? -1 : Long.compare(y, x);
            if (c != 0) return c;
            c = a.post().scope().platform().name().compareTo(b.post().scope().platform().name());
            if (c != 0) return c;
            c = a.post().scope().channelId().compareTo(b.post().scope().channelId());
            return c != 0 ? c : a.post().postId().compareTo(b.post().postId());
        };
    }
    private static String groupKey(DashboardQuery q, PostMetadata p) {
        if (q.grouping() == Grouping.TOPIC) return p.primaryTopicId() == null ? "UNKNOWN" : p.primaryTopicId();
        if (q.grouping() == Grouping.FORMAT) return p.format() == null ? "UNKNOWN" : p.format();
        if (p.publishedAt() == null) return "UNKNOWN";
        Calendar c = Calendar.getInstance(AnalyticsCalculator.zone(q.zoneId()), Locale.ROOT);
        c.setTimeInMillis(p.publishedAt());
        return Integer.toString(c.get(Calendar.HOUR_OF_DAY));
    }
}
