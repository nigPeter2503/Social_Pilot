package com.socialpilot.app.data.ai.ledger;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.socialpilot.app.domain.contract.BudgetManager.Admission;
import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real Room file database on the device: the guarantees the JVM fake can only imitate. */
@RunWith(AndroidJUnit4.class)
public final class LedgerDatabaseDeviceTest {
    private static final long NOW = 1795451400000L; // 2026-11-23T16:30:00Z
    private Context context;
    private String fileName;
    private final List<LedgerDatabase> open = new ArrayList<>();

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        fileName = "ai_ledger_test_" + UUID.randomUUID() + ".db";
    }

    @After public void tearDown() {
        for (LedgerDatabase database : open) database.close();
        context.deleteDatabase(fileName);
    }

    private LedgerDatabase openDatabase() {
        LedgerDatabase database = LedgerDatabase.open(context, fileName);
        open.add(database);
        return database;
    }

    private static AiCostEstimate usd(long micros) {
        return AiCostEstimate.text(micros, 0, 0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                Currency.getInstance("USD"), "fixture-2026-11-23");
    }

    @Test public void freshInstallHasNoLimitsAndAdmitsNothing() {
        BudgetLedger ledger = new BudgetLedger(openDatabase().store(), () -> NOW);
        assertNull(ledger.limits());
        assertEquals(0, ledger.recoverInterrupted());
        assertEquals(Admission.LIMIT_REACHED, ledger.reserve("a", usd(1)));
        ledger.setLimits(new AttemptLedger.Limits("USD", 100, 1_000, "Asia/Bangkok"));
        assertEquals(Admission.RESERVED, ledger.reserve("a", usd(1)));
    }

    @Test public void loweringCapRevokesQueuedButKeepsAlreadyAuthorizedHoldAcrossLedgers() {
        LedgerDatabase database = openDatabase();
        BudgetLedger workerLedger = new BudgetLedger(database.store(), () -> NOW);
        BudgetLedger settingsLedger = new BudgetLedger(database.store(), () -> NOW);
        settingsLedger.setLimits(new AttemptLedger.Limits("USD", 120, 1_000, "Asia/Bangkok"));
        assertEquals(Admission.RESERVED, workerLedger.reserve(request("claimed"), AiTaskType.CONTENT_TEXT, usd(60)));
        assertTrue(workerLedger.claimDispatch("claimed"));
        assertEquals(Admission.RESERVED, workerLedger.reserve(request("queued"), AiTaskType.CONTENT_TEXT, usd(60)));
        assertEquals(120, workerLedger.currentSpend().dayMicros);

        settingsLedger.setLimits(new AttemptLedger.Limits("USD", 119, 1_000, "Asia/Bangkok"));
        assertFalse(workerLedger.claimDispatch("queued"));
        assertEquals(60, workerLedger.currentSpend().dayMicros);
        assertEquals(UsageRecord.Settlement.RELEASED,
                workerLedger.complete("queued", false, null, AiUsage.MISSING).settlement);
        assertEquals(UsageRecord.Settlement.UNCERTAIN,
                workerLedger.complete("claimed", true, null, AiUsage.MISSING).settlement);
        assertEquals(60, workerLedger.currentSpend().dayMicros);
    }

    @Test public void concurrentReservesFromTwoLedgersNeverPassTheCap() throws Exception {
        LedgerDatabase database = openDatabase();
        BudgetLedger first = new BudgetLedger(database.store(), () -> NOW);
        BudgetLedger second = new BudgetLedger(database.store(), () -> NOW);
        first.setLimits(new AttemptLedger.Limits("USD", 100, 1_000, "Asia/Bangkok"));
        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger admitted = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                BudgetLedger ledger = i % 2 == 0 ? first : second;
                String id = "r" + i;
                futures.add(threads.submit(() -> {
                    go.await();
                    if (ledger.reserve(id, usd(7)) == Admission.RESERVED) admitted.incrementAndGet();
                    return null;
                }));
            }
            // Same id from both ledgers at once: exactly one wins.
            Future<Admission> a = threads.submit(() -> { go.await(); return first.reserve("same", usd(1)); });
            Future<Admission> b = threads.submit(() -> { go.await(); return second.reserve("same", usd(1)); });
            go.countDown();
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
            List<Admission> same = new ArrayList<>();
            same.add(a.get(30, TimeUnit.SECONDS));
            same.add(b.get(30, TimeUnit.SECONDS));
            assertTrue(same.contains(Admission.DUPLICATE_REQUEST));
            long spent = first.currentSpend().dayMicros;
            assertTrue("spent " + spent, spent <= 100);
            assertEquals(spent, admitted.get() * 7L + (same.contains(Admission.RESERVED) ? 1 : 0));
            assertTrue(admitted.get() >= 13);
        } finally {
            threads.shutdownNow();
        }
    }

    /** Real Room store whose next update is written to SQLite and then fails, inside the transaction. */
    private static final class FailingAfterWrite implements LedgerStore {
        private final LedgerStore delegate;
        volatile boolean failNextUpdate;

        FailingAfterWrite(LedgerStore delegate) {
            this.delegate = delegate;
        }

        @Override public <T> T inTransaction(java.util.concurrent.Callable<T> work) { return delegate.inTransaction(work); }
        @Override public AttemptEntity find(String requestId) { return delegate.find(requestId); }
        @Override public void insert(AttemptEntity row) { delegate.insert(row); }
        @Override public void update(AttemptEntity row) {
            delegate.update(row);
            if (failNextUpdate) {
                failNextUpdate = false;
                throw new IllegalStateException("fixture: failure after the row was written");
            }
        }
        @Override public void releaseQueued(long now) { delegate.releaseQueued(now); }
        @Override public long committedForDay(String dayKey, String currency) { return delegate.committedForDay(dayKey, currency); }
        @Override public long committedForMonth(String monthKey, String currency) { return delegate.committedForMonth(monthKey, currency); }
        @Override public int uncertainForMonth(String monthKey) { return delegate.uncertainForMonth(monthKey); }
        @Override public BudgetSettingsEntity settings() { return delegate.settings(); }
        @Override public void saveSettings(BudgetSettingsEntity settings) { delegate.saveSettings(settings); }
        @Override public int markInterrupted(String currentBootId, long now) { return delegate.markInterrupted(currentBootId, now); }
        @Override public int forgetChannel(String channelId, long retiredRevision, long now) {
            return delegate.forgetChannel(channelId, retiredRevision, now);
        }
        @Override public List<AttemptEntity> forMonth(String monthKey) { return delegate.forMonth(monthKey); }
    }

    private static com.socialpilot.app.domain.contract.ai.AiRequest request(String requestId) {
        return new com.socialpilot.app.domain.contract.ai.AiRequest("session-1", "page-1", 2L, "draft", requestId,
                com.socialpilot.app.domain.contract.ai.AiRequest.Provider.OPENAI, "fixture-open",
                com.socialpilot.app.domain.contract.ai.AiRequest.Task.TEXT, "prompt", 50);
    }

    @Test public void failedCompleteRollsBackSettlementAndUsageTogether() {
        FailingAfterWrite store = new FailingAfterWrite(openDatabase().store());
        BudgetLedger ledger = new BudgetLedger(store, () -> NOW);
        ledger.setLimits(new AttemptLedger.Limits("USD", 100, 1_000, "Asia/Bangkok"));
        com.socialpilot.app.domain.contract.ai.AiUsage reported =
                new com.socialpilot.app.domain.contract.ai.AiUsage(10L, 5L, 15L, 0L, null);
        assertEquals(Admission.RESERVED, ledger.reserve(request("a"),
                com.socialpilot.app.domain.contract.ai.AiTaskType.CONTENT_TEXT, usd(40)));
        store.failNextUpdate = true;
        try {
            ledger.complete("a", true, usd(15), reported);
            fail("the write failure must reach the caller");
        } catch (IllegalStateException expected) {
            // Room rolled the transaction back.
        }
        AttemptEntity row = store.inTransaction(() -> store.find("a"));
        assertEquals(AttemptEntity.QUEUED, row.state);
        assertNull(row.usageState);
        assertNull(row.settledMicros);
        assertEquals(40, ledger.currentSpend().dayMicros);
        // The terminal can still be written once, settlement and usage together.
        com.socialpilot.app.domain.contract.ai.UsageRecord record = ledger.complete("a", true, usd(15), reported);
        assertEquals(com.socialpilot.app.domain.contract.ai.UsageRecord.Settlement.SETTLED, record.settlement);
        assertEquals("2026-11-23", record.period.dayKey);
        row = store.inTransaction(() -> store.find("a"));
        assertEquals(AttemptEntity.SETTLED, row.state);
        assertEquals("REPORTED", row.usageState);
        assertEquals(15, ledger.currentSpend().dayMicros);
    }

    @Test public void reopeningAfterAProcessDiedKeepsReservationsHeld() {
        BudgetLedger before = new BudgetLedger(openDatabase().store(), () -> NOW);
        before.setLimits(new AttemptLedger.Limits("USD", 100, 1_000, "Asia/Bangkok"));
        assertEquals(Admission.RESERVED, before.reserve("in-flight", usd(70)));
        open.get(0).close();
        open.clear();
        BudgetLedger after = new BudgetLedger(openDatabase().store(), () -> NOW);
        assertEquals(100, after.limits().dailyMicros);
        assertEquals(1, after.recoverInterrupted());
        AttemptLedger.Spend spend = after.currentSpend();
        assertEquals(70, spend.dayMicros);
        assertEquals(1, spend.uncertainAttempts);
        assertEquals(Admission.LIMIT_REACHED, after.reserve("next", usd(40)));
        assertEquals(Admission.DUPLICATE_REQUEST, after.reserve("in-flight", usd(1)));
    }
}
