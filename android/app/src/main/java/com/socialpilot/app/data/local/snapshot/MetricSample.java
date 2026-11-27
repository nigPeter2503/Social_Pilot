package com.socialpilot.app.data.local.snapshot;

import com.socialpilot.app.data.remote.social.FollowerResult.Source;

/** UTC epoch milliseconds. Snapshot supplies the scope, subject and collection foreign key. */
public record MetricSample(MetricDefinition definition, Long value, String missingReason,
        long observedAt, String observedAtBasis, long collectedAt, Long providerPeriodStart,
        Long providerPeriodEnd, String periodEvidenceRef, Long requestedPeriodStart, Long requestedPeriodEnd,
        String providerApiVersion,
        Source source, String sourceRef) {}
