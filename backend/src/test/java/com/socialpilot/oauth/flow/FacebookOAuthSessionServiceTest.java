package com.socialpilot.oauth.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialpilot.oauth.contract.OAuthContract.*;
import com.socialpilot.oauth.facebook.FacebookGraphClient;
import com.socialpilot.oauth.facebook.FacebookOAuthProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacebookOAuthSessionServiceTest {
    private static final String VERIFIER = "a".repeat(43);
    private final MutableClock clock = new MutableClock();
    private MockRestServiceServer server;
    private FacebookOAuthSessionService service;

    @BeforeEach void setUp() {
        FacebookOAuthProperties p = new FacebookOAuthProperties();
        p.setAppId("123456789"); p.setAppSecret("fake-secret");
        p.setRedirectUri("https://example.test/api/oauth/facebook/callback");
        p.setTargetPageId("1234567890"); p.setGraphApiVersion("v27.0");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new FacebookOAuthSessionService(p, new FacebookGraphClient(p, builder, new ObjectMapper()), clock);
    }

    private OAuthStartRequest startRequest() throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        return new OAuthStartRequest("FACEBOOK", "android-dev", challenge);
    }

    private String parameter(String uri, String name) {
        return UriComponentsBuilder.fromUriString(uri).build().getQueryParams().getFirst(name);
    }

    private void expectGraph() {
        server.expect(requestTo(startsWith("https://graph.facebook.com/v27.0/oauth/access_token?")))
                .andRespond(withSuccess("{\"access_token\":\"fake-user-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://graph.facebook.com/v27.0/me/accounts?")))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"1234567890\",\"name\":\"Test Page\","
                        + "\"access_token\":\"fake-page-token\",\"tasks\":[\"CREATE_CONTENT\"]}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://graph.facebook.com/v27.0/1234567890?")))
                .andRespond(withSuccess("{\"id\":\"1234567890\",\"name\":\"Test Page\"}", MediaType.APPLICATION_JSON));
    }

    private OAuthHandoffExchangeRequest handoffAt(long elapsedSeconds) throws Exception {
        expectGraph();
        OAuthStartResponse start = service.start(startRequest());
        clock.advance(Duration.ofSeconds(elapsedSeconds));
        URI callback = service.completeCallback("fake-code", parameter(start.authorizationUrl, "state"), null);
        server.verify();
        return new OAuthHandoffExchangeRequest(start.sessionId, parameter(callback.toString(), "handoffCode"), VERIFIER);
    }

    private void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(OAuthFlowException.class, action).getCode());
    }

    @Test void wrongVerifierAndWrongSessionDoNotConsumeHandoff() throws Exception {
        OAuthHandoffExchangeRequest request = handoffAt(0);
        assertCode("INVALID_SESSION", () -> service.exchange(new OAuthHandoffExchangeRequest(
                request.sessionId, request.handoffCode, "b".repeat(43))));
        OAuthStartResponse other = service.start(startRequest());
        assertCode("INVALID_SESSION", () -> service.exchange(new OAuthHandoffExchangeRequest(
                other.sessionId, request.handoffCode, VERIFIER)));
        assertEquals("1234567890", service.exchange(request).externalAccountId);
        assertCode("HANDOFF_ALREADY_USED", () -> service.exchange(request));
    }

    @Test void handoffWorksJustBeforeDeadline() throws Exception {
        OAuthHandoffExchangeRequest request = handoffAt(0);
        clock.advance(Duration.ofSeconds(60).minusNanos(1));
        assertEquals("1234567890", service.exchange(request).externalAccountId);
    }

    @Test void handoffIsRejectedAtDeadlineAndAfterwards() throws Exception {
        OAuthHandoffExchangeRequest request = handoffAt(0);
        clock.advance(Duration.ofSeconds(60));
        assertCode("HANDOFF_EXPIRED", () -> service.exchange(request));
        clock.advance(Duration.ofSeconds(1));
        assertCode("HANDOFF_EXPIRED", () -> service.exchange(request));
    }

    @Test void sessionExpiryWinsEvenWhileHandoffIsStillValid() throws Exception {
        OAuthHandoffExchangeRequest request = handoffAt(590);
        clock.advance(Duration.ofSeconds(10));
        assertCode("INVALID_SESSION", () -> service.exchange(request));
    }

    @Test void expiryWhileWaitingForStoreLockCannotReturnCredentials() throws Exception {
        blockedExchange(false);
    }

    @Test void cleanupWhileWaitingForStoreLockCannotReturnStaleSession() throws Exception {
        blockedExchange(true);
    }

    private void blockedExchange(boolean cleanup) throws Exception {
        OAuthHandoffExchangeRequest request = handoffAt(590);
        Object lock = ReflectionTestUtils.getField(service, "storeLock");
        AtomicReference<Object> result = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { result.set(service.exchange(request)); }
            catch (Throwable failure) { result.set(failure); }
        });
        synchronized (lock) {
            worker.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (worker.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.sleep(1);
            assertEquals(Thread.State.BLOCKED, worker.getState());
            clock.advance(Duration.ofSeconds(10));
            if (cleanup) service.start(startRequest());
        }
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertEquals("INVALID_SESSION", assertInstanceOf(OAuthFlowException.class, result.get()).getCode());
    }

    @Test void twoExchangesHaveExactlyOneWinner() throws Exception {
        OAuthHandoffExchangeRequest request = handoffAt(0);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Callable<String> exchange = () -> {
                start.await();
                try { service.exchange(request); return "SUCCESS"; }
                catch (OAuthFlowException failure) { return failure.getCode(); }
            };
            Future<String> a = workers.submit(exchange), b = workers.submit(exchange);
            start.countDown();
            java.util.List<String> results = java.util.List.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
            assertTrue(results.contains("SUCCESS"));
            assertTrue(results.contains("HANDOFF_ALREADY_USED"));
        } finally { workers.shutdownNow(); }
    }

    @Test void invalidStateAndExpiredStateNeverCallProvider() throws Exception {
        OAuthStartResponse start = service.start(startRequest());
        assertCode("INVALID_STATE", () -> service.completeCallback("fake-code", null, null));
        assertCode("INVALID_STATE", () -> service.completeCallback("fake-code", "b".repeat(43), null));
        clock.advance(Duration.ofMinutes(10));
        assertCode("INVALID_STATE", () -> service.completeCallback("fake-code", parameter(start.authorizationUrl, "state"), null));
        server.verify();
    }

    @Test void replayedCallbackNeverExchangesProviderCodeTwice() throws Exception {
        expectGraph();
        OAuthStartResponse start = service.start(startRequest());
        String state = parameter(start.authorizationUrl, "state");
        service.completeCallback("fake-code", state, null);
        assertCode("INVALID_STATE", () -> service.completeCallback("fake-code", state, null));
        server.verify();
    }

    @Test void expiryDuringProviderIoCannotIssueHandoffAndDoesNotHoldStoreLock() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        server.expect(requestTo(startsWith("https://graph.facebook.com/v27.0/oauth/access_token?")))
                .andRespond(request -> {
                    entered.countDown();
                    try { if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("Provider was not resumed"); }
                    catch (InterruptedException failure) { throw new AssertionError(failure); }
                    return withSuccess("{\"access_token\":\"fake-user-token\"}", MediaType.APPLICATION_JSON).createResponse(request);
                });
        server.expect(requestTo(startsWith("https://graph.facebook.com/v27.0/me/accounts?")))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"1234567890\",\"name\":\"Test Page\","
                        + "\"access_token\":\"fake-page-token\",\"tasks\":[\"CREATE_CONTENT\"]}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://graph.facebook.com/v27.0/1234567890?")))
                .andRespond(withSuccess("{\"id\":\"1234567890\",\"name\":\"Test Page\"}", MediaType.APPLICATION_JSON));
        OAuthStartResponse start = service.start(startRequest());
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try {
            Future<URI> callback = workers.submit(() -> service.completeCallback("fake-code", parameter(start.authorizationUrl, "state"), null));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            clock.advance(Duration.ofMinutes(10));
            service.start(startRequest()); // Cleans the old session while provider I/O remains suspended.
            resume.countDown();
            assertEquals("INVALID_SESSION", assertInstanceOf(OAuthFlowException.class,
                    assertThrows(ExecutionException.class, () -> callback.get(5, TimeUnit.SECONDS)).getCause()).getCode());
            server.verify();
        } finally { resume.countDown(); workers.shutdownNow(); }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-11T00:00:00Z"));
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
