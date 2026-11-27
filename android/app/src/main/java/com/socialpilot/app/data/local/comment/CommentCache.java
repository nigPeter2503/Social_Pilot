package com.socialpilot.app.data.local.comment;

import com.socialpilot.app.data.remote.social.CommentContinuation;
import com.socialpilot.app.data.remote.social.CommentPageRequest;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Synchronous IO operations. Scope, query membership, metadata and checkpoint share a transaction. */
public final class CommentCache {
    public record Snapshot(List<StoredComment> rows, CommentCheckpoint checkpoint,
            CommentAttempt attempt, ConnectionStatus connection, boolean queryHasRows) {}
    public record WorkKey(String fingerprint, long revision, long completedBefore) {}

    public final CommentPageWriter writer;
    public CommentCache(CommentPageWriter writer) { this.writer = writer; }

    public WorkKey remember(CommentPageRequest q) {
        if (!CommentContinuation.isFirstPage(q)) throw new IllegalArgumentException("first page required");
        return writer.transaction(tx -> {
            writer.requirePost(tx, q, true);
            tx.remember(q);
            CommentCheckpoint cp = tx.checkpoint(new CommentCheckpoint.Key(q));
            return new WorkKey(q.queryFingerprint, q.connectionRevision, cp == null ? 0 : cp.completedChains);
        });
    }

    public CommentPageRequest resolve(WorkKey key) {
        return writer.transaction(tx -> {
            CommentPageRequest q = tx.query(key.fingerprint());
            if (q == null || !key.fingerprint().equals(q.queryFingerprint)
                    || key.revision() != q.connectionRevision || !CommentContinuation.isFirstPage(q)) return null;
            var channel = tx.channel(q.channelId);
            return channel != null && channel.scope.connectionRevision == key.revision()
                    && channel.status != ConnectionStatus.DISCONNECTED ? q : null;
        });
    }

    public List<String> retiredWork(String channel, long revision) {
        return writer.transaction(tx -> {
            requireRetired(tx, channel, revision);
            return new ArrayList<>(tx.workFingerprints(channel, revision));
        });
    }

    public void cleanRetired(String channel, long revision) {
        writer.transaction(tx -> {
            requireRetired(tx, channel, revision);
            tx.deleteRevision(channel, revision);
            return null;
        });
    }

    private static void requireRetired(CommentStore.Transaction tx, String channel, long revision) {
        var current = tx.channel(channel);
        if (revision < 1 || current == null || current.scope.connectionRevision <= revision)
            throw new CommentPageWriter.Rejected(CommentPageWriter.Failure.REVISION_NOT_RETIRED);
    }

    public Snapshot read(CommentPageRequest q, CommentStore.Handled filter) {
        return writer.transaction(tx -> {
            writer.requirePost(tx, q, false);
            CommentCheckpoint.Key key = new CommentCheckpoint.Key(q);
            List<StoredComment> rows = new ArrayList<>(tx.comments(q, filter));
            Collections.sort(rows, (a, b) -> {
                if (a.createdAt == null && b.createdAt != null) return 1;
                if (a.createdAt != null && b.createdAt == null) return -1;
                int order = a.createdAt == null ? 0 : Long.compare(b.createdAt, a.createdAt);
                return order != 0 ? order : a.observation.commentId.compareTo(b.observation.commentId);
            });
            boolean any = !rows.isEmpty() || filter != CommentStore.Handled.ALL
                    && !tx.comments(q, CommentStore.Handled.ALL).isEmpty();
            return new Snapshot(Collections.unmodifiableList(rows), tx.checkpoint(key), tx.attempt(key),
                    tx.channel(q.channelId).status, any);
        });
    }

    public CommentAttempt metadata(CommentPageWriter.Attempt attempt) {
        return writer.transaction(tx -> {
            writer.current(tx, attempt);
            return tx.attempt(new CommentCheckpoint.Key(attempt.request));
        });
    }

    public void record(CommentPageWriter.Attempt attempt, CommentAttempt value) {
        writer.transaction(tx -> {
            boolean authFailure = value.error() != null
                    && value.error().code == com.socialpilot.app.domain.contract.social.SocialError.Code.AUTH_REQUIRED;
            writer.current(tx, attempt, !authFailure);
            tx.putAttempt(new CommentCheckpoint.Key(attempt.request), value);
            return null;
        });
    }

    public Long rateLimitedUntil(CommentPageRequest q, long now) {
        return writer.transaction(tx -> tx.rateLimitedUntil(q.channelId, q.connectionRevision, now));
    }
}
