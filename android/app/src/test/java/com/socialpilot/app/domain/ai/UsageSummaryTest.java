package com.socialpilot.app.domain.ai;

import static org.junit.Assert.*;

import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.BudgetPeriod;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class UsageSummaryTest {
    private static final BudgetPeriod DAY_12 = BudgetPeriod.pinned("Asia/Bangkok", "2026-11-12", "2026-11");
    private static final BudgetPeriod DAY_13 = BudgetPeriod.pinned("Asia/Bangkok", "2026-11-13", "2026-11");
    private static final AiUsage REPORTED = new AiUsage(10L, 5L, 15L, null, null);

    private static UsageRecord record(String id, AiRequest.Provider provider, BudgetPeriod period,
                                      AiUsage usage, UsageRecord.Settlement settlement, Long micros, String currency) {
        return new UsageRecord(id, "s", null, "step", AiTaskType.CONTENT_TEXT, provider, "m", period, 0L,
                usage, settlement, micros, currency, "fixture-rev");
    }

    @Test public void uncertainAndMissingUsageAreCountedButNeverAddedAsZero() {
        List<UsageRecord> records = Arrays.asList(
                record("a", AiRequest.Provider.OPENAI, DAY_12, REPORTED, UsageRecord.Settlement.SETTLED, 120L, "USD"),
                record("b", AiRequest.Provider.OPENAI, DAY_12, AiUsage.MISSING, UsageRecord.Settlement.UNCERTAIN, null, "USD"),
                record("c", AiRequest.Provider.OPENAI, DAY_12, AiUsage.MISSING, UsageRecord.Settlement.RELEASED, null, "USD"));
        UsageSummary.Row row = UsageSummary.summarize(records, UsageSummary.Scope.DAY, "2026-11-12").get(0);
        assertEquals(120, row.estimatedMicros);
        assertEquals(1, row.settledRequests);
        assertEquals(1, row.uncertainRequests);
        assertEquals(1, row.releasedRequests);
        assertEquals(2, row.missingUsage);
        assertFalse(row.complete());
    }

    @Test public void providersPeriodsAndCurrenciesStaySeparate() {
        List<UsageRecord> records = Arrays.asList(
                record("a", AiRequest.Provider.OPENAI, DAY_12, REPORTED, UsageRecord.Settlement.SETTLED, 100L, "USD"),
                record("b", AiRequest.Provider.GEMINI, DAY_12, REPORTED, UsageRecord.Settlement.SETTLED, 50L, "USD"),
                record("c", AiRequest.Provider.GEMINI, DAY_13, REPORTED, UsageRecord.Settlement.SETTLED, 70L, "USD"),
                record("d", AiRequest.Provider.GEMINI, DAY_13, REPORTED, UsageRecord.Settlement.SETTLED, 9L, "EUR"));
        assertEquals(2, UsageSummary.summarize(records, UsageSummary.Scope.DAY, "2026-11-12").size());
        List<UsageSummary.Row> month = UsageSummary.summarize(records, UsageSummary.Scope.MONTH, "2026-11");
        assertEquals(3, month.size());
        assertEquals(120, month.get(1).estimatedMicros); // Gemini USD on both days.
        assertEquals("EUR", month.get(2).currency);
    }

    @Test public void duplicateRequestIdsCountOnce() {
        UsageRecord once = record("a", AiRequest.Provider.OPENAI, DAY_12, REPORTED, UsageRecord.Settlement.SETTLED, 100L, "USD");
        UsageSummary.Row row = UsageSummary.summarize(Arrays.asList(once, once), UsageSummary.Scope.DAY, "2026-11-12").get(0);
        assertEquals(100, row.estimatedMicros);
        assertEquals(1, row.settledRequests);
    }

    @Test public void recordRequiresEstimateExactlyWhenSettled() {
        assertThrows(IllegalArgumentException.class, () ->
                record("a", AiRequest.Provider.OPENAI, DAY_12, REPORTED, UsageRecord.Settlement.UNCERTAIN, 0L, "USD"));
        assertThrows(IllegalArgumentException.class, () ->
                record("a", AiRequest.Provider.OPENAI, DAY_12, REPORTED, UsageRecord.Settlement.SETTLED, null, "USD"));
    }
}
