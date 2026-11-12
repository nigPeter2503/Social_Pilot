package com.socialpilot.app.domain.ai;

import com.socialpilot.app.domain.contract.BudgetManager;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class DualStreamTestSupport {
    public static final DualStreamCoordinator.Pricing PRICING = new DualStreamCoordinator.Pricing() {
        @Override public AiCostEstimate upperBound(AiRequest request) { return estimate(); }

        @Override public AiCostEstimate fromUsage(AiRequest request, AiUsage usage) { return estimate(); }
    };

    private DualStreamTestSupport() { }

    private static AiCostEstimate estimate() {
        return AiCostEstimate.text(1, 0, 0, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                Currency.getInstance("USD"), "fixture");
    }

    public static AiRequest request(String channel, String requestId, String prompt) {
        return new AiRequest("session", channel, 1L, "text", requestId, AiRequest.Provider.OPENAI,
                "fixture-model", AiRequest.Task.TEXT, prompt, 32);
    }

    public static DualStreamCoordinator.Snapshot await(
            DualStreamCoordinator coordinator, Predicate<DualStreamCoordinator.Snapshot> condition) throws Exception {
        CompletableFuture<DualStreamCoordinator.Snapshot> result = new CompletableFuture<>();
        Cancelable subscription = coordinator.observe(Runnable::run, snapshot -> {
            if (condition.test(snapshot)) result.complete(snapshot);
        });
        try {
            return result.get(8, TimeUnit.SECONDS);
        } finally {
            subscription.cancel();
        }
    }

    public static class Ledger implements BudgetManager {
        private final Map<String, String> state = new HashMap<>();
        private final Map<String, Integer> settlements = new HashMap<>();
        private final String blocked;

        public Ledger() { this(null); }

        public Ledger(String blocked) { this.blocked = blocked; }

        @Override public synchronized Admission reserve(String id, AiCostEstimate limit) {
            if (id.equals(blocked)) return Admission.LIMIT_REACHED;
            if (state.containsKey(id)) return Admission.DUPLICATE_REQUEST;
            state.put(id, "RESERVED");
            return Admission.RESERVED;
        }

        @Override public synchronized void settle(String id, AiCostEstimate estimate) { finish(id, "SETTLED"); }

        @Override public synchronized void releaseUndispatched(String id) { finish(id, "RELEASED"); }

        @Override public synchronized void markUncertain(String id) { finish(id, "UNCERTAIN"); }

        public synchronized String state(String id) { return state.get(id); }

        public synchronized int settlements(String id) { return settlements.getOrDefault(id, 0); }

        private void finish(String id, String result) {
            state.put(id, result);
            settlements.put(id, settlements.getOrDefault(id, 0) + 1);
        }
    }
}
