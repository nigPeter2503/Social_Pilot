package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.domain.contract.social.CapabilityAvailability;
import com.socialpilot.app.domain.contract.social.SocialError;

public final class CommentPageResult {
    public enum Status { SUCCESS, FAILURE }
    /** Local reasons supplement Social v1 without inventing shared error codes. */
    public enum Failure {
        SCOPE_MISMATCH, CHANNEL_DISCONNECTED, CAPABILITY_BLOCKED, MAPPING_UNVERIFIED,
        INVALID_CURSOR, INVALID_RESPONSE, PROVIDER_ERROR, NETWORK_ERROR, LOCAL_ERROR
    }
    public final Status status;
    public final CommentPage page;
    public final Failure failure;
    /** Null for unknown provider/local causes, whose codes do not exist in Social v1. */
    public final SocialError error;
    public final CapabilityAvailability blockedAvailability;
    public final Long retryAfterAt;

    private CommentPageResult(CommentPage page, Failure failure, SocialError error,
            CapabilityAvailability blocked, Long retryAt) {
        status = page == null ? Status.FAILURE : Status.SUCCESS;
        this.page = page; this.failure = failure; this.error = error;
        blockedAvailability = blocked; retryAfterAt = retryAt;
    }
    static CommentPageResult success(CommentPage page) {
        return new CommentPageResult(page, null, null, null, null);
    }
    static CommentPageResult failure(Failure failure, SocialError error,
            CapabilityAvailability availability, Long retryAt) {
        return new CommentPageResult(null, failure, error, availability, retryAt);
    }
}
