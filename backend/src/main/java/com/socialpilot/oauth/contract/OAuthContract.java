package com.socialpilot.oauth.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * OAuth wire contract v1. No endpoints, token persistence or session enforcement implemented here.
 * Sensitive instances redact toString; never log serialized DTOs or HTTP bodies.
 */
public final class OAuthContract {
    public static final int HANDOFF_TTL_SECONDS = 60;

    private OAuthContract() {}

    private static void text(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
    }

    private static void provider(String value) {
        if (!"FACEBOOK".equals(value) && !"LINKEDIN".equals(value)) {
            throw new IllegalArgumentException("Invalid provider");
        }
    }

    private static void https(String value) {
        text(value, "authorizationUrl");
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("authorizationUrl must be absolute HTTPS without user info or fragment");
        }
    }

    private static void timestamp(String value) {
        text(value, "expiresAt");
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")) {
            throw new IllegalArgumentException("Use UTC yyyy-MM-dd'T'HH:mm:ss'Z'");
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        if (format.parse(value, position) == null || position.getIndex() != value.length()) {
            throw new IllegalArgumentException("Invalid UTC timestamp");
        }
    }

    public static final class OAuthStartRequest {
        public final String provider;
        public final String redirectId;
        public final String sessionChallenge;

        @JsonCreator
        public OAuthStartRequest(
                @JsonProperty("provider") String provider,
                @JsonProperty("redirectId") String redirectId,
                @JsonProperty("sessionChallenge") String sessionChallenge) {
            this.provider = provider;
            this.redirectId = redirectId;
            this.sessionChallenge = sessionChallenge;
            validate();
        }

        /** Call after deserialization, especially Gson which may bypass constructors. */
        public void validate() {
            provider(provider); text(redirectId, "redirectId");
            if (sessionChallenge == null || !sessionChallenge.matches("[A-Za-z0-9_-]{43}")) {
                throw new IllegalArgumentException("sessionChallenge must be a base64url SHA-256 challenge");
            }
        }

        @Override
        public String toString() {
            return "OAuthStartRequest{redacted}";
        }
    }

    public static final class OAuthStartResponse {
        public final String sessionId;
        public final String authorizationUrl;
        public final String expiresAt;

        @JsonCreator
        public OAuthStartResponse(
                @JsonProperty("sessionId") String sessionId,
                @JsonProperty("authorizationUrl") String authorizationUrl,
                @JsonProperty("expiresAt") String expiresAt) {
            this.sessionId = sessionId;
            this.authorizationUrl = authorizationUrl;
            this.expiresAt = expiresAt;
            validate();
        }

        /** Call after deserialization, especially Gson which may bypass constructors. */
        public void validate() {
            text(sessionId, "sessionId"); https(authorizationUrl); timestamp(expiresAt);
        }

        @Override
        public String toString() {
            return "OAuthStartResponse{redacted}";
        }
    }

    public static final class OAuthHandoffExchangeRequest {
        public final String sessionId;
        public final String handoffCode;
        public final String sessionVerifier;

        @JsonCreator
        public OAuthHandoffExchangeRequest(
                @JsonProperty("sessionId") String sessionId,
                @JsonProperty("handoffCode") String handoffCode,
                @JsonProperty("sessionVerifier") String sessionVerifier) {
            this.sessionId = sessionId;
            this.handoffCode = handoffCode;
            this.sessionVerifier = sessionVerifier;
            validate();
        }

        /** Call after deserialization, especially Gson which may bypass constructors. */
        public void validate() {
            text(sessionId, "sessionId"); text(handoffCode, "handoffCode");
            if (sessionVerifier == null || !sessionVerifier.matches("[A-Za-z0-9._~-]{43,128}")) {
                throw new IllegalArgumentException("Invalid session verifier");
            }
        }

        @Override
        public String toString() {
            return "OAuthHandoffExchangeRequest{redacted}";
        }
    }

    public static final class OAuthExchangeResponse {
        public final String sessionId;
        public final String provider;
        public final String externalAccountId;
        public final String accountType;
        public final String displayName;
        public final String accessToken;
        public final String refreshToken;
        public final String tokenType;
        public final String expiresAt;

        @JsonCreator
        public OAuthExchangeResponse(
                @JsonProperty("sessionId") String sessionId,
                @JsonProperty("provider") String provider,
                @JsonProperty("externalAccountId") String externalAccountId,
                @JsonProperty("accountType") String accountType,
                @JsonProperty("displayName") String displayName,
                @JsonProperty("accessToken") String accessToken,
                @JsonProperty("refreshToken") String refreshToken,
                @JsonProperty("tokenType") String tokenType,
                @JsonProperty("expiresAt") String expiresAt) {
            this.sessionId = sessionId;
            this.provider = provider;
            this.externalAccountId = externalAccountId;
            this.accountType = accountType;
            this.displayName = displayName;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.tokenType = tokenType;
            this.expiresAt = expiresAt;
            validate();
        }

        /** Call after deserialization, especially Gson which may bypass constructors. */
        public void validate() {
            text(sessionId, "sessionId"); provider(provider); text(externalAccountId, "externalAccountId");
            if (!"PAGE".equals(accountType) && !"PERSON".equals(accountType) && !"ORGANIZATION".equals(accountType)) {
                throw new IllegalArgumentException("Invalid accountType");
            }
            text(displayName, "displayName"); text(accessToken, "accessToken"); text(tokenType, "tokenType");
            if (refreshToken != null) text(refreshToken, "refreshToken");
            if (expiresAt != null) timestamp(expiresAt);
        }

        @Override
        public String toString() {
            return "OAuthExchangeResponse{redacted}";
        }
    }

    public static final class OAuthFailure {
        public final String code;

        @JsonCreator
        public OAuthFailure(
                @JsonProperty("code") String code) {
            this.code = code;
            validate();
        }

        /** Call after deserialization, especially Gson which may bypass constructors. */
        public void validate() {
            if (!java.util.Arrays.asList("INVALID_STATE", "INVALID_SESSION", "INVALID_REDIRECT",
                    "HANDOFF_EXPIRED", "HANDOFF_ALREADY_USED", "ACCESS_DENIED",
                    "AUTH_REQUIRED", "PROVIDER_ERROR").contains(code)) {
                throw new IllegalArgumentException("Invalid OAuth error code");
            }
        }

        @Override
        public String toString() {
            return "OAuthFailure{redacted}";
        }
    }
}
