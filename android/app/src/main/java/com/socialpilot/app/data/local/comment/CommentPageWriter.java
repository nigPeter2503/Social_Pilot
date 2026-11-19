package com.socialpilot.app.data.local.comment;

import com.socialpilot.app.data.local.publishedpost.PublishedPost;
import com.socialpilot.app.data.local.publishedpost.PublishedPostInput;
import com.socialpilot.app.data.local.publishedpost.PublishedPostScope;
import com.socialpilot.app.data.local.publishedpost.PublishedPostStore.ChannelState;
import com.socialpilot.app.data.remote.social.*;
import com.socialpilot.app.domain.contract.social.AccountType;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import java.util.Objects;
import static com.socialpilot.app.data.local.comment.CommentCheckpoint.State;

/** Synchronous IO port for SP-041.04. Pass the attempt captured by this request's callback. */
public final class CommentPageWriter {
    public enum Failure { SCOPE, DISCONNECTED, AUTH_REQUIRED, MISSING_POST, POST_CONFLICT,
        SOURCE, INVALID_PAGE, INVALID_CURSOR, SUPERSEDED, STORAGE }

    public static final class Rejected extends RuntimeException {
        public final Failure reason;
        private Rejected(Failure reason) { super(reason.name()); this.reason = reason; }
    }

    public static final class Attempt {
        public final CommentPageRequest request;
        /** Previous committed progress, also available for freshness after a failed request. */
        public final CommentCheckpoint previous;
        private final CommentPageRequest first;
        private final String localPostId;

        private Attempt(CommentPageRequest first, String localPostId, CommentCheckpoint previous) {
            this.first = first;
            this.localPostId = localPostId;
            this.previous = previous;
            // Distinct identity even for two first-page attempts with identical parameters.
            request = new CommentPageRequest(first.account, first.channelId, first.connectionRevision,
                    first.externalAccountId, first.postId, first.target, first.targetId, first.threadId,
                    first.query, first.pageSize, first.historyFrom, first.apiVersion,
                    previous == null ? null : previous.continuation);
        }
    }

    public static final class Commit {
        public final CommentCheckpoint checkpoint;
        /** At least one touched row still needs reconciliation. The flag also lives on that row. */
        public final boolean needsRefresh;
        private Commit(CommentCheckpoint checkpoint, boolean refresh) {
            this.checkpoint = checkpoint;
            needsRefresh = refresh;
        }
    }

    private final CommentStore store;
    private final CommentCursorCodec codec;
    private final CommentRecord.Source source;
    private final PublishedPostInput.Source postSource;

    private CommentPageWriter(CommentStore store, CommentCursorCodec codec, CommentRecord.Source source,
            PublishedPostInput.Source postSource) {
        this.store = Objects.requireNonNull(store);
        this.codec = Objects.requireNonNull(codec);
        this.source = source;
        this.postSource = postSource;
    }

    public static CommentPageWriter forSimulation(CommentStore store, CommentCursorCodec codec) {
        return new CommentPageWriter(store, codec, CommentRecord.Source.MO_PHONG, PublishedPostInput.Source.MO_PHONG);
    }

    public static CommentPageWriter forProduction(CommentStore store, CommentCursorCodec codec) {
        return new CommentPageWriter(store, codec, CommentRecord.Source.API_THAT, PublishedPostInput.Source.API_THAT);
    }

    /** Always pass a cursor-free query. Only the matching checkpoint may supply the position. */
    public Attempt prepare(CommentPageRequest first) {
        if (!CommentContinuation.isFirstPage(first)) throw new Rejected(Failure.INVALID_CURSOR);
        return transaction(tx -> {
            PublishedPost post = requirePost(tx, first);
            CommentCheckpoint previous = tx.checkpoint(new CommentCheckpoint.Key(first));
            if (previous != null && !post.id.equals(previous.localPostId)) throw new Rejected(Failure.POST_CONFLICT);
            return new Attempt(first, post.id, previous);
        });
    }

    /** Call only for a confirmed expired/unusable position, never for an unclassified HTTP error. */
    public Attempt restartExpired(Attempt attempt) {
        return transaction(tx -> {
            CommentCheckpoint old = current(tx, attempt);
            if (old == null || old.state != State.MORE) throw new Rejected(Failure.INVALID_CURSOR);
            CommentCheckpoint reset = new CommentCheckpoint(attempt.first, old.localPostId,
                    State.RESTART_REQUIRED, null, null, old.oldestObservedAt, old.newestObservedAt,
                    old.lastPageAt, old.lastCompleteSyncAt);
            tx.putCheckpoint(reset);
            checkPost(tx, attempt);
            return new Attempt(attempt.first, old.localPostId, reset);
        });
    }

    /** Accept only a successful page. Return only after commit. An obsolete callback cannot rewind progress. */
    public Commit commit(Attempt attempt, CommentPageReceipt receipt) {
        if (receipt.request != attempt.request || receipt.result.status != CommentPageResult.Status.SUCCESS)
            throw new Rejected(Failure.INVALID_PAGE);
        CommentPage page = receipt.result.page;
        return transaction(tx -> {
            CommentCheckpoint old = current(tx, attempt);
            CommentPageRequest query = attempt.request;
            if (page == null || !query.queryFingerprint.equals(page.queryFingerprint)
                    || !matches(query, page.scope, page.postId) || page.fetchedAt < 0)
                throw new Rejected(Failure.INVALID_PAGE);
            if (page.source != source) throw new Rejected(Failure.SOURCE);
            boolean more = Boolean.TRUE.equals(page.hasMore);
            if (more != (page.nextCursor != null)
                    || more && !CommentContinuation.canResume(codec, query, page.nextCursor))
                throw new Rejected(Failure.INVALID_CURSOR);
            Long oldest = old == null ? null : old.oldestObservedAt;
            Long newest = old == null ? null : old.newestObservedAt;
            boolean refresh = false;
            for (CommentRecord item : page.items) {
                if (!matches(query, item.scope, item.postId) || item.syncedAt != page.fetchedAt)
                    throw new Rejected(Failure.INVALID_PAGE);
                if (item.source != source) throw new Rejected(Failure.SOURCE);
                StoredComment prior = tx.comment(query.platform, query.channelId, item.commentId);
                if (prior != null && (!attempt.localPostId.equals(prior.localPostId)
                        || !matches(query, prior.observation.scope, prior.observation.postId)))
                    throw new Rejected(Failure.POST_CONFLICT);
                StoredComment row = prior == null
                        ? new StoredComment(attempt.localPostId, query.queryFingerprint, item, null, false)
                        : prior.merge(item, query.queryFingerprint);
                if (row != prior) tx.putComment(row);
                refresh |= row.needsRefresh;
                if (row.createdAt != null) {
                    if (oldest == null || row.createdAt < oldest) oldest = row.createdAt;
                    if (newest == null || row.createdAt > newest) newest = row.createdAt;
                }
            }
            State state = page.hasMore == null ? State.UNKNOWN : more ? State.MORE : State.COMPLETE;
            Long completeAt = old == null ? null : old.lastCompleteSyncAt;
            if (state == State.COMPLETE && (completeAt == null || page.fetchedAt > completeAt))
                completeAt = page.fetchedAt;
            long pageAt = old == null ? page.fetchedAt : Math.max(old.lastPageAt, page.fetchedAt);
            CommentCheckpoint next = new CommentCheckpoint(query, attempt.localPostId, state, page.paginationReason,
                    page.nextCursor, oldest, newest, pageAt, completeAt);
            tx.putCheckpoint(next);
            checkPost(tx, attempt);
            return new Commit(next, refresh);
        });
    }

    private CommentCheckpoint current(CommentStore.Transaction tx, Attempt attempt) {
        checkPost(tx, attempt);
        CommentCheckpoint actual = tx.checkpoint(new CommentCheckpoint.Key(attempt.first));
        String generation = actual == null ? null : actual.generation;
        if (!Objects.equals(generation, attempt.previous == null ? null : attempt.previous.generation))
            throw new Rejected(Failure.SUPERSEDED);
        return actual;
    }

    private void checkPost(CommentStore.Transaction tx, Attempt attempt) {
        if (!attempt.localPostId.equals(requirePost(tx, attempt.first).id))
            throw new Rejected(Failure.POST_CONFLICT);
    }

    private PublishedPost requirePost(CommentStore.Transaction tx, CommentPageRequest q) {
        ChannelState channel = tx.channel(q.channelId);
        if (channel == null) throw new Rejected(Failure.SCOPE);
        if (channel.status == ConnectionStatus.DISCONNECTED) throw new Rejected(Failure.DISCONNECTED);
        if (channel.status != ConnectionStatus.CONNECTED) throw new Rejected(Failure.AUTH_REQUIRED);
        AccountType type;
        switch (q.account) {
            case FACEBOOK_PAGE: type = AccountType.PAGE; break;
            case LINKEDIN_MEMBER: type = AccountType.PERSON; break;
            case LINKEDIN_ORGANIZATION: type = AccountType.ORGANIZATION; break;
            default: throw new Rejected(Failure.SCOPE);
        }
        PublishedPostScope expected = new PublishedPostScope(q.platform, type, q.channelId,
                q.connectionRevision, q.externalAccountId);
        if (!expected.same(channel.scope)) throw new Rejected(Failure.SCOPE);
        PublishedPost post = tx.byExternalId(q.channelId, q.postId);
        if (post == null) throw new Rejected(Failure.MISSING_POST);
        if (!expected.same(post.scope) || !q.postId.equals(post.externalPostId))
            throw new Rejected(Failure.POST_CONFLICT);
        if (post.source != postSource) throw new Rejected(Failure.SOURCE);
        return post;
    }

    private static boolean matches(CommentPageRequest q, CommentRecord.Scope s, String postId) {
        return q.platform == s.platform && q.channelId.equals(s.channelId)
                && q.connectionRevision == s.connectionRevision && q.postId.equals(postId);
    }

    private <T> T transaction(CommentStore.Work<T> work) {
        try {
            return store.transaction(work);
        } catch (Rejected rejection) {
            throw rejection;
        } catch (RuntimeException failure) {
            throw new Rejected(Failure.STORAGE);
        }
    }
}
