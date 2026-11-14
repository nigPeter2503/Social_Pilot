package com.socialpilot.oauth.provider;
import java.net.URI;
public interface OAuthProviderAdapter {
    String provider();
    URI authorizationUri(String state);
    ProviderIdentity exchangeCode(String code);
}
