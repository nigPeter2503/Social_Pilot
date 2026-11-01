package com.socialpilot.oauth.api;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc class StatusControllerTest {
    @Autowired MockMvc mvc;
    @Test void healthReportsUnconfiguredOAuth() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.oauth").value("NOT_CONFIGURED"));
    }
    @Test void unconfiguredOAuthDoesNotPretendSuccess() throws Exception {
        mvc.perform(get("/api/oauth/facebook/start")).andExpect(status().isNotImplemented());
    }
}
