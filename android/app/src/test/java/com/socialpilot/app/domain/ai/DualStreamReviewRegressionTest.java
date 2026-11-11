package com.socialpilot.app.domain.ai;

import static com.socialpilot.app.domain.ai.BranchSnapshot.Phase.*;
import static com.socialpilot.app.domain.ai.DualStreamTestSupport.*;
import static org.junit.Assert.*;

import com.socialpilot.app.domain.contract.AiProvider;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiEvent;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class DualStreamReviewRegressionTest {
    private static final AiUsage USAGE = new AiUsage(4L, 3L, 7L, null, null);

    @Test public void invalidTerminalCannotReleaseAfterTextAndDuplicateCannotSettleAgain() throws Exception {
        for (long sequence : new long[] {3, 4}) {
            Ledger ledger = new Ledger();
            AiProvider provider = (req, listener) -> new AiProvider.Attempt() {
                @Override public void run() {
                    listener.onEvent(AiEvent.started(req, 1));
                    listener.onEvent(AiEvent.text(req, 2, "prefix"));
                    AiEvent terminal = failed(req, sequence, false);
                    listener.onEvent(terminal);
                    listener.onEvent(terminal);
                }

                @Override public void cancel() { }
            };
            try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING)) {
                coordinator.start(request("A", "a", "a"), request("B", "b", "b"));
                DualStreamCoordinator.Snapshot result = await(coordinator, s -> s.first.canRetry && s.second.canRetry);
                assertEquals(BranchSnapshot.Problem.PROTOCOL, result.first.problem);
                assertEquals("prefix", result.first.text);
                assertEquals("UNCERTAIN", ledger.state("a"));
                assertEquals(1, ledger.settlements("a"));
                assertEquals("UNCERTAIN", ledger.state("b"));
                assertEquals(1, ledger.settlements("b"));
            }
        }
    }

    @Test public void validUndispatchedTerminalReleasesExactlyOnce() throws Exception {
        Ledger ledger = new Ledger();
        AiProvider provider = (req, listener) -> new AiProvider.Attempt() {
            @Override public void run() {
                AiEvent terminal = failed(req, 1, false);
                listener.onEvent(terminal);
                listener.onEvent(terminal);
            }

            @Override public void cancel() { }
        };
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING)) {
            coordinator.start(request("A", "a", "a"), request("B", "b", "b"));
            await(coordinator, s -> s.first.canRetry && s.second.canRetry);
            assertEquals("RELEASED", ledger.state("a"));
            assertEquals(1, ledger.settlements("a"));
            assertEquals("RELEASED", ledger.state("b"));
            assertEquals(1, ledger.settlements("b"));
        }
    }

    @Test public void protocolGapWithoutDispatchEvidenceStillHoldsReservation() throws Exception {
        Ledger ledger = new Ledger();
        AiProvider provider = (req, listener) -> new AiProvider.Attempt() {
            @Override public void run() {
                listener.onEvent(failed(req, 2, false));
            }

            @Override public void cancel() { }
        };
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING)) {
            coordinator.start(request("A", "a", "a"), request("B", "b", "b"));
            await(coordinator, s -> s.first.canRetry && s.second.canRetry);
            assertEquals("UNCERTAIN", ledger.state("a"));
            assertEquals(1, ledger.settlements("a"));
        }
    }

    @Test public void terminalBeforeOrAfterCancelPreservesTheWinningOutcome() throws Exception {
        for (AiEvent.Type terminal : new AiEvent.Type[] {AiEvent.Type.COMPLETED, AiEvent.Type.FAILED}) {
            for (boolean cancelFirst : new boolean[] {true, false}) {
                checkTerminalRace(terminal, cancelFirst);
            }
        }
    }

    private void checkTerminalRace(AiEvent.Type terminal, boolean cancelFirst) throws Exception {
        Ledger ledger = new Ledger();
        CountDownLatch prefix = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger cancellations = new AtomicInteger();
        AiProvider provider = (req, listener) -> new AiProvider.Attempt() {
            @Override public void run() {
                listener.onEvent(AiEvent.started(req, 1));
                listener.onEvent(AiEvent.text(req, 2, req.prompt));
                if ("A".equals(req.channelId)) {
                    prefix.countDown();
                    awaitGate(finish);
                }
                AiEvent event = AiEvent.terminal(req, 3, "A".equals(req.channelId) ? terminal : AiEvent.Type.COMPLETED,
                        "A".equals(req.channelId) && terminal == AiEvent.Type.FAILED
                                ? new AiError(AiError.Code.CONNECTION_FAILED, null) : null,
                        USAGE, terminal == AiEvent.Type.FAILED && "A".equals(req.channelId), true);
                listener.onEvent(event);
                listener.onEvent(event);
            }

            @Override public void cancel() { cancellations.incrementAndGet(); }
        };
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING)) {
            coordinator.start(request("A", "a", "prefix"), request("B", "b", "keep B"));
            assertTrue(prefix.await(5, TimeUnit.SECONDS));
            if (cancelFirst) {
                assertTrue(coordinator.cancelBranch("A"));
                finish.countDown();
            } else {
                finish.countDown();
                await(coordinator, s -> s.first.phase != RUNNING);
                assertFalse(coordinator.cancelBranch("A"));
            }
            DualStreamCoordinator.Snapshot result = await(coordinator,
                    s -> s.first.phase != RUNNING && s.second.phase == COMPLETED && ledger.settlements("a") == 1);
            assertEquals(cancelFirst ? CANCELLED : terminal == AiEvent.Type.COMPLETED ? COMPLETED : FAILED,
                    result.first.phase);
            assertEquals("prefix", result.first.text);
            assertEquals("keep B", result.second.text);
            assertEquals(COMPLETED, result.second.phase);
            assertEquals("SETTLED", ledger.state("a"));
            assertEquals(1, ledger.settlements("a"));
            assertEquals(1, ledger.settlements("b"));
            assertEquals(cancelFirst ? 1 : 0, cancellations.get());
        } finally {
            finish.countDown();
        }
    }

    @Test public void cancelSessionInterruptsBothRunningFuturesAndCancelsBothTransports() throws Exception {
        TrackingExecutor workers = new TrackingExecutor();
        CountDownLatch running = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(2);
        AtomicInteger cancellations = new AtomicInteger();
        Ledger ledger = new Ledger();
        AiProvider provider = (req, listener) -> new AiProvider.Attempt() {
            @Override public void run() {
                listener.onEvent(AiEvent.started(req, 1));
                listener.onEvent(AiEvent.text(req, 2, "prefix-" + req.channelId));
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException expected) {
                    interrupted.countDown();
                    awaitGate(release);
                }
                listener.onEvent(AiEvent.terminal(req, 3, AiEvent.Type.CANCELLED,
                        null, AiUsage.MISSING, true, true));
            }

            @Override public void cancel() { cancellations.incrementAndGet(); }
        };
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING, workers)) {
            coordinator.start(request("A", "a", "a"), request("B", "b", "b"));
            assertTrue(running.await(5, TimeUnit.SECONDS));
            coordinator.cancelSession();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            assertEquals(2, cancellations.get());
            assertEquals(2, workers.submitted.size());
            for (Future<?> task : workers.submitted) assertTrue(task.isCancelled());
            assertEquals(CANCELLED, coordinator.snapshot().first.phase);
            assertEquals(CANCELLED, coordinator.snapshot().second.phase);
            release.countDown();
            await(coordinator, s -> s.first.canRetry && s.second.canRetry);
            assertEquals("prefix-A", coordinator.snapshot().first.text);
            assertEquals("prefix-B", coordinator.snapshot().second.text);
            assertEquals("UNCERTAIN", ledger.state("a"));
            assertEquals(1, ledger.settlements("a"));
            assertEquals("UNCERTAIN", ledger.state("b"));
            assertEquals(1, ledger.settlements("b"));
        } finally {
            release.countDown();
        }
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test public void failedBranchRetriesAfterQueueRejectionWithoutChangingB() throws Exception {
        ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2));
        Map<String, AtomicInteger> runs = new ConcurrentHashMap<>();
        Map<String, AiProvider.Listener> listeners = new ConcurrentHashMap<>();
        Ledger ledger = new Ledger();
        AiProvider provider = (req, listener) -> {
            listeners.put(req.requestId, listener);
            return new AiProvider.Attempt() {
                @Override public void run() {
                    runs.computeIfAbsent(req.requestId, id -> new AtomicInteger()).incrementAndGet();
                    listener.onEvent(AiEvent.started(req, 1));
                    listener.onEvent(AiEvent.text(req, 2, req.prompt));
                    listener.onEvent("a1".equals(req.requestId) ? failed(req, 3, true)
                            : AiEvent.terminal(req, 3, AiEvent.Type.COMPLETED, null, USAGE, false, true));
                }

                @Override public void cancel() { }
            };
        };
        CountDownLatch occupied = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(2);
        Runnable blocker = () -> {
            occupied.countDown();
            awaitGate(release);
        };
        AiRequest old = request("A", "a1", "old partial");
        try (DualStreamCoordinator coordinator = new DualStreamCoordinator(id -> provider, ledger, PRICING, workers)) {
            coordinator.start(old, request("B", "b1", "keep B"));
            DualStreamCoordinator.Snapshot initial = await(coordinator, s -> s.first.canRetry && s.second.phase == COMPLETED);
            workers.execute(blocker);
            workers.execute(blocker);
            assertTrue(occupied.await(5, TimeUnit.SECONDS));
            workers.execute(drained::countDown);
            workers.execute(drained::countDown);
            assertEquals(2, workers.getQueue().size());
            assertTrue(coordinator.retry(request("A", "a2", "rejected")));
            BranchSnapshot rejected = coordinator.snapshot().first;
            assertEquals(FAILED, rejected.phase);
            assertEquals(BranchSnapshot.Problem.EXECUTOR, rejected.problem);
            assertEquals("a2", rejected.requestId);
            assertTrue(rejected.canRetry);
            assertNull(runs.get("a2"));
            assertEquals("RELEASED", ledger.state("a2"));
            assertEquals(1, ledger.settlements("a2"));
            release.countDown();
            assertTrue(drained.await(5, TimeUnit.SECONDS));
            assertTrue(coordinator.retry(request("A", "a3", "retry A")));
            listeners.get("a1").onEvent(failed(old, 4, true));
            DualStreamCoordinator.Snapshot result = await(coordinator, s -> s.first.phase == COMPLETED);
            assertEquals("retry A", result.first.text);
            assertEquals("a3", result.first.requestId);
            assertEquals(initial.second.requestId, result.second.requestId);
            assertEquals(initial.second.phase, result.second.phase);
            assertEquals(initial.second.text, result.second.text);
            assertEquals(1, runs.get("a1").get());
            assertEquals(1, runs.get("a3").get());
            assertEquals(1, runs.get("b1").get());
            assertEquals("SETTLED", ledger.state("a3"));
            assertEquals(1, ledger.settlements("a1"));
            assertEquals(1, ledger.settlements("a2"));
            assertEquals(1, ledger.settlements("a3"));
            assertEquals(1, ledger.settlements("b1"));
        } finally {
            release.countDown();
        }
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static AiEvent failed(AiRequest request, long sequence, boolean dispatched) {
        return AiEvent.terminal(request, sequence, AiEvent.Type.FAILED,
                new AiError(AiError.Code.CONNECTION_FAILED, null),
                dispatched ? USAGE : AiUsage.MISSING, dispatched, dispatched);
    }

    private static void awaitGate(CountDownLatch gate) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    if (!gate.await(5, TimeUnit.SECONDS)) throw new AssertionError("Gate timed out");
                    return;
                } catch (InterruptedException expected) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static final class TrackingExecutor extends ThreadPoolExecutor {
        final List<Future<?>> submitted = new ArrayList<>();

        TrackingExecutor() { super(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2)); }

        @Override public void execute(Runnable command) {
            submitted.add((Future<?>) command);
            super.execute(command);
        }
    }
}
