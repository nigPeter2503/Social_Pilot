package com.socialpilot.app.data.ai;

import static com.socialpilot.app.domain.ai.BranchSnapshot.Phase.*;
import static com.socialpilot.app.domain.ai.DualStreamTestSupport.*;
import static org.junit.Assert.*;

import com.socialpilot.app.domain.ai.DualStreamCoordinator;
import com.socialpilot.app.domain.contract.AiProvider;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.ModelCapabilities;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class DualStreamHttpTest {
    private MockWebServer server;

    @Before public void start() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After public void stop() throws Exception { server.shutdown(); }

    @Test public void bothHttpRequestsOverlapAndFragmentedTextRetainsOrder() throws Exception {
        long origin = System.nanoTime();
        CountDownLatch arrived = new CountDownLatch(2);
        AtomicLong startA = new AtomicLong();
        AtomicLong startB = new AtomicLong();
        AtomicLong finishA = new AtomicLong();
        AtomicLong finishB = new AtomicLong();
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                boolean a = request.getBody().readUtf8().contains("prompt-a");
                (a ? startA : startB).set(System.nanoTime() - origin);
                arrived.countDown();
                if (!arrived.await(5, TimeUnit.SECONDS)) return new MockResponse().setResponseCode(503);
                return stream().setChunkedBody(a ? result("Xin ", "chào 👋") : result("One ", "two"), 1);
            }
        });
        AiProvider adapter = adapter(10_000);
        AiProvider tracked = (req, listener) -> {
            AiProvider.Attempt actual = adapter.prepare(req, listener);
            return new AiProvider.Attempt() {
                @Override public void run() {
                    actual.run();
                    ("A".equals(req.channelId) ? finishA : finishB).set(System.nanoTime() - origin);
                }

                @Override public void cancel() { actual.cancel(); }
            };
        };
        try (DualStreamCoordinator coordinator = coordinator(tracked)) {
            coordinator.start(request("A", "a1", "prompt-a"), request("B", "b1", "prompt-b"));
            DualStreamCoordinator.Snapshot done = await(coordinator, s -> s.first.phase != RUNNING && s.second.phase != RUNNING
                    && finishA.get() > 0 && finishB.get() > 0);
            assertEquals(COMPLETED, done.first.phase);
            assertEquals(COMPLETED, done.second.phase);
            assertEquals("Xin chào 👋", done.first.text);
            assertEquals("One two", done.second.text);
            assertEquals(2, server.getRequestCount());
            assertTrue(Math.max(startA.get(), startB.get()) < Math.min(finishA.get(), finishB.get()));
            System.out.printf("Mock HTTP overlap microseconds since test start: A=[%d,%d] B=[%d,%d]%n",
                    micros(startA), micros(finishA), micros(startB), micros(finishB));
        }
    }

    @Test public void cancelAClosesBlockedHttpWhileBCompletesAndOnlyARetries() throws Exception {
        CountDownLatch bMayFinish = new CountDownLatch(1);
        CountDownLatch aReturned = new CountDownLatch(1);
        AtomicInteger aCancels = new AtomicInteger();
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                String body = request.getBody().readUtf8();
                if (body.contains("first-a")) {
                    String first = delta("prefix");
                    return stream().setBody(first + done()).throttleBody(first.length(), 4, TimeUnit.SECONDS);
                }
                if (body.contains("retry-a")) return stream().setBody(result("retry", " A"));
                if (!bMayFinish.await(5, TimeUnit.SECONDS)) return new MockResponse().setResponseCode(503);
                return stream().setBody(result("keep", " B"));
            }
        });
        AiProvider adapter = adapter(10_000);
        AiProvider tracked = (req, listener) -> {
            AiProvider.Attempt actual = adapter.prepare(req, listener);
            if (!"a1".equals(req.requestId)) return actual;
            return new AiProvider.Attempt() {
                @Override public void run() {
                    try {
                        actual.run();
                    } finally {
                        aReturned.countDown();
                    }
                }

                @Override public void cancel() {
                    aCancels.incrementAndGet();
                    actual.cancel();
                }
            };
        };
        Ledger ledger = new Ledger();
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> tracked, ledger, PRICING)) {
            coordinator.start(request("A", "a1", "first-a"), request("B", "b1", "keep-b"));
            await(coordinator, s -> "prefix".equals(s.first.text));
            assertTrue(coordinator.cancelBranch("A"));
            assertTrue(aReturned.await(2, TimeUnit.SECONDS));
            bMayFinish.countDown();
            DualStreamCoordinator.Snapshot result = await(coordinator,
                    s -> s.first.canRetry && s.second.phase == COMPLETED);
            assertEquals(CANCELLED, result.first.phase);
            assertEquals("prefix", result.first.text);
            assertEquals("keep B", result.second.text);
            assertEquals(1, aCancels.get());
            assertEquals("UNCERTAIN", ledger.state("a1"));
            assertTrue(coordinator.retry(request("A", "a2", "retry-a")));
            DualStreamCoordinator.Snapshot retried = await(coordinator, s -> s.first.phase == COMPLETED);
            assertEquals("retry A", retried.first.text);
            assertEquals("b1", retried.second.requestId);
            assertEquals("keep B", retried.second.text);
            assertEquals(3, server.getRequestCount());
        } finally {
            bMayFinish.countDown();
        }
    }

    @Test public void disconnectKeepsPartialAAndDoesNotFailB() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if (!request.getBody().readUtf8().contains("disconnect-a")) {
                    return stream().setBody(result("keep", " B"));
                }
                String first = delta("prefix");
                StringBuilder body = new StringBuilder(first);
                for (int i = 0; i < 300; i++) body.append(' ');
                return stream().setBody(body.toString()).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY);
            }
        });
        try (DualStreamCoordinator coordinator = coordinator(adapter(10_000))) {
            coordinator.start(request("A", "a1", "disconnect-a"), request("B", "b1", "keep-b"));
            DualStreamCoordinator.Snapshot result = await(coordinator, s -> s.first.canRetry && s.second.phase == COMPLETED);
            assertEquals(FAILED, result.first.phase);
            // A body shorter than its declared Content-Length is a protocol error in the adapter.
            assertEquals(AiError.Code.PROTOCOL_ERROR, result.first.error.code);
            assertEquals("prefix", result.first.text);
            assertTrue(result.first.partial);
            assertEquals("keep B", result.second.text);
        }
    }

    @Test public void timeoutIsNotCancellationAndDoesNotFailB() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if (request.getBody().readUtf8().contains("timeout-a")) {
                    return stream().setBody(done()).setBodyDelay(2, TimeUnit.SECONDS);
                }
                return stream().setBody(result("keep", " B"));
            }
        });
        try (DualStreamCoordinator coordinator = coordinator(adapter(400))) {
            coordinator.start(request("A", "a1", "timeout-a"), request("B", "b1", "keep-b"));
            DualStreamCoordinator.Snapshot result = await(coordinator, s -> s.first.canRetry && s.second.phase == COMPLETED);
            assertEquals(FAILED, result.first.phase);
            assertEquals(AiError.Code.TIMEOUT, result.first.error.code);
            assertEquals("keep B", result.second.text);
        }
    }

    private DualStreamCoordinator coordinator(AiProvider provider) {
        return new DualStreamCoordinator(id -> provider, new Ledger(), PRICING);
    }

    private OpenAiProvider adapter(long timeout) {
        ModelCapabilities.Support yes = ModelCapabilities.Support.SUPPORTED;
        return new OpenAiProvider(server.url("/v1/responses"), () -> "mock-credential",
                Collections.singletonList(new ModelCapabilities(AiRequest.Provider.OPENAI, "fixture-model",
                        yes, yes, ModelCapabilities.Support.UNKNOWN, yes, 64)), timeout);
    }

    private static long micros(AtomicLong offset) { return TimeUnit.NANOSECONDS.toMicros(offset.get()); }

    private static MockResponse stream() { return new MockResponse().setHeader("Content-Type", "text/event-stream"); }

    private static String result(String one, String two) { return delta(one) + delta(two) + done(); }

    private static String delta(String text) {
        return "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"msg\","
                + "\"content_index\":0,\"delta\":\"" + text + "\"}\n\n";
    }

    private static String done() {
        return "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n";
    }
}
