package com.socialpilot.app.domain.contract.ai;

public final class AiUsage {
    public enum State { REPORTED, PARTIAL, MISSING }

    public static final AiUsage MISSING = new AiUsage(null, null, null, null, null);
    public final Long inputTokens;
    public final Long outputTokens;
    public final Long totalTokens;
    public final Long cachedInputTokens;
    public final Long reasoningTokens;
    public final State state;

    /** Token fields retain the corresponding provider's semantics. */
    public AiUsage(Long inputTokens, Long outputTokens, Long totalTokens,
                   Long cachedInputTokens, Long reasoningTokens) {
        this(inputTokens, outputTokens, totalTokens, cachedInputTokens, reasoningTokens, true);
    }

    private AiUsage(Long inputTokens, Long outputTokens, Long totalTokens,
                    Long cachedInputTokens, Long reasoningTokens, boolean finalSnapshot) {
        this.inputTokens = nonnegative(inputTokens);
        this.outputTokens = nonnegative(outputTokens);
        this.totalTokens = nonnegative(totalTokens);
        this.cachedInputTokens = nonnegative(cachedInputTokens);
        this.reasoningTokens = nonnegative(reasoningTokens);
        if (finalSnapshot && inputTokens != null && outputTokens != null && totalTokens != null) {
            state = State.REPORTED;
        } else if (inputTokens != null || outputTokens != null || totalTokens != null
                || cachedInputTokens != null || reasoningTokens != null) {
            state = State.PARTIAL;
        } else {
            state = State.MISSING;
        }
    }

    public AiUsage asPartial() {
        return state == State.REPORTED
                ? new AiUsage(inputTokens, outputTokens, totalTokens, cachedInputTokens, reasoningTokens, false)
                : this;
    }

    private static Long nonnegative(Long value) {
        if (value != null && value < 0) throw new IllegalArgumentException("Negative token count");
        return value;
    }
}
