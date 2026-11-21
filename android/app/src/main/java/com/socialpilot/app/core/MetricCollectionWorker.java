package com.socialpilot.app.core;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.repository.metrics.MetricCollectionCoordinator;
import com.socialpilot.app.data.repository.metrics.MetricJob;
import com.socialpilot.app.domain.contract.social.SocialError;
import java.util.ArrayList;
import java.util.List;

public final class MetricCollectionWorker extends Worker {
    private final MetricCollectionCoordinator coordinator;
    private final MetricCollectionCoordinator.Control control = new MetricCollectionCoordinator.Control();

    public MetricCollectionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        this(context, params, ((SocialPilotApplication) context.getApplicationContext()).container().metrics.coordinator);
    }

    MetricCollectionWorker(Context context, WorkerParameters params, MetricCollectionCoordinator coordinator) {
        super(context, params);
        this.coordinator = coordinator;
    }

    @NonNull @Override public Result doWork() {
        MetricJob job = MetricCollectionWork.decode(getInputData());
        Data.Builder output = new Data.Builder().putString("resultCollection", job.collectionId())
                .putLong("resultStarted", job.collectionStartedAt());
        // Domain terminal states complete the queue item so a later independent manual refresh can still run.
        if (coordinator == null) return Result.success(output.putString("state", "DEPENDENCIES_UNAVAILABLE")
                .putBoolean("gap", true).build());
        try {
            MetricCollectionCoordinator.Result result = coordinator.run(job, getRunAttemptCount(), control);
            boolean retry = result.state() == MetricCollectionCoordinator.State.WAIT
                    || result.state() == MetricCollectionCoordinator.State.BUSY;
            if (retry && getRunAttemptCount() + 1 < MetricCollectionCoordinator.MAX_WORK_STARTS) return Result.retry();
            List<String> errors = new ArrayList<>();
            for (SocialError error : result.errors()) errors.add(error.code.name() + "/" + error.nextAction.name());
            output.putString("state", retry ? "EXHAUSTED" : result.state().name()).putBoolean("gap", result.gap())
                    .putStringArray("errors", errors.toArray(new String[0]));
            if (result.retryAt() != null) output.putLong("retryAt", result.retryAt());
        } catch (IllegalStateException | IllegalArgumentException invalidated) {
            output.putString("state", "WRITE_OR_SCOPE_ERROR").putBoolean("gap", true);
        }
        return Result.success(output.build());
    }

    @Override public void onStopped() { control.cancel(); }
}
