package com.socialpilot.app.data.ai;

import static com.socialpilot.app.domain.contract.ai.ModelCapabilities.Support.*;
import static org.junit.Assert.*;

import com.socialpilot.app.data.ai.pipeline.ChannelPipeline;
import com.socialpilot.app.domain.agent.ChannelBranch;
import com.socialpilot.app.domain.agent.ChannelPlatform;
import com.socialpilot.app.domain.agent.ChannelTarget;
import com.socialpilot.app.domain.agent.PipelineInput;
import com.socialpilot.app.domain.agent.PipelineSnapshot;
import com.socialpilot.app.domain.agent.PipelineStage;
import com.socialpilot.app.domain.agent.RuleCheck;
import com.socialpilot.app.domain.contract.AiProvider;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.ModelCapabilities;
import com.socialpilot.app.domain.contract.ai.ModelEntry;
import com.socialpilot.app.domain.contract.ai.ModelRegistry;
import com.socialpilot.app.domain.contract.ai.PriceSheet;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import com.socialpilot.app.testing.MemoryPreferences;
import com.socialpilot.app.testing.RecordingBudget;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Text pipeline → gateway → real OpenAI adapter → MockWebServer (labelled fixture responses). */
public final class ChannelPipelineHttpTest {
    private MockWebServer server;
    private ExecutorService workers;

    @Before public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        workers = Executors.newFixedThreadPool(4);
    }

    @After public void tearDown() throws Exception {
        workers.shutdownNow();
        server.shutdown();
    }

    private static MockResponse stream(String text) {
        String escaped = text.replace("\n", "\\n");
        return new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"m\",\"content_index\":0,\"delta\":\""
                        + escaped + "\"}\n\n"
                        + "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"usage\":"
                        + "{\"input_tokens\":40,\"output_tokens\":10,\"total_tokens\":50}}}\n\n");
    }

    @Test public void singleFacebookTargetSendsThreeCappedRequestsAndSettlesEach() throws Exception {
        PriceSheet price = new PriceSheet(Currency.getInstance("USD"), new BigDecimal("1.00"), new BigDecimal("0.25"),
                new BigDecimal("4.00"), "fixture-2026-11-23", "fixture", "2026-11-23T00:00:00Z");
        ModelCapabilities capabilities = new ModelCapabilities(AiRequest.Provider.OPENAI, "fixture-open",
                SUPPORTED, SUPPORTED, UNSUPPORTED, SUPPORTED, 4096);
        ModelEntry entry = new ModelEntry(capabilities, EnumSet.of(AiTaskType.CONTENT_TEXT),
                ModelEntry.Verification.VERIFIED, price, "fixture");
        ModelRegistry registry = new ModelRegistry("fixture", Arrays.asList(entry));
        ModelSelectionStore selections = new ModelSelectionStore(new MemoryPreferences(), registry);
        selections.select(AiTaskType.CONTENT_TEXT, entry);
        Map<AiRequest.Provider, AiProvider> providers = new EnumMap<>(AiRequest.Provider.class);
        providers.put(AiRequest.Provider.OPENAI, new OpenAiProvider(server.url("/v1/responses"),
                () -> "fake-openai-key-for-fixture", registry.capabilities(AiRequest.Provider.OPENAI), 5_000));
        RecordingBudget budget = new RecordingBudget();
        AiGateway gateway = new AiGateway(selections, providers, p -> true, budget, workers, System::currentTimeMillis);
        server.enqueue(stream("- Mở đầu\n- Thực đơn"));
        server.enqueue(stream("Quán mới mở! Xem thực đơn: https://example.com/menu"));
        server.enqueue(stream("VERDICT: PASS"));

        AtomicReference<PipelineSnapshot> last = new AtomicReference<>();
        CountDownLatch settled = new CountDownLatch(1);
        new ChannelPipeline(gateway, workers, System::currentTimeMillis).start(
                new PipelineInput("brief-1", "Khai trương", "Tăng lượt ghé", "Sinh viên", "Thân thiện", "vi",
                        Collections.singletonList(new PipelineInput.Source(true, "https://example.com/menu")),
                        Collections.singletonList(new ChannelTarget("page-1", ChannelPlatform.FACEBOOK, 3))),
                snapshot -> {
                    last.set(snapshot);
                    if (snapshot.settled()) settled.countDown();
                });
        assertTrue(settled.await(10, TimeUnit.SECONDS));
        ChannelBranch branch = last.get().branch("page-1");
        assertEquals(ChannelBranch.Status.TEXT_READY, branch.status);
        assertEquals(RuleCheck.Verdict.PASS, branch.ruleCheck.verdict);
        assertTrue(branch.ruleCheck.clean()); // The only link is a brief source.
        int[] caps = {400, 1000, 300};
        for (int i = 0; i < 3; i++) {
            RecordedRequest sent = server.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("Bearer fake-openai-key-for-fixture", sent.getHeader("Authorization"));
            String body = sent.getBody().readUtf8();
            assertTrue(body, body.contains("\"max_output_tokens\":" + caps[i]));
            if (i == 1) assertTrue(body.contains("Thực đơn")); // Draft prompt carries the outline.
        }
        assertEquals(3, server.getRequestCount());
        for (PipelineStage stage : Arrays.asList(PipelineStage.OUTLINE, PipelineStage.DRAFT, PipelineStage.RULE_CHECK)) {
            assertEquals(UsageRecord.Settlement.SETTLED, branch.stage(stage).settlement);
            assertEquals(Long.valueOf(80), branch.stage(stage).estimateMicros); // 40 * 1.00 + 10 * 4.00
        }
        assertEquals(3, budget.count("settle:"));
    }
}
