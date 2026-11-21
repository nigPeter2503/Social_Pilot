package com.socialpilot.app.data.repository.comment;

import com.socialpilot.app.data.local.comment.*;
import com.socialpilot.app.data.remote.social.*;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.SocialError;
import java.util.*;
import java.util.concurrent.*;

/** Application-scoped. Use a background executor separate from the adapter dispatcher. */
public final class CommentSyncCoordinator {
    public interface Adapters { CommentAdapter forQuery(CommentPageRequest query); }
    public interface Network { boolean connected(); }
    public interface Callback { void complete(Result result); }
    public enum State { COMPLETE, PAGE_LIMIT, PAGING_UNKNOWN, WAIT, OFFLINE, FAILED, CANCELLED, OBSOLETE }
    public record Result(State state, String reason, SocialError error, Long retryAt) {}
    public static final long BACKOFF_MS = 30_000;
    public static final int MAX_WORK_STARTS = 4;
    public static final int MAX_PAGES = 5;
    private static Result result(State state) { return new Result(state, null, null, null); }

    private record Post(String channel, String post) {}
    private static final class Flight {
        final CommentPageRequest query;
        final Post post;
        volatile long completedBefore;
        final List<Listener> listeners = new ArrayList<>();
        boolean stopped;
        boolean disconnected;
        Cancelable http;
        Thread runner;
        Flight(CommentPageRequest q, long before) {
            query = q;
            post = new Post(q.channelId, q.postId);
            completedBefore = before;
        }
        synchronized void enter() throws InterruptedException {
            check();
            runner = Thread.currentThread();
        }
        synchronized void check() throws InterruptedException {
            if (stopped || Thread.currentThread().isInterrupted()) throw new InterruptedException();
        }
        synchronized void stop() {
            stopped = true;
            if (http != null) http.cancel();
            if (runner != null) runner.interrupt();
        }
        synchronized void leave() { runner = null; }
    }

    private final class Listener implements Cancelable {
        final Flight flight;
        final Callback callback;
        boolean done;
        Listener(Flight flight, Callback callback) { this.flight = flight; this.callback = callback; }
        @Override public void cancel() {
            synchronized (this) {
                if (done) return;
                done = true;
            }
            detach(this);
        }
        synchronized void deliver(Result value) {
            if (done) return;
            done = true;
            callback.complete(value);
        }
    }

    private final Map<Post, Deque<Flight>> queues = new HashMap<>();
    final CommentCache cache;
    final CommentAdapter.Clock clock;
    final Network network;
    private final Adapters adapters;
    private final Executor io;
    private final int pageBudget;

    public CommentSyncCoordinator(CommentCache cache, Adapters adapters, CommentAdapter.Clock clock,
            Network network, Executor io, int pageBudget) {
        if (pageBudget < 1 || pageBudget > MAX_PAGES) throw new IllegalArgumentException("page budget");
        this.cache = cache;
        this.adapters = adapters;
        this.clock = clock;
        this.network = network;
        this.io = io;
        this.pageBudget = pageBudget;
    }

    public Cancelable refresh(CommentPageRequest q, Callback callback) {
        return submit(q, -1, callback);
    }

    /** Worker IO entry. Work Data carries only fingerprint, revision and completion counter. */
    public Cancelable resume(CommentCache.WorkKey key, Callback callback) {
        CommentPageRequest q = cache.resolve(key);
        if (q != null) return submit(q, key.completedBefore(), value -> {
            // A Worker sharing a pull can lose the revision after resolve and before commit.
            if (value.error() != null && value.error().code == SocialError.Code.CHANNEL_DISCONNECTED)
                callback.complete(result(State.OBSOLETE));
            else callback.complete(value);
        });
        callback.complete(result(State.OBSOLETE));
        return () -> {};
    }

    private synchronized Cancelable submit(CommentPageRequest q, long before, Callback callback) {
        if (!CommentContinuation.isFirstPage(q)) throw new IllegalArgumentException("first page required");
        Flight flight = new Flight(q, before);
        Deque<Flight> queue = queues.get(flight.post);
        if (queue == null) {
            queue = new ArrayDeque<>();
            queues.put(flight.post, queue);
        }
        for (Flight pending : queue) {
            if (!pending.stopped && pending.query.queryFingerprint.equals(q.queryFingerprint)) {
                // A new pull (or a later enqueue) must not inherit an older Work's completion shortcut.
                if (before < 0 || pending.completedBefore < 0) pending.completedBefore = -1;
                else pending.completedBefore = Math.max(before, pending.completedBefore);
                Listener listener = new Listener(pending, callback);
                pending.listeners.add(listener);
                return listener;
            }
        }
        Listener listener = new Listener(flight, callback);
        flight.listeners.add(listener);
        queue.add(flight);
        if (queue.size() == 1) dispatch(flight);
        return listener;
    }

    synchronized boolean loading(CommentPageRequest q) {
        Deque<Flight> queue = queues.get(new Post(q.channelId, q.postId));
        if (queue != null) for (Flight flight : queue)
            if (!flight.stopped && flight.query.queryFingerprint.equals(q.queryFingerprint)) return true;
        return false;
    }

    private synchronized void detach(Listener listener) {
        Flight f = listener.flight;
        if (!f.listeners.remove(listener) || !f.listeners.isEmpty()) return;
        f.stop();
        Deque<Flight> queue = queues.get(f.post);
        if (queue != null && queue.peek() != f) queue.remove(f);
    }

    /** Invalidation owner calls this after advancing the durable revision. */
    public synchronized void cancelForConnection(String channelId, long revision) {
        for (Deque<Flight> queue : queues.values()) for (Flight f : queue)
            if (f.query.channelId.equals(channelId) && f.query.connectionRevision == revision) {
                f.disconnected = true;
                f.stop();
            }
    }

    private void dispatch(Flight flight) {
        io.execute(() -> {
            Result outcome;
            try {
                flight.enter();
                outcome = collect(flight);
            } catch (InterruptedException cancelled) {
                outcome = result(State.CANCELLED);
            } catch (CommentPageWriter.Rejected rejected) {
                outcome = rejection(rejected.reason);
            } catch (RuntimeException unavailable) {
                outcome = new Result(State.FAILED, "LOCAL_ERROR", null, null);
            } finally {
                flight.leave();
                Thread.interrupted(); // Do not transfer this flight's cancellation to the next job.
            }
            List<Listener> listeners;
            synchronized (this) {
                if (flight.disconnected) outcome = rejection(CommentPageWriter.Failure.DISCONNECTED);
                else if (flight.stopped) outcome = result(State.CANCELLED);
                Deque<Flight> queue = queues.get(flight.post);
                queue.removeFirst();
                listeners = new ArrayList<>(flight.listeners);
                flight.listeners.clear();
                if (queue.isEmpty()) queues.remove(flight.post);
                else dispatch(queue.element());
            }
            for (Listener listener : listeners) {
                try { listener.deliver(outcome); }
                catch (RuntimeException ignored) { /* One detached/broken UI cannot strand a Worker. */ }
            }
        });
    }

    static Result rejection(CommentPageWriter.Failure reason) {
        SocialError error = null;
        if (reason == CommentPageWriter.Failure.AUTH_REQUIRED || reason == CommentPageWriter.Failure.DISCONNECTED
                || reason == CommentPageWriter.Failure.RETIRED_REVISION)
            error = new SocialError(reason == CommentPageWriter.Failure.AUTH_REQUIRED
                    ? SocialError.Code.AUTH_REQUIRED : SocialError.Code.CHANNEL_DISCONNECTED, SocialError.NextAction.RECONNECT);
        return new Result(State.FAILED, reason.name(), error, null);
    }

    private Result collect(Flight f) throws InterruptedException {
        boolean restarted = false;
        for (int page = 0; page < pageBudget; page++) {
            f.check();
            CommentPageWriter.Attempt attempt = cache.writer.prepare(f.query);
            long before = f.completedBefore;
            if (before >= 0 && attempt.previous != null
                    && attempt.previous.completedChains > before) return result(State.COMPLETE);
            long at = clock.nowMillis();
            try {
                CommentAttempt prior = cache.metadata(attempt);
                Long embargo = cache.rateLimitedUntil(f.query, at);
                if (embargo != null) return new Result(State.WAIT, "PROVIDER_ERROR",
                        new SocialError(SocialError.Code.RATE_LIMITED, SocialError.NextAction.WAIT), embargo);
                if (prior != null && prior.retryAt() != null && at < prior.retryAt())
                    return new Result(State.WAIT, prior.failure(), prior.error(), prior.retryAt());
                int failures = prior == null ? 0 : prior.transientFailures();
                synchronized (f) {
                    f.check();
                    if (!network.connected()) {
                        SocialError error = new SocialError(SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE);
                        cache.record(attempt, new CommentAttempt(at, "OFFLINE", error, null, failures));
                        return new Result(State.OFFLINE, "OFFLINE", error, null);
                    }
                    cache.record(attempt, new CommentAttempt(at, prior == null ? null : prior.failure(),
                            prior == null ? null : prior.error(), null, failures));
                }
                CommentPageReceipt receipt = read(f, attempt);
                synchronized (f) {
                    f.check();
                    if (receipt == null || receipt.result.status == CommentPageResult.Status.FAILURE) {
                        CommentPageResult failure = receipt == null ? null : receipt.result;
                        if (!restarted && failure != null && failure.failure == CommentPageResult.Failure.INVALID_CURSOR
                                && attempt.previous != null && attempt.previous.state == CommentCheckpoint.State.MORE) {
                            cache.writer.restartExpired(attempt);
                            restarted = true;
                            continue;
                        }
                        return failed(attempt, at, failures, failure);
                    }
                    CommentCheckpoint cp = cache.writer.commit(attempt, receipt).checkpoint;
                    if (cp.state == CommentCheckpoint.State.COMPLETE) return result(State.COMPLETE);
                    if (cp.state == CommentCheckpoint.State.UNKNOWN) return result(State.PAGING_UNKNOWN);
                }
            } catch (CommentPageWriter.Rejected rejected) {
                if (rejected.reason != CommentPageWriter.Failure.SUPERSEDED) {
                    Result failure = rejection(rejected.reason);
                    synchronized (f) {
                        f.check();
                        cache.record(attempt, new CommentAttempt(at, failure.reason(), failure.error(), null, 0));
                    }
                    return failure;
                }
                CommentCheckpoint current = cache.writer.prepare(f.query).previous;
                if (current == null) return result(State.OBSOLETE);
                if (current.state == CommentCheckpoint.State.COMPLETE) return result(State.COMPLETE);
                if (current.state == CommentCheckpoint.State.UNKNOWN) return result(State.PAGING_UNKNOWN);
            }
        }
        return result(State.PAGE_LIMIT);
    }

    private CommentPageReceipt read(Flight f, CommentPageWriter.Attempt attempt) throws InterruptedException {
        ArrayBlockingQueue<CommentPageReceipt> reply = new ArrayBlockingQueue<>(1);
        Cancelable call;
        synchronized (f) {
            f.check();
            CommentAdapter adapter = adapters.forQuery(f.query);
            cache.metadata(attempt); // Revalidate the attempt after binding, immediately before dispatch.
            call = CommentPageReceipt.read(adapter, attempt.request, reply::offer);
            f.http = call;
        }
        CommentPageReceipt receipt = null;
        try {
            receipt = reply.poll(30, TimeUnit.SECONDS);
            return receipt;
        } finally {
            synchronized (f) {
                f.http = null;
                if (receipt == null) call.cancel();
            }
        }
    }

    private Result failed(CommentPageWriter.Attempt attempt, long at, int failures, CommentPageResult r) {
        String reason = r == null ? "CALLBACK_TIMEOUT" : r.failure.name();
        SocialError error = r == null ? new SocialError(SocialError.Code.NETWORK_ERROR,
                SocialError.NextAction.RETRY_WHEN_SAFE) : r.error;
        boolean retry = error != null && (error.code == SocialError.Code.NETWORK_ERROR || error.code == SocialError.Code.RATE_LIMITED);
        Long retryAt = null;
        if (retry) {
            long now = clock.nowMillis();
            long delay = BACKOFF_MS << Math.min(failures, MAX_WORK_STARTS - 1);
            retryAt = now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
            if (r != null && r.retryAfterAt != null) retryAt = Math.max(retryAt, r.retryAfterAt);
        }
        cache.record(attempt, new CommentAttempt(at, reason, error, retryAt, retry ? Math.min(failures + 1, MAX_WORK_STARTS) : 0));
        return new Result(retry ? State.WAIT : State.FAILED, reason, error, retryAt);
    }
}
