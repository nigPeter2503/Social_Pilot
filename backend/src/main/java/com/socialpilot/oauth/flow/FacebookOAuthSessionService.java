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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    // All indexes share one short critical section. Provider I/O never holds this lock.
    private final Object storeLock = new Object();
    private final Map<String, OAuthSession> sessionsById = new HashMap<>();
    private final Map<String, OAuthSession> sessionsByState = new HashMap<>();
    private final Map<String, Handoff> handoffsByDigest = new HashMap<>();
    private final Map<String, TerminalHandoff> terminalHandoffs = new HashMap<>();

    @Autowired
    public FacebookOAuthSessionService(FacebookOAuthProperties properties, FacebookGraphClient graph) {
        this(properties, graph, Clock.systemUTC());
    }

    FacebookOAuthSessionService(FacebookOAuthProperties properties, FacebookGraphClient graph, Clock clock) {
        this.properties = properties;
        this.graph = graph;
        this.clock = clock;
    }

    public OAuthStartResponse start(OAuthStartRequest request) {
        request.validate();
        properties.requireConfigured();
        if (!validChallenge(request.sessionChallenge)) throw OAuthFlowException.invalidSession();
        if (!"FACEBOOK".equals(request.provider) || !ANDROID_REDIRECT_ID.equals(request.redirectId)) {
            throw OAuthFlowException.invalidRedirect();
        }
        String sessionId = UUID.randomUUID().toString();
        String state = randomUrlToken();
        Instant expiresAt;
        synchronized (storeLock) {
            Instant now = clock.instant();
            cleanupExpired(now);
            if (sessionsById.size() >= MAX_ACTIVE_SESSIONS) throw OAuthFlowException.providerError();
            expiresAt = now.plus(SESSION_TTL);
            OAuthSession session = new OAuthSession(sessionId, state, request.sessionChallenge, expiresAt);
            sessionsById.put(sessionId, session);
            sessionsByState.put(state, session);
        }
        String authorizationUrl = com.socialpilot.oauth.facebook.FacebookOAuthUrlBuilder
                .authorizationUri(properties, state).toASCIIString();
        return new OAuthStartResponse(sessionId, authorizationUrl, UTC_SECONDS.format(expiresAt));
    }

    public URI completeCallback(String code, String state, String providerError) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) throw OAuthFlowException.invalidState();
        OAuthSession session;
        synchronized (storeLock) {
            session = sessionsByState.get(state);
            if (session == null || !session.expiresAt.isAfter(clock.instant())) {
                if (session != null) removeSession(session);
                throw OAuthFlowException.invalidState();
            }
            sessionsByState.remove(state);
        }
        if (providerError != null && !providerError.isBlank()) {
            synchronized (storeLock) { removeSession(session); }
            return androidError(session.id, "ACCESS_DENIED");
        }
        if (code == null || code.isBlank() || code.length() > 4096) {
            synchronized (storeLock) { removeSession(session); }
            return androidError(session.id, "PROVIDER_ERROR");
        }
        PageIdentity page;
        try { page = graph.exchangeCodeForTargetPage(code); }
        catch (OAuthFlowException exception) {
            synchronized (storeLock) { removeSession(session); }
            return androidError(session.id, exception.getCode());
        }
        String handoffCode = randomUrlToken();
        String digest = digest(handoffCode);
        synchronized (storeLock) {
            Instant now = clock.instant();
            if (sessionsById.get(session.id) != session || !session.expiresAt.isAfter(now)) {
                removeSession(session);
                throw OAuthFlowException.invalidSession();
            }
            Handoff handoff = new Handoff(session.id, page, now.plus(HANDOFF_TTL));
            if (handoffsByDigest.putIfAbsent(digest, handoff) != null) throw OAuthFlowException.providerError();
        }
        return UriComponentsBuilder.fromUriString(ANDROID_REDIRECT_URI)
                .queryParam("sessionId", session.id).queryParam("handoffCode", handoffCode)
                .build().encode().toUri();
    }

    public OAuthExchangeResponse exchange(OAuthHandoffExchangeRequest request) {
        request.validate();
        if (!request.handoffCode.matches("[A-Za-z0-9_-]{43}")) throw OAuthFlowException.invalidSession();
        String digest = digest(request.handoffCode);
        synchronized (storeLock) {
            Instant now = clock.instant();
            cleanupExpired(now);
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
            if (session == null || !session.expiresAt.isAfter(now)) {
                if (session != null) removeSession(session);
                throw OAuthFlowException.invalidSession();
            }
            if (!handoff.sessionId.equals(session.id)) throw OAuthFlowException.invalidSession();
            byte[] supplied = sha256(request.sessionVerifier);
            byte[] expected = Base64.getUrlDecoder().decode(session.challenge);
            if (!MessageDigest.isEqual(expected, supplied)) throw OAuthFlowException.invalidSession();
            if (!handoffsByDigest.remove(digest, handoff)) throw OAuthFlowException.handoffUsed();
            sessionsById.remove(session.id, session);
            terminalHandoffs.put(digest, new TerminalHandoff("HANDOFF_ALREADY_USED", now.plus(SESSION_TTL)));
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

    private void cleanupExpired(Instant now) {
        // Expired handoffs leave a short-lived error tombstone, never a credential.
        handoffsByDigest.entrySet().removeIf(entry -> {
            if (!entry.getValue().expiresAt.isAfter(now)) {
                String digest = entry.getKey();
                terminalHandoffs.put(digest, new TerminalHandoff("HANDOFF_EXPIRED", now.plus(SESSION_TTL)));
                return true;
            }
            return false;
        });
        sessionsById.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
        sessionsByState.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
        handoffsByDigest.entrySet().removeIf(entry -> !sessionsById.containsKey(entry.getValue().sessionId));
        terminalHandoffs.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
    }

    private void removeSession(OAuthSession session) {
        sessionsById.remove(session.id, session);
        sessionsByState.remove(session.state, session);
        handoffsByDigest.entrySet().removeIf(entry -> entry.getValue().sessionId.equals(session.id));
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
