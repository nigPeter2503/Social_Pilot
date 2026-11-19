package com.socialpilot.app.domain.contract.analytics;

import com.socialpilot.app.domain.contract.analytics.DashboardQuery.ChannelScope;
import com.socialpilot.app.domain.contract.social.SocialError;
import com.socialpilot.app.domain.contract.social.SocialOperation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Shared value types for the SP-046.01 dashboard draft. Timestamps use UTC epoch milliseconds. */
public final class AnalyticsTypes {
    private AnalyticsTypes() {}

    public enum MissingReason {
        PERMISSION_REQUIRED, UNSUPPORTED, UNVERIFIED, NOT_SYNCED, PROVIDER_OMITTED,
        AUTH_REQUIRED, NETWORK_ERROR, RATE_LIMITED
    }

    public enum CalculationReason {
        MISSING_COMPONENT, ZERO_DENOMINATOR, MISSING_BASELINE, MISSING_CURRENT,
        INCOMPATIBLE_SCOPE, INCOMPATIBLE_DEFINITION, INCOMPATIBLE_PERIOD, INCOMPATIBLE_SOURCE,
        INVALID_TIME_ORDER, INVALID_TARGET, PARTIAL_COVERAGE
    }

    public enum CoverageLevel { FULL, PARTIAL, NONE }
    public enum EvidenceSource { API_THAT, FIXTURE_TU_API_DA_CHE, MO_PHONG, PROTOTYPE }
    public enum ObservedAtBasis { PROVIDER, COLLECTION }
    public enum SyncState { IDLE, LOADING, ERROR }
    public enum LocalFailure { CACHE_READ_FAILED, CACHE_WRITE_FAILED }

    public record Freshness(Boolean isStale, Long lastSuccessAt) {}

    /** Requested bounds and optional pair of measured bounds. A single sample has no pair. */
    public record Bounds(long requestedStart, long requestedEnd, Long actualStart, Long actualEnd) {
        public Bounds {
            if (requestedStart >= requestedEnd || (actualStart == null) != (actualEnd == null)) {
                throw new IllegalArgumentException("Expected a valid request window and both actual bounds or neither");
            }
            if (actualStart != null && (actualStart < requestedStart
                    || actualStart >= actualEnd || actualEnd > requestedEnd)) {
                throw new IllegalArgumentException("Expected ordered actual bounds inside the request window");
            }
        }
    }

    public record Coverage(CoverageLevel temporal, CoverageLevel cohort, int includedCount,
                           int totalCount, List<ExcludedPost> excludedReasons) {
        public Coverage {
            excludedReasons = immutable(excludedReasons);
            if (includedCount < 0 || includedCount > totalCount) {
                throw new IllegalArgumentException("Expected 0 <= includedCount <= totalCount");
            }
        }
    }

    public record ExcludedPost(ChannelScope scope, String postId, CalculationReason reason,
                               List<MissingDetail> missingDetails) {
        public ExcludedPost {
            missingDetails = immutable(missingDetails);
        }
    }

    public record MissingDetail(String componentDefinitionId, MissingReason missingReason) {}

    /** Provenance of a specific sample within its snapshot. sourceRef is already redacted. */
    public record MetricSource(
            String snapshotId, String collectionId, ChannelScope scope, String entityId,
            String definitionId, EvidenceSource source, String sourceRef, long observedAt,
            ObservedAtBasis observedAtBasis, long collectedAt, Long providerPeriodStart,
            Long providerPeriodEnd, String providerApiVersion) {}

    /** Raw sample projection. A null value has a missingReason, including on a chart gap. */
    public record MetricReading(String definitionId, Number value, MissingReason missingReason,
                                Long collectedAt, Freshness freshness, List<MetricSource> provenance) {
        public MetricReading {
            id(definitionId, "definitionId");
            checkNumericType(value);
            provenance = immutable(provenance);
            if ((value == null) != (missingReason != null)) {
                throw new IllegalArgumentException("A missing reason is required exactly when value is null");
            }
        }

        public List<String> sourceSnapshotIds() {
            return snapshotIds(provenance);
        }
    }

    public record CalculatedValue(
            String definitionId, Number value, CalculationReason reason, List<MissingDetail> missingDetails,
            Bounds bounds, Coverage coverage, Freshness freshness, List<MetricSource> provenance) {
        public CalculatedValue {
            id(definitionId, "definitionId");
            checkNumericType(value);
            missingDetails = immutable(missingDetails);
            provenance = immutable(provenance);
            if (value == null ? reason == null || reason == CalculationReason.PARTIAL_COVERAGE
                    : reason != null && reason != CalculationReason.PARTIAL_COVERAGE) {
                throw new IllegalArgumentException("Inconsistent calculation value and reason");
            }
        }

        public List<String> sourceSnapshotIds() {
            return snapshotIds(provenance);
        }
    }

    /** Scope and operation may be absent for a cache failure affecting the whole query. */
    public record DashboardError(ChannelScope scope, SocialOperation operation, SocialError error,
                                 LocalFailure localFailure, Long retryAfterAt) {
        public DashboardError {
            if ((error == null) == (localFailure == null)) {
                throw new IllegalArgumentException("Expected exactly one error kind");
            }
        }
    }

    static <T> List<T> immutable(List<T> values) {
        ArrayList<T> copy = new ArrayList<>(values);
        for (T value : copy) {
            Objects.requireNonNull(value, "list item");
        }
        return Collections.unmodifiableList(copy);
    }

    static void id(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must be nonempty");
        }
    }

    private static List<String> snapshotIds(List<MetricSource> provenance) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (MetricSource sample : provenance) {
            ids.add(sample.snapshotId());
        }
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }

    private static void checkNumericType(Number value) {
        if (value == null || value instanceof Long) {
            return;
        }
        if (value instanceof Double && !Double.isNaN(value.doubleValue()) && !Double.isInfinite(value.doubleValue())) {
            return;
        }
        throw new IllegalArgumentException("Use Long for counts and finite Double for percentages");
    }
}
