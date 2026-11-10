package com.socialpilot.app.domain.ai;

import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;

/** Immutable view of one branch, safe to hold after newer stream events arrive. */
public final class BranchSnapshot {
    public enum Phase { RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }
    public enum Problem {
        PROVIDER, BUDGET_LIMIT, BUDGET_UNAVAILABLE, DUPLICATE_REQUEST, PRICE_UNAVAILABLE, EXECUTOR, PROTOCOL
    }

    public final String sessionId;
    public final String channelId;
    public final long connectionRevision;
    public final String stepId;
    public final String requestId;
    public final AiRequest.Provider provider;
    public final String model;
    public final Phase phase;
    public final Problem problem;
    public final AiError error;
    public final AiUsage usage;
    public final String text;
    public final boolean partial;
    public final boolean canRetry;

    BranchSnapshot(AiRequest request, Phase phase, Problem problem, AiError error, AiUsage usage,
                   String text, boolean canRetry) {
        this(request.sessionId, request.channelId, request.connectionRevision, request.stepId,
                request.requestId, request.provider, request.model, phase, problem, error, usage, text, canRetry);
    }

    private BranchSnapshot(String sessionId, String channelId, long connectionRevision, String stepId,
                           String requestId, AiRequest.Provider provider, String model, Phase phase,
                           Problem problem, AiError error, AiUsage usage, String text, boolean canRetry) {
        this.sessionId = sessionId;
        this.channelId = channelId;
        this.connectionRevision = connectionRevision;
        this.stepId = stepId;
        this.requestId = requestId;
        this.provider = provider;
        this.model = model;
        this.phase = phase;
        this.problem = problem;
        this.error = error;
        this.usage = usage;
        this.text = text;
        this.partial = phase != Phase.COMPLETED && !text.isEmpty();
        this.canRetry = canRetry;
    }

    /** For rendering a persisted checkpoint after process death, without starting any work. */
    public BranchSnapshot afterProcessDeath() {
        if (phase != Phase.RUNNING) return this;
        return new BranchSnapshot(sessionId, channelId, connectionRevision, stepId, requestId,
                provider, model, Phase.INTERRUPTED, null, null, usage, text, false);
    }
}
