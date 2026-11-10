package com.socialpilot.app.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.socialpilot.app.R;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.prototype.SampleRepository;
import com.socialpilot.app.data.prototype.SampleResultEntity;
import com.socialpilot.app.prototype.DemoStreamCoordinator;
import com.socialpilot.app.prototype.SampleCheckpointWorker;
import java.util.Arrays;
import java.util.List;

/** UI state survives rotation; a new process marks old streams interrupted without restarting them. */
public final class PrototypeViewModel extends AndroidViewModel {
    private final SampleRepository repository;
    private final DemoStreamCoordinator streams;
    private final WorkManager workManager;

    public PrototypeViewModel(@NonNull Application application) {
        super(application);
        repository = ((SocialPilotApplication) application).container().sampleResults;
        repository.markInterruptedOnOpen();
        Handler main = new Handler(Looper.getMainLooper());
        streams = new DemoStreamCoordinator((action, delay) -> main.postDelayed(action, delay));
        workManager = WorkManager.getInstance(application);
    }

    public LiveData<List<SampleResultEntity>> results() { return repository.observeAll(); }
    public LiveData<List<WorkInfo>> workA() {
        return workManager.getWorkInfosForUniqueWorkLiveData(workName("A"));
    }
    public LiveData<List<WorkInfo>> workB() {
        return workManager.getWorkInfosForUniqueWorkLiveData(workName("B"));
    }

    public void startA() { start("A", R.string.sample_a_chunk_1, R.string.sample_a_chunk_2); }
    public void startB() { start("B", R.string.sample_b_chunk_1, R.string.sample_b_chunk_2); }
    public void cancelA() { cancel("A"); }
    public void cancelB() { cancel("B"); }
    public void failA() { streams.fail("A"); }

    private void start(String channel, int firstChunk, int secondChunk) {
        Application app = getApplication();
        if (streams.start(channel, Arrays.asList(app.getString(firstChunk), app.getString(secondChunk)),
                (id, phase, text) -> repository.save(id, text, phase.name()))) {
            workManager.cancelUniqueWork(workName(channel));
        }
    }

    private void cancel(String channel) {
        streams.cancel(channel);
        workManager.cancelUniqueWork(workName(channel));
    }

    public void enqueueSample(String channel) {
        if (!"A".equals(channel) && !"B".equals(channel)) return;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        Data data = new Data.Builder().putString(SampleCheckpointWorker.KEY_CHANNEL_ID, channel).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SampleCheckpointWorker.class)
                .setConstraints(constraints).setInputData(data).build();
        workManager.enqueueUniqueWork(workName(channel), ExistingWorkPolicy.REPLACE, request);
    }

    public static String workName(String channel) { return "sample-checkpoint-" + channel; }

    @Override protected void onCleared() {
        streams.interruptAll();
        super.onCleared();
    }
}
