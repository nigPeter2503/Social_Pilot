package com.socialpilot.app.domain.contract.ai;

import static com.socialpilot.app.domain.contract.BudgetManager.Admission.*;
import static org.junit.Assert.*;

import com.socialpilot.app.domain.contract.BudgetManager;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public final class BudgetContractTest {
    @Test public void costUsesExclusiveCountsAndRoundsUpInMicros() {
        AiCostEstimate estimate = AiCostEstimate.text(4, 2, 3, new BigDecimal("0.15"),
                new BigDecimal("0.04"), new BigDecimal("0.60"), Currency.getInstance("USD"), "fixture");
        assertEquals(3L, estimate.micros);
        assertEquals("USD", estimate.currency.getCurrencyCode());
        assertEquals("fixture", estimate.priceRevision);
    }

    @Test public void simultaneousBranchesShareTheSameReservationLimit() throws Exception {
        FakeLedger ledger = new FakeLedger(100);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<BudgetManager.Admission> a = workers.submit(() -> {
                start.await();
                return ledger.reserve("A", estimate(60));
            });
            Future<BudgetManager.Admission> b = workers.submit(() -> {
                start.await();
                return ledger.reserve("B", estimate(60));
            });
            start.countDown();
            BudgetManager.Admission first = a.get(3, TimeUnit.SECONDS);
            BudgetManager.Admission second = b.get(3, TimeUnit.SECONDS);
            assertTrue((first == RESERVED && second == LIMIT_REACHED)
                    || (first == LIMIT_REACHED && second == RESERVED));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test public void uncertaintyAndFreshRetryCountTowardSameLimit() {
        FakeLedger ledger = new FakeLedger(100);
        assertEquals(RESERVED, ledger.reserve("primary", estimate(60)));
        ledger.markUncertain("primary");
        assertEquals(DUPLICATE_REQUEST, ledger.reserve("primary", estimate(60)));
        assertEquals(LIMIT_REACHED, ledger.reserve("fallback", estimate(60)));
        assertEquals(RESERVED, ledger.reserve("retry", estimate(40)));
        ledger.releaseUndispatched("retry");
        ledger.releaseUndispatched("retry");
        assertEquals(DUPLICATE_REQUEST, ledger.reserve("retry", estimate(40)));
        assertEquals(RESERVED, ledger.reserve("retry-2", estimate(40)));
        ledger.settle("retry-2", estimate(25));
        ledger.settle("retry-2", estimate(1));
        assertEquals(LIMIT_REACHED, ledger.reserve("retry-3", estimate(16)));
        assertEquals(RESERVED, ledger.reserve("retry-4", estimate(15)));
        ledger.settle("primary", estimate(50));
        assertEquals(RESERVED, ledger.reserve("retry-5", estimate(10)));
    }

    @Test public void modelCapabilityAndScopeAreExplicit() {
        ModelCapabilities text = new ModelCapabilities(AiRequest.Provider.OPENAI, "fixture",
                ModelCapabilities.Support.SUPPORTED, ModelCapabilities.Support.SUPPORTED,
                ModelCapabilities.Support.UNKNOWN, ModelCapabilities.Support.UNKNOWN, 64);
        assertTrue(text.allowsTextStream(request(null, null, AiRequest.Task.TEXT, 64)));
        assertFalse(text.allowsTextStream(request(null, null, AiRequest.Task.TEXT, 65)));
        assertFalse(text.allowsTextStream(request(null, null, AiRequest.Task.IMAGE, 32)));
        assertThrows(IllegalArgumentException.class, () -> request("channel", null, AiRequest.Task.TEXT, 32));
    }

    private static AiRequest request(String channel, Long revision, AiRequest.Task task, int limit) {
        return new AiRequest("s", channel, revision, "step", "r", AiRequest.Provider.OPENAI,
                "fixture", task, "prompt", limit);
    }

    private static AiCostEstimate estimate(long micros) {
        return AiCostEstimate.text(micros, 0, 0, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, Currency.getInstance("USD"), "fixture");
    }

    /** In-memory fixture to exercise the contract with no storage or live billing. */
    private static final class FakeLedger implements BudgetManager {
        private enum Status { RESERVED, UNCERTAIN, FINAL }

        private final long limit;
        private final Map<String, Long> amounts = new HashMap<>();
        private final Map<String, Status> statuses = new HashMap<>();
        private long used;

        FakeLedger(long limit) { this.limit = limit; }

        @Override public synchronized Admission reserve(String requestId, AiCostEstimate upperBound) {
            if (amounts.containsKey(requestId)) return DUPLICATE_REQUEST;
            if (upperBound.micros > limit - used) return LIMIT_REACHED;
            amounts.put(requestId, upperBound.micros);
            statuses.put(requestId, Status.RESERVED);
            used += upperBound.micros;
            return RESERVED;
        }

        @Override public synchronized void settle(String requestId, AiCostEstimate estimated) {
            close(requestId, estimated.micros);
        }

        @Override public synchronized void releaseUndispatched(String requestId) {
            if (statuses.get(requestId) == Status.RESERVED) close(requestId, 0);
        }

        @Override public synchronized void markUncertain(String requestId) {
            if (statuses.get(requestId) == Status.RESERVED) statuses.put(requestId, Status.UNCERTAIN);
        }

        private void close(String requestId, long micros) {
            Status status = statuses.get(requestId);
            if (status == null || status == Status.FINAL) return;
            used = used - amounts.get(requestId) + micros;
            statuses.put(requestId, Status.FINAL);
        }
    }
}
