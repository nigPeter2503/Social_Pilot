package com.socialpilot.app.domain.contract;

import com.socialpilot.app.domain.contract.ai.AiCostEstimate;

/** Shared attempt ledger contract. The production implementation is a separate task. */
public interface BudgetManager {
    enum Admission { RESERVED, LIMIT_REACHED, DUPLICATE_REQUEST }

    /**
     * Atomically reserve against all applicable periods, pinned for this attempt. Duplicate
     * request IDs must never authorize a second dispatch, including after settlement.
     */
    Admission reserve(String requestId, AiCostEstimate upperBound);

    /**
     * Finalize using reported usage and the price pinned at reserve. May reconcile an uncertain
     * attempt. Repeated finalization must not change the booked amount.
     */
    void settle(String requestId, AiCostEstimate reportedUsageEstimate);

    /** Release only when dispatch could not have occurred. */
    void releaseUndispatched(String requestId);

    /** Retain the reservation until reconciliation when dispatch may have incurred unknown cost. */
    void markUncertain(String requestId);
}
