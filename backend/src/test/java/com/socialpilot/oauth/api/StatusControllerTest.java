package com.socialpilot.oauth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"socialpilot.facebook.app-id=1650819950076669","socialpilot.facebook.app-secret=test-only-placeholder","socialpilot.facebook.redirect-uri=https://test-tunnel.example/api/oauth/facebook/callback","socialpilot.facebook.target-page-id=1234567890","socialpilot.facebook.graph-api-version=v27.0"})
@AutoConfigureMockMvc
class StatusControllerTest {
    private static final String FACEBOOK_START = "/api/oauth/facebook/start";
    private static final String CHALLENGE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void healthReportsConfiguredOAuth() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.oauth").value("CONFIGURED"));
    }

    @Test void oauthStartUsesConfiguredFacebookClient() throws Exception {
        mvc.perform(post(FACEBOOK_START).contentType(MediaType.APPLICATION_JSON)
                        .content(startBody("FACEBOOK", "android-dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value(
                        org.hamcrest.Matchers.startsWith("https://www.facebook.com/")));
    }

    @Test void rejectsWrongProviderAndUnknownRedirect() throws Exception {
        mvc.perform(post(FACEBOOK_START).contentType(MediaType.APPLICATION_JSON)
                        .content(startBody("LINKEDIN", "android-dev")))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("INVALID_REDIRECT"));
        mvc.perform(post(FACEBOOK_START).contentType(MediaType.APPLICATION_JSON)
                        .content(startBody("FACEBOOK", "unknown-redirect")))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("INVALID_REDIRECT"));
    }

    @Test void denialOnlyReturnsBoundSessionAndNormalizedError() throws Exception {
        var started = json.readTree(mvc.perform(post(FACEBOOK_START).contentType(MediaType.APPLICATION_JSON)
                        .content(startBody("FACEBOOK", "android-dev")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String sessionId = started.get("sessionId").asText();
        String state = UriComponentsBuilder.fromUriString(started.get("authorizationUrl").asText())
                .build().getQueryParams().getFirst("state");
        mvc.perform(get("/api/oauth/facebook/callback").param("state", state)
                        .param("code", "fake-code-must-not-redirect").param("error", "raw-provider-denial"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location",
                        "socialpilot://oauth/callback?sessionId=" + sessionId + "&error=ACCESS_DENIED"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/oauth/facebook/callback").param("state", state))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(content().string(not(containsString("raw-provider-denial"))));
    }

    private static String startBody(String provider, String redirectId) {
        return "{\"provider\":\"" + provider + "\",\"redirectId\":\"" + redirectId
                + "\",\"sessionChallenge\":\"" + CHALLENGE + "\"}";
    }
}
