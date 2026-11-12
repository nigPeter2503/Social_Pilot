package com.socialpilot.app.ui;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.core.FacebookOAuthCoordinator;
import com.socialpilot.app.data.local.DraftEntity;
import com.socialpilot.app.data.repository.DraftRepository;
import java.util.List;

public final class MainViewModel extends AndroidViewModel {
    public enum SaveState { IDLE, SAVING, SAVED, FAILED }
    public enum OAuthStatus { IDLE, STARTING, AUTHORIZATION_READY, EXCHANGING, CONNECTED, FAILED }
    public static final class OAuthUiState {
        public final OAuthStatus status;
        public final String value;
        private OAuthUiState(OAuthStatus status, String value) { this.status = status; this.value = value; }
    }

    private final DraftRepository repository;
    private final FacebookOAuthCoordinator facebookOAuth;
    private final MutableLiveData<SaveState> saveState = new MutableLiveData<>(SaveState.IDLE);
    private final MutableLiveData<OAuthUiState> oauthState = new MutableLiveData<>(new OAuthUiState(OAuthStatus.IDLE, null));

    public MainViewModel(@NonNull Application application) {
        super(application);

        com.socialpilot.app.core.AppContainer appContainer = ((SocialPilotApplication) application).container();
        repository = appContainer.drafts;
        facebookOAuth = appContainer.facebookOAuth;
    }

    public LiveData<List<DraftEntity>> drafts() { return repository.observeRecent(); }
    public LiveData<SaveState> saveState() { return saveState; }
    public LiveData<OAuthUiState> oauthState() { return oauthState; }
    public String connectedPageSummary() { return facebookOAuth.connectedPageSummary(); }
    public void acknowledge() { saveState.setValue(SaveState.IDLE); }

    public void save(String topic) {
        if (saveState.getValue() == SaveState.SAVING) return;
        saveState.setValue(SaveState.SAVING);
        repository.save(topic, ok -> new Handler(Looper.getMainLooper()).post(
            () -> saveState.setValue(ok ? SaveState.SAVED : SaveState.FAILED)));
    }

    public void connectFacebook() {
        oauthState.setValue(new OAuthUiState(OAuthStatus.STARTING, null));
        facebookOAuth.start(oauthListener());
    }

    public void handleFacebookCallback(Uri callback) {
        oauthState.setValue(new OAuthUiState(OAuthStatus.EXCHANGING, null));
        facebookOAuth.handleCallback(callback, oauthListener());
    }

    public void consumeAuthorizationUrl() {
        OAuthUiState state = oauthState.getValue();
        if (state != null && state.status == OAuthStatus.AUTHORIZATION_READY) {
            oauthState.setValue(new OAuthUiState(OAuthStatus.IDLE, null));
        }
    }

    public void acknowledgeOAuth() { oauthState.setValue(new OAuthUiState(OAuthStatus.IDLE, null)); }

    private FacebookOAuthCoordinator.Listener oauthListener() {
        return new FacebookOAuthCoordinator.Listener() {
            @Override public void onAuthorizationUrl(String url) {
                oauthState.setValue(new OAuthUiState(OAuthStatus.AUTHORIZATION_READY, url));
            }
            @Override public void onConnected(String pageName, String pageId) {
                oauthState.setValue(new OAuthUiState(OAuthStatus.CONNECTED, pageName + " (" + pageId + ")"));
            }
            @Override public void onFailure(String code) {
                oauthState.setValue(new OAuthUiState(OAuthStatus.FAILED, code));
            }
        };
    }
}
