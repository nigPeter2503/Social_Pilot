package com.socialpilot.app.data.remote.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CommentPage {
    public final CommentRecord.Scope scope;
    public final String postId;
    public final List<CommentRecord> items;
    /** Encrypted, query-bound envelope; caller may persist but must not log it. */
    public final String nextCursor;
    public final Boolean hasMore;
    public final String paginationReason;
    public final long fetchedAt;
    public final CommentRecord.Source source;
    public final String sourceRef;
    public final String queryFingerprint;

    CommentPage(CommentPageRequest r, List<CommentRecord> items, String cursor, Boolean more,
            String reason, long at, CommentRecord.Source source, String ref) {
        if (Boolean.TRUE.equals(more) && cursor == null || Boolean.FALSE.equals(more) && cursor != null
                || more == null && cursor != null) CommentPageRequest.invalid();
        scope = new CommentRecord.Scope(r); postId = r.postId;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        nextCursor = cursor; hasMore = more; paginationReason = reason; fetchedAt = at;
        this.source = source; sourceRef = ref; queryFingerprint = r.queryFingerprint;
    }
}
