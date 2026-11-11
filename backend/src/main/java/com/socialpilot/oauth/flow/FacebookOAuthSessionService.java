package com.socialpilot.oauth.flow;

import com.socialpilot.oauth.contract.OAuthContract;
import com.socialpilot.oauth.contract.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.oauth.contract.OAuthContract.OAuthHandoffExchangeRequest;
import com.socialpilot.oauth.contract.OAuthContract.OAuthStartRequest;
import com.socialpilot.oauth.contract.OAuthContract.OAuthStartResponse;
import com.socialpilot.oauth.facebook.FacebookGraphClient;
import com.socialpilot.oauth.facebook.FacebookGraphClient.PageIdentity;
import com.socialpilot.oauth.facebook.FacebookOAuthProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public final class FacebookOAuthSessionService {
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final Duration HANDOFF_TTL = Duration.ofSeconds(OAuthContract.HANDOFF_TTL_SECONDS);
    private static final int MAX_ACTIVE_SESSIONS = 500;
    private static final String ANDROID_REDIRECT_ID = "android-dev";
    private static final String ANDROID_REDIRECT_URI = "socialpilot://oauth/callback";
    private static final DateTimeFormatter UTC_SECONDS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final FacebookOAuthProperties properties;
    private final FacebookGraphClient graph;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, OAuthSession> sessionsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, OAuthSession> sessionsByState = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Handoff> handoffsByDigest = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TerminalHandoff> terminalHandoffs = new ConcurrentHashMap<>();

    public FacebookOAuthSessionService(FacebookOAuthProperties properties, FacebookGraphClient graph) {
        this.properties = properties; this.graph = graph;
    }

    public OAuthStartResponse start(OAuthStartRequest request) {
        request.validate();
        properties.requireConfigured();
        if (!validChallenge(request.sessionChallenge)) throw OAuthFlowException.invalidSession();
        if (!"FACEBOOK".equals(request.provider) || !ANDROID_REDIRECT_ID.equals(request.redirectId)) {
            throw OAuthFlowException.invalidRedirect();
        }
        cleanupExpired();
        if (sessionsById.size() >= MAX_ACTIVE_SESSIONS) throw OAuthFlowException.providerError();
        String sessionId = UUID.randomUUID().toString();
        String state = randomUrlToken();
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        OAuthSession session = new OAuthSession(sessionId, state, request.sessionChallenge, expiresAt);
        sessionsById.put(sessionId, session);
        sessionsByState.put(state, session);
        String authorizationUrl = UriComponentsBuilder.fromUriString("https://www.facebook.com")
                .pathSegment(properties.getGraphApiVersion(), "dialog", "oauth")
                .queryParam("client_id", properties.getAppId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "public_profile,pages_show_list,pages_read_engagement,pages_manage_posts")
                .queryParam("state", state)
                .build().encode().toUriString();
        return new OAuthStartResponse(sessionId, authorizationUrl, UTC_SECONDS.format(expiresAt));
    }

    public URI completeCallback(String code, String state, String providerError) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) throw OAuthFlowException.invalidState();
        OAuthSession session = sessionsByState.get(state);
        if (session == null || !session.expiresAt.isAfter(Instant.now())) {
            if (session != null) removeSession(session);
            throw OAuthFlowException.invalidState();
        }
        if (!sessionsByState.remove(state, session)) throw OAuthFlowException.invalidState();
        if (providerError != null && !providerError.isBlank()) {
            removeSession(session);
            return androidError(session.id, "ACCESS_DENIED");
        }
        if (code == null || code.isBlank() || code.length() > 4096) {
            removeSession(session);
            return androidError(session.id, "PROVIDER_ERROR");
        }
        PageIdentity page;
        try { page = graph.exchangeCodeForTargetPage(code); }
        catch (OAuthFlowException exception) {
            removeSession(session);
            return androidError(session.id, exception.getCode());
        }
        String handoffCode = randomUrlToken();
        String digest = digest(handoffCode);
        Handoff handoff = new Handoff(session.id, page, Instant.now().plus(HANDOFF_TTL));
        if (handoffsByDigest.putIfAbsent(digest, handoff) != null) throw OAuthFlowException.providerError();
        return UriComponentsBuilder.fromUriString(ANDROID_REDIRECT_URI)
                .queryParam("sessionId", session.id).queryParam("handoffCode", handoffCode)
                .build().encode().toUri();
    }

    public OAuthExchangeResponse exchange(OAuthHandoffExchangeRequest request) {
        request.validate();
        if (!request.handoffCode.matches("[A-Za-z0-9_-]{43}")) throw OAuthFlowException.invalidSession();
        String digest = digest(request.handoffCode);
        cleanupExpired();
        Handoff handoff = handoffsByDigest.get(digest);
        if (handoff == null) {
            TerminalHandoff terminal = terminalHandoffs.get(digest);
            if (terminal != null) {
                if ("HANDOFF_EXPIRED".equals(terminal.code)) throw OAuthFlowException.handoffExpired();
                throw OAuthFlowException.handoffUsed();
            }
            throw OAuthFlowException.invalidSession();
        }
        OAuthSession session = sessionsById.get(request.sessionId);
        if (session == null || !session.expiresAt.isAfter(Instant.now())) {
            if (session != null) removeSession(session);
            throw OAuthFlowException.invalidSession();
        }
        synchronized (handoff) {
            if (handoffsByDigest.get(digest) != handoff) throw OAuthFlowException.handoffUsed();
            if (!handoff.expiresAt.isAfter(Instant.now())) {
                handoffsByDigest.remove(digest, handoff);
                terminalHandoffs.put(digest, new TerminalHandoff("HANDOFF_EXPIRED", Instant.now().plus(SESSION_TTL)));
                removeSession(session);
                throw OAuthFlowException.handoffExpired();
            }
            if (!handoff.sessionId.equals(session.id)) throw OAuthFlowException.invalidSession();
            byte[] supplied = sha256(request.sessionVerifier);
            byte[] expected = Base64.getUrlDecoder().decode(session.challenge);
            if (!MessageDigest.isEqual(expected, supplied)) throw OAuthFlowException.invalidSession();
            if (!handoffsByDigest.remove(digest, handoff)) throw OAuthFlowException.handoffUsed();
            sessionsById.remove(session.id, session);
            terminalHandoffs.put(digest, new TerminalHandoff("HANDOFF_ALREADY_USED", Instant.now().plus(SESSION_TTL)));
            PageIdentity page = handoff.page;
            return new OAuthExchangeResponse(session.id, "FACEBOOK", page.id, "PAGE", page.name,
                    page.accessToken, null, "bearer", null);
        }
    }

    private URI androidError(String sessionId, String errorCode) {
        return UriComponentsBuilder.fromUriString(ANDROID_REDIRECT_URI)
                .queryParam("sessionId", sessionId).queryParam("error", errorCode)
                .build().encode().toUri();
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        sessionsById.forEach((id, session) -> { if (!session.expiresAt.isAfter(now)) removeSession(session); });
        handoffsByDigest.forEach((digest, handoff) -> {
            if (!handoff.expiresAt.isAfter(now) && handoffsByDigest.remove(digest, handoff)) {
                terminalHandoffs.put(digest, new TerminalHandoff("HANDOFF_EXPIRED", now.plus(SESSION_TTL)));
            }
        });
        terminalHandoffs.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
    }

    private void removeSession(OAuthSession session) {
        sessionsById.remove(session.id, session);
        sessionsByState.remove(session.state, session);
    }

    private static boolean validChallenge(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return decoded.length == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value);
        } catch (IllegalArgumentException exception) { return false; }
    }

    private String randomUrlToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(value)); }
    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static final class OAuthSession {
        private final String id, state, challenge;
        private final Instant expiresAt;
        private OAuthSession(String id, String state, String challenge, Instant expiresAt) {
            this.id = id; this.state = state; this.challenge = challenge; this.expiresAt = expiresAt;
        }
    }
    private static final class TerminalHandoff {
        private final String code;
        private final Instant expiresAt;
        private TerminalHandoff(String code, Instant expiresAt) { this.code = code; this.expiresAt = expiresAt; }
    }
    private static final class Handoff {
        private final String sessionId;
        private final PageIdentity page;
        private final Instant expiresAt;
        private Handoff(String sessionId, PageIdentity page, Instant expiresAt) {
            this.sessionId = sessionId; this.page = page; this.expiresAt = expiresAt;
        }
    }
}
