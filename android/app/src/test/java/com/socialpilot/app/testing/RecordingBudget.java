package com.socialpilot.app.testing;

import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.BudgetPeriod;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records calls only; admission is scripted. Not a ledger: the real one is BudgetLedger. */
public final class RecordingBudget implements AttemptLedger {
    public final List<String> calls = new ArrayList<>();
    public final Set<String> seen = new HashSet<>();
    public final MemoryUsageLog usage = new MemoryUsageLog();
    private final List<ExhaustionListener> listeners = new CopyOnWriteArrayList<>();
    public Admission next = Admission.RESERVED;
    public Limits limits = new Limits("USD", 1_000_000, 10_000_000, "Asia/Bangkok");
    public long lastReservedMicros = -1;
    public long lastSettledMicros = -1;
    /** Clock the fake pins each reservation's period with, like the ledger does inside reserve. */
    public Clock clock = System::currentTimeMillis;
    /** Fault injection: the next complete() throws and stores nothing. */
    public volatile boolean failNextComplete;
    private final Map<String, Pinned> pinned = new HashMap<>();

    public interface Clock {
        long nowMillis();
    }

    private static final class Pinned {
        final AiRequest request;
        final AiTaskType taskType;
        final BudgetPeriod period;
        final AiCostEstimate upperBound;

        Pinned(AiRequest request, AiTaskType taskType, BudgetPeriod period, AiCostEstimate upperBound) {
            this.request = request;
            this.taskType = taskType;
            this.period = period;
            this.upperBound = upperBound;
        }
    }

    @Override public synchronized Admission reserve(String requestId, AiCostEstimate upperBound) {
        calls.add("reserve:" + requestId);
        lastReservedMicros = upperBound.micros;
        if (!seen.add(requestId)) return Admission.DUPLICATE_REQUEST;
        return next;
    }

    @Override public synchronized Admission reserve(AiRequest request, AiTaskType taskType,
                                                    AiCostEstimate upperBound) {
        Admission admission = reserve(request.requestId, upperBound);
        if (admission == Admission.RESERVED) {
            String zone = limits == null ? "UTC" : limits.zoneId;
            pinned.put(request.requestId, new Pinned(request, taskType,
                    BudgetPeriod.of(clock.nowMillis(), zone), upperBound));
        }
        return admission;
    }

    @Override public synchronized UsageRecord complete(String requestId, boolean mayHaveDispatched,
                                                       AiCostEstimate estimate, AiUsage usage) {
        if (failNextComplete) {
            failNextComplete = false;
            throw new IllegalStateException("fixture: ledger write failed");
        }
        Pinned pin = pinned.get(requestId);
        if (pin == null) return null;
        UsageRecord stored = this.usage.records.get(requestId);
        if (stored != null) return stored;
        UsageRecord.Settlement settlement;
        Long micros = null;
        if (!mayHaveDispatched) {
            calls.add("release:" + requestId);
            settlement = UsageRecord.Settlement.RELEASED;
        } else if (estimate != null) {
            calls.add("settle:" + requestId);
            lastSettledMicros = estimate.micros;
            settlement = UsageRecord.Settlement.SETTLED;
            micros = estimate.micros;
        } else {
            calls.add("uncertain:" + requestId);
            settlement = UsageRecord.Settlement.UNCERTAIN;
        }
        AiRequest request = pin.request;
        UsageRecord record = new UsageRecord(requestId, request.sessionId, request.channelId, request.stepId,
                pin.taskType, request.provider, request.model, pin.period, clock.nowMillis(), usage, settlement,
                micros, pin.upperBound.currency.getCurrencyCode(), pin.upperBound.priceRevision);
        this.usage.record(record);
        return record;
    }

    @Override public synchronized void settle(String requestId, AiCostEstimate estimate) {
        calls.add("settle:" + requestId);
        lastSettledMicros = estimate.micros;
    }

    @Override public synchronized void releaseUndispatched(String requestId) {
        calls.add("release:" + requestId);
    }

    @Override public synchronized void markUncertain(String requestId) {
        calls.add("uncertain:" + requestId);
    }

    @Override public boolean record(UsageRecord record) {
        return usage.record(record);
    }

    @Override public List<UsageRecord> forMonth(String monthKey) {
        return usage.forMonth(monthKey);
    }

    @Override public Limits limits() {
        return limits;
    }

    @Override public void setLimits(Limits limits) {
        this.limits = limits;
    }

    @Override public Spend currentSpend() {
        return null;
    }

    @Override public void addExhaustionListener(ExhaustionListener listener) {
        listeners.add(listener);
    }

    @Override public void removeExhaustionListener(ExhaustionListener listener) {
        listeners.remove(listener);
    }

    /** Simulates a commit that left spend above a cap. */
    public void exhaust() {
        for (ExhaustionListener listener : listeners) listener.onBudgetExhausted();
    }

    public synchronized long count(String prefix) {
        return calls.stream().filter(call -> call.startsWith(prefix)).count();
    }
}
