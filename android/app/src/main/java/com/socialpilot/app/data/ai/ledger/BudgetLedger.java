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

/**
 * Atomic, durable AI budget. Each reserve reads committed spend and inserts the reservation in
 * one write transaction, so two branches racing for the last budget cannot both be admitted.
 * The day/month keys and price revision are pinned at reserve and never re-derived.
 * Methods do database I/O; call them from workers.
 */
public final class BudgetLedger implements AttemptLedger {
    public interface Clock {
        long nowMillis();
    }

    /** Session/step label of attempts whose channel identity was dropped by {@link #forgetChannel}. */
    public static final String FORGOTTEN = "forgotten";

    private final LedgerStore store;
    private final Clock clock;
    private final String bootId = UUID.randomUUID().toString();
    private final List<ExhaustionListener> listeners = new CopyOnWriteArrayList<>();

    public BudgetLedger(LedgerStore store, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
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
            row.state = AttemptEntity.RESERVED;
            row.createdAt = now;
            row.updatedAt = now;
            store.insert(row);
            return Admission.RESERVED;
        });
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
        boolean changed = store.inTransaction(() -> {
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
            return true;
        });
        if (changed) notifyIfExhausted();
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
            if (row == null || !AttemptEntity.RESERVED.equals(row.state)) return null;
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
        boolean[] settled = new boolean[1];
        UsageRecord result = store.inTransaction(() -> {
            AttemptEntity row = store.find(requestId);
            if (row == null) return null;
            if (row.usageState != null) return toRecord(row); // Already completed: no second transition.
            boolean held = AttemptEntity.RESERVED.equals(row.state) || AttemptEntity.UNCERTAIN.equals(row.state);
            if (!mayHaveDispatched) {
                if (AttemptEntity.RESERVED.equals(row.state)) row.state = AttemptEntity.RELEASED;
            } else if (estimate != null && held) {
                if (row.currency.equals(estimate.currency.getCurrencyCode())
                        && row.priceRevision.equals(estimate.priceRevision)) {
                    row.state = AttemptEntity.SETTLED;
                    row.settledMicros = estimate.micros;
                    settled[0] = true;
                } else {
                    row.state = AttemptEntity.UNCERTAIN;
                }
            } else if (AttemptEntity.RESERVED.equals(row.state)) {
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
            return toRecord(row);
        });
        if (settled[0]) notifyIfExhausted();
        return result;
    }

    /** Attempts with scope metadata; in-flight and interrupted ones read as UNCERTAIN. */
    @Override
    public List<UsageRecord> forMonth(String monthKey) {
        List<AttemptEntity> rows = store.inTransaction(() -> store.forMonth(monthKey));
        List<UsageRecord> result = new ArrayList<>();
        for (AttemptEntity row : rows) {
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
    public int forgetChannel(String channelId) {
        java.util.Objects.requireNonNull(channelId);
        return store.inTransaction(() -> store.forgetChannel(channelId, clock.nowMillis()));
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
        store.inTransaction(() -> {
            BudgetSettingsEntity settings = new BudgetSettingsEntity();
            settings.currency = limits.currency;
            settings.dailyMicros = limits.dailyMicros;
            settings.monthlyMicros = limits.monthlyMicros;
            settings.zoneId = limits.zoneId;
            settings.updatedAt = clock.nowMillis();
            store.saveSettings(settings);
            return null;
        });
        notifyIfExhausted();
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

    private void notifyIfExhausted() {
        Limits limits = limits();
        Spend spend = currentSpend();
        if (limits == null || spend == null) return;
        // Strict: a fully reserved budget is normal; only spend above a cap (overrun or a lowered cap) stops work.
        if (spend.dayMicros > limits.dailyMicros || spend.monthMicros > limits.monthlyMicros) {
            for (ExhaustionListener listener : listeners) listener.onBudgetExhausted();
        }
    }
}
