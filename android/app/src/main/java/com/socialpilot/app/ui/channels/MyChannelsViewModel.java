package com.socialpilot.app.ui.channels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.repository.ChannelDirectory.Channel;
import com.socialpilot.app.data.repository.ChannelDirectoryRepository;
import java.util.Collections;
import java.util.List;

public final class MyChannelsViewModel extends AndroidViewModel {
    enum Status { LOADING, READY, OFFLINE, ERROR }

    static final class State {
        final Status status;
        final List<Channel> channels;

        State(Status status, List<Channel> channels) {
            this.status = status;
            this.channels = channels;
        }
    }

    private final ChannelDirectoryRepository repository;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private boolean loading;

    public MyChannelsViewModel(@NonNull Application application) {
        super(application);
        repository = ((SocialPilotApplication) application).container().channels;
        refresh();
    }

    LiveData<State> state() {
        return state;
    }

    void refresh() {
        if (loading) return;
        loading = true;
        State previous = state.getValue();
        List<Channel> cached = previous == null ? Collections.emptyList() : previous.channels;
        state.setValue(new State(Status.LOADING, cached));
        repository.load(new ChannelDirectoryRepository.Callback() {
            @Override public void onLoaded(List<Channel> channels, boolean online) {
                loading = false;
                state.setValue(new State(online ? Status.READY : Status.OFFLINE, channels));
            }

            @Override public void onFailure() {
                loading = false;
                state.setValue(new State(Status.ERROR, cached));
            }
        });
    }
}
