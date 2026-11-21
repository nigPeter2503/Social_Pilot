package com.socialpilot.app.data.local.comment;

import com.socialpilot.app.domain.contract.social.SocialError;

/** Safe metadata persisted beside one exact query. Times are UTC epoch milliseconds. */
public record CommentAttempt(long lastAttemptAt, String failure, SocialError error,
        Long retryAt, int transientFailures) {}
