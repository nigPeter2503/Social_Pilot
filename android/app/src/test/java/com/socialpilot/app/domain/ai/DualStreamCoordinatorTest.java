package com.socialpilot.app.domain.ai;

import static com.socialpilot.app.domain.ai.BranchSnapshot.Phase.*;
import static com.socialpilot.app.domain.ai.DualStreamTestSupport.*;
import static org.junit.Assert.*;

import com.socialpilot.app.domain.contract.AiProvider;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiEvent;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class DualStreamCoordinatorTest {
    private static final AiUsage USAGE = new AiUsage(4L, 3L, 7L, null, null);

    @Test public void cancelStopsFutureAndTransportAndRetryRejectsOldCallbacks() throws Exception {
        CountDownLatch aRunning = new CountDownLatch(1);
        CountDownLatch aInterrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger cancellations = new AtomicInteger();
        Map<String, AiProvider.Listener> listeners = new ConcurrentHashMap<>();
        AiRequest a = request("A", "a1", "old");
        Ledger ledger = new Ledger();
        AiProvider provider = (req, listener) -> {
            listeners.put(req.requestId, listener);
            return new AiProvider.Attempt() {
                @Override public void run() {
                    listener.onEvent(AiEvent.started(req, 1));
                    if (!"a1".equals(req.requestId)) {
                        complete(req, listener, req.prompt);
                        return;
                    }
                    listener.onEvent(AiEvent.text(req, 2, "prefix"));
                    aRunning.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        aInterrupted.countDown();
                    }
                    listener.onEvent(AiEvent.text(req, 3, "late"));
                    listener.onEvent(AiEvent.terminal(req, 4, AiEvent.Type.CANCELLED,
                            null, USAGE, true, true));
                }

                @Override public void cancel() { cancellations.incrementAndGet(); }
            };
        };
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING)) {
            assertTrue(coordinator.start(a, request("B", "b1", "bravo")));
            assertTrue(aRunning.await(5, TimeUnit.SECONDS));
            assertTrue(coordinator.cancelBranch("A"));
            assertFalse(coordinator.cancelBranch("A"));
            assertTrue(aInterrupted.await(5, TimeUnit.SECONDS));
            DualStreamCoordinator.Snapshot cancelled = await(coordinator,
                    s -> s.first.canRetry && s.second.phase == COMPLETED);
            assertEquals(CANCELLED, cancelled.first.phase);
            assertEquals("prefix", cancelled.first.text);
            assertTrue(cancelled.first.partial);
            assertEquals("bravo", cancelled.second.text);
            assertEquals(1, cancellations.get());
            assertEquals("SETTLED", ledger.state("a1"));
            assertEquals(1, ledger.settlements("a1"));
            assertFalse(coordinator.retry(a));
            assertTrue(coordinator.retry(request("A", "a2", "replacement")));
            await(coordinator, s -> s.first.phase == COMPLETED);
            listeners.get("a1").onEvent(AiEvent.text(a, 5, "old after retry"));
            DualStreamCoordinator.Snapshot done = coordinator.snapshot();
            assertEquals("a2", done.first.requestId);
            assertEquals("replacement", done.first.text);
            assertEquals("b1", done.second.requestId);
            assertEquals("bravo", done.second.text);
        } finally {
            release.countDown();
        }
    }

    @Test public void cancellingQueuedFuturesReleasesBeforeDispatch() throws Exception {
        ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2));
        CountDownLatch occupied = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Runnable blocker = () -> {
            occupied.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        workers.execute(blocker);
        workers.execute(blocker);
        AtomicInteger runs = new AtomicInteger();
        Ledger ledger = new Ledger();
        AiProvider provider = (req, listener) -> new AiProvider.Attempt() {
            @Override public void run() { runs.incrementAndGet(); }
            @Override public void cancel() { }
        };
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING, workers)) {
            assertTrue(occupied.await(5, TimeUnit.SECONDS));
            assertTrue(coordinator.start(request("A", "a", "a"), request("B", "b", "b")));
            assertEquals(2, workers.getQueue().size());
            coordinator.cancelSession();
            assertEquals(0, workers.getQueue().size());
            assertEquals(CANCELLED, coordinator.snapshot().first.phase);
            assertEquals(CANCELLED, coordinator.snapshot().second.phase);
            assertEquals("RELEASED", ledger.state("a"));
            assertEquals("RELEASED", ledger.state("b"));
            assertEquals(0, runs.get());
        } finally {
            release.countDown();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(0, runs.get());
    }

    @Test public void rejectsAWithoutStoppingBAndAcceptsFreshRetry() throws Exception {
        Ledger ledger = new Ledger("a1");
        AiProvider provider = successfulProvider();
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING)) {
            coordinator.start(request("A", "a1", "a"), request("B", "b1", "b"));
            DualStreamCoordinator.Snapshot rejected = await(coordinator, s -> s.second.phase == COMPLETED);
            assertEquals(FAILED, rejected.first.phase);
            assertEquals(BranchSnapshot.Problem.BUDGET_LIMIT, rejected.first.problem);
            assertNull(ledger.state("a1"));
            assertFalse(coordinator.retry(request("A", "b1", "reuse other ID")));
            assertTrue(coordinator.retry(request("A", "a2", "retry")));
            DualStreamCoordinator.Snapshot done = await(coordinator, s -> s.first.phase == COMPLETED);
            assertEquals("retry", done.first.text);
            assertEquals("b1", done.second.requestId);
        }
    }

    @Test public void abnormalProviderExitAndSequenceGapFailIndependently() throws Exception {
        Ledger ledger = new Ledger();
        AiProvider provider = (req, listener) -> new AiProvider.Attempt() {
            @Override public void run() {
                listener.onEvent(AiEvent.started(req, 1));
                if ("A".equals(req.channelId)) listener.onEvent(AiEvent.text(req, 3, "gap"));
            }

            @Override public void cancel() { }
        };
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING)) {
            coordinator.start(request("A", "a", "a"), request("B", "b", "b"));
            DualStreamCoordinator.Snapshot done = await(coordinator, s -> s.first.canRetry && s.second.canRetry);
            assertEquals(BranchSnapshot.Problem.PROTOCOL, done.first.problem);
            assertEquals(BranchSnapshot.Problem.PROTOCOL, done.second.problem);
            assertEquals("", done.first.text);
            assertEquals("UNCERTAIN", ledger.state("a"));
            assertEquals("UNCERTAIN", ledger.state("b"));
        }
    }

    @Test public void ledgerFailureDoesNotDispatchAOrBlockB() throws Exception {
        Ledger ledger = new Ledger() {
            @Override public synchronized Admission reserve(String id, AiCostEstimate estimate) {
                if ("a".equals(id)) throw new IllegalStateException("fixture ledger unavailable");
                return super.reserve(id, estimate);
            }
        };
        AtomicInteger prepared = new AtomicInteger();
        AiProvider provider = (req, listener) -> {
            prepared.incrementAndGet();
            assertEquals("B", req.channelId);
            return successfulProvider().prepare(req, listener);
        };
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING)) {
            coordinator.start(request("A", "a", "a"), request("B", "b", "b"));
            DualStreamCoordinator.Snapshot result = await(coordinator, s -> s.second.phase == COMPLETED);
            assertEquals(BranchSnapshot.Problem.BUDGET_UNAVAILABLE, result.first.problem);
            assertEquals(1, prepared.get());
            assertEquals(0, ledger.settlements("a"));
        }
    }

    @Test public void executorRejectionReleasesBothReservations() {
        ThreadPoolExecutor stopped = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2));
        stopped.shutdown();
        Ledger ledger = new Ledger();
        try (DualStreamCoordinator coordinator =
                     new DualStreamCoordinator(id -> successfulProvider(), ledger, PRICING, stopped)) {
            coordinator.start(request("A", "a", "a"), request("B", "b", "b"));
            assertEquals(BranchSnapshot.Problem.EXECUTOR, coordinator.snapshot().first.problem);
            assertEquals(BranchSnapshot.Problem.EXECUTOR, coordinator.snapshot().second.problem);
            assertEquals("RELEASED", ledger.state("a"));
            assertEquals("RELEASED", ledger.state("b"));
        }
    }

    @Test public void rotateAndRestoreDoNotSubmitAgainAndCoalesceSnapshots() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        AiProvider provider = (req, listener) -> new AiProvider.Attempt() {
            @Override public void run() {
                requests.incrementAndGet();
                listener.onEvent(AiEvent.started(req, 1));
                listener.onEvent(AiEvent.text(req, 2, "prefix"));
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                listener.onEvent(AiEvent.text(req, 3, " suffix"));
                listener.onEvent(AiEvent.terminal(req, 4, AiEvent.Type.COMPLETED, null, USAGE, false, true));
                finished.countDown();
            }

            @Override public void cancel() { }
        };
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, new Ledger(), PRICING)) {
            coordinator.start(request("A", "a", "a"), request("B", "b", "b"));
            DualStreamCoordinator.Snapshot checkpoint = await(coordinator,
                    s -> "prefix".equals(s.first.text) && "prefix".equals(s.second.text));
            assertEquals(INTERRUPTED, checkpoint.first.afterProcessDeath().phase);
            assertEquals("prefix", checkpoint.first.afterProcessDeath().text);
            assertFalse(checkpoint.first.afterProcessDeath().canRetry);
            Queue<Runnable> ui = new ConcurrentLinkedQueue<>();
            AtomicInteger frames = new AtomicInteger();
            Cancelable old = coordinator.observe(ui::add, s -> fail("Detached observer invoked"));
            old.cancel();
            Cancelable next = coordinator.observe(ui::add, s -> {
                frames.incrementAndGet();
                assertEquals("prefix suffix", s.first.text);
            });
            assertFalse(coordinator.start(request("A", "a2", "duplicate"), request("B", "b2", "duplicate")));
            assertEquals(2, requests.get());
            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            assertEquals(2, ui.size());
            ui.remove().run();
            ui.remove().run();
            next.cancel();
            assertEquals(1, frames.get());
            DualStreamCoordinator.Snapshot done = await(coordinator,
                    s -> s.first.phase == COMPLETED && s.second.phase == COMPLETED);
            assertEquals("prefix suffix", done.first.text);
            assertEquals("prefix", checkpoint.first.text);
            assertSame(done.second, done.second.afterProcessDeath());
        } finally {
            release.countDown();
        }
    }

    private static AiProvider successfulProvider() {
        return (req, listener) -> new AiProvider.Attempt() {
            @Override public void run() {
                listener.onEvent(AiEvent.started(req, 1));
                complete(req, listener, req.prompt);
            }

            @Override public void cancel() { }
        };
    }

    private static void complete(AiRequest req, AiProvider.Listener listener, String text) {
        listener.onEvent(AiEvent.text(req, 2, text));
        listener.onEvent(AiEvent.terminal(req, 3, AiEvent.Type.COMPLETED, null, USAGE, false, true));
    }
}
