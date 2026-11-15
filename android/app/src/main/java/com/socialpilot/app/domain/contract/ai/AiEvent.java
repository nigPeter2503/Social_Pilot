package com.socialpilot.app.domain.contract.ai;

import java.util.Objects;

public final class AiEvent {
    public enum Type { STARTED, TEXT_DELTA, COMPLETED, FAILED, CANCELLED }

    public final String sessionId;
    public final String channelId;
    public final Long connectionRevision;
    public final String stepId;
    public final String requestId;
    public final AiRequest.Provider provider;
    public final String model;
    public final long sequence;
    public final Type type;
    public final String delta;
    public final AiError error;
    public final AiUsage usage;
    public final boolean partial;
    public final boolean mayHaveDispatched;

    private AiEvent(AiRequest request, long sequence, Type type, String delta, AiError error,
                    AiUsage usage, boolean partial, boolean mayHaveDispatched) {
        if (sequence <= 0) throw new IllegalArgumentException("Invalid sequence");
        sessionId = request.sessionId;
        channelId = request.channelId;
        connectionRevision = request.connectionRevision;
        stepId = request.stepId;
        requestId = request.requestId;
        provider = request.provider;
        model = request.model;
        this.sequence = sequence;
        this.type = type;
        this.delta = delta;
        this.error = error;
        this.usage = usage;
        this.partial = partial;
        this.mayHaveDispatched = mayHaveDispatched;
    }

    public static AiEvent started(AiRequest request, long sequence) {
        return new AiEvent(request, sequence, Type.STARTED, null, null, null, false, false);
    }

    public static AiEvent text(AiRequest request, long sequence, String delta) {
        if (delta == null || delta.isEmpty()) throw new IllegalArgumentException("Empty delta");
        return new AiEvent(request, sequence, Type.TEXT_DELTA, delta, null, null, false, true);
    }

    public static AiEvent terminal(AiRequest request, long sequence, Type type, AiError error,
                                   AiUsage usage, boolean partial, boolean mayHaveDispatched) {
        if (type != Type.COMPLETED && type != Type.FAILED && type != Type.CANCELLED
                || (type == Type.FAILED) != (error != null)
                || (type == Type.COMPLETED && partial)) {
            throw new IllegalArgumentException("Invalid terminal event");
        }
        return new AiEvent(request, sequence, type, null, error, Objects.requireNonNull(usage),
                partial, mayHaveDispatched);
    }
}
