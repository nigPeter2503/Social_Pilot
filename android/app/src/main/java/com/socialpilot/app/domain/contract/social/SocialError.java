package com.socialpilot.app.domain.contract.social;

import java.util.Objects;

/** Stable codes for localized UI. Never carries provider response bodies or credentials. */
public final class SocialError {
    public enum Code {
        APPROVAL_REQUIRED, CHANNEL_DISCONNECTED, AUTH_REQUIRED, PERMISSION_REQUIRED,
        UNSUPPORTED_OPERATION, INVALID_CONTENT, RATE_LIMITED, NETWORK_ERROR,
        PUBLISH_RESULT_UNKNOWN
    }

    public enum NextAction {
        APPROVE_VERSION, RECONNECT, RESOLVE_PERMISSION, EDIT_CONTENT,
        WAIT, RETRY_WHEN_SAFE, RECONCILE, NONE
    }

    public final Code code;
    public final NextAction nextAction;

    public SocialError(Code code, NextAction nextAction) {
        this.code = Objects.requireNonNull(code, "code");
        this.nextAction = Objects.requireNonNull(nextAction, "nextAction");
        if (code == Code.PUBLISH_RESULT_UNKNOWN && nextAction != NextAction.RECONCILE) {
            throw new IllegalArgumentException("Unknown publish result requires reconciliation");
        }
    }
}
