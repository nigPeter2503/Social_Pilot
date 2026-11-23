package com.socialpilot.app.data.ai.pipeline;

import com.socialpilot.app.data.ai.AiGateway;
import com.socialpilot.app.domain.agent.ChannelBranch;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Week-3 text pipeline (SP-031): for each selected target, outline → draft → rule check. Every
 * stage is one {@link AiGateway#submit} with a new requestId, so model choice, BYOK, budget
 * reservation, cancellation and usage all go through the one shared gate.
 *
 * <p>One target runs exactly one branch. Two targets run two independent branches at the same
 * time; a failure, rejection or cancel in one never touches the other. Branches never retry on
 * their own: {@link Session#retry} is an explicit user action that reserves again. Sessions are
 * in memory; after process death nothing is resumed or re-sent automatically.
 *
 * <p>Two-target runs use two independent gateway branches until the SP-014 dual-stream coordinator
 * is merged; the snapshot shape stays the same when that swap happens.
 */
public final class ChannelPipeline {
    public interface Observer {
        /** Called on a worker thread in revision order; post to the main thread, never block. */
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

    public ChannelPipeline(AiGateway gateway, Executor workers, Clock clock) {
        this.gateway = Objects.requireNonNull(gateway);
        this.workers = Objects.requireNonNull(workers);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Starts one branch per target of a validated input. Safe to call from the main thread. */
    public Session start(PipelineInput input, Observer observer) {
        Session session = new Session(input, Objects.requireNonNull(observer));
        for (Run run : session.runs.values()) session.schedule(run, PipelineStage.OUTLINE, run.generation);
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

        Run(ChannelTarget target) {
            this.target = target;
            this.branch = ChannelBranch.initial(target);
        }
    }

    public final class Session implements Cancelable {
        public final String sessionId = UUID.randomUUID().toString();
        private final PipelineInput input;
        private final Observer observer;
        private final Object lock = new Object();
        private final Map<String, Run> runs = new LinkedHashMap<>();
        private long revision;
        private PipelineSnapshot snapshot;

        private Session(PipelineInput input, Observer observer) {
            this.input = Objects.requireNonNull(input);
            this.observer = observer;
            for (ChannelTarget target : input.targets) runs.put(target.channelId, new Run(target));
            synchronized (lock) {
                publish();
            }
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
            }
        }

        /** Worker thread: builds the prompt from earlier stages and submits one attempt. */
        private void runStage(Run run, PipelineStage stage, int generation) {
            AiGateway.Call call;
            synchronized (lock) {
                if (run.generation != generation || run.branch.status != ChannelBranch.Status.RUNNING) return;
                if (run.cancelled) {
                    run.branch = run.branch.withStatus(run.branch.stage(stage), ChannelBranch.Status.CANCELLED, stage);
                    publish();
                    return;
                }
                StageResult running = run.branch.stage(stage).running(run.branch.stage(stage).attempt + 1, now());
                run.branch = run.branch.withStage(running);
                run.streamed.setLength(0);
                call = new AiGateway.Call(sessionId, run.target.channelId, run.target.connectionRevision,
                        stage.stepId, AiTaskType.CONTENT_TEXT, prompt(run, stage), tokensFor(stage));
                publish();
            }
            AiGateway.Submission submission = gateway.submit(call, new StageCallback(run, stage, generation));
            boolean cancelNow = false;
            synchronized (lock) {
                if (run.generation != generation) return;
                StageResult result = run.branch.stage(stage);
                if (!submission.accepted()) {
                    result = result.rejected(submission.requestId, submission.rejection.name(),
                            submission.block == null ? null : submission.block.name(), now());
                    run.branch = run.branch.withStatus(result, ChannelBranch.Status.FAILED, stage);
                    publish();
                    return;
                }
                // The terminal may already have arrived on another thread; then nothing is left to do.
                if (result.status == StageResult.Status.RUNNING && result.requestId == null) {
                    run.current = submission;
                    run.branch = run.branch.withStage(result.accepted(submission.requestId));
                    cancelNow = run.cancelled;
                    publish();
                }
            }
            if (cancelNow) submission.cancel();
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
                    if (run.generation != generation) return;
                    StageResult result = run.branch.stage(stage);
                    if (result.status != StageResult.Status.RUNNING) return;
                    run.streamed.append(event.delta);
                    run.branch = run.branch.withStage(result.streaming(run.streamed.toString()));
                    publish();
                }
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
                    if (terminal.type == AiEvent.Type.COMPLETED && !finished.text.trim().isEmpty()) {
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
                        ChannelBranch.Status branchStatus = run.cancelled && !finished.budgetStopped
                                ? ChannelBranch.Status.CANCELLED : ChannelBranch.Status.FAILED;
                        run.branch = run.branch.withStatus(result, branchStatus, stage);
                    }
                    publish();
                    if (next != null && run.cancelled) {
                        run.branch = run.branch.withStatus(run.branch.stage(next), ChannelBranch.Status.CANCELLED, next);
                        publish();
                        next = null;
                    }
                }
                if (next != null) schedule(run, next, generation);
            }
        }

        /** Holds the lock. */
        private void publish() {
            List<ChannelBranch> branches = new ArrayList<>();
            for (Run run : runs.values()) branches.add(run.branch);
            snapshot = new PipelineSnapshot(sessionId, input.briefId, ++revision, branches);
            observer.onSnapshot(snapshot);
        }
    }

    private long now() {
        return clock.nowMillis();
    }
}
