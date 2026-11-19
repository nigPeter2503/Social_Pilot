package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.domain.contract.social.Platform;

/** Boundary shared with storage. The provider position never leaves the adapter package. */
public final class CommentContinuation {
    private CommentContinuation() {}

    public static boolean isFirstPage(CommentPageRequest query) {
        return query.cursor == null;
    }

    public static boolean canResume(CommentCursorCodec codec, CommentPageRequest query, String envelope) {
        try {
            String position = codec.decode(query.resume(envelope));
            if (query.platform == Platform.LINKEDIN) {
                return position.matches("[1-9][0-9]{0,9}")
                        && Long.parseLong(position) <= Integer.MAX_VALUE - query.pageSize;
            }
            return !position.contains("://") && !position.contains("access_token")
                    && !position.contains("Bearer ");
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
