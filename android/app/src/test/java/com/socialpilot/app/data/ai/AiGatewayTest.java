package com.socialpilot.app.data.ai;

import static com.socialpilot.app.domain.contract.ai.ModelCapabilities.Support.*;
import static org.junit.Assert.*;

import com.socialpilot.app.domain.contract.AiProvider;
import com.socialpilot.app.data.ai.ledger.AttemptEntity;
import com.socialpilot.app.data.ai.ledger.BudgetLedger;
import com.socialpilot.app.data.ai.ledger.MemoryLedgerStore;
import com.socialpilot.app.domain.contract.BudgetManager;
import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiEvent;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.ModelCapabilities;
import com.socialpilot.app.domain.contract.ai.ModelEntry;
import com.socialpilot.app.domain.contract.ai.ModelRegistry;
import com.socialpilot.app.domain.contract.ai.PriceSheet;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import com.socialpilot.app.testing.MemoryPreferences;
import com.socialpilot.app.testing.MemoryUsageLog;
import com.socialpilot.app.testing.RecordingBudget;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class AiGatewayTest {
    /** Labelled fixture rates: 1.00 / 0.25 / 4.00 USD per million tokens. */
    private static final PriceSheet PRICE = new PriceSheet(Currency.getInstance("USD"), new BigDecimal("1.00"),
            new BigDecimal("0.25"), new BigDecimal("4.00"), "fixture-2026-11-23", "fixture", "2026-11-23T00:00:00Z");
    private static final long NOV_23_2330_BANGKOK = 1795451400000L; // 2026-11-23T16:30:00Z
    private ModelEntry openAi;
    private ModelEntry gemini;
    private ModelEntry unverified;
    private ModelRegistry registry;
    private ModelSelectionStore selections;
    private MemoryPreferences selectionFile;
    private RecordingBudget budget;
    private MemoryUsageLog usage;
    private final Map<AiRequest.Provider, AiProvider> providers = new EnumMap<>(AiRequest.Provider.class);
    private final EnumSet<AiRequest.Provider> usableKeys = EnumSet.allOf(AiRequest.Provider.class);
    private final List<Runnable> queued = new ArrayList<>();
    private final Executor manual = queued::add;
    private long now = NOV_23_2330_BANGKOK;
    private MockWebServer server;

    @Before public void setUp() throws Exception {
        openAi = entry(AiRequest.Provider.OPENAI, "fixture-open", ModelEntry.Verification.VERIFIED, PRICE);
        gemini = entry(AiRequest.Provider.GEMINI, "fixture-gem", ModelEntry.Verification.VERIFIED, PRICE);
        unverified = entry(AiRequest.Provider.OPENAI, "fixture-unverified", ModelEntry.Verification.UNVERIFIED, PRICE);
        registry = new ModelRegistry("fixture", Arrays.asList(openAi, gemini, unverified));
        selectionFile = new MemoryPreferences();
        selections = new ModelSelectionStore(selectionFile, registry);
        budget = new RecordingBudget();
        budget.clock = () -> now;
        usage = budget.usage;
        server = new MockWebServer();
        server.start();
    }

    @After public void tearDown() throws Exception {
        server.shutdown();
    }

    private static ModelEntry entry(AiRequest.Provider provider, String model, ModelEntry.Verification verification,
                                    PriceSheet price) {
        return new ModelEntry(new ModelCapabilities(provider, model, SUPPORTED, SUPPORTED, UNSUPPORTED, SUPPORTED, 256),
                EnumSet.of(AiTaskType.CONTENT_TEXT, AiTaskType.COMMENT_REPLY), verification, price, "fixture");
    }

    private AiGateway gateway(AttemptLedger ledger, Executor workers) {
        return new AiGateway(selections, providers, usableKeys::contains, ledger, workers, () -> now);
    }

    private static AiGateway.Call call(String step) {
        return new AiGateway.Call("session-1", "channel-a", 3L, step, AiTaskType.CONTENT_TEXT, "Viết bài", 100);
    }

    @Test public void withoutProductionLedgerNothingIsPreparedOrSent() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted provider = new Scripted();
        providers.put(AiRequest.Provider.OPENAI, provider);
        AiGateway gateway = gateway(null, manual);
        assertFalse(gateway.dispatchEnabled());
        AiGateway.Submission submission = gateway.submit(call("draft"), new Collector());
        assertEquals(AiGateway.Rejection.BUDGET_NOT_CONFIGURED, submission.rejection);
        assertEquals(0, provider.prepared.size());
        assertTrue(queued.isEmpty());
        // Preview resolution still works so the pipeline can build typed requests.
        assertNotNull(gateway.resolve(call("draft")).request);
    }

    @Test public void blockedSelectionsAreRejectedBeforeReserve() {
        providers.put(AiRequest.Provider.OPENAI, new Scripted());
        AiGateway gateway = gateway(budget, manual);
        assertEquals(AiGateway.Rejection.NO_MODEL_SELECTED, gateway.submit(call("s"), new Collector()).rejection);
        selections.select(AiTaskType.CONTENT_TEXT, unverified);
        AiGateway.Submission blocked = gateway.submit(call("s"), new Collector());
        assertEquals(AiGateway.Rejection.MODEL_BLOCKED, blocked.rejection);
        assertEquals(ModelEntry.Block.UNVERIFIED, blocked.block);
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        AiGateway.Call tooLong = new AiGateway.Call("s", null, null, "s", AiTaskType.CONTENT_TEXT, "p", 257);
        assertEquals(ModelEntry.Block.OUTPUT_CAP_EXCEEDED, gateway.submit(tooLong, new Collector()).block);
        usableKeys.remove(AiRequest.Provider.OPENAI);
        assertEquals(AiGateway.Rejection.KEY_NOT_USABLE, gateway.submit(call("s"), new Collector()).rejection);
        assertTrue(budget.calls.isEmpty());
    }

    @Test public void completedCallReservesFreshIdSettlesOnceAndPinsReservePeriod() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted provider = new Scripted();
        providers.put(AiRequest.Provider.OPENAI, provider);
        AiGateway gateway = gateway(budget, manual);
        Collector collector = new Collector();
        AiGateway.Submission first = gateway.submit(call("draft"), collector);
        AiGateway.Submission second = gateway.submit(call("draft"), new Collector());
        assertTrue(first.accepted());
        assertNotEquals(first.requestId, second.requestId);
        assertEquals(400 + "Viết bài".getBytes(java.nio.charset.StandardCharsets.UTF_8).length, budget.lastReservedMicros);
        AiRequest request = provider.prepared.get(0);
        assertEquals("channel-a", request.channelId);
        assertEquals(Long.valueOf(3), request.connectionRevision);
        assertEquals("draft", request.stepId);
        assertEquals(100, request.maxOutputTokens);
        now += TimeUnit.HOURS.toMillis(1); // Terminal arrives after local midnight.
        provider.script = Arrays.asList("Xin ", "chào");
        provider.usage = new AiUsage(100L, 20L, 120L, 0L, null);
        queued.get(0).run();
        provider.finishAgain(request); // Duplicate terminal must not settle twice.
        assertEquals(1, budget.count("settle:" + first.requestId));
        UsageRecord record = usage.records.get(first.requestId);
        assertEquals(UsageRecord.Settlement.SETTLED, record.settlement);
        assertEquals(Long.valueOf(180), record.estimateMicros); // 100 * 1.00 + 20 * 4.00
        assertEquals("2026-11-23", record.period.dayKey);
        assertEquals("fixture-2026-11-23", record.priceRevision);
        assertEquals("Xin chào", collector.finished.get().text);
        assertEquals(AiEvent.Type.COMPLETED, collector.finished.get().terminal.type);
    }

    @Test public void dispatchedCallWithMissingOrPartialUsageStaysUncertain() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted provider = new Scripted();
        providers.put(AiRequest.Provider.OPENAI, provider);
        AiGateway gateway = gateway(budget, manual);
        AiGateway.Submission missing = gateway.submit(call("a"), new Collector());
        provider.script = Arrays.asList("partial");
        provider.failAfterText = true;
        queued.get(0).run();
        assertEquals(1, budget.count("uncertain:" + missing.requestId));
        assertNull(usage.records.get(missing.requestId).estimateMicros);
        assertEquals(AiUsage.State.MISSING, usage.records.get(missing.requestId).usage.state);
        AiGateway.Submission partial = gateway.submit(call("b"), new Collector());
        provider.usage = new AiUsage(100L, 20L, 120L, null, null).asPartial();
        queued.get(1).run();
        assertEquals(UsageRecord.Settlement.UNCERTAIN, usage.records.get(partial.requestId).settlement);
    }

    @Test public void cancelBeforeWorkerRunsReleasesExactlyOnce() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted provider = new Scripted();
        providers.put(AiRequest.Provider.OPENAI, provider);
        AiGateway gateway = gateway(budget, manual);
        Collector collector = new Collector();
        AiGateway.Submission submission = gateway.submit(call("a"), collector);
        submission.cancel();
        submission.cancel();
        assertEquals(0, budget.count("release:")); // Nothing is finalized on the cancelling thread.
        queued.get(0).run(); // The queued worker job finalizes it without starting the attempt.
        assertEquals(1, budget.count("release:"));
        assertEquals(0, provider.runs);
        assertEquals(UsageRecord.Settlement.RELEASED, usage.records.get(submission.requestId).settlement);
        assertEquals(AiEvent.Type.CANCELLED, collector.finished.get().terminal.type);
    }

    /** Refuses every call made on {@link #forbidden}, like Room on the main thread. */
    private static final class ThreadGuard {
        volatile Thread forbidden;

        void check() {
            if (Thread.currentThread() == forbidden) {
                throw new IllegalStateException("fixture: storage access on the UI thread");
            }
        }
    }

    @Test public void cancelFromTheUiThreadBeforeStartFinalizesOnTheWorkerExactlyOnce() throws Exception {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted provider = new Scripted();
        providers.put(AiRequest.Provider.OPENAI, provider);
        ThreadGuard guard = new ThreadGuard();
        HookedLedger guarded = new HookedLedger(budget);
        guarded.guard = guard;
        AiGateway gateway = gateway(guarded, manual);
        java.util.concurrent.atomic.AtomicInteger finishes = new java.util.concurrent.atomic.AtomicInteger();
        AtomicReference<Thread> finishedOn = new AtomicReference<>();
        Collector collector = new Collector() {
            @Override public void onFinished(AiGateway.Finished result) {
                finishes.incrementAndGet();
                finishedOn.set(Thread.currentThread());
                super.onFinished(result);
            }
        };
        AiGateway.Submission submission = gateway.submit(call("a"), collector); // Submitted from a worker in the app.
        guard.forbidden = Thread.currentThread(); // From here on this thread acts as the UI thread.
        submission.cancel(); // Must neither throw nor touch storage here.
        submission.cancel();
        assertEquals(0, budget.count("release:"));
        assertNull(collector.finished.get());
        Thread worker = new Thread(queued.get(0), "ai-worker");
        worker.start();
        worker.join(5_000);
        assertEquals(0, provider.runs); // The provider is never called.
        assertEquals(1, budget.count("release:"));
        assertEquals(1, finishes.get());
        assertSame(worker, finishedOn.get());
        assertEquals(AiEvent.Type.CANCELLED, collector.finished.get().terminal.type);
        assertFalse(collector.finished.get().terminal.mayHaveDispatched);
        assertEquals(UsageRecord.Settlement.RELEASED, usage.records.get(submission.requestId).settlement);
    }

    @Test public void budgetLimitAndDuplicateAdmissionNeverDispatch() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted provider = new Scripted();
        providers.put(AiRequest.Provider.OPENAI, provider);
        budget.next = BudgetManager.Admission.LIMIT_REACHED;
        AiGateway.Submission limited = gateway(budget, manual).submit(call("a"), new Collector());
        assertEquals(AiGateway.Rejection.BUDGET_LIMIT_REACHED, limited.rejection);
        assertTrue(provider.prepared.isEmpty());
        assertTrue(queued.isEmpty());
    }

    @Test public void withoutCapsNothingIsReservedOrPrepared() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted provider = new Scripted();
        providers.put(AiRequest.Provider.OPENAI, provider);
        budget.limits = null;
        AiGateway.Submission submission = gateway(budget, manual).submit(call("a"), new Collector());
        assertEquals(AiGateway.Rejection.BUDGET_NOT_SET, submission.rejection);
        assertEquals(0, budget.count("reserve:"));
        assertTrue(provider.prepared.isEmpty());
    }

    @Test public void exhaustionCancelsPendingAndRunningCallsKeepingTheirResults() throws Exception {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Hanging provider = new Hanging();
        providers.put(AiRequest.Provider.OPENAI, provider);
        ExecutorService threads = Executors.newSingleThreadExecutor();
        // The first job runs at once; later jobs wait here until released, so the second call is
        // certainly still queued when the budget stops. (A single-thread pool alone races: its
        // worker may start the second job before exhaust() reaches it, which is then UNCERTAIN.)
        List<Runnable> held = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean firstJob = new java.util.concurrent.atomic.AtomicBoolean(true);
        Executor workers = command -> {
            if (firstJob.getAndSet(false)) threads.execute(command);
            else held.add(command);
        };
        try {
            AiGateway gateway = gateway(budget, workers);
            Collector running = new Collector();
            AiGateway.Submission first = gateway.submit(call("a"), running);
            assertTrue(provider.started.await(5, TimeUnit.SECONDS));
            Collector pending = new Collector();
            AiGateway.Submission second = gateway.submit(call("b"), pending); // Queued behind the first.
            budget.exhaust();
            assertTrue(running.done.await(5, TimeUnit.SECONDS));
            assertNull(pending.finished.get()); // Finalized by its own worker job, not by exhaust().
            assertEquals(1, held.size());
            threads.execute(held.get(0)); // The worker now reaches the queued job.
            assertTrue(pending.done.await(5, TimeUnit.SECONDS));
            assertTrue(running.finished.get().budgetStopped);
            assertEquals("partial", running.finished.get().text);
            assertEquals(1, budget.count("uncertain:" + first.requestId)); // It may have been charged.
            assertTrue(pending.finished.get().budgetStopped);
            assertEquals(1, budget.count("release:" + second.requestId)); // Never sent.
            budget.exhaust(); // Nothing left to stop; nothing settles twice.
            assertEquals(1, budget.count("uncertain:"));
            assertEquals(1, budget.count("release:"));
        } finally {
            threads.shutdownNow();
        }
    }

    @Test public void fullWorkerReleasesReservationWithoutCallback() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        providers.put(AiRequest.Provider.OPENAI, new Scripted());
        Collector collector = new Collector();
        AiGateway.Submission submission = gateway(budget, command -> {
            throw new java.util.concurrent.RejectedExecutionException();
        }).submit(call("a"), collector);
        assertEquals(AiGateway.Rejection.WORKER_UNAVAILABLE, submission.rejection);
        assertEquals(1, budget.count("release:"));
        assertNull(collector.finished.get());
    }

    private static final String BANGKOK = "Asia/Bangkok";

    /** Real ledger; after an admitted reserve it runs a hook, like another thread changing the caps. */
    private static final class HookedLedger implements AttemptLedger {
        final AttemptLedger delegate;
        Runnable afterReserve;
        /** Optional: storage calls on the guarded thread throw, like Room on the main thread. */
        ThreadGuard guard;
        int completes;

        HookedLedger(AttemptLedger delegate) {
            this.delegate = delegate;
        }

        private void io() {
            if (guard != null) guard.check();
        }

        @Override public Admission reserve(AiRequest request, AiTaskType taskType, AiCostEstimate upperBound) {
            io();
            Admission admission = delegate.reserve(request, taskType, upperBound);
            if (admission == Admission.RESERVED && afterReserve != null) afterReserve.run();
            return admission;
        }
        @Override public Admission reserve(String requestId, AiCostEstimate upperBound) {
            io();
            return delegate.reserve(requestId, upperBound);
        }
        @Override public UsageRecord complete(String requestId, boolean mayHaveDispatched, AiCostEstimate estimate,
                                              AiUsage usage) {
            io();
            completes++;
            return delegate.complete(requestId, mayHaveDispatched, estimate, usage);
        }
        @Override public void settle(String requestId, AiCostEstimate estimate) { io(); delegate.settle(requestId, estimate); }
        @Override public void releaseUndispatched(String requestId) { io(); delegate.releaseUndispatched(requestId); }
        @Override public void markUncertain(String requestId) { io(); delegate.markUncertain(requestId); }
        @Override public boolean record(UsageRecord record) { io(); return delegate.record(record); }
        @Override public List<UsageRecord> forMonth(String monthKey) { io(); return delegate.forMonth(monthKey); }
        @Override public Limits limits() { io(); return delegate.limits(); }
        @Override public void setLimits(Limits limits) { io(); delegate.setLimits(limits); }
        @Override public Spend currentSpend() { io(); return delegate.currentSpend(); }
        @Override public void addExhaustionListener(ExhaustionListener listener) {
            delegate.addExhaustionListener(listener);
        }
        @Override public void removeExhaustionListener(ExhaustionListener listener) {
            delegate.removeExhaustionListener(listener);
        }
    }

    @Test public void capLoweredBetweenReserveAndDispatchNeverSendsAndReleasesOnce() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted provider = new Scripted();
        providers.put(AiRequest.Provider.OPENAI, provider);
        MemoryLedgerStore store = new MemoryLedgerStore();
        HookedLedger ledger = new HookedLedger(new BudgetLedger(store, () -> now));
        ledger.setLimits(new AttemptLedger.Limits("USD", 1_000, 10_000, BANGKOK));
        // The reservation (411 micros) commits, then the cap drops to 50 before the run is armed.
        ledger.afterReserve = () -> ledger.setLimits(new AttemptLedger.Limits("USD", 50, 10_000, BANGKOK));
        Collector collector = new Collector();
        AiGateway.Submission submission = gateway(ledger, manual).submit(call("draft"), collector);
        assertEquals(AiGateway.Rejection.BUDGET_LIMIT_REACHED, submission.rejection);
        assertNotNull(submission.requestId);
        assertTrue(provider.prepared.isEmpty());
        assertTrue(queued.isEmpty());
        assertNull(collector.finished.get()); // A rejected submit never also calls back.
        assertEquals(1, ledger.completes);
        assertEquals(AttemptEntity.RELEASED, store.raw(submission.requestId).state);
        assertEquals(0, ledger.currentSpend().dayMicros);
    }

    @Test public void ledgerWriteFailureStillEndsTheCallClearlyAndStaysHeld() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted provider = new Scripted();
        provider.usage = new AiUsage(10L, 5L, 15L, 0L, null);
        providers.put(AiRequest.Provider.OPENAI, provider);
        MemoryLedgerStore store = new MemoryLedgerStore();
        BudgetLedger ledger = new BudgetLedger(store, () -> now);
        ledger.setLimits(new AttemptLedger.Limits("USD", 1_000, 10_000, BANGKOK));
        Collector collector = new Collector();
        AiGateway.Submission submission = gateway(ledger, manual).submit(call("draft"), collector);
        store.failNextUpdate = true; // The terminal write fails after the provider answered.
        queued.get(0).run();
        AiGateway.Finished finished = collector.finished.get();
        assertNotNull(finished);
        assertTrue(finished.ledgerWriteFailed);
        assertNull(finished.usage);
        assertEquals(AiEvent.Type.COMPLETED, finished.terminal.type);
        AttemptEntity row = store.raw(submission.requestId);
        assertEquals(AttemptEntity.RESERVED, row.state); // Settlement and usage were rolled back together.
        assertNull(row.usageState);
        BudgetLedger afterRestart = new BudgetLedger(store, () -> now);
        assertEquals(1, afterRestart.recoverInterrupted());
        UsageRecord recovered = afterRestart.forMonth("2026-11").get(0);
        assertEquals(UsageRecord.Settlement.UNCERTAIN, recovered.settlement);
        assertEquals(AiUsage.State.MISSING, recovered.usage.state);
        assertEquals(row.reservedMicros, afterRestart.currentSpend().dayMicros); // Still held, never free.
    }

    @Test public void callbackUsesThePeriodTheLedgerPinnedAcrossMidnight() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted provider = new Scripted();
        provider.usage = new AiUsage(10L, 5L, 15L, 0L, null);
        providers.put(AiRequest.Provider.OPENAI, provider);
        now = NOV_23_2330_BANGKOK + TimeUnit.MINUTES.toMillis(30) - 1; // Gateway: 23:59:59.999 on 23/11.
        long[] ledgerNow = {now + 2}; // Ledger reserves at 00:00:00.001 on 24/11.
        BudgetLedger ledger = new BudgetLedger(new MemoryLedgerStore(), () -> ledgerNow[0]);
        ledger.setLimits(new AttemptLedger.Limits("USD", 1_000, 10_000, BANGKOK));
        Collector collector = new Collector();
        gateway(ledger, manual).submit(call("draft"), collector);
        queued.get(0).run();
        UsageRecord reported = collector.finished.get().usage;
        assertEquals("2026-11-24", reported.period.dayKey);
        UsageRecord stored = ledger.forMonth("2026-11").get(0);
        assertEquals(stored.period.dayKey, reported.period.dayKey);
        assertEquals(stored.period.monthKey, reported.period.monthKey);
        assertEquals(UsageRecord.Settlement.SETTLED, reported.settlement);
    }

    @Test public void switchingProviderKeepsRecordedUsageAndUsesNewProvider() {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        Scripted open = new Scripted();
        Scripted gem = new Scripted();
        providers.put(AiRequest.Provider.OPENAI, open);
        providers.put(AiRequest.Provider.GEMINI, gem);
        AiGateway gateway = gateway(budget, manual);
        AiGateway.Submission first = gateway.submit(call("a"), new Collector());
        queued.get(0).run();
        selections.select(AiTaskType.CONTENT_TEXT, gemini);
        assertSame(gemini, new ModelSelectionStore(selectionFile.reboot(), registry).selected(AiTaskType.CONTENT_TEXT));
        gateway.submit(call("b"), new Collector());
        queued.get(1).run();
        assertEquals(1, open.runs);
        assertEquals(1, gem.runs);
        assertEquals(2, usage.records.size());
        assertTrue(usage.records.containsKey(first.requestId));
    }

    @Test public void selectionStoreRejectsForeignEntriesAndForgetsRemovedModels() {
        ModelEntry foreign = entry(AiRequest.Provider.OPENAI, "fixture-open", ModelEntry.Verification.VERIFIED, PRICE);
        assertThrows(IllegalArgumentException.class, () -> selections.select(AiTaskType.CONTENT_TEXT, foreign));
        assertThrows(IllegalArgumentException.class, () -> selections.select(AiTaskType.ANALYTICS_REPORT, openAi));
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        ModelRegistry shrunk = new ModelRegistry("next", Arrays.asList(gemini));
        assertNull(new ModelSelectionStore(selectionFile, shrunk).selected(AiTaskType.CONTENT_TEXT));
        selections.select(AiTaskType.CONTENT_TEXT, null);
        assertNull(selections.selected(AiTaskType.CONTENT_TEXT));
    }

    @Test public void realOpenAiAdapterThroughGatewayUsesHeaderAndRecordsReportedUsage() throws Exception {
        selections.select(AiTaskType.CONTENT_TEXT, openAi);
        providers.put(AiRequest.Provider.OPENAI, new OpenAiProvider(server.url("/v1/responses"),
                () -> "fake-openai-key-for-fixture", registry.capabilities(AiRequest.Provider.OPENAI), 5_000));
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"m\",\"content_index\":0,\"delta\":\"Xin chào\"}\n\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"usage\":"
                + "{\"input_tokens\":12,\"output_tokens\":3,\"total_tokens\":15}}}\n\n"));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Collector collector = new Collector();
            AiGateway.Submission submission = gateway(budget, worker).submit(call("draft"), collector);
            assertTrue(collector.done.await(5, TimeUnit.SECONDS));
            RecordedRequest sent = server.takeRequest();
            assertEquals("Bearer fake-openai-key-for-fixture", sent.getHeader("Authorization"));
            assertTrue(sent.getBody().readUtf8().contains("\"max_output_tokens\":100"));
            UsageRecord record = usage.records.get(submission.requestId);
            assertEquals(AiUsage.State.REPORTED, record.usage.state);
            assertEquals(Long.valueOf(24), record.estimateMicros); // 12 * 1.00 + 3 * 4.00
            assertEquals("Xin chào", collector.finished.get().text);
        } finally {
            worker.shutdownNow();
        }
    }

    @Test public void registryAssetParsesAndEveryCandidateIsBlocked() throws Exception {
        String json = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/main/assets/ai_model_registry.json")), java.nio.charset.StandardCharsets.UTF_8);
        ModelRegistry asset = ModelRegistryParser.parse(json);
        assertFalse(asset.entries.isEmpty());
        for (ModelEntry entry : asset.entries) {
            assertNotEquals(entry.model(), ModelEntry.Block.NONE, entry.blockFor(AiTaskType.CONTENT_TEXT, 1));
            assertNull("Prices must come from a sourced sheet, not memory", entry.price);
        }
        assertTrue(ModelRegistryParser.parseOrEmpty("{broken").entries.isEmpty());
    }

    /** Fake provider emitting a scripted stream on run(). */
    private static final class Scripted implements AiProvider {
        final List<AiRequest> prepared = new ArrayList<>();
        List<String> script = Arrays.asList("ok");
        AiUsage usage = AiUsage.MISSING;
        boolean failAfterText;
        int runs;
        private Listener last;

        @Override public Attempt prepare(AiRequest request, Listener listener) {
            prepared.add(request);
            last = listener;
            return new Attempt() {
                private volatile boolean cancelled;
                @Override public void cancel() { cancelled = true; }
                @Override public void run() {
                    runs++;
                    long sequence = 0;
                    if (cancelled) {
                        listener.onEvent(AiEvent.terminal(request, ++sequence, AiEvent.Type.CANCELLED, null,
                                AiUsage.MISSING, false, false));
                        return;
                    }
                    listener.onEvent(AiEvent.started(request, ++sequence));
                    for (String part : script) listener.onEvent(AiEvent.text(request, ++sequence, part));
                    if (failAfterText) {
                        listener.onEvent(AiEvent.terminal(request, ++sequence, AiEvent.Type.FAILED,
                                new com.socialpilot.app.domain.contract.ai.AiError(
                                        com.socialpilot.app.domain.contract.ai.AiError.Code.INCOMPLETE, null),
                                usage, true, true));
                    } else {
                        listener.onEvent(AiEvent.terminal(request, ++sequence, AiEvent.Type.COMPLETED, null,
                                usage, false, true));
                    }
                }
            };
        }

        void finishAgain(AiRequest request) {
            last.onEvent(AiEvent.terminal(request, 99, AiEvent.Type.COMPLETED, null, usage, false, true));
        }
    }

    /** Sends text, then waits until cancelled, like a stream the budget stops mid-flight. */
    private static final class Hanging implements AiProvider {
        final CountDownLatch started = new CountDownLatch(1);

        @Override public Attempt prepare(AiRequest request, Listener listener) {
            CountDownLatch cancelled = new CountDownLatch(1);
            return new Attempt() {
                @Override public void cancel() { cancelled.countDown(); }
                @Override public void run() {
                    listener.onEvent(AiEvent.started(request, 1));
                    listener.onEvent(AiEvent.text(request, 2, "partial"));
                    started.countDown();
                    try {
                        cancelled.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    listener.onEvent(AiEvent.terminal(request, 3, AiEvent.Type.CANCELLED, null,
                            AiUsage.MISSING, false, true));
                }
            };
        }
    }

    private static class Collector implements AiGateway.Callback {
        final List<AiEvent> events = new ArrayList<>();
        final AtomicReference<AiGateway.Finished> finished = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);

        @Override public void onEvent(AiEvent event) { events.add(event); }

        @Override public void onFinished(AiGateway.Finished result) {
            finished.set(result);
            done.countDown();
        }
    }
}
