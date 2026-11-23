package com.socialpilot.app.domain.contract.ai;

import java.util.Objects;

public final class ModelCapabilities {
    public enum Support { SUPPORTED, UNSUPPORTED, UNKNOWN }

    public final AiRequest.Provider provider;
    public final String model;
    public final Support text;
    public final Support streamingText;
    public final Support image;
    public final Support usage;
    public final int maxOutputTokens;

    public ModelCapabilities(AiRequest.Provider provider, String model, Support text,
                             Support streamingText, Support image, Support usage, int maxOutputTokens) {
        this.provider = Objects.requireNonNull(provider);
        this.model = Objects.requireNonNull(model);
        this.text = Objects.requireNonNull(text);
        this.streamingText = Objects.requireNonNull(streamingText);
        this.image = Objects.requireNonNull(image);
        this.usage = Objects.requireNonNull(usage);
        if (maxOutputTokens <= 0) throw new IllegalArgumentException("Invalid model output limit");
        this.maxOutputTokens = maxOutputTokens;
    }

    public boolean allowsTextStream(AiRequest request) {
        return provider == request.provider && model.equals(request.model)
                && request.task == AiRequest.Task.TEXT && text == Support.SUPPORTED
                && streamingText == Support.SUPPORTED && request.maxOutputTokens <= maxOutputTokens;
    }
}
