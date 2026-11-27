package com.socialpilot.app.domain.contract.ai;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One registry row. Only a VERIFIED row whose capabilities are SUPPORTED and that has a price
 * sheet can be dispatched; everything else is visible but blocked, with the reason.
 */
public final class ModelEntry {
    public enum Verification { VERIFIED, UNVERIFIED }

    public enum Block {
        NONE, UNVERIFIED, CAPABILITY_UNKNOWN, CAPABILITY_UNSUPPORTED, TASK_NOT_ALLOWED,
        OUTPUT_CAP_EXCEEDED, PRICE_MISSING
    }

    public final ModelCapabilities capabilities;
    public final Set<AiTaskType> tasks;
    public final Verification verification;
    /** Null while the price is not confirmed; the model then cannot be dispatched. */
    public final PriceSheet price;
    /** Where the capability and price must be checked; never a credential. */
    public final String source;

    public ModelEntry(ModelCapabilities capabilities, Set<AiTaskType> tasks, Verification verification,
                      PriceSheet price, String source) {
        this.capabilities = Objects.requireNonNull(capabilities);
        if (tasks == null || tasks.isEmpty()) throw new IllegalArgumentException("Model needs tasks");
        this.tasks = Collections.unmodifiableSet(EnumSet.copyOf(tasks));
        this.verification = Objects.requireNonNull(verification);
        this.price = price;
        this.source = Objects.requireNonNull(source);
    }

    public AiRequest.Provider provider() {
        return capabilities.provider;
    }

    public String model() {
        return capabilities.model;
    }

    /** The first reason this row cannot serve {@code task} with {@code maxOutputTokens}. */
    public Block blockFor(AiTaskType task, int maxOutputTokens) {
        if (!tasks.contains(task)) return Block.TASK_NOT_ALLOWED;
        ModelCapabilities.Support support = task.task == AiRequest.Task.IMAGE
                ? capabilities.image : weakest(capabilities.text, capabilities.streamingText);
        if (support == ModelCapabilities.Support.UNSUPPORTED) return Block.CAPABILITY_UNSUPPORTED;
        if (support == ModelCapabilities.Support.UNKNOWN) return Block.CAPABILITY_UNKNOWN;
        if (verification != Verification.VERIFIED) return Block.UNVERIFIED;
        if (maxOutputTokens > capabilities.maxOutputTokens) return Block.OUTPUT_CAP_EXCEEDED;
        if (price == null || (task.task == AiRequest.Task.IMAGE && price.perImage == null)) return Block.PRICE_MISSING;
        return Block.NONE;
    }

    private static ModelCapabilities.Support weakest(ModelCapabilities.Support a, ModelCapabilities.Support b) {
        if (a == ModelCapabilities.Support.UNSUPPORTED || b == ModelCapabilities.Support.UNSUPPORTED) {
            return ModelCapabilities.Support.UNSUPPORTED;
        }
        if (a == ModelCapabilities.Support.UNKNOWN || b == ModelCapabilities.Support.UNKNOWN) {
            return ModelCapabilities.Support.UNKNOWN;
        }
        return ModelCapabilities.Support.SUPPORTED;
    }
}
