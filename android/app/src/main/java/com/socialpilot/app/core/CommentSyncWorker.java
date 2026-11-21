package com.socialpilot.app.core;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.socialpilot.app.data.local.comment.CommentCache;
import com.socialpilot.app.data.remote.social.CommentPageRequest;
import com.socialpilot.app.data.repository.comment.CommentRepository;
import com.socialpilot.app.data.repository.comment.CommentSyncCoordinator;
import com.socialpilot.app.domain.contract.Cancelable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** One-shot, unique per exact query. The application supplies its repository after cold start. */
public final class CommentSyncWorker extends Worker {
    public interface Provider { CommentRepository comments(); }
    private Cancelable subscription;

    public CommentSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    /** Call on IO. KEEP joins an already enqueued request even while its constraint is unmet. */
    public static synchronized Operation enqueue(Context context, CommentRepository repository, CommentPageRequest q) {
        CommentCache.WorkKey key = repository.prepareWork(q);
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(CommentSyncWorker.class)
                .setInputData(encode(key))
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, CommentSyncCoordinator.BACKOFF_MS, TimeUnit.MILLISECONDS)
                .build();
        return WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(key.fingerprint()), ExistingWorkPolicy.KEEP, work);
    }

    /** Serialize the .04 admission/enqueue pair with cancellation in this process.
     * After process death the Worker revalidates the stored revision on every start. */
    public static synchronized void afterInvalidation(Context context, CommentRepository repository,
            String channelId, long oldRevision) throws InterruptedException, java.util.concurrent.ExecutionException {
        WorkManager wm = WorkManager.getInstance(context);
        repository.afterInvalidation(channelId, oldRevision,
                fingerprint -> wm.cancelUniqueWork(uniqueName(fingerprint)).getResult().get());
    }

    static String uniqueName(String fingerprint) { return "sp041-comments-" + fingerprint; }

    static Data encode(CommentCache.WorkKey key) {
        return new Data.Builder().putString("query", key.fingerprint()).putLong("revision", key.revision())
                .putLong("completedBefore", key.completedBefore()).build();
    }

    @NonNull @Override public Result doWork() {
        Context app = getApplicationContext();
        if (!(app instanceof Provider)) return failure("DEPENDENCIES_UNAVAILABLE");
        CommentRepository repository = ((Provider) app).comments();
        if (repository == null) return failure("DEPENDENCIES_UNAVAILABLE");
        String fingerprint = getInputData().getString("query");
        long revision = getInputData().getLong("revision", -1);
        long before = getInputData().getLong("completedBefore", -1);
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || revision < 1 || before < 0)
            return failure("INVALID_WORK");
        if (getRunAttemptCount() >= CommentSyncCoordinator.MAX_WORK_STARTS) return failure("EXHAUSTED");
        try {
            ArrayBlockingQueue<CommentSyncCoordinator.Result> reply = new ArrayBlockingQueue<>(1);
            synchronized (this) {
                if (isStopped()) return failure("CANCELLED");
                subscription = repository.coordinator.resume(new CommentCache.WorkKey(fingerprint, revision, before), reply::offer);
                waiting = Thread.currentThread();
            }
            CommentSyncCoordinator.Result outcome = reply.take();
            Data.Builder output = new Data.Builder().putString("state", outcome.state().name());
            if (outcome.error() != null) output.putString("error", outcome.error().code.name());
            if (outcome.reason() != null) output.putString("reason", outcome.reason());
            if (outcome.retryAt() != null) output.putLong("retryAt", outcome.retryAt());
            switch (outcome.state()) {
                case WAIT:
                case OFFLINE:
                    if (getRunAttemptCount() + 1 < CommentSyncCoordinator.MAX_WORK_STARTS) return Result.retry();
                    return Result.failure(output.putString("state", "EXHAUSTED").build());
                case FAILED:
                case CANCELLED:
                case OBSOLETE:
                    return Result.failure(output.build());
                default:
                    return Result.success(output.build());
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return failure("CANCELLED");
        } catch (com.socialpilot.app.data.local.comment.CommentPageWriter.Rejected rejected) {
            return failure(rejected.reason.name());
        } finally {
            synchronized (this) {
                waiting = null;
                if (subscription != null) subscription.cancel();
                subscription = null;
            }
        }
    }

    private Thread waiting;
    @Override public synchronized void onStopped() {
        if (subscription != null) subscription.cancel();
        if (waiting != null) waiting.interrupt();
    }
    private static Result failure(String reason) {
        return Result.failure(new Data.Builder().putString("state", reason).build());
    }
}
