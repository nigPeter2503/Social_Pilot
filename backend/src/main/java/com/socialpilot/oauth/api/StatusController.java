package com.socialpilot.oauth.api;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController public class StatusController {
    @GetMapping("/api/health") public Map<String, String> health() {
        return Map.of("status", "UP", "service", "socialpilot-oauth", "oauth", "NOT_CONFIGURED");
    }
    @GetMapping("/api/oauth/{provider}/start")
    public ResponseEntity<Map<String, String>> start(@PathVariable String provider) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
            "code", "OAUTH_NOT_CONFIGURED", "message", "OAuth adapters are not implemented yet"));
    }
}
