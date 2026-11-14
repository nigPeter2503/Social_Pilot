package com.socialpilot.oauth.facebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FacebookOAuthPropertiesTest {
    private static final String CALLBACK =
            "https://tunnel.example.trycloudflare.com/api/oauth/facebook/callback";

    @Test
    void trimsOuterWhitespaceAndAcceptsValidConfiguration() {
        FacebookOAuthProperties properties = validProperties();
        properties.setAppId(" 1650819950076669 ");
        properties.setAppSecret("  test-secret-value  ");
        properties.setRedirectUri(" " + CALLBACK + " ");
        properties.setTargetPageId(" 1234567890 ");
        properties.setGraphApiVersion(" v27.0 ");

        assertDoesNotThrow(properties::validateConfiguration);
        assertEquals("1650819950076669", properties.getAppId());
        assertTrue("test-secret-value".equals(properties.getAppSecret()),
                "client secret is trimmed without displaying its value");
        assertEquals(CALLBACK, properties.getRedirectUri());
        assertEquals("1234567890", properties.getTargetPageId());
        assertEquals("v27.0", properties.getGraphApiVersion());
        assertTrue(properties.appSecretHadOuterWhitespace());
    }

    @Test
    void rejectsEmptyOrMalformedConfigurationWithoutEchoingValues() {
        FacebookOAuthProperties properties = new FacebookOAuthProperties();
        IllegalStateException exception = assertThrows(IllegalStateException.class, properties::validateConfiguration);
        assertTrue(exception.getMessage().contains("FACEBOOK_APP_ID"));
        assertTrue(exception.getMessage().contains("FACEBOOK_APP_SECRET"));
    }

    @Test
    void rejectsEmbeddedSecretWhitespaceAndInvalidCallbackUri() {
        FacebookOAuthProperties properties = validProperties();
        properties.setAppSecret("test secret");
        properties.setRedirectUri("http://tunnel.example/api/oauth/facebook/callback");

        IllegalStateException exception = assertThrows(IllegalStateException.class, properties::validateConfiguration);
        assertTrue(exception.getMessage().contains("FACEBOOK_APP_SECRET"));
        assertTrue(exception.getMessage().contains("FACEBOOK_REDIRECT_URI"));
        assertFalse(exception.getMessage().contains("test secret"));
    }

    private static FacebookOAuthProperties validProperties() {
        FacebookOAuthProperties properties = new FacebookOAuthProperties();
        properties.setAppId("1650819950076669");
        properties.setAppSecret("test-secret-value");
        properties.setRedirectUri(CALLBACK);
        properties.setTargetPageId("1234567890");
        properties.setGraphApiVersion("v27.0");
        return properties;
    }
}
