package com.socialpilot.app.domain.agent;

import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.util.Objects;

/** Immutable state of one stage of one channel branch. */
public final class StageResult {
    public enum Status {
        NOT_STARTED,
        RUNNING,
        COMPLETED,
        /** Provider error after acceptance; {@link #error} says which. */
        FAILED,
        CANCELLED,
        /** Refused before dispatch (no model, key, budget...); {@link #rejection} says why. */
        REJECTED,
        /** Week-4 stage not built yet; never shown as complete. */
        NOT_IMPLEMENTED
    }

    public final PipelineStage stage;
    public final Status status;
    /** 1 for the first attempt; a manual retry increments it and always uses a new requestId. */
    public final int attempt;
    public final String requestId;
    /** Final text when COMPLETED, otherwise the partial text received before the stop. */
    public final String text;
    /** Gateway rejection name when REJECTED, for example BUDGET_LIMIT_REACHED. */
    public final String rejection;
    /** Model block name when the rejection is MODEL_BLOCKED. */
    public final String block;
    public final AiError error;
    public final AiUsage usage;
    public final UsageRecord.Settlement settlement;
    public final Long estimateMicros;
    public final boolean budgetStopped;
    public final long startedAtMillis;
    public final long finishedAtMillis;

    private StageResult(PipelineStage stage, Status status, int attempt, String requestId, String text,
                        String rejection, String block, AiError error, AiUsage usage,
                        UsageRecord.Settlement settlement, Long estimateMicros, boolean budgetStopped,
                        long startedAtMillis, long finishedAtMillis) {
        this.stage = Objects.requireNonNull(stage);
        this.status = Objects.requireNonNull(status);
        this.attempt = attempt;
        this.requestId = requestId;
        this.text = text == null ? "" : text;
        this.rejection = rejection;
        this.block = block;
        this.error = error;
        this.usage = usage;
        this.settlement = settlement;
        this.estimateMicros = estimateMicros;
        this.budgetStopped = budgetStopped;
        this.startedAtMillis = startedAtMillis;
        this.finishedAtMillis = finishedAtMillis;
    }

    public static StageResult initial(PipelineStage stage) {
        return new StageResult(stage, stage.textStage ? Status.NOT_STARTED : Status.NOT_IMPLEMENTED, 0, null, null,
                null, null, null, null, null, null, false, 0, 0);
    }

    public StageResult running(int attempt, long now) {
        return new StageResult(stage, Status.RUNNING, attempt, null, null, null, null, null, null, null, null,
                false, now, 0);
    }

    public StageResult accepted(String requestId) {
        return new StageResult(stage, status, attempt, requestId, text, null, null, null, null, null, null, false,
                startedAtMillis, 0);
    }

    public StageResult rejected(String requestId, String rejection, String block, long now) {
        return new StageResult(stage, Status.REJECTED, attempt, requestId, null, rejection, block, null, null, null,
                null, false, startedAtMillis, now);
    }

    /** Streamed text so far; only while RUNNING. */
    public StageResult streaming(String partial) {
        return new StageResult(stage, status, attempt, requestId, partial, null, null, null, null, null, null, false,
                startedAtMillis, 0);
    }

    public StageResult finished(Status terminal, String requestId, String text, AiError error, UsageRecord usage,
                                boolean budgetStopped, long now) {
        return new StageResult(stage, terminal, attempt, requestId, text, null, null, error,
                usage == null ? null : usage.usage, usage == null ? null : usage.settlement,
                usage == null ? null : usage.estimateMicros, budgetStopped, startedAtMillis, now);
    }

    public boolean terminal() {
        return status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED
                || status == Status.REJECTED;
    }
}
