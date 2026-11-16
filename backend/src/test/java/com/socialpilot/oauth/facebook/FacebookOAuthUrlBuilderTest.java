package com.socialpilot.oauth.facebook;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FacebookOAuthUrlBuilderTest {
    private static final String CALLBACK =
            "https://tunnel.example.trycloudflare.com/api/oauth/facebook/callback";

    @Test
    void tokenExchangeEncodesEachDynamicParameterOnceAndPreservesValues() {
        FacebookOAuthProperties properties = properties();
        String fakeSecret = "test+secret/with==";
        String fakeCode = "test+code/with==";
        properties.setAppSecret(fakeSecret);

        URI uri = FacebookOAuthUrlBuilder.tokenExchangeUri(properties, fakeCode);
        Map<String, String> query = decodedQuery(uri);

        assertEquals("/v27.0/oauth/access_token", uri.getPath());
        assertEquals("1650819950076669", query.get("client_id"));
        assertTrue(fakeSecret.equals(query.get("client_secret")),
                "client secret round-trips without displaying its value");
        assertEquals(CALLBACK, query.get("redirect_uri"));
        assertTrue(fakeCode.equals(query.get("code")),
                "authorization code round-trips without displaying its value");
        assertTrue(uri.getRawQuery().contains("client_secret=test%2Bsecret%2Fwith%3D%3D"));
        assertTrue(uri.getRawQuery().contains("code=test%2Bcode%2Fwith%3D%3D"));
    }

    @Test
    void authorizationAndTokenUrisUseTheSameRedirectUriValue() {
        FacebookOAuthProperties properties = properties();
        URI authorization = FacebookOAuthUrlBuilder.authorizationUri(properties, "state-token");
        URI tokenExchange = FacebookOAuthUrlBuilder.tokenExchangeUri(properties, "new-code");

        assertEquals(CALLBACK, decodedQuery(authorization).get("redirect_uri"));
        assertEquals(CALLBACK, decodedQuery(tokenExchange).get("redirect_uri"));
        assertTrue(authorization.getRawQuery().contains("redirect_uri=https%3A%2F%2Ftunnel.example.trycloudflare.com%2Fapi%2Foauth%2Ffacebook%2Fcallback"));
    }

    private static Map<String, String> decodedQuery(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8)));
    }

    private static FacebookOAuthProperties properties() {
        FacebookOAuthProperties properties = new FacebookOAuthProperties();
        properties.setAppId("1650819950076669");
        properties.setAppSecret("test-secret-value");
        properties.setRedirectUri(CALLBACK);
        properties.setTargetPageId("1234567890");
        properties.setGraphApiVersion("v27.0");
        return properties;
    }
}
