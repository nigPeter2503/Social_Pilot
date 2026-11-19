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
import com.socialpilot.app.data.local.BriefDao.BriefListItem;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.repository.BriefRepository;
import com.socialpilot.app.data.repository.PublicArticleRepository;
import com.socialpilot.app.domain.editor.BriefForm;
import com.socialpilot.app.domain.editor.BriefFormValidator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainViewModel extends AndroidViewModel {
    public enum SaveState { IDLE, SAVING, SAVED, FAILED }
    public enum OAuthStatus { IDLE, STARTING, AUTHORIZATION_READY, EXCHANGING, CONNECTED, FAILED }
    public enum ImportStatus { IDLE, LOADING, DONE }
    public static final class ImportUiState {
        public final ImportStatus status;
        public final PublicArticleRepository.Result result;
        private ImportUiState(ImportStatus status, PublicArticleRepository.Result result) {
            this.status = status;
            this.result = result;
        }
    }
    public static final class OAuthUiState {
        public final OAuthStatus status;
        public final String value;
        private OAuthUiState(OAuthStatus status, String value) { this.status = status; this.value = value; }
    }

    private final DraftRepository repository;
    private final BriefRepository briefRepository;
    private final FacebookOAuthCoordinator facebookOAuth;
    private final MutableLiveData<SaveState> saveState = new MutableLiveData<>(SaveState.IDLE);
    private final MutableLiveData<OAuthUiState> oauthState = new MutableLiveData<>(new OAuthUiState(OAuthStatus.IDLE, null));
    private final PublicArticleRepository articleRepository = new PublicArticleRepository();
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger importGeneration = new AtomicInteger();
    private final MutableLiveData<ImportUiState> importState =
            new MutableLiveData<>(new ImportUiState(ImportStatus.IDLE, null));

    public MainViewModel(@NonNull Application application) {
        super(application);

        com.socialpilot.app.core.AppContainer appContainer = ((SocialPilotApplication) application).container();
        repository = appContainer.drafts;
        briefRepository = appContainer.briefs;
        facebookOAuth = appContainer.facebookOAuth;
    }

    public LiveData<List<DraftEntity>> drafts() { return repository.observeRecent(); }
    public LiveData<List<BriefListItem>> briefs() {
        return briefRepository.observeRecentDetails();
    }
    public LiveData<SaveState> saveState() { return saveState; }
    public LiveData<OAuthUiState> oauthState() { return oauthState; }
    public LiveData<ImportUiState> importState() { return importState; }

    public void clearImportState() {
        importGeneration.incrementAndGet();
        importState.setValue(new ImportUiState(ImportStatus.IDLE, null));
    }

    public void inspectSource(String url) {
        int generation = importGeneration.incrementAndGet();
        importState.setValue(new ImportUiState(ImportStatus.LOADING, null));
        importExecutor.execute(() -> {
            PublicArticleRepository.Result result = articleRepository.inspect(url);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (importGeneration.get() == generation) {
                    importState.setValue(new ImportUiState(ImportStatus.DONE, result));
                }
            });
        });
    }

    @Override protected void onCleared() {
        importGeneration.incrementAndGet();
        importExecutor.shutdownNow();
        super.onCleared();
    }
    public String connectedPageSummary() { return facebookOAuth.connectedPageSummary(); }
    public void acknowledge() { saveState.setValue(SaveState.IDLE); }

    public void save(String topic) {
        if (saveState.getValue() == SaveState.SAVING) return;
        saveState.setValue(SaveState.SAVING);
        repository.save(topic, ok -> new Handler(Looper.getMainLooper()).post(
            () -> saveState.setValue(ok ? SaveState.SAVED : SaveState.FAILED)));
    }

    public BriefFormValidator.Errors validateDraft(BriefForm form) {
        return BriefFormValidator.validate(form, false);
    }

    public void saveBriefDraft(BriefForm form) {
        if (saveState.getValue() == SaveState.SAVING) return;
        if (validateDraft(form).hasErrors()) throw new IllegalArgumentException("Invalid brief draft");
        saveState.setValue(SaveState.SAVING);
        briefRepository.saveDraft(form, (id, saved) -> new Handler(Looper.getMainLooper()).post(
                () -> saveState.setValue(saved ? SaveState.SAVED : SaveState.FAILED)));
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
