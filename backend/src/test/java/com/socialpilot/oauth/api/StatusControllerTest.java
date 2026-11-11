package com.socialpilot.oauth.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"socialpilot.facebook.app-id=1650819950076669","socialpilot.facebook.app-secret=test-only-placeholder","socialpilot.facebook.redirect-uri=https://test-tunnel.example/api/oauth/facebook/callback","socialpilot.facebook.target-page-id=1234567890","socialpilot.facebook.graph-api-version=v27.0"})
@AutoConfigureMockMvc
class StatusControllerTest {
    @Autowired MockMvc mvc;

    @Test void healthReportsConfiguredOAuth() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.oauth").value("CONFIGURED"));
    }

    @Test void oauthStartUsesConfiguredFacebookClient() throws Exception {
        mvc.perform(post("/api/oauth/facebook/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"FACEBOOK\",\"redirectId\":\"android-dev\","
                                + "\"sessionChallenge\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value(
                        org.hamcrest.Matchers.startsWith("https://www.facebook.com/")));
    }
}