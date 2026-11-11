package com.socialpilot.app.data.prototype;

import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.socialpilot.app.prototype.SampleCheckpointWorker;
import java.util.List;
import java.util.concurrent.ExecutorService;

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

    public void cancelWork(String channelId, String runId, WorkManager workManager) {
        io.execute(() -> {
            if (dao.invalidateWork(channelId, runId, System.currentTimeMillis()) == 1) {
                workManager.cancelUniqueWork(workName(channelId));
            }
        });
    }

    public void enqueueCheckpoint(String channelId, WorkManager workManager) {
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
                workManager.enqueueUniqueWork(workName(channelId),
                        ExistingWorkPolicy.REPLACE, request);
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
