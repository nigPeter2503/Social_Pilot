package com.socialpilot.app.data.ai;

import com.socialpilot.app.domain.contract.AiProvider;
import com.socialpilot.app.domain.contract.BudgetManager;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiEvent;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.BudgetPeriod;
import com.socialpilot.app.domain.contract.ai.ModelEntry;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The single dispatch path for every AI consumer (content, comments, reports). For each call it
 * resolves the selected model, checks capability/key/price, creates a fresh requestId, reserves
 * budget, runs one adapter attempt and settles exactly once. It never retries or falls back.
 */
public final class AiGateway {
    public enum Rejection {
        NO_MODEL_SELECTED, MODEL_BLOCKED, PROVIDER_NOT_REGISTERED, KEY_NOT_USABLE,
        /** No production ledger is configured (SP-029); dispatch stays closed. */
        BUDGET_NOT_CONFIGURED,
        /** Ledger exists but no daily/monthly caps are set; nothing is admitted without caps. */
        BUDGET_NOT_SET,
        BUDGET_LIMIT_REACHED, DUPLICATE_REQUEST, WORKER_UNAVAILABLE
    }

    public interface Selections {
        ModelEntry selected(AiTaskType task);
    }

    public interface KeyGate {
        boolean usable(AiRequest.Provider provider);
    }

    public interface Clock {
        long nowMillis();
    }

    /** Called on the attempt worker, in order. Post to the UI thread yourself. */
    public interface Callback {
        void onEvent(AiEvent event);

        void onFinished(Finished finished);
    }

    /** One logical call. Scope rules follow {@link AiRequest}: channel and revision together or neither. */
    public static final class Call {
        public final String sessionId;
        public final String channelId;
        public final Long connectionRevision;
        public final String stepId;
        public final AiTaskType taskType;
        public final String prompt;
        public final int maxOutputTokens;

        public Call(String sessionId, String channelId, Long connectionRevision, String stepId,
                    AiTaskType taskType, String prompt, int maxOutputTokens) {
            this.sessionId = sessionId;
            this.channelId = channelId;
            this.connectionRevision = connectionRevision;
            this.stepId = stepId;
            this.taskType = Objects.requireNonNull(taskType);
            this.prompt = prompt;
            this.maxOutputTokens = maxOutputTokens;
        }
    }

    /** Either a typed request ready to reserve, or the reason none can be made. */
    public static final class Resolution {
        public final AiRequest request;
        public final ModelEntry entry;
        public final Rejection rejection;
        public final ModelEntry.Block block;

        private Resolution(AiRequest request, ModelEntry entry, Rejection rejection, ModelEntry.Block block) {
            this.request = request;
            this.entry = entry;
            this.rejection = rejection;
            this.block = block;
        }
    }

    public static final class Submission implements Cancelable {
        public final String requestId;
        public final Rejection rejection;
        public final ModelEntry.Block block;
        private final Run run;

        private Submission(String requestId, Rejection rejection, ModelEntry.Block block, Run run) {
            this.requestId = requestId;
            this.rejection = rejection;
            this.block = block;
            this.run = run;
        }

        public boolean accepted() {
            return rejection == null;
        }

        @Override
        public void cancel() {
            if (run != null) run.cancel();
        }
    }

    public static final class Finished {
        public final AiRequest request;
        public final AiEvent terminal;
        public final String text;
        public final UsageRecord usage;
        /** True when the call was cancelled because spend went above a cap; text is partial. */
        public final boolean budgetStopped;

        Finished(AiRequest request, AiEvent terminal, String text, UsageRecord usage, boolean budgetStopped) {
            this.request = request;
            this.terminal = terminal;
            this.text = text;
            this.usage = usage;
            this.budgetStopped = budgetStopped;
        }
    }

    private final Selections selections;
    private final Map<AiRequest.Provider, AiProvider> providers;
    private final KeyGate keys;
    private final AttemptLedger ledger;
    private final Executor workers;
    private final Clock clock;
    private final Set<Run> active = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * @param ledger the shared budget and usage ledger, or null to keep dispatch closed (every
     *     submit is then {@link Rejection#BUDGET_NOT_CONFIGURED}); {@link #resolve} still works.
     */
    public AiGateway(Selections selections, Map<AiRequest.Provider, AiProvider> providers, KeyGate keys,
                     AttemptLedger ledger, Executor workers, Clock clock) {
        this.selections = Objects.requireNonNull(selections);
        this.providers = new EnumMap<>(providers);
        this.keys = Objects.requireNonNull(keys);
        this.ledger = ledger;
        this.workers = Objects.requireNonNull(workers);
        this.clock = Objects.requireNonNull(clock);
        if (ledger != null) ledger.addExhaustionListener(this::stopForBudget);
    }

    public boolean dispatchEnabled() {
        return ledger != null;
    }

    /** Spend went above a cap: stop every running call; their partial text and usage are kept. */
    private void stopForBudget() {
        for (Run run : active) run.stopForBudget();
    }

    /**
     * Builds a typed request with a new requestId. Does I/O (key status); call from a worker.
     * Shared by the pipeline (SP-031), which reserves through its own coordinator.
     */
    public Resolution resolve(Call call) {
        ModelEntry entry = selections.selected(call.taskType);
        if (entry == null) return new Resolution(null, null, Rejection.NO_MODEL_SELECTED, null);
        ModelEntry.Block block = entry.blockFor(call.taskType, call.maxOutputTokens);
        if (block != ModelEntry.Block.NONE) return new Resolution(null, entry, Rejection.MODEL_BLOCKED, block);
        if (!providers.containsKey(entry.provider())) {
            return new Resolution(null, entry, Rejection.PROVIDER_NOT_REGISTERED, null);
        }
        if (!keys.usable(entry.provider())) return new Resolution(null, entry, Rejection.KEY_NOT_USABLE, null);
        AiRequest request = new AiRequest(call.sessionId, call.channelId, call.connectionRevision, call.stepId,
                UUID.randomUUID().toString(), entry.provider(), entry.model(), call.taskType.task,
                call.prompt, call.maxOutputTokens);
        return new Resolution(request, entry, null, null);
    }

    /** Resolves, reserves and starts one attempt. Does I/O; call from a worker. */
    public Submission submit(Call call, Callback callback) {
        Objects.requireNonNull(callback);
        Resolution resolution = resolve(call);
        if (resolution.rejection != null) return new Submission(null, resolution.rejection, resolution.block, null);
        if (ledger == null) return new Submission(null, Rejection.BUDGET_NOT_CONFIGURED, null, null);
        AttemptLedger.Limits limits = ledger.limits();
        if (limits == null) return new Submission(null, Rejection.BUDGET_NOT_SET, null, null);
        AiRequest request = resolution.request;
        // Display copy only; the ledger pins the authoritative period inside the reserve transaction.
        BudgetPeriod period = BudgetPeriod.of(clock.nowMillis(), limits.zoneId);
        BudgetManager.Admission admission = ledger.reserve(request, call.taskType,
                resolution.entry.price.upperBound(request));
        if (admission == BudgetManager.Admission.LIMIT_REACHED) {
            return new Submission(request.requestId, Rejection.BUDGET_LIMIT_REACHED, null, null);
        }
        if (admission != BudgetManager.Admission.RESERVED) {
            return new Submission(request.requestId, Rejection.DUPLICATE_REQUEST, null, null);
        }
        Run run = new Run(call.taskType, request, resolution.entry, period, callback);
        run.attempt = providers.get(request.provider).prepare(request, run);
        active.add(run);
        try {
            workers.execute(run::execute);
        } catch (RejectedExecutionException full) {
            run.abandon(false);
            return new Submission(request.requestId, Rejection.WORKER_UNAVAILABLE, null, null);
        }
        return new Submission(request.requestId, null, null, run);
    }

    private final class Run implements AiProvider.Listener {
        private static final int PENDING = 0, RUNNING = 1, ABANDONED = 2;
        private final AiTaskType taskType;
        private final AiRequest request;
        private final ModelEntry entry;
        private final BudgetPeriod period;
        private final Callback callback;
        private final AtomicInteger state = new AtomicInteger(PENDING);
        private final AtomicBoolean finished = new AtomicBoolean();
        private final StringBuilder text = new StringBuilder();
        private volatile boolean budgetStopped;
        private AiProvider.Attempt attempt;

        Run(AiTaskType taskType, AiRequest request, ModelEntry entry, BudgetPeriod period, Callback callback) {
            this.taskType = taskType;
            this.request = request;
            this.entry = entry;
            this.period = period;
            this.callback = callback;
        }

        void execute() {
            if (!state.compareAndSet(PENDING, RUNNING)) return;
            attempt.run();
        }

        void cancel() {
            attempt.cancel();
            // Never started: finalize here, because the worker will skip it.
            if (state.compareAndSet(PENDING, ABANDONED)) abandon(true);
        }

        void stopForBudget() {
            budgetStopped = true;
            cancel();
        }

        /** Never dispatched: release once. A rejected submit does not also call back. */
        void abandon(boolean notify) {
            state.set(ABANDONED);
            finish(AiEvent.terminal(request, 1, AiEvent.Type.CANCELLED, null, AiUsage.MISSING, false, false), notify);
        }

        @Override
        public void onEvent(AiEvent event) {
            if (!request.requestId.equals(event.requestId) || finished.get()) return;
            if (event.type == AiEvent.Type.TEXT_DELTA) text.append(event.delta);
            if (event.type == AiEvent.Type.COMPLETED || event.type == AiEvent.Type.FAILED
                    || event.type == AiEvent.Type.CANCELLED) {
                finish(event, true);
            } else {
                callback.onEvent(event);
            }
        }

        private void finish(AiEvent terminal, boolean notify) {
            if (!finished.compareAndSet(false, true)) return;
            active.remove(this);
            UsageRecord.Settlement settlement;
            Long micros = null;
            if (!terminal.mayHaveDispatched) {
                ledger.releaseUndispatched(request.requestId);
                settlement = UsageRecord.Settlement.RELEASED;
            } else {
                // Only a final provider report is priced; partial snapshots keep the reservation.
                AiCostEstimate estimate = terminal.usage.state == AiUsage.State.REPORTED
                        ? entry.price.fromUsage(request.provider, terminal.usage) : null;
                if (estimate != null) {
                    ledger.settle(request.requestId, estimate);
                    settlement = UsageRecord.Settlement.SETTLED;
                    micros = estimate.micros;
                } else {
                    ledger.markUncertain(request.requestId);
                    settlement = UsageRecord.Settlement.UNCERTAIN;
                }
            }
            UsageRecord record = new UsageRecord(request.requestId, request.sessionId, request.channelId,
                    request.stepId, taskType, request.provider, request.model, period, clock.nowMillis(),
                    terminal.usage, settlement, micros, entry.price.currency.getCurrencyCode(),
                    entry.price.priceRevision);
            ledger.record(record);
            if (!notify) return;
            callback.onEvent(terminal);
            callback.onFinished(new Finished(request, terminal, text.toString(), record, budgetStopped));
        }
    }

    /** Adapters for both providers, each reading its key from BYOK at dispatch time. */
    public static Map<AiRequest.Provider, AiProvider> adapters(AiKeyRepository keys,
                                                               com.socialpilot.app.domain.contract.ai.ModelRegistry registry) {
        Map<AiRequest.Provider, AiProvider> result = new EnumMap<>(AiRequest.Provider.class);
        result.put(AiRequest.Provider.OPENAI, new OpenAiProvider(
                keys.credentialSource(AiRequest.Provider.OPENAI), registry.capabilities(AiRequest.Provider.OPENAI)));
        result.put(AiRequest.Provider.GEMINI, new GeminiAiProvider(
                keys.credentialSource(AiRequest.Provider.GEMINI), registry.capabilities(AiRequest.Provider.GEMINI)));
        return result;
    }
}
