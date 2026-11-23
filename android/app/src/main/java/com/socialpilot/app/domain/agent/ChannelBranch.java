package com.socialpilot.app.domain.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable typed snapshot of one channel branch; branches never share stages or text. */
public final class ChannelBranch {
    public enum Status {
        RUNNING,
        /** Outline, draft and rule check completed. Not the full F2 chain: image/package are week 4. */
        TEXT_READY,
        /** A stage failed or was rejected; {@link #failedStage} names it. Earlier results are kept. */
        FAILED,
        CANCELLED
    }

    public final ChannelTarget target;
    public final Status status;
    /** All six stages in order; week-4 stages are NOT_IMPLEMENTED. */
    public final List<StageResult> stages;
    public final PipelineStage failedStage;
    /** Set once the rule-check stage completed. */
    public final RuleCheck ruleCheck;

    public ChannelBranch(ChannelTarget target, Status status, List<StageResult> stages, PipelineStage failedStage,
                         RuleCheck ruleCheck) {
        this.target = Objects.requireNonNull(target);
        this.status = Objects.requireNonNull(status);
        if (stages.size() != PipelineStage.values().length) throw new IllegalArgumentException("all stages required");
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
        this.failedStage = failedStage;
        this.ruleCheck = ruleCheck;
    }

    public static ChannelBranch initial(ChannelTarget target) {
        List<StageResult> stages = new ArrayList<>();
        for (PipelineStage stage : PipelineStage.values()) stages.add(StageResult.initial(stage));
        return new ChannelBranch(target, Status.RUNNING, stages, null, null);
    }

    public StageResult stage(PipelineStage stage) {
        return stages.get(stage.ordinal());
    }

    ChannelBranch with(StageResult result, Status status, PipelineStage failedStage, RuleCheck ruleCheck) {
        List<StageResult> next = new ArrayList<>(stages);
        next.set(result.stage.ordinal(), result);
        return new ChannelBranch(target, status, next, failedStage, ruleCheck);
    }

    public ChannelBranch withStage(StageResult result) {
        return with(result, status, failedStage, ruleCheck);
    }

    public ChannelBranch withStatus(StageResult result, Status status, PipelineStage failedStage) {
        return with(result, status, failedStage, ruleCheck);
    }

    public ChannelBranch withRuleCheck(StageResult result, RuleCheck check) {
        return with(result, Status.TEXT_READY, null, check);
    }

    /** The stage to resume from after a failure, or null when nothing failed. */
    public PipelineStage resumeStage() {
        return status == Status.FAILED || status == Status.CANCELLED ? firstIncompleteTextStage() : null;
    }

    private PipelineStage firstIncompleteTextStage() {
        for (StageResult result : stages) {
            if (result.stage.textStage && result.status != StageResult.Status.COMPLETED) return result.stage;
        }
        return null;
    }
}
