package com.socialpilot.app.domain.contract.ai;

import com.socialpilot.app.domain.contract.BudgetManager;

/**
 * The one shared ledger for every AI provider, task and channel. It is both the budget gate and
 * the durable usage history, so admission and reporting can never disagree.
 */
public interface AttemptLedger extends BudgetManager, UsageLog {
    /** Daily and monthly caps in one currency, evaluated in an IANA zone. */
    final class Limits {
        public final String currency;
        public final long dailyMicros;
        public final long monthlyMicros;
        public final String zoneId;

        public Limits(String currency, long dailyMicros, long monthlyMicros, String zoneId) {
            if (currency == null || currency.length() != 3) throw new IllegalArgumentException("Invalid currency");
            if (dailyMicros <= 0 || monthlyMicros <= 0) throw new IllegalArgumentException("Limits must be positive");
            BudgetPeriod.of(0, zoneId); // Rejects unknown zones.
            this.currency = currency;
            this.dailyMicros = dailyMicros;
            this.monthlyMicros = monthlyMicros;
            this.zoneId = zoneId;
        }
    }

    /** Committed spend of one period: settled estimates plus held reservations. */
    final class Spend {
        public final BudgetPeriod period;
        public final String currency;
        public final long dayMicros;
        public final long monthMicros;
        /** Attempts whose cost may exist but is not known (held at their reservation). */
        public final int uncertainAttempts;

        public Spend(BudgetPeriod period, String currency, long dayMicros, long monthMicros, int uncertainAttempts) {
            this.period = period;
            this.currency = currency;
            this.dayMicros = dayMicros;
            this.monthMicros = monthMicros;
            this.uncertainAttempts = uncertainAttempts;
        }
    }

    /** Fired after a commit leaves the current day or month above its cap (overrun or lowered cap). */
    interface ExhaustionListener {
        void onBudgetExhausted();
    }

    /**
     * Reserve with scope metadata so the attempt appears in usage reports even if the process
     * dies before a terminal event. Same atomicity and duplicate rules as {@link #reserve}.
     */
    Admission reserve(AiRequest request, AiTaskType taskType, AiCostEstimate upperBound);

    /**
     * Terminal of one attempt, in one transaction: the settlement transition and the usage are
     * written together or not at all. Not dispatched releases a still-RESERVED hold; an estimate
     * settles (a pinned currency/price mismatch stays UNCERTAIN); otherwise the hold becomes
     * UNCERTAIN. Idempotent: a repeat changes nothing and returns the stored record.
     *
     * @param estimate priced from a final provider report, or null when usage cannot be priced
     * @return the authoritative record built from the stored row (period, currency and price
     *     revision pinned at reserve), or null when the attempt is unknown or has no scope metadata
     * @throws RuntimeException when the write fails; nothing of the terminal is then stored
     */
    UsageRecord complete(String requestId, boolean mayHaveDispatched, AiCostEstimate estimate, AiUsage usage);

    /** Null when no limits are set; without limits nothing is admitted. */
    Limits limits();

    /** Replaces the caps. Lowering below current spend notifies exhaustion listeners. */
    void setLimits(Limits limits);

    /** Spend of the period containing now, in the configured zone; null without limits. */
    Spend currentSpend();

    void addExhaustionListener(ExhaustionListener listener);

    void removeExhaustionListener(ExhaustionListener listener);
}
