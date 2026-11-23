package com.socialpilot.app.testing;

import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    @Override public synchronized Admission reserve(String requestId, AiCostEstimate upperBound) {
        calls.add("reserve:" + requestId);
        lastReservedMicros = upperBound.micros;
        if (!seen.add(requestId)) return Admission.DUPLICATE_REQUEST;
        return next;
    }

    @Override public Admission reserve(AiRequest request, AiTaskType taskType, AiCostEstimate upperBound) {
        return reserve(request.requestId, upperBound);
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
