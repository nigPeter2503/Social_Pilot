package com.socialpilot.oauth.api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"socialpilot.facebook.enabled=false","socialpilot.linkedin.enabled=true","socialpilot.linkedin.client-id=fakeid","socialpilot.linkedin.client-secret=fake-secret","socialpilot.linkedin.redirect-uri=https://example.test/api/oauth/linkedin/callback"})
@AutoConfigureMockMvc
class LinkedInOnlyConfigurationTest {
    @Autowired MockMvc mvc;
    @Test void startsWithoutFacebookCredentialsAndReportsEachProvider() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.linkedin").value("CONFIGURED")).andExpect(jsonPath("$.facebook").value("NOT_CONFIGURED"));
    }
    @Test void startsLinkedinWithNoStoreOrProviderCredentialsInResponse() throws Exception {
        mvc.perform(post("/api/oauth/linkedin/start").contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"LINKEDIN\",\"redirectId\":\"android-dev\",\"sessionChallenge\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.authorizationUrl").value(org.hamcrest.Matchers.startsWith("https://www.linkedin.com/oauth/v2/authorization")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fake-secret"))));
    }
    @Test void mismatchedStartProviderIsRejected() throws Exception {
        mvc.perform(post("/api/oauth/linkedin/start").contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"FACEBOOK\",\"redirectId\":\"android-dev\",\"sessionChallenge\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REDIRECT"));
    }
    @Test void callbackWithoutValidStateDoesNotContactLinkedin() throws Exception {
        mvc.perform(get("/api/oauth/linkedin/callback").param("code","fake-code")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }
}
