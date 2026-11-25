package com.socialpilot.app.domain.ai;

import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Per provider and pinned period totals. Unpriced attempts are counted, never added as zero,
 * and currencies are never mixed in one total.
 */
public final class UsageSummary {
    public enum Scope { DAY, MONTH }

    public static final class Row {
        public final AiRequest.Provider provider;
        public final String periodKey;
        public final String currency;
        /** Sum of SETTLED estimates only. */
        public final long estimatedMicros;
        public final int settledRequests;
        /** Possibly charged but not priceable; the true total may be higher than the estimate. */
        public final int uncertainRequests;
        public final int releasedRequests;
        /** Requests whose usage was REPORTED / PARTIAL / MISSING as returned by the provider. */
        public final int reportedUsage;
        public final int partialUsage;
        public final int missingUsage;
        public final List<String> priceRevisions;

        Row(AiRequest.Provider provider, String periodKey, String currency, long estimatedMicros,
            int settledRequests, int uncertainRequests, int releasedRequests, int reportedUsage,
            int partialUsage, int missingUsage, List<String> priceRevisions) {
            this.provider = provider;
            this.periodKey = periodKey;
            this.currency = currency;
            this.estimatedMicros = estimatedMicros;
            this.settledRequests = settledRequests;
            this.uncertainRequests = uncertainRequests;
            this.releasedRequests = releasedRequests;
            this.reportedUsage = reportedUsage;
            this.partialUsage = partialUsage;
            this.missingUsage = missingUsage;
            this.priceRevisions = priceRevisions;
        }

        /** True when the estimate is complete for this row (no uncertain attempts). */
        public boolean complete() {
            return uncertainRequests == 0;
        }
    }

    private UsageSummary() { }

    /** Rows keyed by provider, period and currency, in first-seen order. Duplicates by requestId count once. */
    public static List<Row> summarize(List<UsageRecord> records, Scope scope, String periodKey) {
        Objects.requireNonNull(periodKey);
        Map<String, Builder> rows = new LinkedHashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (UsageRecord record : records) {
            String key = scope == Scope.DAY ? record.period.dayKey : record.period.monthKey;
            if (!periodKey.equals(key) || !seen.add(record.requestId)) continue;
            String rowKey = record.provider + "|" + key + "|" + record.currency;
            Builder row = rows.get(rowKey);
            if (row == null) rows.put(rowKey, row = new Builder(record.provider, key, record.currency));
            row.add(record);
        }
        List<Row> result = new ArrayList<>();
        for (Builder builder : rows.values()) result.add(builder.build());
        return Collections.unmodifiableList(result);
    }

    private static final class Builder {
        final AiRequest.Provider provider;
        final String periodKey;
        final String currency;
        long micros;
        int settled, uncertain, released, reported, partial, missing;
        final TreeSet<String> revisions = new TreeSet<>();

        Builder(AiRequest.Provider provider, String periodKey, String currency) {
            this.provider = provider;
            this.periodKey = periodKey;
            this.currency = currency;
        }

        void add(UsageRecord record) {
            switch (record.settlement) {
                case SETTLED: settled++; micros = Math.addExact(micros, record.estimateMicros); break;
                case UNCERTAIN: uncertain++; break;
                default: released++; break;
            }
            switch (record.usage.state) {
                case REPORTED: reported++; break;
                case PARTIAL: partial++; break;
                default: missing++; break;
            }
            revisions.add(record.priceRevision);
        }

        Row build() {
            return new Row(provider, periodKey, currency, micros, settled, uncertain, released,
                    reported, partial, missing, Collections.unmodifiableList(new ArrayList<>(revisions)));
        }
    }
}
