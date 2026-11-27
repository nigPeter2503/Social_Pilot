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
import com.socialpilot.app.data.local.BriefDao.BriefListItem;
import com.socialpilot.app.data.repository.BriefRepository;
import com.socialpilot.app.data.repository.PublicArticleRepository;
import com.socialpilot.app.data.repository.ChannelRepository;
import com.socialpilot.app.data.repository.ChannelRepository.PageCheck;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.repository.InstagramPublishRepository;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedOAuthStore.InstagramSession;
import com.socialpilot.app.data.security.EncryptedOAuthStore.InstagramMetadata;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Scope;
import com.socialpilot.app.domain.editor.Brief;
import com.socialpilot.app.domain.editor.BriefForm;
import com.socialpilot.app.domain.editor.BriefFormValidator;
import com.socialpilot.app.domain.editor.ContentVersion;
import com.socialpilot.app.domain.editor.VersionApprovalState;
import java.net.URI;
import java.util.Collections;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

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
        public final String provider;
        private OAuthUiState(OAuthStatus status, String value) { this(status, value, null); }
        private OAuthUiState(OAuthStatus status, String value, String provider) {
            this.status = status;
            this.value = value;
            this.provider = provider;
        }
    }

    private static final class ScopedPageCheck {
        final Scope scope;
        final PageCheck status;

        ScopedPageCheck(Scope scope, PageCheck status) {
            this.scope = scope;
            this.status = status;
        }
    }

    private final DraftRepository repository;
    private final BriefRepository briefRepository;
    private final FacebookOAuthCoordinator facebookOAuth;
    private final EncryptedOAuthStore connections;
    private final InstagramPublishRepository instagram;
    private final Executor io;
    private final ChannelRepository channels;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<SaveState> saveState = new MutableLiveData<>(SaveState.IDLE);
    private final MutableLiveData<OAuthUiState> oauthState = new MutableLiveData<>(new OAuthUiState(OAuthStatus.IDLE, null));
    private final PublicArticleRepository articleRepository = new PublicArticleRepository();
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger importGeneration = new AtomicInteger();
    private final MutableLiveData<ImportUiState> importState =
            new MutableLiveData<>(new ImportUiState(ImportStatus.IDLE, null));
    private final MutableLiveData<InstagramPublishRepository.Result> instagramResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> instagramBusy = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> instagramApproved = new MutableLiveData<>(false);
    private final MutableLiveData<Set<Platform>> restoreFailures = new MutableLiveData<>();
    private final MutableLiveData<List<ChannelRepository.Snapshot>> channelOptions =
            new MutableLiveData<>();
    private final MutableLiveData<ScopedPageCheck> facebookPageCheck = new MutableLiveData<>();
    private VersionApprovalState approval;
    private ContentVersion approvedVersion;
    private InstagramSession approvedSession;

    public MainViewModel(@NonNull Application application) {
        this(application, ((SocialPilotApplication) application).container());
    }

    private MainViewModel(Application application, AppContainer appContainer) {
        this(application, appContainer.drafts, appContainer.briefs,
                appContainer.facebookOAuth, appContainer.oauthStore,
                appContainer.instagram, appContainer.io, appContainer.channels);
        io.execute(() -> {
            try {
                restoreFailures.postValue(connections.restoreChannels());
            } catch (RuntimeException failure) {
                restoreFailures.postValue(EnumSet.of(Platform.FACEBOOK, Platform.INSTAGRAM));
            }
        });
    }

    MainViewModel(Application application, DraftRepository repository, BriefRepository briefRepository,
            FacebookOAuthCoordinator facebookOAuth,
            EncryptedOAuthStore connections, InstagramPublishRepository instagram, Executor io) {
        this(application, repository, briefRepository, facebookOAuth, connections, instagram, io, null);
    }

    MainViewModel(Application application, DraftRepository repository, FacebookOAuthCoordinator facebookOAuth,
            EncryptedOAuthStore connections, InstagramPublishRepository instagram, Executor io,
            ChannelRepository channels) {
        this(application, repository, null, facebookOAuth, connections, instagram, io, channels);
    }

    MainViewModel(Application application, DraftRepository repository, BriefRepository briefRepository,
            FacebookOAuthCoordinator facebookOAuth, EncryptedOAuthStore connections,
            InstagramPublishRepository instagram, Executor io, ChannelRepository channels) {
        super(application);
        this.repository = repository;
        this.briefRepository = briefRepository;
        this.facebookOAuth = facebookOAuth;
        this.connections = connections;
        this.instagram = instagram;
        this.io = io;
        this.channels = channels;
    }

    public LiveData<List<DraftEntity>> drafts() { return repository.observeRecent(); }
    public LiveData<List<BriefListItem>> briefs() {
        return briefRepository.observeRecentDetails();
    }
    public LiveData<List<ChannelRepository.Snapshot>> channelOptions() { return channelOptions; }

    public void refreshChannelOptions() {
        if (channels == null) return;
        io.execute(() -> {
            try {
                channelOptions.postValue(channels.list());
            } catch (RuntimeException failure) {
                channelOptions.postValue(Collections.emptyList());
            }
        });
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
    LiveData<?> facebookPageCheck() { return facebookPageCheck; }

    PageCheck currentFacebookPageCheck() {
        ScopedPageCheck check = facebookPageCheck.getValue();
        ChannelRepository.Snapshot page = channels == null ? null : channels.selected(Platform.FACEBOOK);
        return check != null && page != null && check.scope.matches(page.scope) ? check.status : PageCheck.IDLE;
    }

    String facebookPageRequiringReconnect() {
        ChannelRepository.Snapshot page = channels == null ? null : channels.selected(Platform.FACEBOOK);
        return page == null || page.connected() ? null
                : page.info.displayName + " (" + page.info.externalAccountId + ")";
    }

    public void checkFacebookPage() {
        if (channels == null || currentFacebookPageCheck() == PageCheck.CHECKING) return;
        ChannelRepository.Snapshot page = channels.selected(Platform.FACEBOOK);
        if (page == null || !page.connected()) return;
        ScopedPageCheck pending = new ScopedPageCheck(page.scope, PageCheck.CHECKING);
        facebookPageCheck.setValue(pending);
        io.execute(() -> {
            PageCheck result;
            try {
                result = channels.checkFacebookPage(pending.scope);
            } catch (RuntimeException failure) {
                result = PageCheck.FAILED;
            }
            ScopedPageCheck completed = new ScopedPageCheck(pending.scope, result);
            main.post(() -> {
                if (facebookPageCheck.getValue() != pending) return;
                facebookPageCheck.setValue(currentFacebookPageCheck() == PageCheck.CHECKING ? completed : null);
            });
        });
    }
    LiveData<Set<Platform>> restoreFailures() { return restoreFailures; }
    boolean restoreFailed(Platform platform) {
        Set<Platform> failed = restoreFailures.getValue();
        return failed != null && failed.contains(platform);
    }

    public InstagramMetadata instagramConnection() { return connections.instagramMetadata(); }
    InstagramMetadata selectedInstagramConnection() { return connections.selectedInstagramMetadata(); }
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

    public BriefFormValidator.Errors validateDraft(BriefForm form) {
        return BriefFormValidator.validate(form, false);
    }

    public void saveBriefDraft(BriefForm form) {
        saveBriefDraft(form, Collections.emptyList());
    }

    public void saveBriefDraft(BriefForm form, List<ChannelRepository.Snapshot> selectedChannels) {
        if (saveState.getValue() == SaveState.SAVING) return;
        if (validateDraft(form).hasErrors()) throw new IllegalArgumentException("Invalid brief draft");
        if (form.targets.size() != selectedChannels.size() && channels != null) {
            throw new IllegalArgumentException("Target selection mismatch");
        }
        saveState.setValue(SaveState.SAVING);
        io.execute(() -> {
            if (channels != null) {
                try {
                    for (ChannelRepository.Snapshot selected : selectedChannels) {
                        ChannelRepository.Snapshot current = channels.find(selected.info.channelId);
                        if (current == null || !selected.scope.matches(current.scope)
                                || !current.connected()
                                || !channels.isCurrent(selected.scope)
                                || current.info.platform != selected.info.platform) {
                            saveState.postValue(SaveState.FAILED);
                            refreshChannelOptions();
                            return;
                        }
                    }
                } catch (RuntimeException failure) {
                    saveState.postValue(SaveState.FAILED);
                    refreshChannelOptions();
                    return;
                }
            }
            List<Scope> scopes = new ArrayList<>();
            for (ChannelRepository.Snapshot selected : selectedChannels) scopes.add(selected.scope);
            briefRepository.saveDraft(form, scopes, (id, saved) -> new Handler(Looper.getMainLooper()).post(
                    () -> saveState.setValue(saved ? SaveState.SAVED : SaveState.FAILED)));
        });
    }

    public void connectFacebook() {
        facebookPageCheck.setValue(null);
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
                // The render path rechecks the same session against the latest published selection.
                boolean current = completed == null ? connections.isCurrentInstagramSession(session)
                        : completed.isCurrent(connections);
                if (current) instagramResult.setValue(completed);
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
                    InstagramMetadata selected = selectedInstagramConnection();
                    if (selected == null || !sessionId.equals(selected.sessionId)
                            || !accountId.equals(selected.externalAccountId)) return;
                    clearInstagramApproval();
                    instagramResult.setValue(null);
                    summary = FacebookOAuthCoordinator.instagramSummary(displayName, accountId);
                } else if (currentFacebookPageCheck() == PageCheck.IDLE) {
                    facebookPageCheck.setValue(null);
                }
                oauthState.setValue(new OAuthUiState(OAuthStatus.CONNECTED, summary, provider));
            }
            @Override public void onFailure(String code) {
                oauthState.setValue(new OAuthUiState(OAuthStatus.FAILED, code));
            }
        };
    }
}
