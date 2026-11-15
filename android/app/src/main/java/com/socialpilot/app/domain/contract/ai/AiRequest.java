package com.socialpilot.app.domain.contract.ai;

import java.util.Objects;

public final class AiRequest {
    public enum Provider { OPENAI, GEMINI }
    public enum Task { TEXT, IMAGE }

    public final String sessionId;
    public final String channelId;
    public final Long connectionRevision;
    public final String stepId;
    public final String requestId;
    public final Provider provider;
    public final String model;
    public final Task task;
    public final String prompt;
    public final int maxOutputTokens;

    /** A null channel and revision explicitly denote a shared task. */
    public AiRequest(String sessionId, String channelId, Long connectionRevision,
                     String stepId, String requestId, Provider provider, String model,
                     Task task, String prompt, int maxOutputTokens) {
        this.sessionId = required(sessionId);
        this.channelId = channelId == null ? null : required(channelId);
        if ((channelId == null) != (connectionRevision == null)
                || (connectionRevision != null && connectionRevision <= 0)) {
            throw new IllegalArgumentException("Invalid channel scope");
        }
        this.connectionRevision = connectionRevision;
        this.stepId = required(stepId);
        this.requestId = required(requestId);
        this.provider = Objects.requireNonNull(provider);
        this.model = required(model);
        if (!model.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Invalid model identifier");
        }
        this.task = Objects.requireNonNull(task);
        this.prompt = required(prompt);
        if (maxOutputTokens <= 0) throw new IllegalArgumentException("Invalid output limit");
        this.maxOutputTokens = maxOutputTokens;
    }

    private static String required(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Required request field");
        return value;
    }
}
