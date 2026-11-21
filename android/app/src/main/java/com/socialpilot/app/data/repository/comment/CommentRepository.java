package com.socialpilot.app.data.repository.comment;

import com.socialpilot.app.data.local.comment.*;
import com.socialpilot.app.data.remote.social.CommentPageRequest;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.SocialError;
import java.util.Collections;
import java.util.List;

/** Pull refresh and WorkManager must share this application-scoped instance. */
public final class CommentRepository {
    public enum State { LOADING, OFFLINE_CACHED, OFFLINE_NO_CACHE, EMPTY, READY, PARTIAL, NO_CACHE, ERROR }
    public enum Freshness { NEVER_COMPLETED, FRESH, STALE }
    public record View(State state, boolean online, ConnectionStatus connection, List<StoredComment> rows,
            Long historyFrom, Long oldestObservedAt, Long newestObservedAt, Long lastAttemptAt,
            Long lastCompleteSyncAt, Freshness freshness, CommentCheckpoint.State paging, String paginationReason,
            String failure, SocialError error, Long retryAt) {}

    public final CommentSyncCoordinator coordinator;
    private final long freshForMillis;

    public CommentRepository(CommentSyncCoordinator coordinator, long freshForMillis) {
        if (freshForMillis < 0) throw new IllegalArgumentException("freshness interval");
        this.coordinator = coordinator;
        this.freshForMillis = freshForMillis;
    }

    public Cancelable refresh(CommentPageRequest q, CommentSyncCoordinator.Callback callback) {
        return coordinator.refresh(q, callback);
    }

    /** IO thread, before enqueue. Persists the query definition outside Work Data. */
    public CommentCache.WorkKey prepareWork(CommentPageRequest q) { return coordinator.cache.remember(q); }

    public interface CancelWork {
        void cancelAndAwait(String fingerprint) throws InterruptedException, java.util.concurrent.ExecutionException;
    }

    /** IO only, after the Channel owner commits invalidation. Retry with the same retired tuple. */
    public void afterInvalidation(String channel, long revision, CancelWork work)
            throws InterruptedException, java.util.concurrent.ExecutionException {
        List<String> fingerprints = coordinator.cache.retiredWork(channel, revision);
        try {
            for (String fingerprint : fingerprints) work.cancelAndAwait(fingerprint);
        } finally {
            coordinator.cancelForConnection(channel, revision);
        }
        coordinator.cache.cleanRetired(channel, revision);
    }

    /** IO thread. The UI chooses the freshness interval. Missing provider dates remain visible. */
    public View loadCached(CommentPageRequest q, CommentStore.Handled filter) {
        boolean online = coordinator.network.connected();
        CommentCache.Snapshot cached;
        try {
            cached = coordinator.cache.read(q, filter);
        } catch (CommentPageWriter.Rejected denied) {
            CommentSyncCoordinator.Result result = CommentSyncCoordinator.rejection(denied.reason);
            return new View(State.ERROR, online, denied.reason == CommentPageWriter.Failure.DISCONNECTED
                    ? ConnectionStatus.DISCONNECTED : null, Collections.emptyList(), q.historyFrom, null, null,
                    null, null, Freshness.NEVER_COMPLETED, null, null, result.reason(), result.error(), null);
        }
        CommentCheckpoint cp = cached.checkpoint();
        CommentAttempt attempt = cached.attempt();
        String failure = attempt == null ? null : attempt.failure();
        SocialError error = attempt == null ? null : attempt.error();
        if (cached.connection() == ConnectionStatus.REAUTH_REQUIRED) {
            failure = "AUTH_REQUIRED";
            error = new SocialError(SocialError.Code.AUTH_REQUIRED, SocialError.NextAction.RECONNECT);
        }
        Long complete = cp == null ? null : cp.lastCompleteSyncAt;
        boolean finished = cp != null && cp.state == CommentCheckpoint.State.COMPLETE;
        State state;
        if (coordinator.loading(q)) state = State.LOADING;
        else if (!online) state = complete != null || cached.queryHasRows() ? State.OFFLINE_CACHED : State.OFFLINE_NO_CACHE;
        else if (failure != null) state = State.ERROR;
        else if (finished) state = cached.rows().isEmpty() ? State.EMPTY : State.READY;
        else state = cp == null ? State.NO_CACHE : State.PARTIAL;
        long now = coordinator.clock.nowMillis();
        Freshness freshness = complete == null ? Freshness.NEVER_COMPLETED
                : now >= complete && now - complete <= freshForMillis
                ? Freshness.FRESH : Freshness.STALE;
        return new View(state, online, cached.connection(), cached.rows(), q.historyFrom,
                cp == null ? null : cp.oldestObservedAt, cp == null ? null : cp.newestObservedAt,
                attempt == null ? null : attempt.lastAttemptAt(), complete, freshness,
                cp == null ? null : cp.state, cp == null ? null : cp.paginationReason, failure, error,
                attempt == null ? null : attempt.retryAt());
    }
}
