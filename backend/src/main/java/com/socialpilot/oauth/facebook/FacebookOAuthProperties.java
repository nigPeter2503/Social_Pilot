package com.socialpilot.oauth.facebook;

import com.socialpilot.oauth.flow.OAuthFlowException;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "socialpilot.facebook")
public class FacebookOAuthProperties {
    private boolean enabled=true;
    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean v){enabled=v;}
    private String appId = "";
    private String appSecret = "";
    private String redirectUri = "";
    private String targetPageId = "";
    private String graphApiVersion = "v27.0";
    private boolean appSecretHadOuterWhitespace;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId == null ? "" : appId.trim(); }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) {
        String raw = appSecret == null ? "" : appSecret;
        this.appSecret = raw.trim();
        this.appSecretHadOuterWhitespace = !raw.equals(this.appSecret);
    }
    boolean appSecretHadOuterWhitespace() { return appSecretHadOuterWhitespace; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri == null ? "" : redirectUri.trim(); }
    public String getTargetPageId() { return targetPageId; }
    public void setTargetPageId(String targetPageId) { this.targetPageId = targetPageId == null ? "" : targetPageId.trim(); }
    public String getGraphApiVersion() { return graphApiVersion; }
    public void setGraphApiVersion(String graphApiVersion) { this.graphApiVersion = graphApiVersion == null ? "" : graphApiVersion.trim(); }

    @PostConstruct
    public void validateConfiguration() {
        if (!enabled) return;
        List<String> errors = new ArrayList<>();
        if (appId.isBlank()) errors.add("FACEBOOK_APP_ID is required");
        else if (!appId.matches("[0-9]{5,30}")) errors.add("FACEBOOK_APP_ID must contain 5 to 30 digits");

        if (appSecret.isBlank()) errors.add("FACEBOOK_APP_SECRET is required");
        else if (hasWhitespaceOrControl(appSecret)) errors.add("FACEBOOK_APP_SECRET must not contain embedded whitespace or control characters");
        else if (isWrappedInQuotes(appSecret)) errors.add("FACEBOOK_APP_SECRET must not include wrapping quotes");

        if (!validCallback()) errors.add("FACEBOOK_REDIRECT_URI must be an HTTPS URL with path /api/oauth/facebook/callback and no user info, query, or fragment");
        if (!targetPageId.matches("[0-9]{5,30}")) errors.add("FACEBOOK_TARGET_PAGE_ID must contain 5 to 30 digits");
        if (!graphApiVersion.matches("v[0-9]+\\.[0-9]+")) errors.add("FACEBOOK_GRAPH_API_VERSION must match v<major>.<minor>");

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid Facebook OAuth configuration: " + String.join("; ", errors));
        }
    }

    public boolean isConfigured() {
        return enabled && appId.matches("[0-9]{5,30}") && !appSecret.isBlank()
                && !hasWhitespaceOrControl(appSecret) && !isWrappedInQuotes(appSecret)
                && validCallback() && targetPageId.matches("[0-9]{5,30}")
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
        if (redirectUri.isBlank() || hasWhitespaceOrControl(redirectUri)) return false;
        try {
            URI uri = URI.create(redirectUri);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && "/api/oauth/facebook/callback".equals(uri.getPath());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean hasWhitespaceOrControl(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint) || Character.isISOControl(codePoint));
    }

    private static boolean isWrappedInQuotes(String value) {
        if (value.length() < 2) return false;
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        return first == 34 || last == 34 || first == 39 || last == 39;
    }
}
