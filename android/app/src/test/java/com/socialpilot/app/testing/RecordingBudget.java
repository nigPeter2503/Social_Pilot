package com.socialpilot.app.testing;

import com.socialpilot.app.domain.contract.BudgetManager;
import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Records calls only; admission is scripted. Not a ledger. */
public final class RecordingBudget implements BudgetManager {
    public final List<String> calls = new ArrayList<>();
    public final Set<String> seen = new HashSet<>();
    public Admission next = Admission.RESERVED;
    public long lastReservedMicros = -1;
    public long lastSettledMicros = -1;

    @Override public synchronized Admission reserve(String requestId, AiCostEstimate upperBound) {
        calls.add("reserve:" + requestId);
        lastReservedMicros = upperBound.micros;
        if (!seen.add(requestId)) return Admission.DUPLICATE_REQUEST;
        return next;
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

    public synchronized long count(String prefix) {
        return calls.stream().filter(call -> call.startsWith(prefix)).count();
    }
}
