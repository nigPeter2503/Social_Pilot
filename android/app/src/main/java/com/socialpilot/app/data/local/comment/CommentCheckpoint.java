package com.socialpilot.app.data.local.comment;

import com.socialpilot.app.data.remote.social.CommentPageRequest;
import java.util.Objects;
import java.util.UUID;

/** Immutable checkpoint. Observed bounds describe cached observations, not continuous coverage. */
public final class CommentCheckpoint {
    public enum State { MORE, UNKNOWN, COMPLETE, RESTART_REQUIRED }

    public static final class Key {
        public final String channelId;
        public final String postId;
        public final String queryFingerprint;
        public final long revision;

        public Key(CommentPageRequest query) {
            channelId = query.channelId;
            postId = query.postId;
            queryFingerprint = query.queryFingerprint;
            revision = query.connectionRevision;
        }

        @Override public boolean equals(Object value) {
            if (!(value instanceof Key)) return false;
            Key other = (Key) value;
            return revision == other.revision && channelId.equals(other.channelId)
                    && postId.equals(other.postId) && queryFingerprint.equals(other.queryFingerprint);
        }

        @Override public int hashCode() {
            return Objects.hash(channelId, postId, queryFingerprint, revision);
        }
    }

    public final Key key;
    public final String localPostId;
    public final State state;
    public final String paginationReason;
    public final Long historyFrom;
    public final Long oldestObservedAt;
    public final Long newestObservedAt;
    public final long lastPageAt;
    public final Long lastCompleteSyncAt;
    public final long completedChains;
    // Internal concurrency marker and encrypted continuation. Never render or log either.
    final String generation;
    final String continuation;

    CommentCheckpoint(CommentPageRequest query, String post, State state, String reason,
            String continuation, Long oldest, Long newest, long pageAt, Long completeAt, long completedChains) {
        key = new Key(query);
        localPostId = post;
        this.state = state;
        paginationReason = reason;
        this.continuation = continuation;
        historyFrom = query.historyFrom;
        oldestObservedAt = oldest;
        newestObservedAt = newest;
        lastPageAt = pageAt;
        lastCompleteSyncAt = completeAt;
        this.completedChains = completedChains;
        generation = UUID.randomUUID().toString();
    }
}
