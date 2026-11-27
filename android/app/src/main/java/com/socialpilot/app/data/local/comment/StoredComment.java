package com.socialpilot.app.data.local.comment;

import com.socialpilot.app.data.remote.social.CommentRecord;
import java.util.Objects;
import static com.socialpilot.app.data.remote.social.CommentRecord.ContentState.OMITTED;
import static com.socialpilot.app.data.remote.social.CommentRecord.ParentState.UNKNOWN;

/** Selected provider observation plus known fields retained when a sparse observation wins. */
public final class StoredComment {
    public final String localPostId;
    public final CommentRecord observation;
    public final String observationQuery;
    public final CommentRecord.ParentState parentState;
    public final String parentCommentId;
    public final CommentRecord.ContentState contentState;
    public final String message;
    public final String authorDisplayName;
    public final Long createdAt;
    public final CommentRecord.TimeMissingReason timeMissingReason;
    public final boolean needsRefresh;

    StoredComment(String post, String query, CommentRecord selected, StoredComment previous, boolean refresh) {
        localPostId = post;
        observationQuery = query;
        observation = selected;
        boolean retainParent = previous != null && selected.parentState == UNKNOWN;
        parentState = retainParent ? previous.parentState : selected.parentState;
        parentCommentId = retainParent ? previous.parentCommentId : selected.parentCommentId;
        boolean retainMessage = previous != null && selected.contentState == OMITTED;
        contentState = retainMessage ? previous.contentState : selected.contentState;
        message = retainMessage ? previous.message : selected.message;
        authorDisplayName = selected.authorDisplayName == null && previous != null
                ? previous.authorDisplayName : selected.authorDisplayName;
        createdAt = selected.createdAt == null && previous != null ? previous.createdAt : selected.createdAt;
        timeMissingReason = createdAt == null ? selected.timeMissingReason : null;
        needsRefresh = refresh;
    }

    /** Older responses can supply missing display metadata without becoming the selected payload. */
    private StoredComment(StoredComment selected, CommentRecord fallback) {
        localPostId = selected.localPostId;
        observation = selected.observation;
        observationQuery = selected.observationQuery;
        parentState = selected.parentState;
        parentCommentId = selected.parentCommentId;
        contentState = selected.contentState;
        message = selected.message;
        needsRefresh = selected.needsRefresh;
        authorDisplayName = selected.authorDisplayName != null ? selected.authorDisplayName : fallback.authorDisplayName;
        createdAt = selected.createdAt != null ? selected.createdAt : fallback.createdAt;
        timeMissingReason = createdAt == null ? selected.timeMissingReason : null;
    }

    StoredComment merge(CommentRecord item, String query) {
        boolean providerOrder = observation.providerUpdatedAt != null && item.providerUpdatedAt != null;
        // Receipt order is evidence only within one stream. Other filters may deliver older views later.
        if (!providerOrder && !query.equals(observationQuery)) {
            return conflicts(item) ? flagForRefresh() : this;
        }
        int order = providerOrder ? Long.compare(item.providerUpdatedAt, observation.providerUpdatedAt)
                : Long.compare(item.syncedAt, observation.syncedAt);
        if (order < 0) {
            if (createdAt == null && item.createdAt != null || authorDisplayName == null && item.authorDisplayName != null)
                return new StoredComment(this, item);
            return this;
        }
        if (order == 0 && conflicts(item)) {
            return flagForRefresh();
        }
        // Only a strictly newer explicit observation can resolve a previously conflicting payload.
        boolean refresh = needsRefresh && (order == 0 || item.contentState == OMITTED
                || item.parentState == UNKNOWN && parentState != UNKNOWN
                || item.authorDisplayName == null && authorDisplayName != null
                || item.createdAt == null && createdAt != null);
        if (order == 0 && item.syncedAt < observation.syncedAt) return this;
        return new StoredComment(localPostId, query, item, this, refresh);
    }

    private StoredComment flagForRefresh() {
        return needsRefresh ? this : new StoredComment(localPostId, observationQuery, observation, this, true);
    }

    private boolean conflicts(CommentRecord item) {
        return item.contentState != OMITTED && contentState != OMITTED
                    && (item.contentState != contentState || !Objects.equals(item.message, message))
                || item.parentState != UNKNOWN && parentState != UNKNOWN
                    && (item.parentState != parentState || !Objects.equals(item.parentCommentId, parentCommentId))
                || differs(item.authorDisplayName, authorDisplayName) || differs(item.createdAt, createdAt);
    }

    private static boolean differs(Object first, Object second) {
        return first != null && second != null && !first.equals(second);
    }
}
