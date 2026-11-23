package com.socialpilot.oauth.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class OAuthContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void sharedWireExamplesRoundTripWithoutLosingFields() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "../docs/contracts/oauth-examples.json")), StandardCharsets.UTF_8);
        JsonNode examples = mapper.readTree(source);
        OAuthContract.OAuthStartRequest oauthStartRequest = mapper.treeToValue(examples.get("oauthStartRequest"), OAuthContract.OAuthStartRequest.class);
        oauthStartRequest.validate();
        assertEquals(examples.get("oauthStartRequest"), mapper.valueToTree(oauthStartRequest));
        OAuthContract.OAuthStartResponse oauthStartResponse = mapper.treeToValue(examples.get("oauthStartResponse"), OAuthContract.OAuthStartResponse.class);
        oauthStartResponse.validate();
        assertEquals(examples.get("oauthStartResponse"), mapper.valueToTree(oauthStartResponse));
        OAuthContract.OAuthHandoffExchangeRequest oauthHandoffExchangeRequest = mapper.treeToValue(examples.get("oauthHandoffExchangeRequest"), OAuthContract.OAuthHandoffExchangeRequest.class);
        oauthHandoffExchangeRequest.validate();
        assertEquals(examples.get("oauthHandoffExchangeRequest"), mapper.valueToTree(oauthHandoffExchangeRequest));
        OAuthContract.OAuthExchangeResponse oauthExchangeResponse = mapper.treeToValue(examples.get("oauthExchangeResponse"), OAuthContract.OAuthExchangeResponse.class);
        oauthExchangeResponse.validate();
        assertEquals(examples.get("oauthExchangeResponse"), mapper.valueToTree(oauthExchangeResponse));
        OAuthContract.OAuthFailure oauthFailure = mapper.treeToValue(examples.get("oauthFailure"), OAuthContract.OAuthFailure.class);
        oauthFailure.validate();
        assertEquals(examples.get("oauthFailure"), mapper.valueToTree(oauthFailure));
        assertFalse(oauthExchangeResponse.toString().contains("FAKE_TEST_ACCESS_TOKEN"));
        assertFalse(oauthHandoffExchangeRequest.toString().contains("fake-demo-handoff"));
    }

    @Test
    public void sessionBindingAndRedirectShapeAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new OAuthContract.OAuthStartRequest(
                "FACEBOOK", "android-demo", "too-short"));
        assertThrows(IllegalArgumentException.class, () -> new OAuthContract.OAuthHandoffExchangeRequest(
                "s1", "fake-code", "too-short"));
        assertThrows(IllegalArgumentException.class, () -> new OAuthContract.OAuthStartResponse(
                "s1", "http://example.com/auth", "2026-11-07T12:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> new OAuthContract.OAuthStartResponse(
                "s1", "https://example.com/auth", "2026-02-30T12:00:00Z"));
    }

    @Test
    public void protocolFailuresHaveDistinctCodes() {
        for (String code : new String[] {"INVALID_STATE", "INVALID_SESSION", "HANDOFF_EXPIRED",
                "HANDOFF_ALREADY_USED", "AUTH_REQUIRED"}) {
            assertEquals(code, new OAuthContract.OAuthFailure(code).code);
        }
        assertEquals(60, OAuthContract.HANDOFF_TTL_SECONDS);
    }

    @Test
    public void malformedWireRequestCannotBypassConstructorValidation() {
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> mapper.readValue("{}", OAuthContract.OAuthHandoffExchangeRequest.class));
    }
}
