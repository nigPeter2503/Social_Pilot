package com.socialpilot.app.domain.contract.ai;

import static com.socialpilot.app.domain.contract.ai.ModelCapabilities.Support.*;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Currency;
import java.util.EnumSet;
import org.junit.Test;

public final class ModelPricingTest {
    /** Labelled fixture rates, not a provider price list. */
    static final PriceSheet FIXTURE_PRICE = new PriceSheet(Currency.getInstance("USD"),
            new BigDecimal("1.00"), new BigDecimal("0.25"), new BigDecimal("4.00"),
            "fixture-2026-11-23", "fixture", "2026-11-23T00:00:00Z");

    static ModelEntry entry(AiRequest.Provider provider, String model, ModelCapabilities.Support text,
                            ModelEntry.Verification verification, PriceSheet price) {
        return new ModelEntry(new ModelCapabilities(provider, model, text, text, UNSUPPORTED, SUPPORTED, 512),
                EnumSet.of(AiTaskType.CONTENT_TEXT), verification, price, "fixture");
    }

    private static AiRequest request(String prompt, int cap) {
        return new AiRequest("s", null, null, "step", "r1", AiRequest.Provider.OPENAI, "m",
                AiRequest.Task.TEXT, prompt, cap);
    }

    @Test public void onlyVerifiedSupportedPricedModelsAreDispatchable() {
        ModelEntry ready = entry(AiRequest.Provider.OPENAI, "m", SUPPORTED, ModelEntry.Verification.VERIFIED, FIXTURE_PRICE);
        assertEquals(ModelEntry.Block.NONE, ready.blockFor(AiTaskType.CONTENT_TEXT, 512));
        assertEquals(ModelEntry.Block.OUTPUT_CAP_EXCEEDED, ready.blockFor(AiTaskType.CONTENT_TEXT, 513));
        assertEquals(ModelEntry.Block.TASK_NOT_ALLOWED, ready.blockFor(AiTaskType.COMMENT_REPLY, 10));
        assertEquals(ModelEntry.Block.CAPABILITY_UNSUPPORTED, entry(AiRequest.Provider.OPENAI, "m", UNSUPPORTED,
                ModelEntry.Verification.VERIFIED, FIXTURE_PRICE).blockFor(AiTaskType.CONTENT_TEXT, 10));
        assertEquals(ModelEntry.Block.CAPABILITY_UNKNOWN, entry(AiRequest.Provider.OPENAI, "m", UNKNOWN,
                ModelEntry.Verification.VERIFIED, FIXTURE_PRICE).blockFor(AiTaskType.CONTENT_TEXT, 10));
        assertEquals(ModelEntry.Block.UNVERIFIED, entry(AiRequest.Provider.OPENAI, "m", SUPPORTED,
                ModelEntry.Verification.UNVERIFIED, FIXTURE_PRICE).blockFor(AiTaskType.CONTENT_TEXT, 10));
        assertEquals(ModelEntry.Block.PRICE_MISSING, entry(AiRequest.Provider.OPENAI, "m", SUPPORTED,
                ModelEntry.Verification.VERIFIED, null).blockFor(AiTaskType.CONTENT_TEXT, 10));
    }

    @Test public void imageTasksUseTheSeparateImageCapability() {
        ModelEntry textOnly = new ModelEntry(new ModelCapabilities(AiRequest.Provider.GEMINI, "m", SUPPORTED,
                SUPPORTED, UNKNOWN, SUPPORTED, 512), EnumSet.of(AiTaskType.IMAGE), ModelEntry.Verification.VERIFIED,
                FIXTURE_PRICE, "fixture");
        assertEquals(ModelEntry.Block.CAPABILITY_UNKNOWN, textOnly.blockFor(AiTaskType.IMAGE, 10));
    }

    @Test public void registryRejectsDuplicatesAndUnknownPairs() {
        ModelEntry a = entry(AiRequest.Provider.OPENAI, "m", SUPPORTED, ModelEntry.Verification.VERIFIED, FIXTURE_PRICE);
        ModelRegistry registry = new ModelRegistry("r", Arrays.asList(a,
                entry(AiRequest.Provider.GEMINI, "m", SUPPORTED, ModelEntry.Verification.VERIFIED, FIXTURE_PRICE)));
        assertSame(a, registry.find(AiRequest.Provider.OPENAI, "m"));
        assertNull(registry.find(AiRequest.Provider.OPENAI, "other"));
        assertEquals(1, registry.capabilities(AiRequest.Provider.GEMINI).size());
        assertThrows(IllegalArgumentException.class, () -> new ModelRegistry("r", Arrays.asList(a, a)));
    }

    @Test public void upperBoundCoversWorstCaseInputBytesAndFullOutputCap() {
        // "Xin chào" is 9 UTF-8 bytes: 9 * 1.00 + 100 * 4.00 = 409 micros.
        assertEquals(409, FIXTURE_PRICE.upperBound(request("Xin chào", 100)).micros);
    }

    @Test public void openAiCachedInputIsASubsetAndReasoningIsInsideOutput() {
        AiUsage usage = new AiUsage(1000L, 200L, 1200L, 400L, 150L);
        // 600 * 1.00 + 400 * 0.25 + 200 * 4.00 = 1500 micros.
        assertEquals(1500, FIXTURE_PRICE.fromUsage(AiRequest.Provider.OPENAI, usage).micros);
    }

    @Test public void geminiThoughtsAreBilledAsOutputInAdditionToCandidates() {
        AiUsage usage = new AiUsage(1000L, 200L, 1350L, 0L, 150L);
        // 1000 * 1.00 + 350 * 4.00 = 2400 micros.
        assertEquals(2400, FIXTURE_PRICE.fromUsage(AiRequest.Provider.GEMINI, usage).micros);
    }

    @Test public void missingUsageIsNotPricedAsZeroButReportedZeroIs() {
        assertNull(FIXTURE_PRICE.fromUsage(AiRequest.Provider.OPENAI, AiUsage.MISSING));
        assertNull(FIXTURE_PRICE.fromUsage(AiRequest.Provider.OPENAI, new AiUsage(10L, null, null, null, null)));
        assertNull(FIXTURE_PRICE.fromUsage(AiRequest.Provider.OPENAI, new AiUsage(10L, 1L, 11L, 20L, null)));
        assertEquals(0, FIXTURE_PRICE.fromUsage(AiRequest.Provider.OPENAI, new AiUsage(0L, 0L, 0L, 0L, 0L)).micros);
    }

    @Test public void priceSheetRequiresProvenance() {
        assertThrows(IllegalArgumentException.class, () -> new PriceSheet(Currency.getInstance("USD"),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "rev", " ", "2026-11-23"));
        assertThrows(IllegalArgumentException.class, () -> new PriceSheet(Currency.getInstance("USD"),
                BigDecimal.ONE.negate(), BigDecimal.ONE, BigDecimal.ONE, "rev", "src", "2026-11-23"));
    }

    @Test public void periodUsesTheZoneDateAndPinsKeys() {
        long instant = 1794418200000L; // 2026-11-11T17:30:00Z
        BudgetPeriod bangkok = BudgetPeriod.of(instant, "Asia/Bangkok");
        assertEquals("2026-11-12", bangkok.dayKey);
        assertEquals("2026-11", bangkok.monthKey);
        assertEquals("2026-11-11", BudgetPeriod.of(instant, "UTC").dayKey);
        assertEquals("2026-12", BudgetPeriod.of(1796059800000L, "Asia/Bangkok").monthKey); // 2026-11-30T17:30Z
        assertThrows(IllegalArgumentException.class, () -> BudgetPeriod.of(instant, "Mars/Base"));
    }
}
