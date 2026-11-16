package com.socialpilot.oauth.facebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialpilot.oauth.flow.OAuthFlowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FacebookPagePaginationTest {
    private MockRestServiceServer server;
    private FacebookGraphClient client;

    @BeforeEach void setUp() {
        FacebookOAuthProperties p = new FacebookOAuthProperties();
        p.setAppId("123456789"); p.setAppSecret("fake-secret");
        p.setRedirectUri("https://example.test/api/oauth/facebook/callback");
        p.setTargetPageId("1234567890"); p.setGraphApiVersion("v27.0");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FacebookGraphClient(p, builder, new ObjectMapper());
        server.expect(requestTo(startsWith("https://graph.facebook.com/v27.0/oauth/access_token?")))
                .andRespond(withSuccess("{\"access_token\":\"fake-user-token\"}", MediaType.APPLICATION_JSON));
    }

    private String next(String cursor) {
        // Deliberately untrusted URL: only its presence may be used, never its host/path/token.
        return "{\"data\":[{\"id\":\"9876543210\"}],\"paging\":{\"cursors\":{\"after\":\"" + cursor
                + "\"},\"next\":\"https://untrusted.example/steal?access_token=fake-token\"}}";
    }

    private void page(String after, String json) {
        var expectation = server.expect(requestTo(startsWith("https://graph.facebook.com/v27.0/me/accounts?")))
                .andExpect(header("Authorization", "Bearer fake-user-token"))
                .andExpect(queryParam("fields", "id,name,tasks,access_token"));
        if (after == null) expectation.andExpect(request -> assertFalse(
                org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI())
                        .build().getQueryParams().containsKey("after")));
        else expectation.andExpect(request -> assertEquals(after, org.springframework.web.util.UriUtils.decode(
                org.springframework.web.util.UriComponentsBuilder.fromUri(request.getURI()).build()
                        .getQueryParams().getFirst("after"), java.nio.charset.StandardCharsets.UTF_8)));
        expectation.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void assertFailure(String code) {
        assertEquals(code, assertThrows(OAuthFlowException.class,
                () -> client.exchangeCodeForTargetPage("fake-code")).getCode());
        server.verify();
    }

    @Test void findsTargetOnSecondPageWithoutFollowingNextUrl() {
        page(null, next("cursor+/=2"));
        page("cursor+/=2", "{\"data\":[{\"id\":\"1234567890\",\"name\":\"Target\",\"tasks\":[\"CREATE_CONTENT\"],"
                + "\"access_token\":\"fake-page-token\"}]}");
        server.expect(requestTo(startsWith("https://graph.facebook.com/v27.0/1234567890?")))
                .andExpect(header("Authorization", "Bearer fake-page-token"))
                .andRespond(withSuccess("{\"id\":\"1234567890\",\"name\":\"Target\"}", MediaType.APPLICATION_JSON));
        FacebookGraphClient.PageIdentity page = client.exchangeCodeForTargetPage("fake-code");
        assertEquals("1234567890", page.id);
        assertEquals("fake-page-token", page.accessToken);
        server.verify();
    }

    @Test void deniesOnlyAfterReachingTheLastPageWithoutTarget() {
        page(null, next("cursor-2"));
        page("cursor-2", "{\"data\":[],\"paging\":{\"cursors\":{\"after\":\"last-cursor\"}}}");
        assertFailure("ACCESS_DENIED");
    }

    @Test void repeatedCursorIsProviderErrorRatherThanPermissionFailure() {
        page(null, next("repeated"));
        page("repeated", next("repeated"));
        assertFailure("PROVIDER_ERROR");
    }

    @Test void missingCursorWithNextPageIsProviderError() {
        page(null, "{\"data\":[],\"paging\":{\"next\":\"https://untrusted.example/\"}}");
        assertFailure("PROVIDER_ERROR");
    }

    @Test void laterPageHttpErrorIsNotReportedAsMissingPermission() {
        page(null, next("cursor-2"));
        server.expect(requestTo(startsWith("https://graph.facebook.com/v27.0/me/accounts?")))
                .andExpect(queryParam("after", "cursor-2"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertFailure("PROVIDER_ERROR");
    }

    @Test void traversalStopsAtRequestLimit() {
        for (int request = 0; request < FacebookGraphClient.MAX_PAGE_LIST_REQUESTS; request++) {
            page(request == 0 ? null : "cursor-" + request, next("cursor-" + (request + 1)));
        }
        assertFailure("PROVIDER_ERROR");
    }
}
