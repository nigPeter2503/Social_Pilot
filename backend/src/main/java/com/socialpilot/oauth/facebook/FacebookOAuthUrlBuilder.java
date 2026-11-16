package com.socialpilot.oauth.facebook;

import java.net.URI;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

public final class FacebookOAuthUrlBuilder {
    private FacebookOAuthUrlBuilder() {}

    public static URI authorizationUri(FacebookOAuthProperties properties, String state) {
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
                        "scope", "public_profile,pages_show_list,pages_read_engagement,pages_manage_posts",
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
}
