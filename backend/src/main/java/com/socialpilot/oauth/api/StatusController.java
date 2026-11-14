package com.socialpilot.oauth.api;

import com.socialpilot.oauth.facebook.FacebookOAuthProperties;
import java.util.Map;
import com.socialpilot.oauth.linkedin.LinkedInOAuthProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class StatusController {
    private final FacebookOAuthProperties facebook;
    private final LinkedInOAuthProperties linkedin;
    public StatusController(FacebookOAuthProperties facebook,LinkedInOAuthProperties linkedin){this.facebook=facebook;this.linkedin=linkedin;}
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "socialpilot-oauth",
                "oauth", (facebook.isConfigured()||linkedin.isConfigured()) ? "CONFIGURED" : "NOT_CONFIGURED",
                "facebook",facebook.isConfigured()?"CONFIGURED":"NOT_CONFIGURED",
                "linkedin",linkedin.isConfigured()?"CONFIGURED":"NOT_CONFIGURED");
    }
}
