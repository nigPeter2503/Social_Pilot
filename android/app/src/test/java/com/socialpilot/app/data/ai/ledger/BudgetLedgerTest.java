package com.socialpilot.app.data.ai.ledger;

import static org.junit.Assert.*;

import com.socialpilot.app.domain.contract.BudgetManager.Admission;
import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.BudgetPeriod;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;

public final class BudgetLedgerTest {
    private static final String REV = "fixture-2026-11-23";
    private static final String BANGKOK = "Asia/Bangkok";
    /** 2026-11-23T16:30:00Z = 23:30 in Bangkok. */
    private static final long NOV_23_2330_BANGKOK = 1795451400000L;
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private MemoryLedgerStore store;
    private long now = NOV_23_2330_BANGKOK;
    private BudgetLedger ledger;

    @Before public void setUp() {
        store = new MemoryLedgerStore();
        ledger = new BudgetLedger(store, () -> now);
        ledger.setLimits(new AttemptLedger.Limits("USD", 100, 1_000, BANGKOK));
    }

    /** Labelled fixture: 1 micro per token at 1.00 USD per million. */
    private static AiCostEstimate usd(long micros) {
        return usd(micros, REV);
    }

    private static AiCostEstimate usd(long micros, String revision) {
        return AiCostEstimate.text(micros, 0, 0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                Currency.getInstance("USD"), revision);
    }

    private static AiRequest request(String requestId, String channel) {
        return new AiRequest("session-1", channel, 2L, "draft", requestId, AiRequest.Provider.OPENAI, "fixture-open",
                AiRequest.Task.TEXT, "prompt", 50);
    }

    private static UsageRecord usage(AiRequest request, AiUsage usage, UsageRecord.Settlement settlement, Long micros) {
        return new UsageRecord(request.requestId, request.sessionId, request.channelId, request.stepId,
                AiTaskType.CONTENT_TEXT, request.provider, request.model, BudgetPeriod.of(0, BANGKOK), 0, usage,
                settlement, micros, "USD", REV);
    }

    @Test public void withoutLimitsNothingIsAdmitted() {
        BudgetLedger fresh = new BudgetLedger(new MemoryLedgerStore(), () -> now);
        assertNull(fresh.limits());
        assertNull(fresh.currentSpend());
        assertEquals(Admission.LIMIT_REACHED, fresh.reserve("a", usd(1)));
    }

    @Test public void twoBranchesRacingForTheLastBudgetAdmitOnlyOne() throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Future<Admission> a = threads.submit(() -> { go.await(); return ledger.reserve(request("a", "facebook"), AiTaskType.CONTENT_TEXT, usd(60)); });
            Future<Admission> b = threads.submit(() -> { go.await(); return ledger.reserve(request("b", "instagram"), AiTaskType.CONTENT_TEXT, usd(60)); });
            go.countDown();
            List<Admission> results = new ArrayList<>();
            results.add(a.get(5, TimeUnit.SECONDS));
            results.add(b.get(5, TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(results, Admission.RESERVED));
            assertEquals(1, Collections.frequency(results, Admission.LIMIT_REACHED));
            assertEquals(60, ledger.currentSpend().dayMicros);
        } finally {
            threads.shutdownNow();
        }
    }

    @Test public void manyConcurrentReservesNeverExceedTheCap() throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            AtomicInteger admitted = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                String id = "r" + i;
                futures.add(threads.submit(() -> {
                    if (ledger.reserve(id, usd(7)) == Admission.RESERVED) admitted.incrementAndGet();
                }));
            }
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
            assertEquals(14, admitted.get()); // floor(100 / 7)
            assertTrue(ledger.currentSpend().dayMicros <= 100);
        } finally {
            threads.shutdownNow();
        }
    }

    @Test public void dailyAndMonthlyCapsBothBlock() {
        assertEquals(Admission.RESERVED, ledger.reserve("a", usd(100)));
        assertEquals(Admission.LIMIT_REACHED, ledger.reserve("b", usd(1))); // Day full.
        ledger.settle("a", usd(100));
        now += 2 * HOUR; // 2026-11-24 01:30 Bangkok: new day, same month.
        assertEquals(Admission.RESERVED, ledger.reserve("c", usd(100)));
        AttemptLedger.Spend spend = ledger.currentSpend();
        assertEquals("2026-11-24", spend.period.dayKey);
        assertEquals(100, spend.dayMicros);
        assertEquals(200, spend.monthMicros);
        now += 7 * 24 * HOUR; // 2026-12-01: new month.
        assertEquals("2026-12", ledger.currentSpend().period.monthKey);
        assertEquals(0, ledger.currentSpend().monthMicros);
    }

    @Test public void monthlyCapBlocksEvenWhenTheDayHasRoom() {
        ledger.setLimits(new AttemptLedger.Limits("USD", 100, 150, BANGKOK));
        assertEquals(Admission.RESERVED, ledger.reserve("a", usd(100)));
        ledger.settle("a", usd(100));
        now += 2 * HOUR; // 2026-11-24, still November.
        assertEquals(Admission.LIMIT_REACHED, ledger.reserve("b", usd(60)));
        assertEquals(Admission.RESERVED, ledger.reserve("c", usd(50)));
    }

    @Test public void retryNeedsANewIdAndAnOldIdStaysRejectedAfterSettle() {
        assertEquals(Admission.RESERVED, ledger.reserve("a", usd(10)));
        assertEquals(Admission.DUPLICATE_REQUEST, ledger.reserve("a", usd(10)));
        ledger.settle("a", usd(4));
        assertEquals(Admission.DUPLICATE_REQUEST, ledger.reserve("a", usd(10)));
        ledger.releaseUndispatched("b-never-reserved");
        assertEquals(Admission.RESERVED, ledger.reserve("a-retry", usd(10)));
        assertEquals(14, ledger.currentSpend().dayMicros);
    }

    @Test public void settleAndReleaseApplyOnce() {
        ledger.reserve("a", usd(30));
        ledger.settle("a", usd(12));
        ledger.settle("a", usd(25)); // Duplicate terminal: ignored.
        ledger.releaseUndispatched("a"); // Already settled: cannot free it.
        assertEquals(12, ledger.currentSpend().dayMicros);
        ledger.reserve("b", usd(30));
        ledger.releaseUndispatched("b");
        ledger.releaseUndispatched("b");
        ledger.settle("b", usd(5)); // A released attempt never becomes charged later.
        assertEquals(12, ledger.currentSpend().dayMicros);
        assertEquals(AttemptEntity.RELEASED, store.raw("b").state);
    }

    @Test public void dispatchedWithoutUsageStaysHeldAsUncertain() {
        AiRequest request = request("a", "facebook");
        ledger.reserve(request, AiTaskType.CONTENT_TEXT, usd(40));
        ledger.markUncertain("a");
        ledger.releaseUndispatched("a"); // Uncertain cannot be freed.
        assertTrue(ledger.record(usage(request, AiUsage.MISSING, UsageRecord.Settlement.UNCERTAIN, null)));
        assertFalse(ledger.record(usage(request, AiUsage.MISSING, UsageRecord.Settlement.UNCERTAIN, null)));
        AttemptLedger.Spend spend = ledger.currentSpend();
        assertEquals(40, spend.dayMicros);
        assertEquals(1, spend.uncertainAttempts);
        UsageRecord row = ledger.forMonth("2026-11").get(0);
        assertEquals(UsageRecord.Settlement.UNCERTAIN, row.settlement);
        assertNull(row.estimateMicros);
        assertEquals("facebook", row.channelId);
        // Late reconciliation with a real report may settle it.
        ledger.settle("a", usd(9));
        assertEquals(9, ledger.currentSpend().dayMicros);
    }

    @Test public void restartKeepsInterruptedReservationsHeldAndVisible() {
        AiRequest request = request("a", "instagram");
        ledger.reserve(request, AiTaskType.CONTENT_TEXT, usd(70));
        assertEquals(0, ledger.recoverInterrupted()); // Own process: untouched.
        BudgetLedger afterRestart = new BudgetLedger(store, () -> now);
        assertEquals(1, afterRestart.recoverInterrupted());
        assertEquals(0, afterRestart.recoverInterrupted());
        assertTrue(store.raw("a").interrupted);
        assertEquals(AttemptEntity.UNCERTAIN, store.raw("a").state);
        assertEquals(Admission.LIMIT_REACHED, afterRestart.reserve("b", usd(40))); // 70 still held.
        assertEquals(Admission.DUPLICATE_REQUEST, afterRestart.reserve("a", usd(1)));
        UsageRecord row = afterRestart.forMonth("2026-11").get(0);
        assertEquals(UsageRecord.Settlement.UNCERTAIN, row.settlement);
        assertEquals(AiUsage.State.MISSING, row.usage.state);
    }

    @Test public void lateTerminalAfterMidnightOrZoneChangeSettlesIntoPinnedPeriod() {
        ledger.reserve("a", usd(80)); // 2026-11-23 23:30 Bangkok.
        now += HOUR; // 2026-11-24 00:30 Bangkok.
        ledger.setLimits(new AttemptLedger.Limits("USD", 100, 1_000, "America/Los_Angeles"));
        ledger.settle("a", usd(60));
        AttemptEntity row = store.raw("a");
        assertEquals("2026-11-23", row.dayKey);
        assertEquals(BANGKOK, row.zoneId);
        assertEquals(AttemptEntity.SETTLED, row.state);
        // Los Angeles is still on 2026-11-23, but the row is keyed to the Bangkok day it was reserved in.
        assertEquals("2026-11-23", ledger.currentSpend().period.dayKey);
        assertEquals(60, ledger.currentSpend().dayMicros);
    }

    @Test public void completeSettlesAndStoresUsageTogetherOnceInThePinnedPeriod() {
        AiRequest request = request("a", "facebook");
        ledger.reserve(request, AiTaskType.CONTENT_TEXT, usd(80)); // 2026-11-23 23:30 Bangkok.
        now += HOUR; // Terminal after local midnight.
        AiUsage reported = new AiUsage(10L, 5L, 15L, 0L, null);
        UsageRecord record = ledger.complete("a", true, usd(15), reported);
        assertEquals(UsageRecord.Settlement.SETTLED, record.settlement);
        assertEquals(Long.valueOf(15), record.estimateMicros);
        assertEquals(AiUsage.State.REPORTED, record.usage.state);
        assertEquals("2026-11-23", record.period.dayKey);
        // A duplicate terminal changes nothing and returns the stored record.
        UsageRecord again = ledger.complete("a", true, usd(60), AiUsage.MISSING);
        assertEquals(Long.valueOf(15), again.estimateMicros);
        assertEquals(AiUsage.State.REPORTED, again.usage.state);
        assertEquals(15, store.raw("a").settledMicros.longValue());
        assertNull(ledger.complete("unknown", true, usd(1), reported));
    }

    @Test public void completeReleasesUndispatchedAndKeepsUnpricedOrMismatchedHeld() {
        ledger.reserve(request("a", "facebook"), AiTaskType.CONTENT_TEXT, usd(20));
        assertEquals(UsageRecord.Settlement.RELEASED, ledger.complete("a", false, null, AiUsage.MISSING).settlement);
        ledger.reserve(request("b", "facebook"), AiTaskType.CONTENT_TEXT, usd(30));
        assertEquals(UsageRecord.Settlement.UNCERTAIN, ledger.complete("b", true, null, AiUsage.MISSING).settlement);
        ledger.reserve(request("c", "facebook"), AiTaskType.CONTENT_TEXT, usd(40));
        UsageRecord mismatch = ledger.complete("c", true, usd(5, "fixture-other"), new AiUsage(5L, 0L, 5L, 0L, null));
        assertEquals(UsageRecord.Settlement.UNCERTAIN, mismatch.settlement);
        assertEquals(70, ledger.currentSpend().dayMicros); // 30 + 40 held, 20 released.
    }

    @Test public void failedCompleteStoresNothingAndRestartHoldsItAsUncertain() {
        AiRequest request = request("a", "facebook");
        ledger.reserve(request, AiTaskType.CONTENT_TEXT, usd(40));
        assertTrue(ledger.claimDispatch("a"));
        store.failNextUpdate = true;
        try {
            ledger.complete("a", true, usd(15), new AiUsage(10L, 5L, 15L, 0L, null));
            fail("the write failure must reach the caller");
        } catch (IllegalStateException expected) {
            // Rolled back: neither the settlement nor the usage was stored.
        }
        AttemptEntity row = store.raw("a");
        assertEquals(AttemptEntity.DISPATCHING, row.state);
        assertNull(row.usageState);
        assertNull(row.settledMicros);
        BudgetLedger afterRestart = new BudgetLedger(store, () -> now);
        assertEquals(1, afterRestart.recoverInterrupted());
        UsageRecord recovered = afterRestart.forMonth("2026-11").get(0);
        assertEquals(UsageRecord.Settlement.UNCERTAIN, recovered.settlement); // Never SETTLED with MISSING usage.
        assertEquals(AiUsage.State.MISSING, recovered.usage.state);
        assertEquals(40, afterRestart.currentSpend().dayMicros);
    }

    @Test public void priceRevisionOrCurrencyMismatchKeepsTheReservationHeld() {
        ledger.reserve("a", usd(50));
        ledger.settle("a", usd(5, "fixture-other"));
        assertEquals(AttemptEntity.UNCERTAIN, store.raw("a").state);
        assertEquals(50, ledger.currentSpend().dayMicros);
        AiCostEstimate euros = AiCostEstimate.text(5, 0, 0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                Currency.getInstance("EUR"), REV);
        assertEquals(Admission.LIMIT_REACHED, ledger.reserve("b", euros)); // Caps are USD only.
    }

    @Test public void overrunOrLoweredCapNotifiesListenersButFullBudgetDoesNot() {
        AtomicInteger stops = new AtomicInteger();
        AttemptLedger.ExhaustionListener listener = stops::incrementAndGet;
        ledger.addExhaustionListener(listener);
        ledger.reserve("a", usd(100));
        ledger.settle("a", usd(100)); // Exactly at the cap: normal.
        assertEquals(0, stops.get());
        ledger.setLimits(new AttemptLedger.Limits("USD", 50, 1_000, BANGKOK)); // Lowered below spend.
        assertEquals(1, stops.get());
        ledger.removeExhaustionListener(listener);
        ledger.setLimits(new AttemptLedger.Limits("USD", 40, 1_000, BANGKOK));
        assertEquals(1, stops.get());
    }

    @Test public void limitsSurviveAndRejectInvalidValues() {
        BudgetLedger reopened = new BudgetLedger(store, () -> now);
        assertEquals(100, reopened.limits().dailyMicros);
        assertEquals(BANGKOK, reopened.limits().zoneId);
        assertThrows(IllegalArgumentException.class, () -> new AttemptLedger.Limits("USD", 0, 10, BANGKOK));
        assertThrows(IllegalArgumentException.class, () -> new AttemptLedger.Limits("USD", 1, 10, "Mars/Base"));
        assertThrows(IllegalArgumentException.class, () -> new AttemptLedger.Limits("US", 1, 10, BANGKOK));
    }

    @Test public void overflowingReservationIsRejected() {
        ledger.setLimits(new AttemptLedger.Limits("USD", Long.MAX_VALUE, Long.MAX_VALUE, BANGKOK));
        assertEquals(Admission.RESERVED, ledger.reserve("a", usd(Long.MAX_VALUE - 1)));
        assertEquals(Admission.LIMIT_REACHED, ledger.reserve("b", usd(10)));
    }

    @Test public void forgettingAChannelKeepsItsSpendButNotItsIdentity() {
        ledger.reserve(request("a", "facebook"), AiTaskType.CONTENT_TEXT, usd(30));
        ledger.reserve(request("b", "instagram"), AiTaskType.CONTENT_TEXT, usd(20));
        ledger.settle("a", usd(25));
        AiRequest newer = new AiRequest("new-session", "facebook", 3L, "draft", "new-revision",
                AiRequest.Provider.OPENAI, "fixture-open", AiRequest.Task.TEXT, "test", 20);
        assertEquals(Admission.RESERVED, ledger.reserve(newer, AiTaskType.CONTENT_TEXT, usd(10)));
        assertEquals(1, ledger.forgetChannel("facebook", 2L));
        assertEquals(0, ledger.forgetChannel("facebook", 2L));
        AttemptEntity row = store.raw("a");
        assertNull(row.channelId);
        assertNull(row.sessionId);
        assertEquals(AttemptEntity.SETTLED, row.state);
        assertEquals(55, ledger.currentSpend().dayMicros); // Caps still see the spend.
        assertEquals(Long.valueOf(3), store.raw("new-revision").connectionRevision);
        assertFalse(ledger.forMonth("2026-11").stream().anyMatch(record -> "a".equals(record.requestId)));
        assertEquals("instagram", store.raw("b").channelId);
        assertEquals(Admission.DUPLICATE_REQUEST, ledger.reserve("a", usd(1))); // ID still burned.
    }

    @Test public void disconnectClosesAdmissionAndDispatchForTheRetiredRevisionOnly() {
        AtomicLong facebook = new AtomicLong(2);
        BudgetLedger scoped = new BudgetLedger(store, () -> now,
                (channel, revision) -> revision == ("facebook".equals(channel) ? facebook.get() : 2));
        assertEquals(Admission.RESERVED,
                scoped.reserve(request("a-queued", "facebook"), AiTaskType.CONTENT_TEXT, usd(30)));
        assertEquals(Admission.RESERVED,
                scoped.reserve(request("b-queued", "instagram"), AiTaskType.CONTENT_TEXT, usd(20)));

        facebook.set(3);
        assertEquals(Admission.STALE_CHANNEL,
                scoped.reserve(request("a-late", "facebook"), AiTaskType.CONTENT_TEXT, usd(10)));
        assertFalse(scoped.claimDispatch("a-queued"));
        assertTrue(scoped.claimDispatch("b-queued"));
        assertEquals(1, scoped.forgetChannel("facebook", 2));
        assertEquals(AttemptEntity.RELEASED, store.raw("a-queued").state);
        assertEquals(20, scoped.currentSpend().dayMicros);
    }

    @Test public void usageReportListsAttemptsAcrossChannelsAndProviders() {
        AiRequest facebook = request("a", "facebook");
        AiRequest instagram = new AiRequest("session-1", "instagram", 4L, "draft", "b", AiRequest.Provider.GEMINI,
                "fixture-gem", AiRequest.Task.TEXT, "prompt", 50);
        ledger.reserve(facebook, AiTaskType.CONTENT_TEXT, usd(20));
        ledger.reserve(instagram, AiTaskType.CONTENT_TEXT, usd(20));
        ledger.reserve("bare", usd(5)); // No scope metadata: counted in spend, not listed.
        AiUsage reported = new AiUsage(10L, 2L, 12L, 0L, null);
        ledger.settle("a", usd(18));
        ledger.record(usage(facebook, reported, UsageRecord.Settlement.SETTLED, 18L));
        List<UsageRecord> rows = ledger.forMonth("2026-11");
        assertEquals(2, rows.size());
        assertEquals(UsageRecord.Settlement.SETTLED, rows.get(0).settlement);
        assertEquals(Long.valueOf(18), rows.get(0).estimateMicros);
        assertEquals(Long.valueOf(12), rows.get(0).usage.totalTokens);
        assertEquals(AiRequest.Provider.GEMINI, rows.get(1).provider);
        assertEquals(UsageRecord.Settlement.UNCERTAIN, rows.get(1).settlement); // In flight reads as uncertain.
        assertEquals(43, ledger.currentSpend().dayMicros);
    }
}
