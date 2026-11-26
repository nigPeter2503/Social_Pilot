package com.socialpilot.app.data.remote.instagram;

import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class InstagramGraphClientTest {
    private static final OAuthExchangeResponse CONNECTION = new OAuthExchangeResponse("test-session",
            "INSTAGRAM", "9876543210", "PROFESSIONAL", "demo_user", "test-page-token",
            null, "bearer", null, "1234567890", Collections.singletonList("instagram_basic"), "v27.0");

    @Test public void lostPublishResponseIsFollowedByAReadOnlyStatusCheck() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        InstagramGraphClient graph = new InstagramGraphClient(new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    int count = requests.incrementAndGet();
                    assertEquals("Bearer test-page-token", request.header("Authorization"));
                    if (count == 1) {
                        assertEquals("POST", request.method());
                        assertEquals("/v27.0/9876543210/media_publish", request.url().encodedPath());
                        assertTrue(request.body().isOneShot());
                        Buffer body = new Buffer();
                        request.body().writeTo(body);
                        assertEquals("creation_id=4567890123", body.readUtf8());
                        throw new IOException("Simulated lost publish response");
                    }
                    assertEquals("GET", request.method());
                    assertEquals("/v27.0/4567890123", request.url().encodedPath());
                    assertEquals("status_code", request.url().queryParameter("fields"));
                    return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                            .code(200).message("OK")
                            .body(ResponseBody.create("{\"status_code\":\"PUBLISHED\"}", null)).build();
                }).build());
        assertThrows(IOException.class, () -> graph.publishContainer(CONNECTION, "4567890123"));
        assertEquals(1, requests.get());
        assertEquals("PUBLISHED", graph.containerStatus(CONNECTION, "4567890123"));
        assertEquals(2, requests.get());
    }

    @Test public void graphRejectsRevokedTokensEvenWhenHttpStatusIsBadRequest() {
        InstagramGraphClient graph = new InstagramGraphClient(new OkHttpClient.Builder()
                .addInterceptor(chain -> new Response.Builder().request(chain.request())
                        .protocol(Protocol.HTTP_1_1).code(400).message("Bad Request")
                        .body(ResponseBody.create("{\"error\":{\"code\":190}}", null)).build()).build());
        assertThrows(InstagramGraphClient.AuthorizationExpired.class, () -> graph.validateConnection(CONNECTION));
    }

    @Test public void pageVerificationUsesReadOnlyGraphRequestAndDistinguishesRevocation() throws Exception {
        OAuthExchangeResponse page = new OAuthExchangeResponse("page-session", "FACEBOOK", "1234567890",
                "PAGE", "Page", "test-page-token", null, "bearer", null);
        AtomicInteger calls = new AtomicInteger();
        InstagramGraphClient graph = new InstagramGraphClient(new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    assertEquals("GET", request.method());
                    assertEquals("graph.facebook.com", request.url().host());
                    assertEquals("/v27.0/1234567890", request.url().encodedPath());
                    assertEquals(Collections.singleton("fields"), request.url().queryParameterNames());
                    assertEquals("Bearer test-page-token", request.header("Authorization"));
                    int response = calls.getAndIncrement();
                    if (response == 4) throw new SocketTimeoutException("fixture");
                    int[] statuses = {200, 403, 401, 200};
                    String[] bodies = {"{\"id\":\"1234567890\"}", "{\"error\":{\"code\":200}}",
                            "{\"error\":{\"code\":190}}", "{\"id\":\"9999999999\"}"};
                    return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                            .code(statuses[response]).message("Fixture")
                            .body(ResponseBody.create(bodies[response], null)).build();
                }).build());
        graph.validatePageConnection(page);
        assertThrows(InstagramGraphClient.PermissionDenied.class, () -> graph.validatePageConnection(page));
        assertThrows(InstagramGraphClient.AuthorizationExpired.class, () -> graph.validatePageConnection(page));
        assertEquals("Unexpected Graph account",
                assertThrows(IOException.class, () -> graph.validatePageConnection(page)).getMessage());
        assertThrows(SocketTimeoutException.class, () -> graph.validatePageConnection(page));
    }
}
