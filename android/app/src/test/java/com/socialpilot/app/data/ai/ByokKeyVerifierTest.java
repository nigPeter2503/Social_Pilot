package com.socialpilot.app.data.ai;

import static com.socialpilot.app.data.ai.ByokKeyVerifier.Outcome.*;
import static com.socialpilot.app.domain.contract.ai.AiRequest.Provider.*;
import static org.junit.Assert.*;

import com.socialpilot.app.data.ai.ByokKeyVerifier.Outcome;
import com.socialpilot.app.domain.contract.ai.AiRequest.Provider;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class ByokKeyVerifierTest {
    private static final String KEY = "fake-byok-key-000000000000";
    private MockWebServer server;
    private ByokKeyVerifier verifier;

    @Before public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        Map<Provider, HttpUrl> endpoints = new EnumMap<>(Provider.class);
        endpoints.put(OPENAI, server.url("/v1/models"));
        endpoints.put(GEMINI, server.url("/v1beta/models?pageSize=1"));
        verifier = new ByokKeyVerifier(endpoints, 15_000);
    }

    @After public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test public void productionEndpointsAreFixedHttpsProviderHosts() {
        ByokKeyVerifier production = new ByokKeyVerifier();
        // Prepare only; no network.
        assertEquals("https://api.openai.com/v1/models", production.prepare(OPENAI, KEY).request().url().toString());
        HttpUrl geminiUrl = production.prepare(GEMINI, KEY).request().url();
        assertTrue(geminiUrl.isHttps());
        assertEquals("generativelanguage.googleapis.com", geminiUrl.host());
        assertFalse(geminiUrl.toString().contains(KEY));
        ByokKeyVerifier.Check open = production.prepare(OPENAI, KEY);
        ByokKeyVerifier.Check gemini = production.prepare(GEMINI, KEY);
        open.cancel();
        gemini.cancel();
        assertEquals(CANCELLED, open.run());
        assertEquals(CANCELLED, gemini.run());
    }

    @Test public void openAiKeyTravelsOnlyInBearerHeader() throws Exception {
        server.enqueue(json(200, "{\"object\":\"list\",\"data\":[{\"id\":\"m\"}]}"));
        assertEquals(VALID, verifier.prepare(OPENAI, KEY).run());
        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/v1/models", request.getPath());
        assertEquals("Bearer " + KEY, request.getHeader("Authorization"));
        assertNull(request.getHeader("x-goog-api-key"));
        assertFalse(request.getRequestUrl().toString().contains(KEY));
        assertEquals(0, request.getBodySize());
    }

    @Test public void geminiKeyTravelsOnlyInApiKeyHeader() throws Exception {
        server.enqueue(json(200, "{\"models\":[{\"name\":\"models/m\"}]}"));
        assertEquals(VALID, verifier.prepare(GEMINI, KEY).run());
        RecordedRequest request = server.takeRequest();
        assertEquals(KEY, request.getHeader("x-goog-api-key"));
        assertNull(request.getHeader("Authorization"));
        assertNull(request.getRequestUrl().queryParameter("key"));
        assertFalse(request.getRequestUrl().toString().contains(KEY));
    }

    @Test public void wrongOrRevokedKeyIsInvalid() {
        server.enqueue(json(401, "{\"error\":{\"code\":\"invalid_api_key\"}}"));
        assertEquals(INVALID_KEY, verifier.prepare(OPENAI, KEY).run());
        server.enqueue(json(400, "{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\",\"details\":["
                + "{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"API_KEY_INVALID\"}]}}"));
        assertEquals(INVALID_KEY, verifier.prepare(GEMINI, KEY).run());
    }

    @Test public void geminiBadRequestWithoutKeyReasonIsNotReportedAsWrongKey() {
        server.enqueue(json(400, "{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\"}}"));
        assertEquals(UNEXPECTED_RESPONSE, verifier.prepare(GEMINI, KEY).run());
    }

    @Test public void missingPermissionIsSeparateFromWrongKey() {
        server.enqueue(json(403, "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}"));
        assertEquals(PERMISSION_DENIED, verifier.prepare(GEMINI, KEY).run());
    }

    @Test public void transientProviderStatesDoNotDescribeTheKey() {
        server.enqueue(json(429, "{}"));
        server.enqueue(json(503, "{}"));
        server.enqueue(json(504, "{}"));
        assertOutcome(RATE_LIMITED, verifier.prepare(OPENAI, KEY).run());
        assertOutcome(PROVIDER_UNAVAILABLE, verifier.prepare(OPENAI, KEY).run());
        assertOutcome(TIMEOUT, verifier.prepare(OPENAI, KEY).run());
    }

    @Test public void retryAfterZeroOn503StillUsesOnlyOneRequest() {
        server.enqueue(json(503, "{}").addHeader("Retry-After", "0"));
        server.enqueue(json(200, "{\"data\":[]}"));
        assertEquals(PROVIDER_UNAVAILABLE, verifier.prepare(OPENAI, KEY).run());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void networkFailuresAreNotInvalidKeys() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        assertOutcome(NETWORK_ERROR, verifier.prepare(OPENAI, KEY).run());
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        Map<Provider, HttpUrl> endpoints = new EnumMap<>(Provider.class);
        endpoints.put(OPENAI, server.url("/v1/models"));
        Outcome timeout = new ByokKeyVerifier(endpoints, 200).prepare(OPENAI, KEY).run();
        assertOutcome(TIMEOUT, timeout);
    }

    @Test public void redirectIsNotFollowedAndKeyIsNotForwarded() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", server.url("/elsewhere")));
        assertEquals(UNEXPECTED_RESPONSE, verifier.prepare(OPENAI, KEY).run());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void successPageThatIsNotProviderJsonIsNotProof() {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "text/html").setBody("<html>portal</html>"));
        assertEquals(UNEXPECTED_RESPONSE, verifier.prepare(OPENAI, KEY).run());
        server.enqueue(json(200, "{\"unexpected\":true}"));
        assertEquals(UNEXPECTED_RESPONSE, verifier.prepare(GEMINI, KEY).run());
    }

    @Test public void cancelInterruptsInFlightCheck() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        ByokKeyVerifier.Check check = verifier.prepare(OPENAI, KEY);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Outcome> result = worker.submit(check::run);
            server.takeRequest(2, TimeUnit.SECONDS);
            check.cancel();
            assertEquals(CANCELLED, result.get(2, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
        }
    }

    @Test public void onlyDefiniteOutcomesDescribeTheKey() {
        for (Outcome outcome : Outcome.values()) {
            boolean definite = outcome == VALID || outcome == INVALID_KEY || outcome == PERMISSION_DENIED;
            assertEquals(outcome.name(), definite, outcome.describesKey());
        }
    }

    private static void assertOutcome(Outcome expected, Outcome actual) {
        assertEquals(expected, actual);
        assertFalse(actual.describesKey());
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body);
    }
}
