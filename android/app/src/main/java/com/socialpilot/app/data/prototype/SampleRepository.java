package com.socialpilot.app.data.prototype;

import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.socialpilot.app.prototype.SampleCheckpointWorker;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;

/** Serializes demo writes so a late UI event cannot reorder saved stream states. */
public final class SampleRepository {
    private final SampleResultDao dao;
    private final ExecutorService io;

    public SampleRepository(SampleResultDao dao, ExecutorService io) {
        this.dao = dao;
        this.io = io;
    }

    public LiveData<List<SampleResultEntity>> observeAll() { return dao.observeAll(); }

    public void startRun(String channelId, String runId, WorkManager workManager) {
        io.execute(() -> {
            dao.save(new SampleResultEntity(channelId, "", "RUNNING", "NOT_STARTED",
                    runId, null, System.currentTimeMillis()));
            workManager.cancelUniqueWork(workName(channelId));
        });
    }

    public void updateRun(String channelId, String runId, String text, String state) {
        io.execute(() -> dao.updateRun(channelId, runId, text, state,
                System.currentTimeMillis()));
    }

    public void cancelCurrentWork(String channelId, WorkManager workManager) {
        io.execute(() -> {
            SampleResultEntity current = dao.get(channelId);
            if (current != null && current.workRequestId != null
                    && dao.invalidateWork(channelId, current.runId, current.workRequestId,
                    System.currentTimeMillis()) == 1) {
                try {
                    workManager.cancelWorkById(UUID.fromString(current.workRequestId));
                } catch (IllegalArgumentException error) {
                    // The conditional Room update still prevents a malformed saved ID from writing.
                }
            }
        });
    }

    public void enqueueCheckpoint(String channelId, WorkManager workManager) {
        enqueueCheckpoint(channelId, workManager, (name, request) ->
                workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
                        .getResult());
    }

    interface WorkEnqueuer {
        ListenableFuture<Operation.State.SUCCESS> enqueue(String name, OneTimeWorkRequest request);
    }

    void enqueueCheckpoint(String channelId, WorkManager workManager, WorkEnqueuer enqueuer) {
        io.execute(() -> {
            SampleResultEntity result = dao.get(channelId);
            if (result == null || !"COMPLETE".equals(result.state)) return;
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build();
            Data data = new Data.Builder()
                    .putString(SampleCheckpointWorker.KEY_CHANNEL_ID, channelId)
                    .putString(SampleCheckpointWorker.KEY_RUN_ID, result.runId).build();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SampleCheckpointWorker.class)
                    .setConstraints(constraints).setInputData(data).build();
            String workRequestId = request.getId().toString();
            if (dao.assignWork(channelId, result.runId, workRequestId,
                    System.currentTimeMillis()) == 1) {
                try {
                    ListenableFuture<Operation.State.SUCCESS> operation =
                            enqueuer.enqueue(workName(channelId), request);
                    operation.addListener(() -> {
                        try {
                            operation.get();
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            failEnqueue(channelId, result.runId, workRequestId,
                                    request.getId(), workManager);
                        } catch (ExecutionException | RuntimeException error) {
                            failEnqueue(channelId, result.runId, workRequestId,
                                    request.getId(), workManager);
                        }
                    }, io);
                } catch (RuntimeException error) {
                    failEnqueue(channelId, result.runId, workRequestId,
                            request.getId(), workManager);
                }
            }
        });
    }

    private void failEnqueue(String channelId, String runId, String workRequestId,
                             UUID requestId, WorkManager workManager) {
        if (dao.markWorkEnqueueFailed(channelId, runId, workRequestId,
                System.currentTimeMillis()) == 1) {
            workManager.cancelWorkById(requestId);
        }
    }

    /** Repairs a Room assignment if the process stopped before WorkManager stored its job. */
    public void reconcileWorkOnOpen(WorkManager workManager) {
        io.execute(() -> {
            for (SampleResultEntity result : dao.withAssignedWork()) {
                try {
                    WorkInfo info = workManager.getWorkInfoById(
                            UUID.fromString(result.workRequestId)).get();
                    if (info == null || (info.getState() == WorkInfo.State.SUCCEEDED
                            && !result.workRequestId.equals(result.completedWorkRequestId))) {
                        dao.markWorkEnqueueFailed(result.channelId, result.runId,
                                result.workRequestId, System.currentTimeMillis());
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IllegalArgumentException error) {
                    dao.markWorkEnqueueFailed(result.channelId, result.runId,
                            result.workRequestId, System.currentTimeMillis());
                } catch (ExecutionException error) {
                    // A transient WorkManager read error is not evidence that the job is absent.
                }
            }
        });
    }

    /** A new process never resumes a sample stream or issues an AI request. */
    public void markInterruptedOnOpen() {
        io.execute(() -> dao.markInterrupted(System.currentTimeMillis()));
    }

    private static String workName(String channelId) {
        return "sample-checkpoint-" + channelId;
    }
}
