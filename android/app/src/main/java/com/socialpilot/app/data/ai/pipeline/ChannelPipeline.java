package com.socialpilot.app.data.ai.pipeline;

import com.socialpilot.app.data.ai.AiGateway;
import com.socialpilot.app.domain.agent.ChannelBranch;
import com.socialpilot.app.domain.agent.CheckpointPolicy;
import com.socialpilot.app.domain.agent.ChannelTarget;
import com.socialpilot.app.domain.agent.PipelineInput;
import com.socialpilot.app.domain.agent.PipelineSnapshot;
import com.socialpilot.app.domain.agent.PipelineStage;
import com.socialpilot.app.domain.agent.PromptBuilder;
import com.socialpilot.app.domain.agent.RuleCheck;
import com.socialpilot.app.domain.agent.StageResult;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiEvent;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;

/**
 * Week-3 text pipeline (SP-031): for each selected target, outline → draft → rule check. Every
 * stage is one {@link AiGateway#submit} with a new requestId, so model choice, BYOK, budget
 * reservation, cancellation and usage all go through the one shared gate.
 *
 * <p>One target runs exactly one branch. Two targets run two independent branches at the same
 * time; a failure, rejection or cancel in one never touches the other. Branches never retry on
 * their own: {@link Session#retry} is an explicit user action that reserves again.
 *
 * <p>Checkpoints (SP-033, partial): every structural change is offered to a {@link CheckpointStore}
 * (streamed deltas are not). {@link #restore} rebuilds a session from a stored snapshot through
 * {@link CheckpointPolicy#recover}: stages that were running become INTERRUPTED and nothing is
 * scheduled or re-sent; the user decides whether to {@link Session#retry}. The production Room
 * store is not wired yet.
 *
 * <p>Two-target runs use two independent gateway branches until the SP-014 dual-stream coordinator
 * is merged; the snapshot shape stays the same when that swap happens.
 */
public final class ChannelPipeline {
    private static final Logger LOGGER = Logger.getLogger(ChannelPipeline.class.getName());

    public interface Observer {
        /** Serial delivery in revision order, outside the session lock. Post UI work to the main thread. */
        void onSnapshot(PipelineSnapshot snapshot);
    }

    public interface Clock {
        long nowMillis();
    }

    /** Output caps per stage; the registry still blocks models whose cap is lower. */
    static final int OUTLINE_TOKENS = 400;
    static final int DRAFT_TOKENS = 1000;
    static final int RULE_CHECK_TOKENS = 300;

    private final AiGateway gateway;
    private final Executor workers;
    private final Clock clock;
    private final CheckpointStore checkpoints;

    public ChannelPipeline(AiGateway gateway, Executor workers, Clock clock) {
        this(gateway, workers, clock, CheckpointStore.NONE);
    }

    public ChannelPipeline(AiGateway gateway, Executor workers, Clock clock, CheckpointStore checkpoints) {
        this.gateway = Objects.requireNonNull(gateway);
        this.workers = Objects.requireNonNull(workers);
        this.clock = Objects.requireNonNull(clock);
        this.checkpoints = Objects.requireNonNull(checkpoints);
    }

    /** Starts one branch per target of a validated input. Safe to call from the main thread. */
    public Session start(PipelineInput input, Observer observer) {
        List<ChannelBranch> branches = new ArrayList<>();
        for (ChannelTarget target : input.targets) branches.add(ChannelBranch.initial(target));
        PipelineSnapshot initial = new PipelineSnapshot(UUID.randomUUID().toString(), input.briefId,
                input.briefFingerprint, 1, branches);
        Session session = new Session(input, Objects.requireNonNull(observer), initial);
        saveCheckpoint(initial);
        session.dispatchSnapshots();
        for (Run run : session.runs.values()) session.schedule(run, PipelineStage.OUTLINE, run.generation);
        return session;
    }

    /**
     * Rebuilds a session after process death from its last checkpoint. Nothing is scheduled or sent:
     * interrupted branches wait for {@link Session#retry}, which uses a new requestId and reserves
     * again. Throws when the checkpoint is stale, missing from the configured store, or incompatible.
     */
    public Session restore(PipelineInput input, PipelineSnapshot checkpoint, Observer observer) {
        Objects.requireNonNull(observer);
        PipelineSnapshot recovered = checkpoints.claimRecovery(input, checkpoint, now());
        Session session = new Session(input, observer, recovered);
        session.dispatchSnapshots();
        return session;
    }

    static int tokensFor(PipelineStage stage) {
        switch (stage) {
            case OUTLINE: return OUTLINE_TOKENS;
            case DRAFT: return DRAFT_TOKENS;
            default: return RULE_CHECK_TOKENS;
        }
    }

    /** Per-branch mutable state, guarded by the session lock. */
    private static final class Run {
        final ChannelTarget target;
        ChannelBranch branch;
        /** Bumped by retry so callbacks of an older attempt can never write. */
        int generation;
        boolean cancelled;
        AiGateway.Submission current;
        final StringBuilder streamed = new StringBuilder();

        Run(ChannelBranch branch) {
            this.target = branch.target;
            this.branch = branch;
        }
    }

    public final class Session implements Cancelable {
        public final String sessionId;
        private final PipelineInput input;
        private final Observer observer;
        private final Object lock = new Object();
        private final Map<String, Run> runs = new LinkedHashMap<>();
        private final ArrayDeque<PipelineSnapshot> pendingSnapshots = new ArrayDeque<>();
        private boolean delivering;
        private long revision;
        private PipelineSnapshot snapshot;

        private Session(PipelineInput input, Observer observer, PipelineSnapshot initial) {
            this.input = Objects.requireNonNull(input);
            this.observer = observer;
            sessionId = initial.sessionId;
            revision = initial.revision;
            snapshot = initial;
            for (ChannelBranch branch : initial.branches) runs.put(branch.target.channelId, new Run(branch));
            pendingSnapshots.addLast(initial);
        }

        public PipelineSnapshot snapshot() {
            synchronized (lock) {
                return snapshot;
            }
        }

        /** Cancels every branch; finished results and partial text stay in the snapshot. */
        @Override
        public void cancel() {
            for (String channelId : new ArrayList<>(runs.keySet())) cancelBranch(channelId);
        }

        /** Cancels one branch only. Returns false when it is unknown or already finished. */
        public boolean cancelBranch(String channelId) {
            AiGateway.Submission inFlight;
            synchronized (lock) {
                Run run = runs.get(channelId);
                if (run == null || run.branch.status != ChannelBranch.Status.RUNNING || run.cancelled) return false;
                run.cancelled = true;
                inFlight = run.current;
            }
            // The terminal CANCELLED callback finalizes the stage; a stage not yet submitted is
            // finalized by runStage when it sees the flag.
            if (inFlight != null) inFlight.cancel();
            return true;
        }

        /**
         * Explicit user retry of a failed or cancelled branch from its first incomplete text stage.
         * Uses a new attempt and requestId; completed stages are kept. Returns false otherwise.
         */
        public boolean retry(String channelId) {
            PipelineStage stage;
            int generation;
            Run run;
            synchronized (lock) {
                run = runs.get(channelId);
                if (run == null || run.current != null) return false;
                stage = run.branch.resumeStage();
                if (stage == null) return false;
                run.cancelled = false;
                generation = ++run.generation;
                run.branch = run.branch.withStatus(run.branch.stage(stage), ChannelBranch.Status.RUNNING, null);
                publish();
            }
            dispatchSnapshots();
            schedule(run, stage, generation);
            return true;
        }

        private void schedule(Run run, PipelineStage stage, int generation) {
            try {
                workers.execute(() -> runStage(run, stage, generation));
            } catch (RejectedExecutionException full) {
                synchronized (lock) {
                    if (run.generation != generation) return;
                    StageResult result = run.branch.stage(stage).running(run.branch.stage(stage).attempt + 1, now())
                            .rejected(null, AiGateway.Rejection.WORKER_UNAVAILABLE.name(), null, now());
                    run.branch = run.branch.withStatus(result, ChannelBranch.Status.FAILED, stage);
                    publish();
                }
                dispatchSnapshots();
            }
        }

        /** Worker thread: builds the prompt from earlier stages and submits one attempt. */
        private void runStage(Run run, PipelineStage stage, int generation) {
            AiGateway.Call call = null;
            synchronized (lock) {
                if (run.generation != generation || run.branch.status != ChannelBranch.Status.RUNNING) return;
                if (run.cancelled) {
                    run.branch = run.branch.withStatus(run.branch.stage(stage), ChannelBranch.Status.CANCELLED, stage);
                } else {
                    StageResult running = run.branch.stage(stage).running(run.branch.stage(stage).attempt + 1, now());
                    run.branch = run.branch.withStage(running);
                    run.streamed.setLength(0);
                    call = new AiGateway.Call(sessionId, run.target.channelId, run.target.connectionRevision,
                            stage.stepId, AiTaskType.CONTENT_TEXT, prompt(run, stage), tokensFor(stage));
                }
                publish();
            }
            dispatchSnapshots();
            if (call == null) return;
            synchronized (lock) {
                if (run.cancelled) {
                    StageResult stopped = run.branch.stage(stage).finished(StageResult.Status.CANCELLED, null,
                            null, null, null, false, now());
                    run.branch = run.branch.withStatus(stopped, ChannelBranch.Status.CANCELLED, stage);
                    publish();
                    call = null;
                }
            }
            if (call == null) {
                dispatchSnapshots();
                return;
            }

            AiGateway.Submission submission;
            try {
                submission = gateway.submit(call, new StageCallback(run, stage, generation));
            } catch (RuntimeException failure) {
                synchronized (lock) {
                    StageResult result = run.branch.stage(stage);
                    if (run.generation != generation || result.status != StageResult.Status.RUNNING) return;
                    if (run.cancelled) {
                        result = result.finished(StageResult.Status.CANCELLED, null, null, null, null, false, now());
                        run.branch = run.branch.withStatus(result, ChannelBranch.Status.CANCELLED, stage);
                    } else {
                        result = result.rejected(null, "SUBMISSION_FAILED", null, now());
                        run.branch = run.branch.withStatus(result, ChannelBranch.Status.FAILED, stage);
                    }
                    publish();
                }
                dispatchSnapshots();
                return;
            }
            boolean cancelNow = false;
            synchronized (lock) {
                if (run.generation != generation) return;
                StageResult result = run.branch.stage(stage);
                // A terminal may already have arrived before submit() returned.
                if (result.status != StageResult.Status.RUNNING) return;
                if (!submission.accepted()) {
                    result = result.rejected(submission.requestId, submission.rejection.name(),
                            submission.block == null ? null : submission.block.name(), now());
                    run.branch = run.branch.withStatus(result, ChannelBranch.Status.FAILED, stage);
                    publish();
                } else if (result.requestId == null) {
                    run.current = submission;
                    run.branch = run.branch.withStage(result.accepted(submission.requestId));
                    cancelNow = run.cancelled;
                    publish();
                }
            }
            if (cancelNow) submission.cancel();
            dispatchSnapshots();
        }

        private String prompt(Run run, PipelineStage stage) {
            switch (stage) {
                case OUTLINE:
                    return PromptBuilder.outline(input, run.target);
                case DRAFT:
                    return PromptBuilder.draft(input, run.target, run.branch.stage(PipelineStage.OUTLINE).text);
                default:
                    return PromptBuilder.ruleCheck(input, run.target, run.branch.stage(PipelineStage.DRAFT).text);
            }
        }

        private final class StageCallback implements AiGateway.Callback {
            private final Run run;
            private final PipelineStage stage;
            private final int generation;

            StageCallback(Run run, PipelineStage stage, int generation) {
                this.run = run;
                this.stage = stage;
                this.generation = generation;
            }

            @Override
            public void onEvent(AiEvent event) {
                if (event.type != AiEvent.Type.TEXT_DELTA) return;
                synchronized (lock) {
                    if (run.generation != generation || run.cancelled) return;
                    StageResult result = run.branch.stage(stage);
                    if (result.status != StageResult.Status.RUNNING) return;
                    run.streamed.append(event.delta);
                    run.branch = run.branch.withStage(result.streaming(run.streamed.toString()));
                    publish(false);
                }
                dispatchSnapshots();
            }

            @Override
            public void onFinished(AiGateway.Finished finished) {
                PipelineStage next = null;
                synchronized (lock) {
                    // A retry started after this attempt: its result must not overwrite the new one.
                    if (run.generation != generation) return;
                    run.current = null;
                    StageResult result = run.branch.stage(stage);
                    if (result.status != StageResult.Status.RUNNING) return;
                    String requestId = finished.request.requestId;
                    AiEvent terminal = finished.terminal;
                    if (run.cancelled || finished.budgetStopped) {
                        ChannelBranch.Status branchStatus = finished.budgetStopped
                                ? ChannelBranch.Status.FAILED : ChannelBranch.Status.CANCELLED;
                        StageResult.Status stageStatus = finished.budgetStopped
                                ? StageResult.Status.FAILED : StageResult.Status.CANCELLED;
                        String partial = run.cancelled ? result.text : finished.text;
                        result = result.finished(stageStatus, requestId, partial, terminal.error,
                                finished.usage, finished.budgetStopped, now());
                        run.branch = run.branch.withStatus(result, branchStatus, stage);
                    } else if (terminal.type == AiEvent.Type.COMPLETED && !finished.text.trim().isEmpty()) {
                        result = result.finished(StageResult.Status.COMPLETED, requestId, finished.text, null,
                                finished.usage, false, now());
                        if (stage == PipelineStage.RULE_CHECK) {
                            RuleCheck check = RuleCheck.of(finished.text,
                                    run.branch.stage(PipelineStage.DRAFT).text, input.sources);
                            run.branch = run.branch.withRuleCheck(result, check);
                        } else {
                            run.branch = run.branch.withStage(result);
                            next = PipelineStage.values()[stage.ordinal() + 1];
                        }
                    } else {
                        StageResult.Status status;
                        AiError error = terminal.error;
                        if (terminal.type == AiEvent.Type.COMPLETED) {
                            status = StageResult.Status.FAILED; // Completed with no text: nothing to build on.
                            error = new AiError(AiError.Code.INCOMPLETE, null);
                        } else {
                            status = terminal.type == AiEvent.Type.CANCELLED
                                    ? StageResult.Status.CANCELLED : StageResult.Status.FAILED;
                        }
                        result = result.finished(status, requestId, finished.text, error, finished.usage,
                                finished.budgetStopped, now());
                        run.branch = run.branch.withStatus(result, ChannelBranch.Status.FAILED, stage);
                    }
                    publish();
                }
                dispatchSnapshots();
                if (next != null) schedule(run, next, generation);
            }
        }

        /** Holds the lock. Structural change: published and checkpointed. */
        private void publish() {
            publish(true);
        }

        /** Holds the lock. Streamed deltas are published only; the store sees them with the next structural change. */
        private void publish(boolean checkpoint) {
            List<ChannelBranch> branches = new ArrayList<>();
            for (Run run : runs.values()) branches.add(run.branch);
            snapshot = new PipelineSnapshot(sessionId, input.briefId, input.briefFingerprint, ++revision, branches);
            if (checkpoint) saveCheckpoint(snapshot);
            pendingSnapshots.addLast(snapshot);
        }

        private void dispatchSnapshots() {
            synchronized (lock) {
                if (delivering || pendingSnapshots.isEmpty()) return;
                delivering = true;
            }
            try {
                workers.execute(this::drainSnapshots);
            } catch (RejectedExecutionException unavailable) {
                // Deliver even the terminal snapshot reporting a rejected worker.
                drainSnapshots();
            }
        }

        private void drainSnapshots() {
            while (true) {
                PipelineSnapshot next;
                synchronized (lock) {
                    next = pendingSnapshots.pollFirst();
                    if (next == null) {
                        delivering = false;
                        return;
                    }
                }
                try {
                    observer.onSnapshot(next);
                } catch (RuntimeException ignored) {
                    // Observer errors cannot interrupt dispatch or delivery of later revisions.
                }
            }
        }
    }

    private void saveCheckpoint(PipelineSnapshot snapshot) {
        try {
            checkpoints.save(snapshot);
        } catch (RuntimeException failed) {
            // A failed writer must not strand a live stage or hide its snapshot from observers.
            LOGGER.warning("Pipeline checkpoint write failed");
        }
    }

    private long now() {
        return clock.nowMillis();
    }
}
