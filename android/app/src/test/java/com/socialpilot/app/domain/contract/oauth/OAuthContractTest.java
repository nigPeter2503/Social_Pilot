package com.socialpilot.app.domain.contract.oauth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class OAuthContractTest {
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    @Test
    public void sharedWireExamplesRoundTripWithoutLosingFields() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "../../docs/contracts/oauth-examples.json")), StandardCharsets.UTF_8);
        JsonObject examples = gson.fromJson(source, JsonObject.class);
        OAuthContract.OAuthStartRequest oauthStartRequest = gson.fromJson(examples.get("oauthStartRequest"), OAuthContract.OAuthStartRequest.class);
        oauthStartRequest.validate();
        assertEquals(examples.get("oauthStartRequest"), gson.toJsonTree(oauthStartRequest));
        OAuthContract.OAuthStartResponse oauthStartResponse = gson.fromJson(examples.get("oauthStartResponse"), OAuthContract.OAuthStartResponse.class);
        oauthStartResponse.validate();
        assertEquals(examples.get("oauthStartResponse"), gson.toJsonTree(oauthStartResponse));
        OAuthContract.OAuthHandoffExchangeRequest oauthHandoffExchangeRequest = gson.fromJson(examples.get("oauthHandoffExchangeRequest"), OAuthContract.OAuthHandoffExchangeRequest.class);
        oauthHandoffExchangeRequest.validate();
        assertEquals(examples.get("oauthHandoffExchangeRequest"), gson.toJsonTree(oauthHandoffExchangeRequest));
        OAuthContract.OAuthExchangeResponse oauthExchangeResponse = gson.fromJson(examples.get("oauthExchangeResponse"), OAuthContract.OAuthExchangeResponse.class);
        oauthExchangeResponse.validate();
        assertEquals(examples.get("oauthExchangeResponse"), gson.toJsonTree(oauthExchangeResponse));
        OAuthContract.OAuthFailure oauthFailure = gson.fromJson(examples.get("oauthFailure"), OAuthContract.OAuthFailure.class);
        oauthFailure.validate();
        assertEquals(examples.get("oauthFailure"), gson.toJsonTree(oauthFailure));
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
    public void gsonPayloadMustBeValidatedBeforeUse() {
        OAuthContract.OAuthHandoffExchangeRequest invalid = gson.fromJson("{}",
                OAuthContract.OAuthHandoffExchangeRequest.class);
        assertThrows(IllegalArgumentException.class, invalid::validate);
    }
}
