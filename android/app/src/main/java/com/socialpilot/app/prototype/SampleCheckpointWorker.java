package com.socialpilot.app.prototype;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.prototype.SampleResultDao;
import com.socialpilot.app.data.prototype.SampleResultEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Finite sample processing only: checksum a completed local preview after network is available. */
public final class SampleCheckpointWorker extends Worker {
    public static final String KEY_CHANNEL_ID = "channelId";
    public static final String KEY_RUN_ID = "runId";
    public static final String KEY_CHECKPOINT = "checkpoint";

    public SampleCheckpointWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        String channelId = getInputData().getString(KEY_CHANNEL_ID);
        String runId = getInputData().getString(KEY_RUN_ID);
        if (!"A".equals(channelId) && !"B".equals(channelId)) return Result.failure();
        if (runId == null || runId.isEmpty()) return Result.failure();
        SampleResultDao dao = ((SocialPilotApplication) getApplicationContext())
                .container().prototypeDatabase.results();
        SampleResultEntity result = dao.get(channelId);
        String workRequestId = getId().toString();
        if (result == null || !"COMPLETE".equals(result.state)
                || !runId.equals(result.runId)
                || !workRequestId.equals(result.workRequestId)) return Result.failure();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(result.text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("SHA256:");
            for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
            String checkpoint = hex.toString();
            if (isStopped() || dao.completeCheckpoint(channelId, runId, workRequestId,
                    checkpoint, System.currentTimeMillis()) != 1) {
                return Result.failure();
            }
            return Result.success(new Data.Builder().putString(KEY_CHECKPOINT, checkpoint).build());
        } catch (NoSuchAlgorithmException error) {
            return Result.failure();
        }
    }
}
