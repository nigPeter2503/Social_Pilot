package com.socialpilot.app.domain.contract.ai;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.Objects;

/**
 * Text token prices for one model, in currency units per million tokens. A sheet is only valid
 * with its source and the time it was captured; estimates are never invoices.
 */
public final class PriceSheet {
    public final Currency currency;
    public final BigDecimal uncachedInputPerMillion;
    public final BigDecimal cachedInputPerMillion;
    public final BigDecimal outputPerMillion;
    /** Currency units per generated image; null when the model has no sourced image price. */
    public final BigDecimal perImage;
    public final String priceRevision;
    public final String source;
    public final String capturedAt;

    public PriceSheet(Currency currency, BigDecimal uncachedInputPerMillion, BigDecimal cachedInputPerMillion,
                      BigDecimal outputPerMillion, String priceRevision, String source, String capturedAt) {
        this(currency, uncachedInputPerMillion, cachedInputPerMillion, outputPerMillion, null, priceRevision, source,
                capturedAt);
    }

    public PriceSheet(Currency currency, BigDecimal uncachedInputPerMillion, BigDecimal cachedInputPerMillion,
                      BigDecimal outputPerMillion, BigDecimal perImage, String priceRevision, String source,
                      String capturedAt) {
        this.currency = Objects.requireNonNull(currency);
        this.uncachedInputPerMillion = nonNegative(uncachedInputPerMillion);
        this.cachedInputPerMillion = nonNegative(cachedInputPerMillion);
        this.outputPerMillion = nonNegative(outputPerMillion);
        this.perImage = perImage == null ? null : nonNegative(perImage);
        this.priceRevision = required(priceRevision);
        this.source = required(source);
        this.capturedAt = required(capturedAt);
    }

    /**
     * Conservative reservation before dispatch. Byte-level tokenizers emit at most one token per
     * UTF-8 byte, so the prompt's byte length bounds input tokens; output is bounded by the cap.
     * All input is priced uncached because caching is unknown before the call.
     */
    public AiCostEstimate upperBound(AiRequest request) {
        long inputBound = request.prompt.getBytes(StandardCharsets.UTF_8).length;
        return AiCostEstimate.text(inputBound, 0, request.maxOutputTokens,
                uncachedInputPerMillion.max(cachedInputPerMillion), cachedInputPerMillion,
                outputPerMillion, currency, priceRevision);
    }

    /**
     * Maps provider usage to non-overlapping billable buckets, or returns null when the usage is
     * insufficient. A null result must never be treated as zero cost.
     * <ul>
     *   <li>OpenAI: input includes cached input; output already includes reasoning.</li>
     *   <li>Gemini: prompt includes cached content; thoughts are billed as output in addition to
     *   candidate tokens.</li>
     * </ul>
     */
    public AiCostEstimate fromUsage(AiRequest.Provider provider, AiUsage usage) {
        if (usage == null || usage.inputTokens == null || usage.outputTokens == null) return null;
        long cached = usage.cachedInputTokens == null ? 0 : usage.cachedInputTokens;
        if (cached > usage.inputTokens) return null;
        try {
            long output = usage.outputTokens;
            if (provider == AiRequest.Provider.GEMINI && usage.reasoningTokens != null) {
                output = Math.addExact(output, usage.reasoningTokens);
            }
            return AiCostEstimate.text(usage.inputTokens - cached, cached, output,
                    uncachedInputPerMillion, cachedInputPerMillion, outputPerMillion, currency, priceRevision);
        } catch (ArithmeticException unpriceable) {
            return null;
        }
    }

    /** Estimate for {@code images} generated images, or null without a sourced per-image price. */
    public AiCostEstimate imageCost(int images) {
        return perImage == null ? null : AiCostEstimate.image(images, perImage, currency, priceRevision);
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Invalid rate");
        return value;
    }

    private static String required(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Missing price provenance");
        return value;
    }
}
