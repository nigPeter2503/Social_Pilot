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
import com.socialpilot.app.core.AppContainer;
import com.socialpilot.app.core.FacebookOAuthCoordinator;
import com.socialpilot.app.data.local.DraftEntity;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.repository.InstagramPublishRepository;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedOAuthStore.InstagramSession;
import com.socialpilot.app.data.security.EncryptedOAuthStore.InstagramMetadata;
import com.socialpilot.app.domain.editor.Brief;
import com.socialpilot.app.domain.editor.ContentVersion;
import com.socialpilot.app.domain.editor.VersionApprovalState;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

public final class MainViewModel extends AndroidViewModel {
    public enum SaveState { IDLE, SAVING, SAVED, FAILED }
    public enum OAuthStatus { IDLE, STARTING, AUTHORIZATION_READY, EXCHANGING, CONNECTED, FAILED }
    public static final class OAuthUiState {
        public final OAuthStatus status;
        public final String value;
        public final String provider;
        private OAuthUiState(OAuthStatus status, String value) { this(status, value, null); }
        private OAuthUiState(OAuthStatus status, String value, String provider) {
            this.status = status;
            this.value = value;
            this.provider = provider;
        }
    }

    private final DraftRepository repository;
    private final FacebookOAuthCoordinator facebookOAuth;
    private final EncryptedOAuthStore connections;
    private final InstagramPublishRepository instagram;
    private final Executor io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<SaveState> saveState = new MutableLiveData<>(SaveState.IDLE);
    private final MutableLiveData<OAuthUiState> oauthState = new MutableLiveData<>(new OAuthUiState(OAuthStatus.IDLE, null));
    private final MutableLiveData<InstagramPublishRepository.Result> instagramResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> instagramBusy = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> instagramApproved = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> connectionsLoaded = new MutableLiveData<>(false);
    private VersionApprovalState approval;
    private ContentVersion approvedVersion;
    private InstagramSession approvedSession;

    public MainViewModel(@NonNull Application application) {
        this(application, ((SocialPilotApplication) application).container());
    }

    private MainViewModel(Application application, AppContainer appContainer) {
        this(application, appContainer.drafts, appContainer.facebookOAuth, appContainer.oauthStore,
                appContainer.instagram, appContainer.io);
        io.execute(() -> {
            try {
                connections.restoreChannels();
            } catch (RuntimeException failure) {
                main.post(() -> oauthState.setValue(new OAuthUiState(OAuthStatus.FAILED, "PROVIDER_ERROR")));
            }
            connectionsLoaded.postValue(true);
        });
    }

    MainViewModel(Application application, DraftRepository repository, FacebookOAuthCoordinator facebookOAuth,
            EncryptedOAuthStore connections, InstagramPublishRepository instagram, Executor io) {
        super(application);
        this.repository = repository;
        this.facebookOAuth = facebookOAuth;
        this.connections = connections;
        this.instagram = instagram;
        this.io = io;
    }

    public LiveData<List<DraftEntity>> drafts() { return repository.observeRecent(); }
    public LiveData<SaveState> saveState() { return saveState; }
    public LiveData<OAuthUiState> oauthState() { return oauthState; }
    public String connectedPageSummary() { return facebookOAuth.connectedPageSummary(); }
    public LiveData<Boolean> connectionsLoaded() { return connectionsLoaded; }
    public InstagramMetadata instagramConnection() { return connections.instagramMetadata(); }
    public LiveData<InstagramPublishRepository.Result> instagramResult() { return instagramResult; }
    public InstagramPublishRepository.Result instagramResultFor(InstagramMetadata connection) {
        InstagramPublishRepository.Result result = instagramResult.getValue();
        return result != null && result.matchesConnection(connection) && result.isCurrent(connections) ? result : null;
    }
    public String instagramPermalink() {
        InstagramPublishRepository.Result result = instagramResultFor(instagramConnection());
        return result == null ? null : result.permalink;
    }
    public LiveData<Boolean> instagramBusy() { return instagramBusy; }
    public LiveData<Boolean> instagramApproved() { return instagramApproved; }
    public boolean isInstagramApprovedFor(InstagramMetadata connection) {
        return approvedSession != null && approvedSession.matches(connection);
    }
    public void acknowledge() { saveState.setValue(SaveState.IDLE); }

    public void save(String topic) {
        if (saveState.getValue() == SaveState.SAVING) return;
        saveState.setValue(SaveState.SAVING);
        repository.save(topic, ok -> new Handler(Looper.getMainLooper()).post(
            () -> saveState.setValue(ok ? SaveState.SAVED : SaveState.FAILED)));
    }

    public void connectFacebook() {
        connect("FACEBOOK");
    }

    public void connectInstagram() {
        connect("INSTAGRAM");
    }

    private void connect(String provider) {
        oauthState.setValue(new OAuthUiState(OAuthStatus.STARTING, null));
        facebookOAuth.start(provider, oauthListener());
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

    public void approveInstagram(String caption, String image) {
        InstagramSession session = connections.instagramSession();
        if (session.sessionId == null) throw new IllegalStateException("Instagram connection required");
        String text = caption.trim();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 2200) {
            throw new IllegalArgumentException("Invalid caption");
        }
        String imageUrl = image.trim();
        URI uri = URI.create(imageUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Invalid image URL");
        }
        String briefId = UUID.randomUUID().toString();
        String accountId = session.accountId;
        Brief brief = new Brief(briefId, text, "Feed image verification", "Demo", "Neutral",
                "vi", Collections.singletonList(new Brief.Target(accountId, "INSTAGRAM")),
                Collections.emptyList());
        VersionApprovalState nextApproval = new VersionApprovalState(brief);
        ContentVersion version = new ContentVersion(UUID.randomUUID().toString(), briefId, accountId,
                null, text, imageUrl, System.currentTimeMillis());
        nextApproval.saveVersion(version, null);
        nextApproval.approve(accountId, version.versionId());
        approval = nextApproval;
        approvedVersion = version;
        approvedSession = session;
        instagramApproved.setValue(true);
    }

    public void clearInstagramApproval() {
        approval = null;
        approvedVersion = null;
        approvedSession = null;
        instagramApproved.setValue(false);
    }

    public void publishInstagram() {
        if (approvedVersion == null || Boolean.TRUE.equals(instagramBusy.getValue())) return;
        ContentVersion version = approval.requireEnqueueable(approvedVersion.channelId(), approvedVersion.versionId());
        InstagramSession session = approvedSession;
        clearInstagramApproval();
        runInstagram(session, () -> instagram.publish(version, session));
    }

    public void reconcileInstagram() {
        if (Boolean.TRUE.equals(instagramBusy.getValue())) return;
        InstagramSession session = connections.instagramSession();
        runInstagram(session, () -> instagram.reconcile(session));
    }

    private void runInstagram(InstagramSession session, Callable<InstagramPublishRepository.Result> operation) {
        instagramBusy.setValue(true);
        io.execute(() -> {
            InstagramPublishRepository.Result result;
            try {
                result = operation.call();
            } catch (Exception failure) {
                result = null;
            }
            InstagramPublishRepository.Result completed = result;
            main.post(() -> {
                // Order result delivery against OAuth credential replacement, even on different threads.
                synchronized (connections) {
                    boolean current = completed == null ? connections.isCurrentInstagramSession(session)
                            : completed.isCurrent(connections);
                    if (current) instagramResult.setValue(completed);
                }
                instagramBusy.setValue(false);
            });
        });
    }

    private FacebookOAuthCoordinator.Listener oauthListener() {
        return new FacebookOAuthCoordinator.Listener() {
            @Override public void onAuthorizationUrl(String url) {
                oauthState.setValue(new OAuthUiState(OAuthStatus.AUTHORIZATION_READY, url));
            }
            @Override public void onConnected(String provider, String displayName, String accountId, String sessionId) {
                String summary = displayName + " (" + accountId + ")";
                if ("INSTAGRAM".equals(provider)) {
                    if (!connections.instagramSession().matches(sessionId, accountId)) return;
                    clearInstagramApproval();
                    instagramResult.setValue(null);
                    summary = FacebookOAuthCoordinator.instagramSummary(displayName, accountId);
                }
                oauthState.setValue(new OAuthUiState(OAuthStatus.CONNECTED, summary, provider));
            }
            @Override public void onFailure(String code) {
                oauthState.setValue(new OAuthUiState(OAuthStatus.FAILED, code));
            }
        };
    }
}
