package com.socialpilot.app.data.ai.ledger;

import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.BudgetPeriod;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Atomic, durable AI budget. Each reserve reads committed spend and inserts the reservation in
 * one write transaction, so two branches racing for the last budget cannot both be admitted.
 * The day/month keys and price revision are pinned at reserve and never re-derived.
 * Methods do database I/O; call them from workers.
 */
public final class BudgetLedger implements AttemptLedger {
    private static final Logger LOGGER = Logger.getLogger(BudgetLedger.class.getName());

    public interface Clock {
        long nowMillis();
    }

    public interface ConnectionCheck {
        boolean current(String channelId, long revision);
    }

    /** Session/step label of attempts whose channel identity was dropped by {@link #forgetChannel}. */
    public static final String FORGOTTEN = "forgotten";

    private final LedgerStore store;
    private final Clock clock;
    private final ConnectionCheck connectionCurrent;
    private final String bootId = UUID.randomUUID().toString();
    private final List<ExhaustionListener> listeners = new CopyOnWriteArrayList<>();

    public BudgetLedger(LedgerStore store, Clock clock) {
        this(store, clock, (channelId, revision) -> true);
    }

    public BudgetLedger(LedgerStore store, Clock clock, ConnectionCheck connectionCurrent) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.connectionCurrent = Objects.requireNonNull(connectionCurrent);
    }

    /**
     * Call once per process before serving the UI. Reservations of a previous process whose
     * terminal never arrived may have been charged: they stay held as UNCERTAIN, never free.
     */
    public int recoverInterrupted() {
        return store.inTransaction(() -> store.markInterrupted(bootId, clock.nowMillis()));
    }

    @Override
    public Admission reserve(String requestId, AiCostEstimate upperBound) {
        return reserve(requestId, null, null, upperBound);
    }

    @Override
    public Admission reserve(AiRequest request, AiTaskType taskType, AiCostEstimate upperBound) {
        return reserve(request.requestId, request, Objects.requireNonNull(taskType), upperBound);
    }

    private Admission reserve(String requestId, AiRequest request, AiTaskType taskType, AiCostEstimate upperBound) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(upperBound);
        return store.inTransaction(() -> {
            if (request != null && request.channelId != null
                    && !connectionCurrent.current(request.channelId, request.connectionRevision)) {
                return Admission.STALE_CHANNEL;
            }
            if (store.find(requestId) != null) return Admission.DUPLICATE_REQUEST;
            BudgetSettingsEntity settings = store.settings();
            String currency = upperBound.currency.getCurrencyCode();
            if (settings == null || !settings.currency.equals(currency)) return Admission.LIMIT_REACHED;
            long now = clock.nowMillis();
            BudgetPeriod period = BudgetPeriod.of(now, settings.zoneId);
            if (!fits(store.committedForDay(period.dayKey, currency), upperBound.micros, settings.dailyMicros)
                    || !fits(store.committedForMonth(period.monthKey, currency), upperBound.micros, settings.monthlyMicros)) {
                return Admission.LIMIT_REACHED;
            }
            AttemptEntity row = new AttemptEntity();
            row.requestId = requestId;
            row.bootId = bootId;
            if (request != null) {
                row.sessionId = request.sessionId;
                row.channelId = request.channelId;
                row.connectionRevision = request.connectionRevision;
                row.stepId = request.stepId;
                row.taskType = taskType.name();
                row.provider = request.provider.name();
                row.model = request.model;
            }
            row.zoneId = period.zoneId;
            row.dayKey = period.dayKey;
            row.monthKey = period.monthKey;
            row.currency = currency;
            row.priceRevision = upperBound.priceRevision;
            row.reservedMicros = upperBound.micros;
            // Legacy unscoped reservations have no dispatch gate and must remain conservative.
            row.state = request == null ? AttemptEntity.RESERVED : AttemptEntity.QUEUED;
            row.createdAt = now;
            row.updatedAt = now;
            store.insert(row);
            return Admission.RESERVED;
        });
    }

    @Override
    public boolean claimDispatch(String requestId) {
        return store.inTransaction(() -> {
            AttemptEntity row = store.find(requestId);
            if (row == null || !AttemptEntity.QUEUED.equals(row.state) || !bootId.equals(row.bootId)) return false;
            if (row.channelId != null && !connectionCurrent.current(row.channelId, row.connectionRevision)) return false;
            BudgetSettingsEntity settings = store.settings();
            if (settings == null || !row.currency.equals(settings.currency)
                    || !row.zoneId.equals(settings.zoneId)) return false;
            row.state = AttemptEntity.DISPATCHING;
            row.updatedAt = clock.nowMillis();
            store.update(row);
            return true;
        });
    }

    private static boolean holdsReservation(AttemptEntity row) {
        return AttemptEntity.RESERVED.equals(row.state) || AttemptEntity.QUEUED.equals(row.state)
                || AttemptEntity.DISPATCHING.equals(row.state);
    }

    private static boolean fits(long committed, long requested, long limit) {
        try {
            return Math.addExact(committed, requested) <= limit;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    /**
     * Idempotent. Accepts RESERVED or UNCERTAIN (reconciliation). An estimate with a different
     * currency or price revision than pinned is not applied; the attempt stays held.
     */
    @Override
    public void settle(String requestId, AiCostEstimate estimate) {
        Objects.requireNonNull(estimate);
        boolean exhausted = store.inTransaction(() -> {
            AttemptEntity row = store.find(requestId);
            if (row == null || AttemptEntity.SETTLED.equals(row.state) || AttemptEntity.RELEASED.equals(row.state)) {
                return false;
            }
            if (!row.currency.equals(estimate.currency.getCurrencyCode())
                    || !row.priceRevision.equals(estimate.priceRevision)) {
                row.state = AttemptEntity.UNCERTAIN;
            } else {
                row.state = AttemptEntity.SETTLED;
                row.settledMicros = estimate.micros;
            }
            row.updatedAt = clock.nowMillis();
            store.update(row);
            return AttemptEntity.SETTLED.equals(row.state)
                    && revokeIfExhausted(store.settings(), null, row.updatedAt);
        });
        if (exhausted) notifyExhaustion();
    }

    /** Only a still-RESERVED attempt can be released; release never applies twice. */
    @Override
    public void releaseUndispatched(String requestId) {
        transition(requestId, AttemptEntity.RELEASED);
    }

    @Override
    public void markUncertain(String requestId) {
        transition(requestId, AttemptEntity.UNCERTAIN);
    }

    private void transition(String requestId, String target) {
        store.inTransaction(() -> {
            AttemptEntity row = store.find(requestId);
            if (row == null || !holdsReservation(row)) return null;
            row.state = target;
            row.updatedAt = clock.nowMillis();
            store.update(row);
            return null;
        });
    }

    /** Stores usage once per requestId; the pinned period and state stay authoritative. */
    @Override
    public boolean record(UsageRecord record) {
        return store.inTransaction(() -> {
            AttemptEntity row = store.find(record.requestId);
            if (row == null || row.usageState != null) return false;
            if (row.provider == null) {
                row.sessionId = record.sessionId;
                row.channelId = record.channelId;
                row.stepId = record.stepId;
                row.taskType = record.taskType.name();
                row.provider = record.provider.name();
                row.model = record.model;
            }
            AiUsage usage = record.usage;
            row.usageState = usage.state.name();
            row.inputTokens = usage.inputTokens;
            row.outputTokens = usage.outputTokens;
            row.totalTokens = usage.totalTokens;
            row.cachedInputTokens = usage.cachedInputTokens;
            row.reasoningTokens = usage.reasoningTokens;
            row.updatedAt = clock.nowMillis();
            store.update(row);
            return true;
        });
    }

    @Override
    public UsageRecord complete(String requestId, boolean mayHaveDispatched, AiCostEstimate estimate,
                                AiUsage usage) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(usage);
        boolean[] exhausted = new boolean[1];
        UsageRecord result = store.inTransaction(() -> {
            AttemptEntity row = store.find(requestId);
            if (row == null) return null;
            if (row.usageState != null) return toRecord(row); // Already completed: no second transition.
            boolean held = holdsReservation(row);
            if (!mayHaveDispatched) {
                if (held) row.state = AttemptEntity.RELEASED;
            } else if (estimate != null && (held || AttemptEntity.UNCERTAIN.equals(row.state))) {
                if (row.currency.equals(estimate.currency.getCurrencyCode())
                        && row.priceRevision.equals(estimate.priceRevision)) {
                    row.state = AttemptEntity.SETTLED;
                    row.settledMicros = estimate.micros;
                } else {
                    row.state = AttemptEntity.UNCERTAIN;
                }
            } else if (held) {
                row.state = AttemptEntity.UNCERTAIN;
            }
            row.usageState = usage.state.name();
            row.inputTokens = usage.inputTokens;
            row.outputTokens = usage.outputTokens;
            row.totalTokens = usage.totalTokens;
            row.cachedInputTokens = usage.cachedInputTokens;
            row.reasoningTokens = usage.reasoningTokens;
            row.updatedAt = clock.nowMillis();
            store.update(row);
            if (AttemptEntity.SETTLED.equals(row.state)) {
                exhausted[0] = revokeIfExhausted(store.settings(), null, row.updatedAt);
            }
            return toRecord(row);
        });
        if (exhausted[0]) notifyExhaustion();
        return result;
    }

    /** Attempts with scope metadata; in-flight and interrupted ones read as UNCERTAIN. */
    @Override
    public List<UsageRecord> forMonth(String monthKey) {
        List<AttemptEntity> rows = store.inTransaction(() -> store.forMonth(monthKey));
        List<UsageRecord> result = new ArrayList<>();
        for (AttemptEntity row : rows) {
            if (row.sessionId == null) continue; // Retired attempts contribute to totals only.
            UsageRecord record = toRecord(row);
            if (record != null) result.add(record);
        }
        return result;
    }

    /** The stored row as a record; null without scope metadata (plain {@link #reserve(String, AiCostEstimate)}). */
    private static UsageRecord toRecord(AttemptEntity row) {
        if (row.provider == null) return null;
        UsageRecord.Settlement settlement = AttemptEntity.SETTLED.equals(row.state) ? UsageRecord.Settlement.SETTLED
                : AttemptEntity.RELEASED.equals(row.state) ? UsageRecord.Settlement.RELEASED
                : UsageRecord.Settlement.UNCERTAIN;
        AiUsage usage = row.usageState == null ? AiUsage.MISSING
                : new AiUsage(row.inputTokens, row.outputTokens, row.totalTokens, row.cachedInputTokens,
                row.reasoningTokens);
        if (AiUsage.State.PARTIAL.name().equals(row.usageState)) usage = usage.asPartial();
        return new UsageRecord(row.requestId, row.sessionId == null ? FORGOTTEN : row.sessionId,
                row.channelId, row.stepId == null ? FORGOTTEN : row.stepId,
                AiTaskType.valueOf(row.taskType), AiRequest.Provider.valueOf(row.provider), row.model,
                BudgetPeriod.pinned(row.zoneId, row.dayKey, row.monthKey), row.updatedAt, usage, settlement,
                settlement == UsageRecord.Settlement.SETTLED ? row.settledMicros : null,
                row.currency, row.priceRevision);
    }

    /**
     * For channel disconnect (retention policy owned by SP-024): keeps the spend so caps and
     * reconciliation stay correct, but the rows no longer identify the channel or session.
     */
    public int forgetChannel(String channelId, long retiredRevision) {
        java.util.Objects.requireNonNull(channelId);
        return store.inTransaction(() -> store.forgetChannel(channelId, retiredRevision, clock.nowMillis()));
    }

    @Override
    public Limits limits() {
        BudgetSettingsEntity settings = store.inTransaction(store::settings);
        return settings == null ? null
                : new Limits(settings.currency, settings.dailyMicros, settings.monthlyMicros, settings.zoneId);
    }

    @Override
    public void setLimits(Limits limits) {
        Objects.requireNonNull(limits);
        boolean exhausted = store.inTransaction(() -> {
            BudgetSettingsEntity previous = store.settings();
            BudgetSettingsEntity settings = new BudgetSettingsEntity();
            settings.currency = limits.currency;
            settings.dailyMicros = limits.dailyMicros;
            settings.monthlyMicros = limits.monthlyMicros;
            settings.zoneId = limits.zoneId;
            settings.updatedAt = clock.nowMillis();
            store.saveSettings(settings);
            return revokeIfExhausted(settings, previous, settings.updatedAt);
        });
        if (exhausted) notifyExhaustion();
    }

    @Override
    public Spend currentSpend() {
        return store.inTransaction(() -> {
            BudgetSettingsEntity settings = store.settings();
            if (settings == null) return null;
            BudgetPeriod period = BudgetPeriod.of(clock.nowMillis(), settings.zoneId);
            return new Spend(period, settings.currency, store.committedForDay(period.dayKey, settings.currency),
                    store.committedForMonth(period.monthKey, settings.currency),
                    store.uncertainForMonth(period.monthKey));
        });
    }

    @Override
    public void addExhaustionListener(ExhaustionListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void removeExhaustionListener(ExhaustionListener listener) {
        listeners.remove(listener);
    }

    /** Runs in the same transaction as the cap or settlement change. */
    private boolean revokeIfExhausted(BudgetSettingsEntity limits, BudgetSettingsEntity previous, long now) {
        if (limits == null) return false;
        BudgetPeriod period = BudgetPeriod.of(now, limits.zoneId);
        long day = store.committedForDay(period.dayKey, limits.currency);
        long month = store.committedForMonth(period.monthKey, limits.currency);
        boolean loweredToDay = previous != null && previous.dailyMicros > limits.dailyMicros && day == limits.dailyMicros;
        boolean loweredToMonth = previous != null && previous.monthlyMicros > limits.monthlyMicros
                && month == limits.monthlyMicros;
        boolean exhausted = day > limits.dailyMicros || month > limits.monthlyMicros || loweredToDay || loweredToMonth;
        if (exhausted) store.releaseQueued(now);
        return exhausted;
    }

    private void notifyExhaustion() {
        for (ExhaustionListener listener : listeners) {
            try {
                listener.onBudgetExhausted();
            } catch (RuntimeException failed) {
                // Notification failure cannot change the result of the committed ledger transaction.
                LOGGER.warning("AI budget exhaustion listener failed");
            }
        }
    }
}
