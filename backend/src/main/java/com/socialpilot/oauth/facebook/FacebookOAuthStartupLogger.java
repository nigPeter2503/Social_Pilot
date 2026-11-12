package com.socialpilot.oauth.facebook;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

@Component
public final class FacebookOAuthStartupLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(FacebookOAuthStartupLogger.class);

    private final FacebookOAuthProperties properties;
    private final ConfigurableEnvironment environment;

    public FacebookOAuthStartupLogger(
            FacebookOAuthProperties properties, ConfigurableEnvironment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void logEffectiveConfiguration() {
        LOGGER.info("Facebook OAuth configuration appId={} appSecretLength={} appSecretHadOuterWhitespace={} "
                        + "redirectUri={} graphApiVersion={} configSources[appId={},appSecret={},redirectUri={},graphApiVersion={}]",
                properties.getAppId(),
                properties.getAppSecret().length(),
                properties.appSecretHadOuterWhitespace(),
                properties.getRedirectUri(),
                properties.getGraphApiVersion(),
                sourceOf("socialpilot.facebook.app-id", "FACEBOOK_APP_ID"),
                sourceOf("socialpilot.facebook.app-secret", "FACEBOOK_APP_SECRET"),
                sourceOf("socialpilot.facebook.redirect-uri", "FACEBOOK_REDIRECT_URI"),
                sourceOf("socialpilot.facebook.graph-api-version", "FACEBOOK_GRAPH_API_VERSION"));
    }

    private String sourceOf(String propertyName, String environmentName) {
        String placeholderSource = null;
        for (PropertySource<?> source : environment.getPropertySources()) {
            if ("configurationProperties".equals(source.getName())) continue;
            Object directValue = source.getProperty(propertyName);
            if (isEffectiveValue(directValue)) return safeSourceName(source.getName());
            if (isPlaceholder(directValue)) placeholderSource = safeSourceName(source.getName());

            Object environmentValue = source.getProperty(environmentName);
            if (isEffectiveValue(environmentValue)) return safeSourceName(source.getName());
        }
        return placeholderSource == null ? "unknown" : placeholderSource + " (placeholder default)";
    }

    private static boolean isEffectiveValue(Object value) {
        if (value == null) return false;
        String text = value.toString();
        return !text.isBlank() && !isPlaceholder(text);
    }

    private static boolean isPlaceholder(Object value) {
        if (value == null) return false;
        String text = value.toString().trim();
        return text.startsWith("$" + "{") && text.endsWith("}");
    }

    private static String safeSourceName(String name) {
        if (name == null) return "unknown";
        String safe = name.replaceAll("[\\p{Cntrl}]", " ");
        return safe.length() <= 100 ? safe : safe.substring(0, 100);
    }
}
