package com.socialpilot.oauth.flow;

public final class OAuthFlowException extends RuntimeException {
    private final String code;
    private final int status;

    public OAuthFlowException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public int getStatus() { return status; }

    public static OAuthFlowException invalidState() { return new OAuthFlowException("INVALID_STATE", 400); }
    public static OAuthFlowException invalidSession() { return new OAuthFlowException("INVALID_SESSION", 401); }
    public static OAuthFlowException invalidRedirect() { return new OAuthFlowException("INVALID_REDIRECT", 400); }
    public static OAuthFlowException handoffExpired() { return new OAuthFlowException("HANDOFF_EXPIRED", 410); }
    public static OAuthFlowException handoffUsed() { return new OAuthFlowException("HANDOFF_ALREADY_USED", 409); }
    public static OAuthFlowException accessDenied() { return new OAuthFlowException("ACCESS_DENIED", 403); }
    public static OAuthFlowException providerError() { return new OAuthFlowException("PROVIDER_ERROR", 502); }
    public static OAuthFlowException notConfigured() { return new OAuthFlowException("AUTH_REQUIRED", 503); }
}
