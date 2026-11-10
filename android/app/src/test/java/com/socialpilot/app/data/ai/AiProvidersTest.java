package com.socialpilot.app.data.ai;

import static com.socialpilot.app.domain.contract.ai.AiError.Code.*;
import static com.socialpilot.app.domain.contract.ai.AiEvent.Type.*;
import static com.socialpilot.app.domain.contract.ai.AiRequest.Provider.*;
import static com.socialpilot.app.domain.contract.ai.ModelCapabilities.Support.*;
import static org.junit.Assert.*;

import com.socialpilot.app.domain.contract.AiProvider;
import com.socialpilot.app.domain.contract.ai.AiError.Code;
import com.socialpilot.app.domain.contract.ai.AiEvent;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.ModelCapabilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class AiProvidersTest {
    private static final String KEY = "mock-credential";
    private static final String MODEL = "fixture-model";
    private static final String OPEN_DELTA = "{\"type\":\"response.output_text.delta\","
            + "\"item_id\":\"msg_a\",\"content_index\":0,\"delta\":\"%s\"}";
    private static final String OPEN_DONE = "{\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}";
    private static final String GEM_DELTA = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"%s\"}]}}]}";
    private MockWebServer server;

    @Before public void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After public void stopServer() throws Exception {
        server.shutdown();
    }

    @Test public void openAiReadsFragmentedUtf8AndDoesNotAppendDoneText() throws Exception {
        String data = ": heartbeat\r\n\r\n"
                + frame(String.format(OPEN_DELTA, "Xin "))
                + "event: response.output_text.delta\r\ndata: {\"type\":\"response.output_text.delta\",\r\n"
                + "data: \"item_id\":\"msg_a\",\"content_index\":0,\"delta\":\"chào 👋\"}\r\n\r\n"
                + frame("{\"type\":\"response.output_text.done\",\"text\":\"Xin chào 👋\"}")
                + frame("{\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"usage\":"
                + "{\"input_tokens\":8,\"output_tokens\":5,\"total_tokens\":13,"
                + "\"input_tokens_details\":{\"cached_tokens\":0},"
                + "\"output_tokens_details\":{\"reasoning_tokens\":2}}}}");
        server.enqueue(sse().setChunkedBody(data, 1));
        List<AiEvent> events = run(open(), request(OPENAI));
        assertEquals(4, events.size());
        assertEquals("Xin chào 👋", text(events));
        AiEvent end = end(events);
        assertEquals(COMPLETED, end.type);
        assertEquals(AiUsage.State.REPORTED, end.usage.state);
        assertEquals(Long.valueOf(13), end.usage.totalTokens);
        assertEquals(Long.valueOf(0), end.usage.cachedInputTokens);
        assertEquals(Long.valueOf(2), end.usage.reasoningTokens);
        RecordedRequest sent = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(sent);
        assertEquals("/v1/responses", sent.getPath());
        assertEquals("Bearer " + KEY, sent.getHeader("Authorization"));
        assertTrue(sent.getBody().readUtf8().contains("\"store\":false"));
    }

    @Test public void geminiHandlesTextPartsAndTrailingUsageWithoutSumming() throws Exception {
        String usage = "{\"usageMetadata\":{\"promptTokenCount\":4,\"candidatesTokenCount\":3,"
                + "\"totalTokenCount\":9,\"thoughtsTokenCount\":2}}";
        server.enqueue(sse().setChunkedBody(frame(usage)
                + frame("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"internal\",\"thought\":true},"
                + "{\"text\":\"Xin \"},{\"text\":\"chào\"}]},\"finishReason\":\"STOP\",\"index\":0}]}")
                + frame(usage), 3));
        List<AiEvent> events = run(gemini(), request(GEMINI));
        assertEquals("Xin chào", text(events));
        AiEvent end = end(events);
        assertEquals(COMPLETED, end.type);
        assertEquals(AiUsage.State.REPORTED, end.usage.state);
        assertEquals(Long.valueOf(9), end.usage.totalTokens);
        assertEquals(Long.valueOf(2), end.usage.reasoningTokens);
        assertNull(end.usage.cachedInputTokens);
        RecordedRequest sent = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(sent);
        assertEquals("/v1beta/models/fixture-model:streamGenerateContent?alt=sse", sent.getPath());
        assertEquals(KEY, sent.getHeader("x-goog-api-key"));
        assertNull(sent.getHeader("Authorization"));
        assertFalse(sent.getBody().readUtf8().contains(KEY));
    }

    @Test public void openAiUsageAbsentIsNotZero() {
        server.enqueue(sse().setBody(frame(String.format(OPEN_DELTA, "Hello")) + frame(OPEN_DONE)));
        AiEvent event = end(run(open(), request(OPENAI)));
        assertEquals(COMPLETED, event.type);
        assertEquals(AiUsage.State.MISSING, event.usage.state);
        assertNull(event.usage.totalTokens);
    }

    @Test public void openAiRejectsSecondOutputPart() {
        server.enqueue(sse().setBody(frame(String.format(OPEN_DELTA, "partial"))
                + frame(String.format(OPEN_DELTA, "other").replace("msg_a", "msg_b")) + frame(OPEN_DONE)));
        List<AiEvent> events = run(open(), request(OPENAI));
        assertEquals("partial", text(events));
        assertFailure(events, UNSUPPORTED_CAPABILITY, true);
    }

    @Test public void openAiIncompleteAndRefusalAreNotSuccess() {
        server.enqueue(sse().setBody(frame(String.format(OPEN_DELTA, "partial"))
                + frame("{\"type\":\"response.incomplete\",\"response\":{\"incomplete_details\":"
                + "{\"reason\":\"max_output_tokens\"}}}")));
        assertFailure(run(open(), request(OPENAI)), INCOMPLETE, true);
        server.enqueue(sse().setBody(frame("{\"type\":\"response.refusal.delta\",\"delta\":\"not shown\"}")
                + frame(OPEN_DONE)));
        List<AiEvent> events = run(open(), request(OPENAI));
        assertEquals("", text(events));
        assertFailure(events, CONTENT_BLOCKED, false);
    }

    @Test public void geminiRecognizesBlockedPromptAndIncompleteCandidate() {
        server.enqueue(sse().setBody(frame("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")));
        assertFailure(run(gemini(), request(GEMINI)), CONTENT_BLOCKED, false);
        server.enqueue(sse().setBody(frame(String.format(GEM_DELTA, "prefix"))
                + frame("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}")));
        assertFailure(run(gemini(), request(GEMINI)), INCOMPLETE, true);
    }

    @Test public void geminiEofWithoutStopMarksInterimUsagePartial() {
        server.enqueue(sse().setBody(frame(String.format(GEM_DELTA, "prefix"))
                + frame("{\"usageMetadata\":{\"promptTokenCount\":2,\"candidatesTokenCount\":1,\"totalTokenCount\":3}}")));
        List<AiEvent> events = run(gemini(), request(GEMINI));
        assertFailure(events, INCOMPLETE, true);
        assertEquals(AiUsage.State.PARTIAL, end(events).usage.state);
        assertEquals(Long.valueOf(3), end(events).usage.totalTokens);
    }

    @Test public void malformedOrTruncatedSseAndWrongContentTypeAreProtocolErrors() {
        server.enqueue(sse().setBody(frame("{bad")));
        assertFailure(run(open(), request(OPENAI)), PROTOCOL_ERROR, false);
        server.enqueue(sse().setBody("data: " + OPEN_DONE));
        assertFailure(run(open(), request(OPENAI)), PROTOCOL_ERROR, false);
        server.enqueue(new MockResponse().setBody("{}"));
        assertFailure(run(gemini(), request(GEMINI)), PROTOCOL_ERROR, false);
    }

    @Test public void oversizedUnterminatedLineIsRejected() {
        StringBuilder body = new StringBuilder("data: ");
        for (int i = 0; i < 270_000; i++) body.append('x');
        server.enqueue(sse().setBody(body.toString()));
        assertFailure(run(open(), request(OPENAI)), PROTOCOL_ERROR, false);
    }

    @Test public void connectionInterruptedAndMissingTerminalCannotComplete() {
        server.enqueue(sse().setBody(frame(String.format(OPEN_DELTA, "prefix"))));
        assertFailure(run(open(), request(OPENAI)), INCOMPLETE, true);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        assertFailure(run(open(), request(OPENAI)), CONNECTION_FAILED, false);
    }

    @Test public void normalizesAuthPermissionRateLimitQuotaAndUnavailable() {
        enqueueError(401, "{}");
        assertFailure(run(open(), request(OPENAI)), INVALID_CREDENTIAL, false);
        enqueueError(403, "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}");
        assertFailure(run(gemini(), request(GEMINI)), PERMISSION_DENIED, false);
        enqueueError(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}");
        List<AiEvent> rate = run(gemini(), request(GEMINI));
        assertFailure(rate, RATE_LIMITED, false);
        assertEquals(Long.valueOf(7), end(rate).error.retryAfterSeconds);
        enqueueError(429, "{\"error\":{\"code\":\"insufficient_quota\"}}");
        assertFailure(run(open(), request(OPENAI)), QUOTA_EXHAUSTED, false);
        enqueueError(400, "{\"error\":{\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}");
        assertFailure(run(gemini(), request(GEMINI)), INVALID_CREDENTIAL, false);
        enqueueError(503, "{}");
        assertFailure(run(open(), request(OPENAI)), PROVIDER_UNAVAILABLE, false);
    }

    @Test public void redirectAndRetryAfterZeroDoNotSendAnotherRequest() {
        server.enqueue(new MockResponse().setResponseCode(307).setHeader("Location", server.url("/other")));
        assertFailure(run(open(), request(OPENAI)), PROTOCOL_ERROR, false);
        server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "0"));
        // A second queued reply would hide an unintended automatic repeat.
        server.enqueue(sse().setBody(frame(String.format(OPEN_DELTA, "duplicate")) + frame(OPEN_DONE)));
        assertFailure(run(open(), request(OPENAI)), PROVIDER_UNAVAILABLE, false);
        assertEquals(2, server.getRequestCount());
    }

    @Test public void unsupportedCapabilityDoesNotReadCredentialsOrSend() {
        AiProvider provider = new OpenAiProvider(server.url("/v1/responses"),
                () -> { throw new AssertionError("Credential source called"); }, Collections.emptyList(), 2000);
        List<AiEvent> events = run(provider, request(OPENAI));
        assertEquals(1, events.size());
        assertFailure(events, UNSUPPORTED_CAPABILITY, false);
        assertFalse(end(events).mayHaveDispatched);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void missingCredentialDoesNotDispatch() {
        AiProvider provider = new OpenAiProvider(server.url("/v1/responses"),
                () -> null, models(OPENAI), 2000);
        List<AiEvent> events = run(provider, request(OPENAI));
        assertFailure(events, INVALID_CREDENTIAL, false);
        assertFalse(end(events).mayHaveDispatched);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void cancelBeforeRunAndRunTwiceOnlyEmitOneTerminal() {
        List<AiEvent> events = new ArrayList<>();
        AiProvider.Attempt attempt = open().prepare(request(OPENAI), events::add);
        assertTrue(events.isEmpty());
        attempt.cancel();
        attempt.run();
        attempt.run();
        attempt.cancel();
        assertEquals(1, events.size());
        assertEquals(CANCELLED, end(events).type);
        assertFalse(end(events).mayHaveDispatched);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void cancellationDuringCredentialLoadPreventsDispatch() {
        List<AiEvent> events = new ArrayList<>();
        AiProvider.Attempt[] handle = new AiProvider.Attempt[1];
        AiProvider provider = new OpenAiProvider(server.url("/v1/responses"), () -> {
            handle[0].cancel();
            return KEY;
        }, models(OPENAI), 2000);
        handle[0] = provider.prepare(request(OPENAI), events::add);
        handle[0].run();
        assertEquals(CANCELLED, end(events).type);
        assertFalse(end(events).mayHaveDispatched);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void cancelInterruptsBlockedHttpReadAndKeepsPartial() throws Exception {
        // MockWebServer's throttle also applies while reading the incoming request.
        String first = ": padding so the first burst is larger than the request body\n"
                + ": padding so the first burst is larger than the request body\n"
                + frame(String.format(OPEN_DELTA, "prefix"));
        String data = first + frame(OPEN_DONE);
        server.enqueue(sse().setBody(data).throttleBody(first.length(), 3, TimeUnit.SECONDS));
        List<AiEvent> events = new ArrayList<>();
        CountDownLatch gotDelta = new CountDownLatch(1);
        AiProvider.Attempt attempt = open(30_000).prepare(request(OPENAI), event -> {
            events.add(event);
            if (event.type == TEXT_DELTA) gotDelta.countDown();
        });
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> running = worker.submit(attempt);
            assertTrue(gotDelta.await(5, TimeUnit.SECONDS));
            attempt.cancel();
            attempt.cancel();
            running.get(2, TimeUnit.SECONDS);
            assertEquals("prefix", text(events));
            assertEquals(3, events.size());
            assertEquals(CANCELLED, end(events).type);
            assertTrue(end(events).partial);
            assertTrue(end(events).mayHaveDispatched);
        } finally {
            attempt.cancel();
            worker.shutdownNow();
        }
    }

    @Test public void cancelUnblocksAnHttpCallWaitingForHeaders() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        List<AiEvent> events = new ArrayList<>();
        AiProvider.Attempt attempt = open(30_000).prepare(request(OPENAI), events::add);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> running = worker.submit(attempt);
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS));
            attempt.cancel();
            running.get(2, TimeUnit.SECONDS);
            assertEquals(CANCELLED, end(events).type);
            assertTrue(end(events).mayHaveDispatched);
        } finally {
            attempt.cancel();
            worker.shutdownNow();
        }
    }

    @Test public void readTimeoutIsDistinctFromCancellation() {
        server.enqueue(sse().setBody(frame(OPEN_DONE)).setBodyDelay(3, TimeUnit.SECONDS));
        assertFailure(run(open(100), request(OPENAI)), TIMEOUT, false);
    }

    private void enqueueError(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setHeader("Retry-After", "7").setBody(body));
    }

    private AiProvider open() { return open(2000); }

    private AiProvider open(long timeoutMillis) {
        return new OpenAiProvider(server.url("/v1/responses"), () -> KEY, models(OPENAI), timeoutMillis);
    }

    private AiProvider gemini() {
        return new GeminiAiProvider(server.url("/v1beta/models/"), () -> KEY, models(GEMINI), 2000);
    }

    private static List<ModelCapabilities> models(AiRequest.Provider provider) {
        return Collections.singletonList(new ModelCapabilities(provider, MODEL, SUPPORTED, SUPPORTED,
                UNKNOWN, SUPPORTED, 64));
    }

    private static AiRequest request(AiRequest.Provider provider) {
        return new AiRequest("session", "channel", 2L, "step", "request", provider,
                MODEL, AiRequest.Task.TEXT, "synthetic prompt", 32);
    }

    private static List<AiEvent> run(AiProvider provider, AiRequest request) {
        List<AiEvent> events = new ArrayList<>();
        AiProvider.Attempt attempt = provider.prepare(request, events::add);
        assertTrue(events.isEmpty());
        attempt.run();
        for (int i = 0; i < events.size(); i++) {
            AiEvent event = events.get(i);
            assertEquals(i + 1, event.sequence);
            assertEquals("session", event.sessionId);
            assertEquals("channel", event.channelId);
            assertEquals("step", event.stepId);
            assertEquals("request", event.requestId);
            assertEquals(Long.valueOf(2), event.connectionRevision);
            if (i < events.size() - 1) assertTrue(event.type == STARTED || event.type == TEXT_DELTA);
        }
        return events;
    }

    private static MockResponse sse() {
        return new MockResponse().setHeader("Content-Type", "text/event-stream; charset=utf-8");
    }

    private static String frame(String json) { return "data: " + json + "\n\n"; }

    private static AiEvent end(List<AiEvent> events) { return events.get(events.size() - 1); }

    private static String text(List<AiEvent> events) {
        StringBuilder result = new StringBuilder();
        for (AiEvent event : events) if (event.type == TEXT_DELTA) result.append(event.delta);
        return result.toString();
    }

    private static void assertFailure(List<AiEvent> events, Code expected, boolean partial) {
        AiEvent end = end(events);
        assertEquals(FAILED, end.type);
        assertEquals(expected, end.error.code);
        assertEquals(partial, end.partial);
    }
}
