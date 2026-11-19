package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.domain.contract.social.ChannelCapability;
import com.socialpilot.app.domain.contract.social.Platform;

/** Epoch millisecond timestamps; neither receipt time nor omission implies creation/deletion. */
public final class CommentRecord {
    public enum ParentState { ROOT, KNOWN, UNKNOWN }
    public enum ContentState { AVAILABLE, OMITTED, DELETED }
    public enum TimeMissingReason { PROVIDER_OMITTED }
    public enum Source { API_THAT, FIXTURE_TU_API_DA_CHE, MO_PHONG, PROTOTYPE }

    public static final class Scope {
        public final Platform platform;
        public final String channelId;
        public final long connectionRevision;
        Scope(CommentPageRequest r) {
            platform = r.platform; channelId = r.channelId; connectionRevision = r.connectionRevision;
        }
    }

    public final Scope scope;
    public final String postId;
    /** LinkedIn uses the complete composite URN, never a naked numeric comment ID. */
    public final String commentId;
    public final ParentState parentState;
    public final String parentCommentId;
    public final ContentState contentState;
    public final String message;
    public final String authorDisplayName;
    public final Long createdAt;
    public final Long providerUpdatedAt;
    public final TimeMissingReason timeMissingReason;
    public final long syncedAt;
    public final Source source;
    public final String sourceRef;
    public final ChannelCapability replyCapability;

    CommentRecord(CommentPageRequest r, String id, ParentState parentState, String parent,
            ContentState contentState, String message, String author, Long created, Long updated,
            long at, Source source, String ref, ChannelCapability reply) {
        if ((parentState == ParentState.KNOWN) != (parent != null)
                || (contentState == ContentState.AVAILABLE) != (message != null)) CommentPageRequest.invalid();
        scope = new Scope(r); postId = r.postId; commentId = CommentPageRequest.text(id);
        this.parentState = parentState; parentCommentId = parent; this.contentState = contentState;
        this.message = message; authorDisplayName = author; createdAt = created; providerUpdatedAt = updated;
        timeMissingReason = created == null ? TimeMissingReason.PROVIDER_OMITTED : null;
        syncedAt = at; this.source = source; sourceRef = ref; replyCapability = reply;
    }
}
