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
        /** The ledger's stored record (period pinned at reserve); null when {@link #ledgerWriteFailed}. */
        public final UsageRecord usage;
        /** True when the call was cancelled because spend went above a cap; text is partial. */
        public final boolean budgetStopped;
        /**
         * The terminal could not be written to the ledger. Cost is unknown and the reservation stays
         * held (recovered as UNCERTAIN at next start); never treat it as free or settled.
         */
        public final boolean ledgerWriteFailed;

        Finished(AiRequest request, AiEvent terminal, String text, UsageRecord usage, boolean budgetStopped,
                 boolean ledgerWriteFailed) {
            this.request = request;
            this.terminal = terminal;
            this.text = text;
            this.usage = usage;
            this.budgetStopped = budgetStopped;
            this.ledgerWriteFailed = ledgerWriteFailed;
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
        // Registered before reserve: a cap lowered right after the reservation commits must find
        // this run. The period is pinned by the ledger inside reserve; the gateway never derives one.
        Run run = new Run(request, resolution.entry, callback);
        active.add(run);
        BudgetManager.Admission admission;
        try {
            admission = ledger.reserve(request, call.taskType, resolution.entry.price.upperBound(request));
        } catch (RuntimeException failure) {
            active.remove(run);
            throw failure;
        }
        if (admission == BudgetManager.Admission.LIMIT_REACHED) {
            active.remove(run);
            return new Submission(request.requestId, Rejection.BUDGET_LIMIT_REACHED, null, null);
        }
        if (admission != BudgetManager.Admission.RESERVED) {
            active.remove(run);
            return new Submission(request.requestId, Rejection.DUPLICATE_REQUEST, null, null);
        }
        if (!run.arm()) {
            // Budget stopped between reserve and dispatch: never sent; release the hold once.
            run.abandon(false);
            return new Submission(request.requestId, Rejection.BUDGET_LIMIT_REACHED, null, null);
        }
        run.attempt = providers.get(request.provider).prepare(request, run);
        try {
            workers.execute(run::execute);
        } catch (RejectedExecutionException full) {
            if (!run.disarm()) return new Submission(request.requestId, null, null, run); // Already finished.
            run.abandon(false);
            return new Submission(request.requestId, Rejection.WORKER_UNAVAILABLE, null, null);
        }
        return new Submission(request.requestId, null, null, run);
    }

    private final class Run implements AiProvider.Listener {
        /** NEW until the reservation is admitted and armed; a cancelled PENDING run is finalized by its worker job. */
        private static final int NEW = 0, PENDING = 1, RUNNING = 2, ABANDONED = 3;
        private final AiRequest request;
        private final ModelEntry entry;
        private final Callback callback;
        private final AtomicInteger state = new AtomicInteger(NEW);
        private final AtomicBoolean finished = new AtomicBoolean();
        private final StringBuilder text = new StringBuilder();
        private volatile boolean cancelled;
        private volatile boolean budgetStopped;
        private volatile AiProvider.Attempt attempt;

        Run(AiRequest request, ModelEntry entry, Callback callback) {
            this.request = request;
            this.entry = entry;
            this.callback = callback;
        }

        /**
         * After an admitted reserve, on the submitting worker. False when a cancel (budget stop)
         * arrived first; the caller then finalizes without callback. Only one of arm and the queued
         * job can move the run out of PENDING.
         */
        boolean arm() {
            if (!state.compareAndSet(NEW, PENDING)) return false;
            return !(cancelled && state.compareAndSet(PENDING, ABANDONED));
        }

        /** Worker refused the job: true when this call, not the queued job, must finalize. */
        boolean disarm() {
            return state.compareAndSet(PENDING, ABANDONED);
        }

        /**
         * Worker thread. A run cancelled while still queued is finalized here, never on the thread
         * that called {@link #cancel}: release, usage and the callback are ledger I/O and must not run
         * on the UI thread or throw into it.
         */
        void execute() {
            if (cancelled && state.compareAndSet(PENDING, ABANDONED)) {
                abandon(true);
                return;
            }
            if (!state.compareAndSet(PENDING, RUNNING)) return;
            // A cancel racing with this start is honoured by the attempt: it ends CANCELLED, not dispatched.
            attempt.run();
        }

        /** Safe on any thread, including the UI thread: it only flags and forwards; it does no I/O. */
        void cancel() {
            cancelled = true;
            AiProvider.Attempt current = attempt;
            if (current != null) current.cancel();
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
            // Only a final provider report is priced; partial snapshots keep the reservation.
            AiCostEstimate estimate = terminal.mayHaveDispatched && terminal.usage.state == AiUsage.State.REPORTED
                    ? entry.price.fromUsage(request.provider, terminal.usage) : null;
            UsageRecord record;
            try {
                record = ledger.complete(request.requestId, terminal.mayHaveDispatched, estimate, terminal.usage);
            } catch (RuntimeException failure) {
                // Nothing of the terminal was stored: the hold stays and is recovered as UNCERTAIN.
                record = null;
            }
            if (!notify) return;
            callback.onEvent(terminal);
            callback.onFinished(new Finished(request, terminal, text.toString(), record, budgetStopped,
                    record == null));
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
