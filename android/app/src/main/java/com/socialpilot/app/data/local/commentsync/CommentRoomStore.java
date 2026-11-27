package com.socialpilot.app.data.local.commentsync;

import com.google.gson.Gson;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.local.ChannelEntity;
import com.socialpilot.app.data.local.comment.*;
import com.socialpilot.app.data.local.publishedpost.*;
import com.socialpilot.app.data.remote.social.CommentContinuation;
import com.socialpilot.app.data.remote.social.CommentPageRequest;
import com.socialpilot.app.domain.contract.social.*;
import java.util.ArrayList;
import java.util.List;

/** All store ports use the same Room transaction and Channel DAO, including after process restart. */
public final class CommentRoomStore {
    public interface Schema { CommentSyncDao commentSync(); }

    private final AppDatabase db;
    private final CommentSyncDao dao;
    private final Gson json = new Gson();
    private final Transaction tx = new Transaction();

    public CommentRoomStore(AppDatabase db) {
        this.db = db;
        this.dao = ((Schema) db).commentSync();
    }

    public final PublishedPostStore posts = new PublishedPostStore() {
        @Override public <T> T transaction(Work<T> work) {
            return db.runInTransaction(() -> work.run(tx));
        }
    };
    public final CommentStore comments = new CommentStore() {
        @Override public <T> T transaction(Work<T> work) {
            return db.runInTransaction(() -> work.run(tx));
        }
    };

    private static void requireUpdated(int count) {
        if (count != 1) throw new IllegalStateException("Missing sync row");
    }

    private final class Transaction implements PublishedPostStore.Transaction, CommentStore.Transaction {
        public PublishedPostStore.ChannelState channel(String id) {
            ChannelEntity row = db.channels().find(id);
            if (row == null) return null;
            PublishedPostScope scope = new PublishedPostScope(Platform.valueOf(row.platform),
                    AccountType.valueOf(row.accountType), row.channelId, row.connectionRevision, row.externalAccountId);
            ConnectionStatus status = ConnectionStatus.valueOf(row.status);
            if (status == ConnectionStatus.CONNECTED && (row.credentialRef == null || row.credentialRevision == null
                    || row.effectiveExpiresAtMillis != null && row.effectiveExpiresAtMillis <= System.currentTimeMillis()))
                status = ConnectionStatus.REAUTH_REQUIRED;
            return new PublishedPostStore.ChannelState(scope, status);
        }

        public PublishedPost byExternalId(String channel, String external) {
            return json.fromJson(dao.post(channel, external), PublishedPost.class);
        }
        public PublishedPost byLocalId(String id) { return json.fromJson(dao.post(id), PublishedPost.class); }
        public void insert(PublishedPost post) { dao.insert(postRow(post)); }
        public void update(PublishedPost post) { requireUpdated(dao.update(postRow(post))); }

        private CommentTables.Post postRow(PublishedPost post) {
            CommentTables.Post row = new CommentTables.Post();
            row.id = post.id;
            row.channelId = post.scope.channelId;
            row.externalPostId = post.externalPostId;
            row.payload = json.toJson(post);
            return row;
        }

        public StoredComment comment(Platform platform, String channel, String id) {
            CommentTables.Comment row = dao.comment(platform.name(), channel, id);
            return row == null ? null : json.fromJson(row.payload, StoredComment.class);
        }
        public void putComment(StoredComment value) {
            var observed = value.observation;
            String platform = observed.scope.platform.name();
            CommentTables.Comment row = dao.comment(platform, observed.scope.channelId, observed.commentId);
            if (row == null || row.revision < observed.scope.connectionRevision) row = new CommentTables.Comment();
            else if (row.revision != observed.scope.connectionRevision || !value.localPostId.equals(row.localPostId))
                throw new IllegalStateException("Comment ownership conflict");
            row.platform = platform;
            row.channelId = observed.scope.channelId;
            row.commentId = observed.commentId;
            row.localPostId = value.localPostId;
            row.revision = observed.scope.connectionRevision;
            row.createdAt = value.createdAt;
            row.payload = json.toJson(value);
            dao.save(row);
        }

        public CommentCheckpoint checkpoint(CommentCheckpoint.Key key) {
            return json.fromJson(dao.checkpoint(key.queryFingerprint), CommentCheckpoint.class);
        }
        public void putCheckpoint(CommentCheckpoint value) {
            requireUpdated(dao.checkpoint(value.key.queryFingerprint, json.toJson(value)));
        }
        public void include(CommentCheckpoint.Key key, String id) {
            var member = new CommentTables.Membership();
            member.fingerprint = key.queryFingerprint;
            member.commentId = id;
            dao.include(member);
        }
        public List<StoredComment> comments(CommentPageRequest q, CommentStore.Handled filter) {
            Boolean handled = filter == CommentStore.Handled.ALL ? null : filter == CommentStore.Handled.HANDLED;
            List<StoredComment> rows = new ArrayList<>();
            for (String payload : dao.comments(q.queryFingerprint, handled, q.historyFrom))
                rows.add(json.fromJson(payload, StoredComment.class));
            return rows;
        }
        public Long rateLimitedUntil(String channel, long revision, long now) {
            return dao.embargo(channel, revision, now);
        }
        public CommentAttempt attempt(CommentCheckpoint.Key key) {
            return json.fromJson(dao.attempt(key.queryFingerprint), CommentAttempt.class);
        }
        public void putAttempt(CommentCheckpoint.Key key, CommentAttempt value) {
            Long until = value.error() != null && value.error().code == SocialError.Code.RATE_LIMITED
                    ? value.retryAt() : null;
            requireUpdated(dao.attempt(key.queryFingerprint, json.toJson(value), until));
        }
        public void remember(CommentPageRequest q) {
            if (!CommentContinuation.isFirstPage(q)) throw new IllegalArgumentException("Cursor-free query required");
            PublishedPost post = byExternalId(q.channelId, q.postId);
            if (post == null) throw new IllegalStateException("Missing published post");
            var row = new CommentTables.QueryState();
            row.fingerprint = q.queryFingerprint;
            row.channelId = q.channelId;
            row.revision = q.connectionRevision;
            row.localPostId = post.id;
            row.definition = json.toJson(q);
            dao.remember(row);
        }
        public CommentPageRequest query(String fingerprint) {
            var saved = json.fromJson(dao.definition(fingerprint), CommentPageRequest.class);
            if (saved == null) return null;
            var q = new CommentPageRequest(saved.account, saved.channelId, saved.connectionRevision,
                    saved.externalAccountId, saved.postId, saved.target, saved.targetId, saved.threadId,
                    saved.query, saved.pageSize, saved.historyFrom, saved.apiVersion, null);
            return fingerprint.equals(q.queryFingerprint) ? q : null;
        }
        public List<String> workFingerprints(String channel, long revision) {
            return dao.fingerprints(channel, revision);
        }
        public void deleteRevision(String channel, long revision) {
            dao.deleteQueries(channel, revision);
            dao.deleteComments(channel, revision);
        }
    }
}
