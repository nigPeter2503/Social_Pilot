package com.socialpilot.app.data.ai.ledger;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.socialpilot.app.domain.contract.BudgetManager.Admission;
import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
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
