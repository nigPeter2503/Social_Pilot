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
import com.socialpilot.app.core.OAuthCoordinator;
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
    private final OAuthCoordinator oauth;
    private final com.socialpilot.app.data.repository.social.LinkedInSpikePublisher publisher;
    private final MutableLiveData<Boolean> publishing=new MutableLiveData<>(false);
    private final MutableLiveData<String> publishState=new MutableLiveData<>();
    public LiveData<Boolean> publishing(){return publishing;}
    public LiveData<String> publishState(){return publishState;}
    private volatile com.socialpilot.app.domain.contract.Cancelable activePublish;
    private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
    @Override protected void onCleared(){closed.set(true);var active=activePublish;if(active!=null)active.cancel();super.onCleared();}
    public void publishApprovedLinkedInText(String text){
        if(Boolean.TRUE.equals(publishing.getValue()))return;
        publishing.setValue(true);
        ((SocialPilotApplication)getApplication()).container().io.execute(()->{
            try{activePublish=publisher.publishApprovedText(text,r->new Handler(Looper.getMainLooper()).post(()->{
                if(closed.get())return;
                publishing.setValue(false);publishState.setValue(r.outcome.name()+(r.postId==null?"":"\n"+r.postId));
            }));if(closed.get())activePublish.cancel();}catch(RuntimeException e){if(!closed.get()){publishing.postValue(false);publishState.postValue("NOT_SENT");}}
        });
    }
    public void confirmOpenedLinkedInUrl(String url){
        if(Boolean.TRUE.equals(publishing.getValue()))return;
        ((SocialPilotApplication)getApplication()).container().io.execute(()->{
            try{publisher.confirmOpenedPostUrl(url);publishState.postValue(publisher.lastResult());}
            catch(RuntimeException e){publishState.postValue("URL_NOT_VERIFIED");}
        });
    }
    private final MutableLiveData<SaveState> saveState = new MutableLiveData<>(SaveState.IDLE);
    private final MutableLiveData<OAuthUiState> oauthState = new MutableLiveData<>(new OAuthUiState(OAuthStatus.IDLE, null));

    public MainViewModel(@NonNull Application application) {
        super(application);

        com.socialpilot.app.core.AppContainer appContainer = ((SocialPilotApplication) application).container();
        repository = appContainer.drafts;
        oauth = appContainer.oauth;
        publisher=appContainer.linkedinPublisher;publishState.setValue(publisher.lastResult());
    }

    public LiveData<List<DraftEntity>> drafts() { return repository.observeRecent(); }
    public LiveData<SaveState> saveState() { return saveState; }
    public LiveData<OAuthUiState> oauthState() { return oauthState; }
    public static final class Connections {
        public final String facebook,linkedin;
        public final boolean facebookReauth,linkedinReauth;
        Connections(String f,String l,boolean fr,boolean lr){facebook=f;linkedin=l;facebookReauth=fr;linkedinReauth=lr;}
    }
    private final MutableLiveData<Connections> connections=new MutableLiveData<>();
    public LiveData<Connections> connections(){return connections;}
    public void refreshConnections(){
        ((SocialPilotApplication)getApplication()).container().io.execute(()->{
            try{connections.postValue(new Connections(oauth.summary("FACEBOOK"),oauth.summary("LINKEDIN"),oauth.needsReauth("FACEBOOK"),oauth.needsReauth("LINKEDIN")));}
            catch(RuntimeException e){connections.postValue(new Connections(null,null,false,false));}
        });
    }
    public void acknowledge() { saveState.setValue(SaveState.IDLE); }

    public void save(String topic) {
        if (saveState.getValue() == SaveState.SAVING) return;
        saveState.setValue(SaveState.SAVING);
        repository.save(topic, ok -> new Handler(Looper.getMainLooper()).post(
            () -> saveState.setValue(ok ? SaveState.SAVED : SaveState.FAILED)));
    }

    public void connectFacebook() {
        oauthState.setValue(new OAuthUiState(OAuthStatus.STARTING, null));
        oauth.start("FACEBOOK",oauthListener());
    }

    public void connectLinkedIn() {
        oauthState.setValue(new OAuthUiState(OAuthStatus.STARTING,null));
        oauth.start("LINKEDIN",oauthListener());
    }

    public void handleOAuthCallback(Uri callback) {
        oauthState.setValue(new OAuthUiState(OAuthStatus.EXCHANGING, null));
        oauth.handleCallback(callback, oauthListener());
    }

    public void consumeAuthorizationUrl() {
        OAuthUiState state = oauthState.getValue();
        if (state != null && state.status == OAuthStatus.AUTHORIZATION_READY) {
            oauthState.setValue(new OAuthUiState(OAuthStatus.IDLE, null));
        }
    }

    public void acknowledgeOAuth() { oauthState.setValue(new OAuthUiState(OAuthStatus.IDLE, null)); }

    private OAuthCoordinator.Listener oauthListener() {
        return new OAuthCoordinator.Listener() {
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
