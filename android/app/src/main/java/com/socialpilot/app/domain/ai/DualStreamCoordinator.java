package com.socialpilot.app.domain.ai;

import com.socialpilot.app.domain.ai.BranchSnapshot.Phase;
import com.socialpilot.app.domain.ai.BranchSnapshot.Problem;
import com.socialpilot.app.domain.contract.AiProvider;
import com.socialpilot.app.domain.contract.BudgetManager;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiEvent;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one interactive session and its two transport workers. */
public final class DualStreamCoordinator implements AutoCloseable {
    private static final int MAX_TEXT_CHARS = 1024 * 1024;

    public interface Providers { AiProvider resolve(AiRequest.Provider provider); }

    public interface Pricing {
        AiCostEstimate upperBound(AiRequest request);
        AiCostEstimate fromUsage(AiRequest request, AiUsage usage);
    }

    public interface Observer { void onSnapshot(Snapshot snapshot); }

    public static final class Snapshot {
        public final long revision;
        public final BranchSnapshot first;
        public final BranchSnapshot second;

        private Snapshot(long revision, BranchSnapshot first, BranchSnapshot second) {
            this.revision = revision;
            this.first = first;
            this.second = second;
        }
    }

    private final Providers providers;
    private final BudgetManager budget;
    private final Pricing pricing;
    private final ThreadPoolExecutor workers;
    private final Set<String> requestIds = new HashSet<>();
    // The coordinator monitor serializes commands and provider events. The stream workers
    // apply each event before reading more bytes. No per-delta work is queued for the UI.
    private Branch first;
    private Branch second;
    private volatile long revision;
    private boolean closed;
    private volatile Subscription subscription;

    public DualStreamCoordinator(Providers providers, BudgetManager budget, Pricing pricing) {
        this(providers, budget, pricing, new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2)));
    }

    DualStreamCoordinator(Providers providers, BudgetManager budget, Pricing pricing,
                          ThreadPoolExecutor workers) {
        this.providers = Objects.requireNonNull(providers);
        this.budget = Objects.requireNonNull(budget);
        this.pricing = Objects.requireNonNull(pricing);
        this.workers = Objects.requireNonNull(workers);
    }

    /** Accepts one pair per coordinator. Each branch can fail admission independently. */
    public boolean start(AiRequest a, AiRequest b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        if (!a.sessionId.equals(b.sessionId) || a.channelId == null || b.channelId == null
                || a.channelId.equals(b.channelId) || a.requestId.equals(b.requestId)) {
            throw new IllegalArgumentException("Expected two distinct channel attempts for the same session");
        }
        synchronized (this) {
            if (closed || first != null) return false;
            first = new Branch(a);
            second = new Branch(b);
            launch(first);
            launch(second);
            revision++;
        }
        notifyObserver();
        return true;
    }

    /** Accepts a fresh attempt for a failed or cancelled branch after its worker has drained. */
    public boolean retry(AiRequest request) {
        Objects.requireNonNull(request);
        synchronized (this) {
            Branch previous = find(request.channelId);
            if (closed || previous == null || !previous.canRetry()
                    || !sameScope(previous.request, request) || requestIds.contains(request.requestId)) return false;
            Branch replacement = new Branch(request);
            if (previous == first) first = replacement;
            else second = replacement;
            launch(replacement);
            revision++;
        }
        notifyObserver();
        return true;
    }

    public boolean cancelBranch(String channelId) {
        Work cancelled;
        synchronized (this) {
            cancelled = cancelLocked(find(channelId));
            if (cancelled == null) return false;
            revision++;
        }
        stopTransport(cancelled);
        notifyObserver();
        return true;
    }

    public void cancelSession() {
        stopSession(false);
    }

    @Override
    public void close() {
        stopSession(true);
    }

    private void stopSession(boolean dispose) {
        Work a;
        Work b;
        synchronized (this) {
            if (closed) return;
            if (dispose) closed = true;
            a = cancelLocked(first);
            b = cancelLocked(second);
            if (a != null || b != null || dispose) revision++;
        }
        if (a != null) stopTransport(a);
        if (b != null) stopTransport(b);
        if (dispose) workers.shutdown();
        notifyObserver();
    }

    /** Returns null before start. Taking a snapshot never starts or resumes an attempt. */
    public synchronized Snapshot snapshot() {
        return first == null ? null : new Snapshot(revision, first.snapshot(closed), second.snapshot(closed));
    }

    /** Replaces the previous observer and delivers the latest snapshot using its executor. */
    public Cancelable observe(Executor executor, Observer observer) {
        Subscription next = new Subscription(Objects.requireNonNull(executor), Objects.requireNonNull(observer));
        synchronized (this) {
            subscription = next;
        }
        next.signal();
        return next::detach;
    }

    private void launch(Branch branch) {
        AiRequest request = branch.request;
        if (!requestIds.add(request.requestId)) {
            branch.fail(Problem.DUPLICATE_REQUEST, null);
            return;
        }
        AiCostEstimate limit;
        try {
            limit = pricing.upperBound(request);
        } catch (RuntimeException unavailable) {
            limit = null;
        }
        if (limit == null) {
            branch.fail(Problem.PRICE_UNAVAILABLE, null);
            return;
        }
        BudgetManager.Admission admission;
        try {
            admission = budget.reserve(request.requestId, limit);
        } catch (RuntimeException unavailable) {
            // Do not dispatch or release if the outcome of reserve itself is unknown.
            branch.fail(Problem.BUDGET_UNAVAILABLE, null);
            return;
        }
        if (admission != BudgetManager.Admission.RESERVED) {
            branch.fail(admission == BudgetManager.Admission.LIMIT_REACHED
                    ? Problem.BUDGET_LIMIT : Problem.DUPLICATE_REQUEST, null);
            return;
        }
        Work work = new Work(branch);
        try {
            AiProvider provider = Objects.requireNonNull(providers.resolve(request.provider));
            work.transport = Objects.requireNonNull(provider.prepare(request, event -> accept(work, event)));
        } catch (RuntimeException failure) {
            releaseUndispatched(request.requestId);
            branch.fail(Problem.PROVIDER, null);
            return;
        }
        branch.work = work;
        try {
            workers.execute(work.future);
        } catch (RejectedExecutionException failure) {
            branch.work = null;
            releaseUndispatched(request.requestId);
            branch.fail(Problem.EXECUTOR, null);
        }
    }

    private void run(Work work) {
        synchronized (this) {
            if (work.branch.work != work || work.branch.phase != Phase.RUNNING) return;
            work.entered = true;
        }
        try {
            work.transport.run();
        } finally {
            synchronized (this) {
                Branch branch = work.branch;
                if (!work.settled) holdReservation(branch.request.requestId);
                if (branch.phase == Phase.RUNNING) branch.fail(Problem.PROTOCOL, null);
                branch.work = null;
                revision++;
            }
            notifyObserver();
        }
    }

    private void accept(Work work, AiEvent event) {
        boolean stop = false;
        synchronized (this) {
            Branch branch = work.branch;
            if (branch.work != work || work.settled || !matches(branch.request, event)
                    || event.sequence <= work.sequence) return;
            boolean sequential = event.sequence == work.sequence + 1;
            work.sequence = event.sequence;
            boolean terminal = event.type == AiEvent.Type.COMPLETED || event.type == AiEvent.Type.FAILED
                    || event.type == AiEvent.Type.CANCELLED;
            work.mayHaveDispatched |= event.mayHaveDispatched
                    || event.type == AiEvent.Type.TEXT_DELTA || event.type == AiEvent.Type.COMPLETED;
            boolean valid = sequential && !work.protocolFailed && recordProgress(work, event)
                    && (!terminal || event.mayHaveDispatched || !work.mayHaveDispatched);
            work.protocolFailed |= !valid;
            if (terminal) settle(work, event, valid);
            // A late terminal still closes the budget reservation. Cancelled UI state stays frozen.
            if (branch.phase != Phase.RUNNING) return;
            if (!valid) {
                branch.fail(Problem.PROTOCOL, null);
                stop = !terminal;
            } else {
                applyEvent(work, event);
            }
            revision++;
        }
        if (stop) stopTransport(work);
        notifyObserver();
    }

    private boolean recordProgress(Work work, AiEvent event) {
        switch (event.type) {
            case STARTED:
                if (work.startedEvent) return false;
                work.startedEvent = true;
                return true;
            case TEXT_DELTA:
                if (!work.startedEvent || event.delta.length() > MAX_TEXT_CHARS - work.textChars) return false;
                work.textChars += event.delta.length();
                return true;
            case COMPLETED:
                return work.startedEvent && work.textChars > 0;
            default:
                return true;
        }
    }

    private void applyEvent(Work work, AiEvent event) {
        Branch branch = work.branch;
        switch (event.type) {
            case STARTED:
                break;
            case TEXT_DELTA:
                branch.text.append(event.delta);
                break;
            case COMPLETED:
                branch.usage = event.usage;
                branch.phase = Phase.COMPLETED;
                break;
            case FAILED:
                branch.usage = event.usage;
                branch.fail(Problem.PROVIDER, event.error);
                break;
            case CANCELLED:
                branch.usage = event.usage;
                branch.phase = Phase.CANCELLED;
                break;
            default:
                break;
        }
    }

    private void settle(Work work, AiEvent event, boolean valid) {
        AiRequest request = work.branch.request;
        if (!valid) {
            holdReservation(request.requestId);
        } else if (!work.mayHaveDispatched) {
            releaseUndispatched(request.requestId);
        } else {
            AiCostEstimate charge = null;
            if (event.usage.state == AiUsage.State.REPORTED) {
                try {
                    charge = pricing.fromUsage(request, event.usage);
                } catch (RuntimeException unavailable) {
                    // Keep the reservation if the pinned price cannot be applied.
                }
            }
            if (charge == null) {
                holdReservation(request.requestId);
            } else {
                try {
                    budget.settle(request.requestId, charge);
                } catch (RuntimeException unavailable) {
                    holdReservation(request.requestId);
                }
            }
        }
        work.settled = true;
    }

    private void releaseUndispatched(String requestId) {
        try {
            budget.releaseUndispatched(requestId);
        } catch (RuntimeException unavailable) {
            holdReservation(requestId);
        }
    }

    private void holdReservation(String requestId) {
        try {
            budget.markUncertain(requestId);
        } catch (RuntimeException unavailable) {
            // Leave the existing reservation in place for the ledger to reconcile.
        }
    }

    private Work cancelLocked(Branch branch) {
        if (branch == null || branch.phase != Phase.RUNNING) return null;
        Work work = branch.work;
        branch.phase = Phase.CANCELLED;
        if (!work.entered) {
            releaseUndispatched(branch.request.requestId);
            work.settled = true;
            branch.work = null;
        }
        return work;
    }

    private void stopTransport(Work work) {
        try {
            work.transport.cancel();
        } catch (RuntimeException unavailable) {
            // Still interrupt the worker. A transport without a terminal leaves cost uncertain.
        } finally {
            work.future.cancel(true);
            workers.remove(work.future);
        }
    }

    private Branch find(String channel) {
        if (first == null || channel == null) return null;
        if (channel.equals(first.request.channelId)) return first;
        return channel.equals(second.request.channelId) ? second : null;
    }

    private static boolean sameScope(AiRequest previous, AiRequest next) {
        return previous.sessionId.equals(next.sessionId) && previous.channelId.equals(next.channelId)
                && previous.connectionRevision.equals(next.connectionRevision)
                && previous.stepId.equals(next.stepId) && previous.task == next.task;
    }

    private static boolean matches(AiRequest request, AiEvent event) {
        return request.requestId.equals(event.requestId) && request.sessionId.equals(event.sessionId)
                && request.channelId.equals(event.channelId) && request.connectionRevision.equals(event.connectionRevision)
                && request.stepId.equals(event.stepId) && request.provider == event.provider
                && request.model.equals(event.model);
    }

    private void notifyObserver() {
        Subscription current = subscription;
        if (current != null) current.signal();
    }

    private final class Subscription {
        private final Executor executor;
        private final Observer observer;
        private final AtomicBoolean queued = new AtomicBoolean();
        private long delivered;

        Subscription(Executor executor, Observer observer) {
            this.executor = executor;
            this.observer = observer;
        }

        void signal() {
            if (!queued.compareAndSet(false, true)) return;
            try {
                executor.execute(this::deliver);
            } catch (RejectedExecutionException ignored) {
                queued.set(false);
                detach();
            }
        }

        private void deliver() {
            try {
                if (subscription != this) return;
                long observedRevision = revision;
                Snapshot latest = snapshot();
                if (latest == null) {
                    delivered = observedRevision;
                    return;
                }
                if (latest.revision == delivered) return;
                delivered = latest.revision;
                observer.onSnapshot(latest);
            } finally {
                queued.set(false);
                if (subscription == this && revision != delivered) signal();
            }
        }

        void detach() {
            synchronized (DualStreamCoordinator.this) {
                if (subscription == this) subscription = null;
            }
        }
    }

    private final class Work {
        final Branch branch;
        final FutureTask<Void> future;
        AiProvider.Attempt transport;
        boolean entered;
        boolean startedEvent;
        boolean mayHaveDispatched;
        boolean protocolFailed;
        int textChars;
        boolean settled;
        long sequence;

        Work(Branch branch) {
            this.branch = branch;
            this.future = new FutureTask<>(() -> run(this), null);
        }
    }

    private static final class Branch {
        final AiRequest request;
        final StringBuilder text = new StringBuilder();
        Phase phase = Phase.RUNNING;
        Problem problem;
        AiError error;
        AiUsage usage = AiUsage.MISSING;
        Work work;

        Branch(AiRequest request) { this.request = request; }

        void fail(Problem problem, AiError error) {
            this.problem = problem;
            this.error = error;
            phase = Phase.FAILED;
        }

        boolean canRetry() {
            return work == null && (phase == Phase.FAILED || phase == Phase.CANCELLED);
        }

        BranchSnapshot snapshot(boolean closed) {
            return new BranchSnapshot(request, phase, problem, error, usage, text.toString(), !closed && canRetry());
        }
    }
}
