package com.socialpilot.oauth.facebook;

import java.net.URI;
import com.socialpilot.oauth.flow.OAuthFlowException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "socialpilot.facebook")
public class FacebookOAuthProperties {
    private String appId = "";
    private String appSecret = "";
    private String redirectUri = "";
    private String targetPageId = "";
    private String graphApiVersion = "v27.0";

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId == null ? "" : appId.trim(); }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret == null ? "" : appSecret; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri == null ? "" : redirectUri.trim(); }
    public String getTargetPageId() { return targetPageId; }
    public void setTargetPageId(String targetPageId) { this.targetPageId = targetPageId == null ? "" : targetPageId.trim(); }
    public String getGraphApiVersion() { return graphApiVersion; }
    public void setGraphApiVersion(String graphApiVersion) { this.graphApiVersion = graphApiVersion == null ? "" : graphApiVersion.trim(); }

    public boolean isConfigured() {
        return !appId.isBlank() && !appSecret.isBlank() && validCallback() && targetPageId.matches("[0-9]{5,30}")
                && graphApiVersion.matches("v[0-9]+\\.[0-9]+");
    }

    public void requireConfigured() {
        if (!isConfigured()) throw OAuthFlowException.notConfigured();
    }

    public URI callbackUri() {
        requireConfigured();
        return URI.create(redirectUri);
    }

    private boolean validCallback() {
        if (redirectUri.isBlank()) return false;
        try {
            URI uri = URI.create(redirectUri);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && "/api/oauth/facebook/callback".equals(uri.getPath());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
