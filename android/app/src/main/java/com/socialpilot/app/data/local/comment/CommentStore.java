package com.socialpilot.app.data.local.comment;

import com.socialpilot.app.data.local.publishedpost.PublishedPost;
import com.socialpilot.app.data.local.publishedpost.PublishedPostStore.ChannelState;
import com.socialpilot.app.domain.contract.social.Platform;

/** One serialized transaction shared with Channel invalidation and PublishedPost writes.
 * Throw on lookup, constraint, write or commit failure and roll back the entire callback.
 * putComment enforces unique (platform, channelId, commentId) without reparenting.
 */
public interface CommentStore {
    interface Transaction {
        ChannelState channel(String channelId);
        PublishedPost byExternalId(String channelId, String externalPostId);
        StoredComment comment(Platform platform, String channelId, String commentId);
        void putComment(StoredComment comment);
        CommentCheckpoint checkpoint(CommentCheckpoint.Key key);
        void putCheckpoint(CommentCheckpoint checkpoint);
    }

    interface Work<T> { T run(Transaction tx); }
    <T> T transaction(Work<T> work);
}
