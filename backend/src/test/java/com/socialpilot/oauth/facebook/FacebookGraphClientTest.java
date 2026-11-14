package com.socialpilot.oauth.facebook;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class FacebookGraphClientTest {
    private static final String FAKE_SECRET = "test-secret-value";
    private FacebookGraphClient client;

    @BeforeEach
    void setUp() {
        FacebookOAuthProperties properties = new FacebookOAuthProperties();
        properties.setAppId("1650819950076669");
        properties.setAppSecret(FAKE_SECRET);
        properties.setRedirectUri("https://tunnel.example/api/oauth/facebook/callback");
        properties.setTargetPageId("1234567890");
        properties.setGraphApiVersion("v27.0");
        client = new FacebookGraphClient(properties, RestClient.builder(), new ObjectMapper());
    }

    @Test
    void parsesGraphErrorFieldsAndRedactsCredentialsAndAuthorizationCode() {
        String fakeCode = "AQD-fake-test-authorization-code-123456789012345";
        String body = "{\"error\":{\"type\":\"OAuthException\",\"code\":1,"
                + "\"error_subcode\":-1,\"message\":\"Failed code=" + fakeCode
                + " client_secret=" + FAKE_SECRET + "\",\"fbtrace_id\":\"AbCdEf-123\"}}";

        FacebookGraphClient.GraphError error = client.safeGraphError(
                body.getBytes(StandardCharsets.UTF_8), fakeCode);

        assertTrue(error.parsed());
        assertEquals(1, error.code());
        assertEquals(-1, error.subcode());
        assertEquals("OAuthException", error.type());
        assertEquals("AbCdEf-123", error.fbtraceId());
        assertFalse(error.message().contains(fakeCode));
        assertFalse(error.message().contains(FAKE_SECRET));
        assertTrue(error.message().contains("[REDACTED]"));
    }

    @Test
    void recordsOnlyBodyLengthWhenErrorBodyIsMissingOrMalformed() {
        FacebookGraphClient.GraphError empty = client.safeGraphError(new byte[0], "fake-code");
        FacebookGraphClient.GraphError malformed = client.safeGraphError(
                "not-json".getBytes(StandardCharsets.UTF_8), "fake-code");

        assertFalse(empty.parsed());
        assertEquals(0, empty.bodyLength());
        assertFalse(malformed.parsed());
        assertEquals(8, malformed.bodyLength());
        assertEquals("unknown", malformed.message());
    }

    @Test
    void parsesJsonEvenWhenProviderUsesAnUnexpectedContentType() throws Exception {
        byte[] body = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
        org.springframework.http.ResponseEntity<byte[]> response = org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body(body);

        com.fasterxml.jackson.databind.JsonNode json = client.parseGraphJson(response, "page_list");

        assertTrue(json.path("data").isArray());
    }

    @Test
    void reportsOnlySafeMetadataForNonJsonGraphPayload() {
        byte[] body = "blocked".getBytes(StandardCharsets.UTF_8);
        org.springframework.http.ResponseEntity<byte[]> response = org.springframework.http.ResponseEntity
                .status(org.springframework.http.HttpStatus.BAD_GATEWAY)
                .contentType(org.springframework.http.MediaType.TEXT_HTML)
                .body(body);

        FacebookGraphClient.GraphPayloadException exception = assertThrows(
                FacebookGraphClient.GraphPayloadException.class,
                () -> client.parseGraphJson(response, "page_list"));

        assertEquals("page_list", exception.stage);
        assertEquals(502, exception.httpStatus);
        assertEquals("text/html", exception.contentType);
        assertEquals(body.length, exception.bodyLength);
        assertFalse(exception.getMessage().contains("blocked"));
    }
    @Test
    void fingerprintsCodeWithoutReturningTheCode() {
        String fingerprint = FacebookGraphClient.codeFingerprint("fake-code-for-fingerprint");
        assertEquals(8, fingerprint.length());
        assertTrue(fingerprint.matches("[0-9a-f]{8}"));
        assertFalse(fingerprint.contains("fake-code"));
    }
}