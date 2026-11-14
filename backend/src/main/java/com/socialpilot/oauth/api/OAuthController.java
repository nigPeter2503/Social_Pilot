package com.socialpilot.oauth.api;

import com.socialpilot.oauth.contract.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.oauth.contract.OAuthContract.OAuthFailure;
import com.socialpilot.oauth.contract.OAuthContract.OAuthHandoffExchangeRequest;
import com.socialpilot.oauth.contract.OAuthContract.OAuthStartRequest;
import com.socialpilot.oauth.contract.OAuthContract.OAuthStartResponse;
import com.socialpilot.oauth.flow.OAuthSessionService;
import com.socialpilot.oauth.flow.OAuthFlowException;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth")
public final class OAuthController {
    private final OAuthSessionService sessions;
    public OAuthController(OAuthSessionService sessions) { this.sessions = sessions; }

    @PostMapping("/{provider}/start")
    public ResponseEntity<OAuthStartResponse> start(@PathVariable String provider, @RequestBody OAuthStartRequest request) {
        if (!provider.equalsIgnoreCase(request.provider)) throw OAuthFlowException.invalidRedirect();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.start(request));
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        URI destination = sessions.completeCallback(provider, code, state, error);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, destination.toASCIIString())
                .cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
                .header("Referrer-Policy", "no-referrer").build();
    }

    @PostMapping("/{provider}/exchange")
    public ResponseEntity<OAuthExchangeResponse> exchange(@PathVariable String provider, @RequestBody OAuthHandoffExchangeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.exchange(provider, request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<OAuthFailure> invalidPayload(){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(new OAuthFailure("INVALID_SESSION"));}
    @ExceptionHandler(OAuthFlowException.class)
    public ResponseEntity<OAuthFailure> handleOAuthError(OAuthFlowException exception) {
        return ResponseEntity.status(exception.getStatus()).cacheControl(CacheControl.noStore())
                .body(new OAuthFailure(exception.getCode()));
    }
}
