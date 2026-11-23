package com.socialpilot.app.domain.contract.ai;

public final class AiError {
    public enum Code {
        INVALID_CREDENTIAL, PERMISSION_DENIED, RATE_LIMITED, QUOTA_EXHAUSTED,
        TIMEOUT, CONNECTION_FAILED, UNSUPPORTED_CAPABILITY, PROVIDER_UNAVAILABLE,
        INVALID_REQUEST, CONTENT_BLOCKED, INCOMPLETE, PROTOCOL_ERROR
    }

    public final Code code;
    public final Long retryAfterSeconds;

    public AiError(Code code, Long retryAfterSeconds) {
        this.code = java.util.Objects.requireNonNull(code);
        if (retryAfterSeconds != null && retryAfterSeconds < 0) {
            throw new IllegalArgumentException("Invalid retry delay");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
