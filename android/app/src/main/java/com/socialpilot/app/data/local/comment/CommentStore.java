package com.socialpilot.app.data.local.comment;

import com.socialpilot.app.data.local.publishedpost.PublishedPost;
import com.socialpilot.app.data.local.publishedpost.PublishedPostStore.ChannelState;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.data.remote.social.CommentPageRequest;
import java.util.List;

/** One serialized transaction shared with Channel invalidation and PublishedPost writes.
 * Throw on lookup, constraint, write or commit failure and roll back the entire callback.
 * putComment enforces unique (platform, channelId, commentId) without reparenting.
 */
public interface CommentStore {
    enum Handled { ALL, HANDLED, UNHANDLED }

    interface Transaction {
        ChannelState channel(String channelId);
        PublishedPost byExternalId(String channelId, String externalPostId);
        StoredComment comment(Platform platform, String channelId, String commentId);
        void putComment(StoredComment comment);
        CommentCheckpoint checkpoint(CommentCheckpoint.Key key);
        void putCheckpoint(CommentCheckpoint checkpoint);
        // Query membership is independent of the winning payload of an overlapping comment.
        void include(CommentCheckpoint.Key key, String commentId);
        // Join membership with current payload and local handled state; apply the inclusive history bound
        // again, since another query may have supplied a previously missing creation date.
        List<StoredComment> comments(CommentPageRequest query, Handled filter);
        Long rateLimitedUntil(String channelId, long revision, long now);
        CommentAttempt attempt(CommentCheckpoint.Key key);
        void putAttempt(CommentCheckpoint.Key key, CommentAttempt attempt);
        // Persist only the cursor-free definition for WorkManager recovery. Clean with the channel.
        void remember(CommentPageRequest query);
        CommentPageRequest query(String fingerprint);
        // Work scheduled by .04 already has a unique name derived from each fingerprint.
        List<String> workFingerprints(String channelId, long retiredRevision);
        // Delete only this revision's comment payload, membership, progress, attempt,
        // query definition and comment-local handled state. Never delete a parent.
        void deleteRevision(String channelId, long retiredRevision);
    }

    interface Work<T> { T run(Transaction tx); }
    <T> T transaction(Work<T> work);
}
