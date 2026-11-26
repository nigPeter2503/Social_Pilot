package com.socialpilot.app.domain.contract.ai;

import java.util.Objects;

/**
 * Usage of one dispatch (one requestId). Missing usage stays MISSING with a null estimate; it
 * is never recorded as zero. Estimates are application estimates, not provider invoices.
 */
public final class UsageRecord {
    public enum Settlement {
        /** Estimated from provider usage and the price pinned at reserve. */
        SETTLED,
        /** May have been dispatched but usage cannot be priced; the reservation stays held. */
        UNCERTAIN,
        /** Certainly not dispatched; no provider cost. */
        RELEASED
    }

    public final String requestId;
    public final String sessionId;
    public final String channelId;
    public final String stepId;
    public final AiTaskType taskType;
    public final AiRequest.Provider provider;
    public final String model;
    public final BudgetPeriod period;
    public final long recordedAtMillis;
    public final AiUsage usage;
    public final Settlement settlement;
    /** Null unless SETTLED. */
    public final Long estimateMicros;
    public final String currency;
    public final String priceRevision;

    public UsageRecord(String requestId, String sessionId, String channelId, String stepId,
                       AiTaskType taskType, AiRequest.Provider provider, String model, BudgetPeriod period,
                       long recordedAtMillis, AiUsage usage, Settlement settlement, Long estimateMicros,
                       String currency, String priceRevision) {
        this.requestId = Objects.requireNonNull(requestId);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.channelId = channelId;
        this.stepId = Objects.requireNonNull(stepId);
        this.taskType = Objects.requireNonNull(taskType);
        this.provider = Objects.requireNonNull(provider);
        this.model = Objects.requireNonNull(model);
        this.period = Objects.requireNonNull(period);
        this.recordedAtMillis = recordedAtMillis;
        this.usage = Objects.requireNonNull(usage);
        this.settlement = Objects.requireNonNull(settlement);
        if ((settlement == Settlement.SETTLED) != (estimateMicros != null)
                || (estimateMicros != null && estimateMicros < 0)) {
            throw new IllegalArgumentException("Estimate must exist exactly when settled");
        }
        this.estimateMicros = estimateMicros;
        this.currency = Objects.requireNonNull(currency);
        this.priceRevision = Objects.requireNonNull(priceRevision);
    }
}
