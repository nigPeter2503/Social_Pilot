package com.socialpilot.app.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.ai.AiKeyRepository;
import com.socialpilot.app.data.ai.AiKeyRepository.KeyStatus;
import com.socialpilot.app.data.ai.ByokKeyVerifier.Outcome;
import com.socialpilot.app.domain.contract.ai.AiRequest.Provider;
import com.socialpilot.app.domain.contract.secret.SecretStoreException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Per-provider BYOK state. Every user action starts a new generation; results of an older
 * generation (for example a check still running after the key was replaced) are dropped.
 */
public final class AiKeysViewModel extends AndroidViewModel {
    public enum Busy { NONE, LOADING, SAVING, CHECKING, DELETING }
    public enum Notice { NONE, FORMAT_ERROR, STORE_ERROR, DELETE_ERROR, DELETED }

    public static final class ProviderState {
        public final Provider provider;
        public final KeyStatus status;
        public final Busy busy;
        /** Outcome of the latest check in this screen session, or null. */
        public final Outcome outcome;
        public final Notice notice;

        ProviderState(Provider provider, KeyStatus status, Busy busy, Outcome outcome, Notice notice) {
            this.provider = provider;
            this.status = status;
            this.busy = busy;
            this.outcome = outcome;
            this.notice = notice;
        }

        ProviderState with(KeyStatus nextStatus, Busy nextBusy, Outcome nextOutcome, Notice nextNotice) {
            return new ProviderState(provider, nextStatus, nextBusy, nextOutcome, nextNotice);
        }
    }

    private final AiKeyRepository keys;
    private final Executor io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Provider, MutableLiveData<ProviderState>> states = new EnumMap<>(Provider.class);
    private final Map<Provider, Integer> generations = new EnumMap<>(Provider.class);
    private final Map<Provider, AiKeyRepository.Verification> running = new EnumMap<>(Provider.class);
    private boolean cleared;

    public AiKeysViewModel(@NonNull Application application) {
        this(application, ((SocialPilotApplication) application).container());
    }

    private AiKeysViewModel(Application application, com.socialpilot.app.core.AppContainer container) {
        this(application, container.aiKeys, container.io);
    }

    AiKeysViewModel(Application application, AiKeyRepository keys, Executor io) {
        super(application);
        this.keys = keys;
        this.io = io;
        for (Provider provider : Provider.values()) {
            states.put(provider, new MutableLiveData<>(new ProviderState(provider, null, Busy.LOADING, null, Notice.NONE)));
            generations.put(provider, 0);
        }
        for (Provider provider : Provider.values()) load(provider);
    }

    public LiveData<ProviderState> state(Provider provider) {
        return states.get(provider);
    }

    public void acknowledgeNotice(Provider provider) {
        ProviderState current = current(provider);
        if (current.notice != Notice.NONE) set(provider, current.with(current.status, current.busy, current.outcome, Notice.NONE));
    }

    /** Validates on the main thread (no I/O), then saves and checks the new key in the background. */
    public boolean save(Provider provider, String rawKey) {
        final String key;
        try {
            key = AiKeyRepository.normalize(rawKey);
        } catch (IllegalArgumentException invalid) {
            ProviderState current = current(provider);
            set(provider, current.with(current.status, current.busy, current.outcome, Notice.FORMAT_ERROR));
            return false;
        }
        int generation = begin(provider, Busy.SAVING);
        keys.enqueueStorage(provider, io, () -> {
            try {
                KeyStatus saved = keys.save(provider, key);
                if (!post(provider, generation, state -> state.with(saved, Busy.CHECKING, null, Notice.NONE))) return;
                io.execute(() -> verify(provider, generation));
            } catch (SecretStoreException failure) {
                KeyStatus status = keys.status(provider);
                post(provider, generation, state -> state.with(status, Busy.NONE, null, Notice.STORE_ERROR));
            }
        });
        return true;
    }

    public void check(Provider provider) {
        int generation = begin(provider, Busy.CHECKING);
        io.execute(() -> verify(provider, generation));
    }

    public void delete(Provider provider) {
        int generation = begin(provider, Busy.DELETING);
        keys.enqueueStorage(provider, io, () -> {
            try {
                KeyStatus status = keys.delete(provider);
                post(provider, generation, state -> state.with(status, Busy.NONE, null, Notice.DELETED));
            } catch (SecretStoreException failure) {
                KeyStatus status = keys.status(provider);
                post(provider, generation, state -> state.with(status, Busy.NONE, null, Notice.DELETE_ERROR));
            }
        });
    }

    @Override
    protected void onCleared() {
        synchronized (running) {
            cleared = true;
            for (AiKeyRepository.Verification verification : running.values()) verification.cancel();
            running.clear();
        }
    }

    private void load(Provider provider) {
        int generation = generations.get(provider);
        keys.enqueueStorage(provider, io, () -> {
            KeyStatus status = keys.status(provider);
            post(provider, generation, state -> state.with(status, Busy.NONE, null, Notice.NONE));
        });
    }

    /** Runs on the I/O worker. */
    private void verify(Provider provider, int generation) {
        AiKeyRepository.Verification verification;
        synchronized (running) {
            if (cleared || generation != generations.get(provider)) return;
            verification = keys.prepareVerification(provider);
            running.put(provider, verification);
        }
        AiKeyRepository.CheckResult result;
        try {
            result = verification.run();
        } finally {
            synchronized (running) {
                if (running.get(provider) == verification) running.remove(provider);
            }
        }
        post(provider, generation, state -> state.with(result.status, Busy.NONE, result.outcome, Notice.NONE));
    }

    /** Main thread: invalidates older work for this provider and cancels its running check. */
    private int begin(Provider provider, Busy busy) {
        int generation;
        synchronized (running) {
            generation = generations.get(provider) + 1;
            generations.put(provider, generation);
            AiKeyRepository.Verification previous = running.remove(provider);
            if (previous != null) previous.cancel();
        }
        ProviderState current = current(provider);
        set(provider, current.with(current.status, busy, null, Notice.NONE));
        return generation;
    }

    private boolean isCurrent(Provider provider, int generation) {
        synchronized (running) {
            return !cleared && generation == generations.get(provider);
        }
    }

    private interface Update {
        ProviderState apply(ProviderState state);
    }

    /** Returns false when the work is already stale; the update itself is re-checked on main. */
    private boolean post(Provider provider, int generation, Update update) {
        if (!isCurrent(provider, generation)) return false;
        main.post(() -> {
            if (isCurrent(provider, generation)) set(provider, update.apply(current(provider)));
        });
        return true;
    }

    private ProviderState current(Provider provider) {
        return states.get(provider).getValue();
    }

    private void set(Provider provider, ProviderState state) {
        states.get(provider).setValue(state);
    }
}
