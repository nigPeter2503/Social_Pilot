package com.socialpilot.app.data.ai.pipeline;

import static com.socialpilot.app.domain.contract.ai.ModelCapabilities.Support.*;
import static org.junit.Assert.*;

import com.socialpilot.app.data.ai.AiGateway;
import com.socialpilot.app.data.ai.ModelSelectionStore;
import com.socialpilot.app.domain.agent.ChannelBranch;
import com.socialpilot.app.domain.agent.ChannelPlatform;
import com.socialpilot.app.domain.agent.ChannelTarget;
import com.socialpilot.app.domain.agent.PipelineInput;
import com.socialpilot.app.domain.agent.PipelineSnapshot;
import com.socialpilot.app.domain.agent.PipelineStage;
import com.socialpilot.app.domain.agent.RuleCheck;
import com.socialpilot.app.domain.agent.StageResult;
import com.socialpilot.app.domain.agent.TextStageOutput;
import com.socialpilot.app.domain.contract.AiProvider;
import com.socialpilot.app.domain.contract.BudgetManager;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiEvent;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.ModelCapabilities;
import com.socialpilot.app.domain.contract.ai.ModelEntry;
import com.socialpilot.app.domain.contract.ai.ModelRegistry;
import com.socialpilot.app.domain.contract.ai.PriceSheet;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import com.socialpilot.app.testing.MemoryPreferences;
import com.socialpilot.app.testing.RecordingBudget;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Fake providers (labelled fixtures) through the real gateway and a recording budget. */
public final class ChannelPipelineTest {
    private static final PriceSheet PRICE = new PriceSheet(Currency.getInstance("USD"), new BigDecimal("1.00"),
            new BigDecimal("0.25"), new BigDecimal("4.00"), "fixture-2026-11-23", "fixture", "2026-11-23T00:00:00Z");
    private static final ChannelTarget FACEBOOK = new ChannelTarget("page-1", ChannelPlatform.FACEBOOK, 3);
    private static final ChannelTarget INSTAGRAM = new ChannelTarget("ig-1", ChannelPlatform.INSTAGRAM, 5);
    private final Executor inline = Runnable::run;
    private final AtomicLong clock = new AtomicLong(1_000);
    private final List<ExecutorService> pools = new ArrayList<>();
    private RecordingBudget budget;
    private Scripted provider;
    private ModelSelectionStore selections;

    @Before public void setUp() {
        ModelEntry entry = new ModelEntry(new ModelCapabilities(AiRequest.Provider.OPENAI, "fixture-open",
                SUPPORTED, SUPPORTED, UNSUPPORTED, SUPPORTED, 4096), EnumSet.of(AiTaskType.CONTENT_TEXT),
                ModelEntry.Verification.VERIFIED, PRICE, "fixture");
        selections = new ModelSelectionStore(new MemoryPreferences(), new ModelRegistry("fixture", Arrays.asList(entry)));
        selections.select(AiTaskType.CONTENT_TEXT, entry);
        budget = new RecordingBudget();
        provider = new Scripted();
    }

    @After public void tearDown() {
        for (ExecutorService pool : pools) pool.shutdownNow();
    }

    private ExecutorService pool() {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        pools.add(pool);
        return pool;
    }

    private ChannelPipeline pipeline(Executor workers) {
        return pipeline(workers, p -> true);
    }

    private ChannelPipeline pipeline(Executor workers, AiGateway.KeyGate keys) {
        Map<AiRequest.Provider, AiProvider> providers = new EnumMap<>(AiRequest.Provider.class);
        providers.put(AiRequest.Provider.OPENAI, provider);
        AiGateway gateway = new AiGateway(selections, providers, keys, budget, workers, clock::get);
        return new ChannelPipeline(gateway, workers, clock::incrementAndGet);
    }

    private static PipelineInput input(ChannelTarget... targets) {
        return new PipelineInput("brief-1", "Ra mắt quán cà phê", "Tăng lượt ghé", "Sinh viên", "Thân thiện", "vi",
                Collections.singletonList(new PipelineInput.Source(true, "https://example.com/menu")),
                Arrays.asList(targets));
    }

    private static final class Recorder implements ChannelPipeline.Observer {
        final List<PipelineSnapshot> snapshots = new CopyOnWriteArrayList<>();
        final CountDownLatch settled = new CountDownLatch(1);

        @Override public void onSnapshot(PipelineSnapshot snapshot) {
            if (!snapshots.isEmpty() && snapshot.revision <= snapshots.get(snapshots.size() - 1).revision) {
                throw new AssertionError("revision went backwards");
            }
            snapshots.add(snapshot);
            if (snapshot.settled()) settled.countDown();
        }

        PipelineSnapshot last() {
            return snapshots.get(snapshots.size() - 1);
        }
    }

    @Test public void singleTargetRunsOneBranchWithOneAttemptPerTextStage() {
        Recorder recorder = new Recorder();
        ChannelPipeline.Session session = pipeline(inline).start(input(FACEBOOK), recorder);
        PipelineSnapshot done = session.snapshot();
        assertEquals(1, done.branches.size());
        ChannelBranch branch = done.branch("page-1");
        assertEquals(ChannelBranch.Status.TEXT_READY, branch.status);
        assertEquals(3, provider.prepared.size());
        assertEquals(3, budget.count("reserve:"));
        Set<String> ids = new HashSet<>();
        List<String> steps = new ArrayList<>();
        for (AiRequest request : provider.prepared) {
            assertEquals("page-1", request.channelId);
            assertEquals(Long.valueOf(3), request.connectionRevision);
            assertEquals(session.sessionId, request.sessionId);
            ids.add(request.requestId);
            steps.add(request.stepId);
        }
        assertEquals(3, ids.size());
        assertEquals(Arrays.asList("outline", "draft", "rule-check"), steps);
        assertTrue(provider.prepared.get(1).prompt.contains("outline for page-1"));
        assertTrue(provider.prepared.get(2).prompt.contains("post for page-1"));
        for (PipelineStage stage : PipelineStage.values()) {
            StageResult result = branch.stage(stage);
            assertEquals(stage.textStage ? StageResult.Status.COMPLETED : StageResult.Status.NOT_IMPLEMENTED, result.status);
        }
        assertEquals(RuleCheck.Verdict.PASS, branch.ruleCheck.verdict);
        assertEquals(UsageRecord.Settlement.SETTLED, branch.stage(PipelineStage.DRAFT).settlement);
        TextStageOutput output = TextStageOutput.of(done, "page-1", "vi");
        assertNotNull(output);
        assertFalse(output.fullChainComplete);
        assertEquals("post for page-1", output.draft);
        assertEquals(provider.prepared.get(1).requestId, output.requestIds.get(PipelineStage.DRAFT));
        assertNull(TextStageOutput.of(done, "ig-1", "vi")); // No second channel was dispatched.
    }

    @Test public void observerFailureDuringOutlineDoesNotPreventSubmission() {
        Recorder recorder = new Recorder();
        ChannelPipeline.Session session = pipeline(inline).start(input(FACEBOOK), snapshot -> {
            if (snapshot.branch("page-1").stage(PipelineStage.OUTLINE).status == StageResult.Status.RUNNING) {
                throw new IllegalStateException("observer failed");
            }
            recorder.onSnapshot(snapshot);
        });
        assertEquals(ChannelBranch.Status.TEXT_READY, session.snapshot().branch("page-1").status);
        assertEquals(3, provider.prepared.size());
        assertTrue(recorder.last().settled());
    }

    @Test public void submissionFailureLeavesTheBranchRetryable() {
        AtomicBoolean fail = new AtomicBoolean(true);
        ChannelPipeline pipeline = pipeline(inline, ignored -> {
            if (fail.getAndSet(false)) throw new IllegalStateException("admission store unavailable");
            return true;
        });
        ChannelPipeline.Session session = pipeline.start(input(FACEBOOK), new Recorder());
        ChannelBranch failed = session.snapshot().branch("page-1");
        assertEquals(ChannelBranch.Status.FAILED, failed.status);
        assertEquals("SUBMISSION_FAILED", failed.stage(PipelineStage.OUTLINE).rejection);
        assertEquals(0, provider.prepared.size());

        assertTrue(session.retry("page-1"));
        assertEquals(ChannelBranch.Status.TEXT_READY, session.snapshot().branch("page-1").status);
        assertEquals(3, provider.prepared.size());
    }

    @Test public void adapterPrepareFailureReleasesTheReservationAndCanRetry() {
        provider.failPrepareStep = "page-1/outline";
        ChannelPipeline.Session session = pipeline(inline).start(input(FACEBOOK), new Recorder());
        ChannelBranch failed = session.snapshot().branch("page-1");
        assertEquals(ChannelBranch.Status.FAILED, failed.status);
        StageResult outline = failed.stage(PipelineStage.OUTLINE);
        assertEquals("PROVIDER_PREPARE_FAILED", outline.rejection);
        assertEquals(1, budget.count("release:"));
        assertNotNull(outline.requestId);

        provider.failPrepareStep = null;
        assertTrue(session.retry("page-1"));
        assertEquals(ChannelBranch.Status.TEXT_READY, session.snapshot().branch("page-1").status);
        assertNotEquals(outline.requestId, session.snapshot().branch("page-1").stage(PipelineStage.OUTLINE).requestId);
    }

    @Test public void adapterRunFailureKeepsPartialAndUncertainUsage() {
        provider.throwStep = "page-1/outline";
        ChannelPipeline.Session session = pipeline(inline).start(input(FACEBOOK), new Recorder());
        ChannelBranch failed = session.snapshot().branch("page-1");
        assertEquals(ChannelBranch.Status.FAILED, failed.status);
        StageResult outline = failed.stage(PipelineStage.OUTLINE);
        assertEquals("partial before exception", outline.text);
        assertEquals(UsageRecord.Settlement.UNCERTAIN, outline.settlement);
        assertEquals(0, budget.count("release:"));
        assertEquals(1, provider.prepared.size());
    }

    @Test public void slowObserverDoesNotHoldSessionLockOrStopBothBranches() throws Exception {
        ExecutorService workers = pool();
        CountDownLatch sessionReturned = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Recorder recorder = new Recorder();
        AtomicBoolean blocked = new AtomicBoolean();
        try {
            ChannelPipeline.Session session = pipeline(workers).start(input(FACEBOOK, INSTAGRAM), snapshot -> {
                if (snapshot.revision > 1 && blocked.compareAndSet(false, true)) {
                    entered.countDown();
                    try {
                        if (!sessionReturned.await(5, TimeUnit.SECONDS) || !release.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("observer did not get released");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                recorder.onSnapshot(snapshot);
            });
            sessionReturned.countDown();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            ExecutorService reader = Executors.newSingleThreadExecutor();
            pools.add(reader);
            assertNotNull(reader.submit(session::snapshot).get(2, TimeUnit.SECONDS));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!session.snapshot().settled() && System.nanoTime() < deadline) Thread.yield();
            assertTrue("both branches can settle while delivery is paused", session.snapshot().settled());
            assertEquals(6, provider.prepared.size());
            release.countDown();
            assertTrue(recorder.settled.await(5, TimeUnit.SECONDS));
            assertEquals(session.snapshot().revision, recorder.last().revision);
        } finally {
            release.countDown();
        }
    }

    @Test public void twoTargetsOverlapAndOneFailureDoesNotTouchTheOther() throws Exception {
        provider.barrier = new CyclicBarrier(2); // Both outlines must be in flight at once.
        provider.failStep = "ig-1/draft";
        Recorder recorder = new Recorder();
        pipeline(pool()).start(input(FACEBOOK, INSTAGRAM), recorder);
        assertTrue(recorder.settled.await(10, TimeUnit.SECONDS));
        PipelineSnapshot done = recorder.last();
        ChannelBranch facebook = done.branch("page-1");
        ChannelBranch instagram = done.branch("ig-1");
        assertEquals(ChannelBranch.Status.TEXT_READY, facebook.status);
        assertEquals(ChannelBranch.Status.FAILED, instagram.status);
        assertEquals(PipelineStage.DRAFT, instagram.failedStage);
        assertEquals(AiError.Code.PROVIDER_UNAVAILABLE, instagram.stage(PipelineStage.DRAFT).error.code);
        assertEquals("outline for ig-1", instagram.stage(PipelineStage.OUTLINE).text); // Earlier result kept.
        assertEquals(StageResult.Status.NOT_STARTED, instagram.stage(PipelineStage.RULE_CHECK).status);
        // Timestamps prove the outlines overlapped.
        StageResult a = facebook.stage(PipelineStage.OUTLINE);
        StageResult b = instagram.stage(PipelineStage.OUTLINE);
        assertTrue(a.startedAtMillis < b.finishedAtMillis && b.startedAtMillis < a.finishedAtMillis);
        // Text never crosses channels.
        assertTrue(facebook.stage(PipelineStage.DRAFT).text.contains("page-1"));
        assertEquals(5, provider.prepared.size()); // 3 Facebook + outline and draft for Instagram, no retry.
        for (AiRequest request : provider.prepared) {
            assertEquals(request.channelId.equals("ig-1") ? Long.valueOf(5) : Long.valueOf(3), request.connectionRevision);
            assertTrue(request.prompt.contains(request.channelId.equals("ig-1") ? "Instagram" : "Facebook"));
        }
    }

    @Test public void budgetRejectionStopsTheBranchBeforeAnythingIsSent() {
        budget.next = BudgetManager.Admission.LIMIT_REACHED;
        ChannelPipeline.Session session = pipeline(inline).start(input(FACEBOOK), new Recorder());
        ChannelBranch branch = session.snapshot().branch("page-1");
        assertEquals(ChannelBranch.Status.FAILED, branch.status);
        assertEquals(PipelineStage.OUTLINE, branch.failedStage);
        assertEquals(StageResult.Status.REJECTED, branch.stage(PipelineStage.OUTLINE).status);
        assertEquals("BUDGET_LIMIT_REACHED", branch.stage(PipelineStage.OUTLINE).rejection);
        assertTrue(provider.prepared.isEmpty());
        assertEquals(1, budget.count("reserve:")); // No automatic second attempt.
    }

    @Test public void noModelSelectedIsReportedOnTheOutlineStage() {
        selections.select(AiTaskType.CONTENT_TEXT, null);
        ChannelBranch branch = pipeline(inline).start(input(FACEBOOK), new Recorder()).snapshot().branch("page-1");
        assertEquals("NO_MODEL_SELECTED", branch.stage(PipelineStage.OUTLINE).rejection);
        assertEquals(0, budget.count("reserve:"));
    }

    @Test public void cancellingOneBranchMidStreamKeepsPartialTextAndTheOtherBranch() throws Exception {
        provider.hangStep = "ig-1/outline";
        Recorder recorder = new Recorder();
        ChannelPipeline.Session session = pipeline(pool()).start(input(FACEBOOK, INSTAGRAM), recorder);
        assertTrue(provider.hanging.await(5, TimeUnit.SECONDS));
        assertTrue(session.cancelBranch("ig-1"));
        assertFalse(session.cancelBranch("ig-1"));
        assertTrue(recorder.settled.await(10, TimeUnit.SECONDS));
        PipelineSnapshot done = recorder.last();
        ChannelBranch instagram = done.branch("ig-1");
        assertEquals(ChannelBranch.Status.CANCELLED, instagram.status);
        StageResult outline = instagram.stage(PipelineStage.OUTLINE);
        assertEquals(StageResult.Status.CANCELLED, outline.status);
        assertEquals("partial ig-1", outline.text);
        assertEquals(UsageRecord.Settlement.UNCERTAIN, outline.settlement); // May have been charged.
        assertEquals(ChannelBranch.Status.TEXT_READY, done.branch("page-1").status);
        assertFalse(session.cancelBranch("page-1")); // Finished branches cannot be cancelled.
    }

    @Test public void retryResumesFromTheFailedStageWithANewRequestAndKeepsEarlierStages() {
        provider.failStep = "page-1/draft";
        ChannelPipeline.Session session = pipeline(inline).start(input(FACEBOOK), new Recorder());
        ChannelBranch failed = session.snapshot().branch("page-1");
        assertEquals(PipelineStage.DRAFT, failed.failedStage);
        String outlineRequest = failed.stage(PipelineStage.OUTLINE).requestId;
        String failedDraft = failed.stage(PipelineStage.DRAFT).requestId;
        assertFalse(session.retry("unknown"));
        provider.failStep = null;
        assertTrue(session.retry("page-1"));
        ChannelBranch retried = session.snapshot().branch("page-1");
        assertEquals(ChannelBranch.Status.TEXT_READY, retried.status);
        assertEquals(outlineRequest, retried.stage(PipelineStage.OUTLINE).requestId); // Not re-run.
        assertEquals(2, retried.stage(PipelineStage.DRAFT).attempt);
        assertNotEquals(failedDraft, retried.stage(PipelineStage.DRAFT).requestId);
        assertEquals(4, budget.count("reserve:")); // outline + failed draft + draft + rule check.
        assertFalse(session.retry("page-1")); // Nothing left to retry.
    }

    @Test public void completedRuleCheckAfterCancellationKeepsTheCancelledBranchAndPartial() throws Exception {
        provider.hangStep = "page-1/rule-check";
        provider.completeAfterCancel = true;
        Recorder recorder = new Recorder();
        ChannelPipeline.Session session = pipeline(pool()).start(input(FACEBOOK), recorder);
        assertTrue(provider.hanging.await(5, TimeUnit.SECONDS));
        assertTrue(session.cancelBranch("page-1"));
        assertTrue(recorder.settled.await(5, TimeUnit.SECONDS));

        ChannelBranch branch = recorder.last().branch("page-1");
        StageResult ruleCheck = branch.stage(PipelineStage.RULE_CHECK);
        assertEquals(ChannelBranch.Status.CANCELLED, branch.status);
        assertEquals(StageResult.Status.CANCELLED, ruleCheck.status);
        assertEquals("partial page-1", ruleCheck.text);
        assertEquals(UsageRecord.Settlement.SETTLED, ruleCheck.settlement);
        assertNull(branch.ruleCheck);
        assertEquals(PipelineStage.RULE_CHECK, branch.resumeStage());
    }

    @Test public void staleTerminalFromAnOlderAttemptNeverOverwritesTheRetry() throws Exception {
        provider.hangStep = "page-1/outline";
        Recorder recorder = new Recorder();
        ChannelPipeline.Session session = pipeline(pool()).start(input(FACEBOOK), recorder);
        assertTrue(provider.hanging.await(5, TimeUnit.SECONDS));
        session.cancelBranch("page-1");
        assertTrue(recorder.settled.await(10, TimeUnit.SECONDS));
        provider.hangStep = null;
        // Replay a late terminal of the cancelled attempt after the retry has started.
        AiProvider.Listener late = provider.listeners.get(0);
        AiRequest old = provider.prepared.get(0);
        assertTrue(session.retry("page-1"));
        late.onEvent(AiEvent.terminal(old, 50, AiEvent.Type.COMPLETED, null, AiUsage.MISSING, false, true));
        long deadline = System.currentTimeMillis() + 10_000;
        while (session.snapshot().branch("page-1").status == ChannelBranch.Status.RUNNING
                && System.currentTimeMillis() < deadline) {
            Thread.yield();
        }
        ChannelBranch branch = session.snapshot().branch("page-1");
        assertEquals(ChannelBranch.Status.TEXT_READY, branch.status);
        assertNotEquals(old.requestId, branch.stage(PipelineStage.OUTLINE).requestId);
        assertEquals(2, branch.stage(PipelineStage.OUTLINE).attempt);
    }

    @Test public void sessionCancelStopsAllBranches() throws Exception {
        provider.hangStep = "*/outline";
        Recorder recorder = new Recorder();
        ChannelPipeline.Session session = pipeline(pool()).start(input(FACEBOOK, INSTAGRAM), recorder);
        assertTrue(provider.hanging.await(5, TimeUnit.SECONDS));
        session.cancel();
        assertTrue(recorder.settled.await(10, TimeUnit.SECONDS));
        for (ChannelBranch branch : recorder.last().branches) {
            assertEquals(ChannelBranch.Status.CANCELLED, branch.status);
            assertEquals(StageResult.Status.NOT_STARTED, branch.stage(PipelineStage.DRAFT).status);
        }
        assertTrue(provider.prepared.size() <= 2); // Never more than the two outlines.
    }

    @Test public void budgetExhaustionMidStreamFailsTheBranchWithItsPartial() throws Exception {
        provider.hangStep = "page-1/outline";
        Recorder recorder = new Recorder();
        pipeline(pool()).start(input(FACEBOOK), recorder);
        assertTrue(provider.hanging.await(5, TimeUnit.SECONDS));
        budget.exhaust();
        assertTrue(recorder.settled.await(10, TimeUnit.SECONDS));
        ChannelBranch branch = recorder.last().branch("page-1");
        assertEquals(ChannelBranch.Status.FAILED, branch.status);
        assertTrue(branch.stage(PipelineStage.OUTLINE).budgetStopped);
        assertEquals("partial page-1", branch.stage(PipelineStage.OUTLINE).text);
    }

    @Test public void completedWithoutTextIsAFailureNotAnEmptyStage() {
        provider.emptyStep = "page-1/outline";
        ChannelBranch branch = pipeline(inline).start(input(FACEBOOK), new Recorder()).snapshot().branch("page-1");
        assertEquals(ChannelBranch.Status.FAILED, branch.status);
        assertEquals(AiError.Code.INCOMPLETE, branch.stage(PipelineStage.OUTLINE).error.code);
        assertEquals(1, provider.prepared.size());
    }

    /**
     * Labelled fake: answers by "channel/step". It can fail, hang until cancelled, wait on a barrier
     * so two branches must overlap, or complete without text.
     */
    private static final class Scripted implements AiProvider {
        final List<AiRequest> prepared = new CopyOnWriteArrayList<>();
        final List<Listener> listeners = new CopyOnWriteArrayList<>();
        final CountDownLatch hanging = new CountDownLatch(1);
        volatile CyclicBarrier barrier;
        volatile String failStep;
        volatile String failPrepareStep;
        volatile String throwStep;
        volatile String hangStep;
        volatile boolean completeAfterCancel;
        volatile String emptyStep;

        private boolean matches(String rule, AiRequest request) {
            if (rule == null) return false;
            String[] parts = rule.split("/");
            return (parts[0].equals("*") || parts[0].equals(request.channelId)) && parts[1].equals(request.stepId);
        }

        @Override public Attempt prepare(AiRequest request, Listener listener) {
            if (matches(failPrepareStep, request)) throw new IllegalStateException("fixture prepare failure");
            prepared.add(request);
            listeners.add(listener);
            CountDownLatch cancelled = new CountDownLatch(1);
            return new Attempt() {
                @Override public void cancel() { cancelled.countDown(); }
                @Override public void run() {
                    listener.onEvent(AiEvent.started(request, 1));
                    if (matches(throwStep, request)) {
                        listener.onEvent(AiEvent.text(request, 2, "partial before exception"));
                        throw new IllegalStateException("fixture run failure");
                    }
                    if (matches(hangStep, request)) {
                        listener.onEvent(AiEvent.text(request, 2, "partial " + request.channelId));
                        hanging.countDown();
                        await(cancelled);
                        if (completeAfterCancel) {
                            listener.onEvent(AiEvent.text(request, 3, " late result"));
                            listener.onEvent(AiEvent.terminal(request, 4, AiEvent.Type.COMPLETED, null,
                                    new AiUsage(10L, 5L, 15L, 0L, null), false, true));
                            return;
                        }
                        listener.onEvent(AiEvent.terminal(request, 3, AiEvent.Type.CANCELLED, null,
                                AiUsage.MISSING, false, true));
                        return;
                    }
                    CyclicBarrier both = barrier;
                    if (both != null && "outline".equals(request.stepId)) {
                        try {
                            both.await(5, TimeUnit.SECONDS);
                        } catch (Exception broken) {
                            throw new AssertionError("branches did not overlap", broken);
                        }
                    }
                    if (matches(failStep, request)) {
                        listener.onEvent(AiEvent.terminal(request, 2, AiEvent.Type.FAILED,
                                new AiError(AiError.Code.PROVIDER_UNAVAILABLE, null), AiUsage.MISSING, true, false));
                        return;
                    }
                    String text = matches(emptyStep, request) ? "" : answer(request);
                    if (!text.isEmpty()) listener.onEvent(AiEvent.text(request, 2, text));
                    listener.onEvent(AiEvent.terminal(request, 3, AiEvent.Type.COMPLETED, null,
                            new AiUsage(10L, 5L, 15L, 0L, null), false, true));
                }
            };
        }

        private static String answer(AiRequest request) {
            switch (request.stepId) {
                case "outline": return "outline for " + request.channelId;
                case "draft": return "post for " + request.channelId;
                default: return "VERDICT: PASS";
            }
        }

        private static void await(CountDownLatch latch) {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
