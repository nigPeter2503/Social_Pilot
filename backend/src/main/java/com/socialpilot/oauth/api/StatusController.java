package com.socialpilot.oauth.api;

import com.socialpilot.oauth.facebook.FacebookOAuthProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class StatusController {
    private final FacebookOAuthProperties facebook;
    public StatusController(FacebookOAuthProperties facebook) { this.facebook = facebook; }
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "socialpilot-oauth",
                "oauth", facebook.isConfigured() ? "CONFIGURED" : "NOT_CONFIGURED");
    }
}
