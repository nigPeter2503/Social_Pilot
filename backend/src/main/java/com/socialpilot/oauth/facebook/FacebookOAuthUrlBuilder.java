package com.socialpilot.oauth.facebook;

import java.net.URI;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

public final class FacebookOAuthUrlBuilder {
    private FacebookOAuthUrlBuilder() {}

    public static URI authorizationUri(FacebookOAuthProperties properties, String state) {
        return authorizationUri(properties, state, false);
    }

    public static URI authorizationUri(FacebookOAuthProperties properties, String state, boolean instagram) {
        String scopes = instagram
                ? "public_profile,pages_show_list,pages_read_engagement,instagram_basic,instagram_content_publish"
                : "public_profile,pages_show_list,pages_read_engagement,pages_manage_posts";
        if (properties.isBusinessManagedPage()) scopes += ",business_management";
        return UriComponentsBuilder.fromUriString("https://www.facebook.com")
                .pathSegment(properties.getGraphApiVersion(), "dialog", "oauth")
                .queryParam("client_id", "{clientId}")
                .queryParam("redirect_uri", "{redirectUri}")
                .queryParam("response_type", "code")
                .queryParam("scope", "{scope}")
                .queryParam("state", "{state}")
                .encode()
                .buildAndExpand(Map.of(
                        "clientId", properties.getAppId(),
                        "redirectUri", properties.getRedirectUri(),
                        "scope", scopes,
                        "state", state))
                .toUri();
    }

    public static URI tokenExchangeUri(FacebookOAuthProperties properties, String code) {
        return UriComponentsBuilder.fromUriString("https://graph.facebook.com")
                .pathSegment(properties.getGraphApiVersion(), "oauth", "access_token")
                .queryParam("client_id", "{clientId}")
                .queryParam("client_secret", "{clientSecret}")
                .queryParam("redirect_uri", "{redirectUri}")
                .queryParam("code", "{code}")
                .encode()
                .buildAndExpand(Map.of(
                        "clientId", properties.getAppId(),
                        "clientSecret", properties.getAppSecret(),
                        "redirectUri", properties.getRedirectUri(),
                        "code", code))
                .toUri();
    }

    public static URI longLivedUserTokenUri(FacebookOAuthProperties properties, String userToken) {
        return UriComponentsBuilder.fromUriString("https://graph.facebook.com")
                .pathSegment(properties.getGraphApiVersion(), "oauth", "access_token")
                .queryParam("grant_type", "fb_exchange_token")
                .queryParam("client_id", "{clientId}")
                .queryParam("client_secret", "{clientSecret}")
                .queryParam("fb_exchange_token", "{userToken}")
                .encode()
                .buildAndExpand(Map.of("clientId", properties.getAppId(),
                        "clientSecret", properties.getAppSecret(), "userToken", userToken))
                .toUri();
    }
}
