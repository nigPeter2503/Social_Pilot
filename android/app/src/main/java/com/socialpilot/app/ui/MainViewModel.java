package com.socialpilot.app.ui;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.local.DraftEntity;
import com.socialpilot.app.data.repository.DraftRepository;
import java.util.List;
public final class MainViewModel extends AndroidViewModel {
    public enum SaveState { IDLE, SAVING, SAVED, FAILED }
    private final DraftRepository repository;
    private final MutableLiveData<SaveState> saveState = new MutableLiveData<>(SaveState.IDLE);
    public MainViewModel(@NonNull Application application) {
        super(application); repository = ((SocialPilotApplication) application).container().drafts;
    }
    public LiveData<List<DraftEntity>> drafts() { return repository.observeRecent(); }
    public LiveData<SaveState> saveState() { return saveState; }
    public void acknowledge() { saveState.setValue(SaveState.IDLE); }
    public void save(String topic) {
        if (saveState.getValue() == SaveState.SAVING) return;
        saveState.setValue(SaveState.SAVING);
        repository.save(topic, ok -> new Handler(Looper.getMainLooper()).post(
            () -> saveState.setValue(ok ? SaveState.SAVED : SaveState.FAILED)));
    }
}
