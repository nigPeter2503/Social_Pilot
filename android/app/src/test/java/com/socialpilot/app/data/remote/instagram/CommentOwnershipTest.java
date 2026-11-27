package com.socialpilot.app.data.remote.instagram;

import static org.junit.Assert.*;

import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import java.io.IOException;
import java.util.Collections;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.*;

public class CommentOwnershipTest {
    private final MockWebServer server = new MockWebServer();
    private InstagramGraphClient graph;

    @Before public void setUp() throws Exception {
        server.start();
        graph = new InstagramGraphClient(new OkHttpClient.Builder().addInterceptor(chain -> {
            var url = chain.request().url().newBuilder().scheme("http").host(server.getHostName())
                    .port(server.getPort()).build();
            return chain.proceed(chain.request().newBuilder().url(url).build());
        }).build());
    }

    @After public void close() throws Exception { server.shutdown(); }

    private OAuthExchangeResponse grant(boolean ig) {
        return new OAuthExchangeResponse("session", ig ? "INSTAGRAM" : "FACEBOOK", ig ? "00191" : "00192",
                ig ? "PROFESSIONAL" : "PAGE", "fixture", "fixture-value", null, "bearer", null,
                ig ? "00192" : null, Collections.singletonList("instagram_basic"), "v27.0");
    }

    @Test public void pagePostAndProfessionalMediaUseDistinctOwners() throws Exception {
        for (boolean ig : new boolean[] {false, true}) {
            var credential = grant(ig);
            String field = ig ? "owner" : "from";
            server.enqueue(new MockResponse().setBody("{\"id\":\"0007\",\"" + field
                    + "\":{\"id\":\"" + credential.externalAccountId + "\"}}"));
            graph.verifyPostOwner(credential, "v27.0", "0007");
            var request = server.takeRequest();
            assertEquals("GET", request.getMethod());
            assertEquals("/v27.0/0007", request.getRequestUrl().encodedPath());
            assertEquals("id," + field + "{id}", request.getRequestUrl().queryParameter("fields"));
            assertEquals("Bearer fixture-value", request.getHeader("Authorization"));
        }
    }

    @Test public void linkedPageMissingOwnerNumericIdAndWrongMediaCannotAttestOwnership() {
        String[] invalid = {"{\"id\":\"0007\",\"owner\":{\"id\":\"00192\"}}",
                "{\"id\":\"0007\"}", "{\"id\":7,\"owner\":{\"id\":\"00191\"}}",
                "{\"id\":\"another\",\"owner\":{\"id\":\"00191\"}}"};
        for (String body : invalid) {
            server.enqueue(new MockResponse().setBody(body));
            assertThrows(IOException.class, () -> graph.verifyPostOwner(grant(true), "v27.0", "0007"));
        }
    }
}
