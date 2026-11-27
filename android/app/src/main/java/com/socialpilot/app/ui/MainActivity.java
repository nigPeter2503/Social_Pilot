package com.socialpilot.app.ui;

import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;
import android.widget.CheckBox;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.socialpilot.app.R;
import com.socialpilot.app.core.FacebookOAuthCoordinator;
import com.socialpilot.app.databinding.ActivityMainBinding;
import com.socialpilot.app.data.repository.InstagramPublishRepository;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.editor.Brief;
import com.socialpilot.app.domain.editor.BriefForm;
import com.socialpilot.app.domain.editor.BriefFormValidator;
import com.socialpilot.app.domain.editor.SharedUrlParser;
import com.socialpilot.app.data.repository.PublicArticleRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.net.URI;
import java.net.URISyntaxException;
import com.socialpilot.app.data.repository.ChannelRepository.PageCheck;
import com.socialpilot.app.data.repository.ChannelRepository;
import com.socialpilot.app.data.security.EncryptedOAuthStore.InstagramMetadata;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Scope;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import java.util.EnumMap;
import java.util.Map;

public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private MainViewModel model;
    private final Map<Platform, ChannelRepository.Snapshot> selectedTargets =
            new EnumMap<>(Platform.class);
    private final Map<Platform, Scope> pendingTargets = new EnumMap<>(Platform.class);
    private List<ChannelRepository.Snapshot> availableTargets = Collections.emptyList();
    private boolean targetsLoaded;
    private boolean savingBrief;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        model = new ViewModelProvider(this).get(MainViewModel.class);
        if (savedInstanceState != null) {
            for (Platform platform : Platform.values()) {
                String id = savedInstanceState.getString("target." + platform.name());
                long revision = savedInstanceState.getLong("targetRevision." + platform.name(), 0);
                if (id != null && revision > 0) {
                    pendingTargets.put(platform, new Scope(platform.name(), id, revision));
                }
            }
        }
        binding.connectFacebookButton.setOnClickListener(view -> model.connectFacebook());
        binding.checkFacebookButton.setOnClickListener(view -> model.checkFacebookPage());
        model.facebookPageCheck().observe(this, result -> {
            renderFacebookConnection();
            renderInstagramControls();
        });
        binding.openAiKeysButton.setOnClickListener(view -> startActivity(new Intent(this, AiKeysActivity.class)));
        binding.connectInstagramButton.setOnClickListener(view -> model.connectInstagram());
        model.restoreFailures().observe(this, failures -> {
            renderFacebookConnection();
            renderInstagramControls();
            model.refreshChannelOptions();
        });
        model.channelOptions().observe(this, channels -> {
            targetsLoaded = channels != null;
            availableTargets = channels == null ? Collections.emptyList() : channels;
            renderTargetOptions();
        });
        model.oauthState().observe(this, state -> {
            if (state.status == MainViewModel.OAuthStatus.AUTHORIZATION_READY) {
                String url = state.value;
                model.consumeAuthorizationUrl();
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (RuntimeException exception) { showOAuthError("PROVIDER_ERROR"); }
            } else if (state.status == MainViewModel.OAuthStatus.CONNECTED) {
                if (!"INSTAGRAM".equals(state.provider)) {
                    binding.facebookPageStatus.setText(getString(R.string.facebook_connected, state.value));
                    Toast.makeText(this, R.string.facebook_connected_short, Toast.LENGTH_SHORT).show();
                }
                model.acknowledgeOAuth();
            } else if (state.status == MainViewModel.OAuthStatus.FAILED) {
                showOAuthError(state.value == null ? "PROVIDER_ERROR" : state.value);
                model.acknowledgeOAuth();
            }
            renderFacebookConnection();
            renderInstagramControls();
        });
        setupInstagram();
        model.drafts().observe(this, drafts -> {
            StringBuilder text = new StringBuilder();
            for (com.socialpilot.app.data.local.DraftEntity draft : drafts) {
                text.append("• ").append(draft.topic == null
                        ? getString(R.string.legacy_empty_topic) : draft.topic).append("\n\n");
            }
            binding.legacyDraftList.setText(text.length() == 0 ? getString(R.string.empty) : text.toString());
        });
        model.briefs().observe(this, briefs -> {
            StringBuilder text = new StringBuilder();
            for (com.socialpilot.app.data.local.BriefDao.BriefListItem item : briefs) {
                com.socialpilot.app.data.local.BriefEntity brief = item.brief;
                text.append("• ").append(brief.topic).append(" · ")
                        .append(brief.contentLanguage).append("\n")
                        .append(getString(R.string.goal_hint)).append(": ").append(brief.goal)
                        .append("\n").append(getString(R.string.audience_hint)).append(": ")
                        .append(brief.audience).append("\n")
                        .append(getString(R.string.tone_hint)).append(": ").append(brief.tone);
                List<com.socialpilot.app.data.local.BriefSourceEntity> orderedSources =
                        new ArrayList<>(item.sources);
                Collections.sort(orderedSources,
                        (a, b) -> Integer.compare(a.position, b.position));
                for (com.socialpilot.app.data.local.BriefSourceEntity source : orderedSources) {
                    text.append("\n").append(getString(R.string.sources_title)).append(": ")
                            .append(source.value);
                }
                if (!item.targets.isEmpty()) {
                    text.append("\n").append(getString(R.string.targets_title)).append(": ");
                    for (int index = 0; index < item.targets.size(); index++) {
                        if (index > 0) text.append(", ");
                        text.append(item.targets.get(index).platform);
                    }
                }
                text.append("\n\n");
            }
            binding.draftList.setText(text.length() == 0 ? getString(R.string.empty) : text.toString());
        });
        model.saveState().observe(this, state -> {
            savingBrief = state == MainViewModel.SaveState.SAVING;
            setBriefInputsEnabled(!savingBrief);
            renderTargetOptions();
            if (state == MainViewModel.SaveState.SAVED) {
                binding.topicInput.setText("");
                binding.goalInput.setText("");
                binding.audienceInput.setText("");
                binding.toneInput.setText("");
                binding.sourceInput.setText("");
                model.clearImportState();
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show(); model.acknowledge();
            } else if (state == MainViewModel.SaveState.FAILED) {
                Toast.makeText(this, R.string.save_error, Toast.LENGTH_SHORT).show(); model.acknowledge();
            }
        });
        model.importState().observe(this, state -> {
            binding.inspectSourceButton.setEnabled(state.status != MainViewModel.ImportStatus.LOADING);
            if (state.status == MainViewModel.ImportStatus.LOADING) {
                binding.sourceImportStatus.setText(R.string.source_loading);
            } else if (state.status == MainViewModel.ImportStatus.DONE) {
                PublicArticleRepository.Result result = state.result;
                String currentSource = String.valueOf(binding.sourceInput.getText()).trim();
                if (!currentSource.equals(result.requestedUrl)
                        && !currentSource.equals(result.url)) {
                    binding.sourceImportStatus.setText("");
                    return;
                }
                if (result.isSuccess()) {
                    binding.sourceImportStatus.setText(getString(R.string.source_inspected, result.url));
                    if (String.valueOf(binding.sourceInput.getText()).trim()
                            .equals(result.requestedUrl)) {
                        binding.sourceInput.setText(result.url);
                        if (String.valueOf(binding.topicInput.getText()).trim().isEmpty()) {
                            String suggestion = result.title.isEmpty() ? result.description
                                    : result.title + (result.description.isEmpty() ? ""
                                    : "\n" + result.description);
                            binding.topicInput.setText(suggestion);
                        }
                    }
                } else {
                    binding.sourceImportStatus.setText(getString(R.string.source_unavailable,
                            importFailureMessage(result.failure)));
                }
            } else {
                binding.sourceImportStatus.setText("");
            }
        });
        binding.inspectSourceButton.setOnClickListener(view -> {
            String url = String.valueOf(binding.sourceInput.getText()).trim();
            if (!binding.sourceUrlSwitch.isChecked() || url.contains("\n")
                    || !validHttpUrl(url)) {
                binding.sourceImportStatus.setText(R.string.source_manual);
                return;
            }
            if (!isOnline()) {
                binding.sourceImportStatus.setText(getString(R.string.source_unavailable,
                        getString(R.string.source_reason_network)));
                return;
            }
            model.inspectSource(url);
        });
        binding.saveButton.setOnClickListener(view -> saveBrief());
        binding.openPrototype.setOnClickListener(view ->
                startActivity(new Intent(this, PrototypeActivity.class)));
        handleOAuthIntent(getIntent());
        if (savedInstanceState == null) handleShareIntent(getIntent());
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        for (Map.Entry<Platform, Scope> target : pendingTargets.entrySet()) {
            outState.putString("target." + target.getKey().name(), target.getValue().channelId);
            outState.putLong("targetRevision." + target.getKey().name(),
                    target.getValue().connectionRevision);
        }
        for (Map.Entry<Platform, ChannelRepository.Snapshot> target : selectedTargets.entrySet()) {
            outState.putString("target." + target.getKey().name(), target.getValue().scope.channelId);
            outState.putLong("targetRevision." + target.getKey().name(),
                    target.getValue().scope.connectionRevision);
        }
        super.onSaveInstanceState(outState);
    }

    private void setBriefInputsEnabled(boolean enabled) {
        binding.topicInput.setEnabled(enabled);
        binding.goalInput.setEnabled(enabled);
        binding.audienceInput.setEnabled(enabled);
        binding.toneInput.setEnabled(enabled);
        binding.sourceInput.setEnabled(enabled);
        binding.sourceUrlSwitch.setEnabled(enabled);
        binding.contentLanguageVi.setEnabled(enabled);
        binding.contentLanguageEn.setEnabled(enabled);
        binding.saveButton.setEnabled(enabled && (targetsLoaded || pendingTargets.isEmpty()));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            handleShareIntent(intent);
        } else {
            setIntent(intent);
            handleOAuthIntent(intent);
        }
    }

    private void handleOAuthIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if (!"socialpilot".equals(data.getScheme())) return;
        intent.setData(null);
        model.handleFacebookCallback(data);
    }

    private void showOAuthError(String code) {
        String message = FacebookOAuthCoordinator.GRANT_EXPIRED.equals(code)
                ? getString(R.string.oauth_grant_expired) : getString(R.string.oauth_error, code);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void renderFacebookConnection() {
        String page = model.connectedPageSummary();
        PageCheck check = model.currentFacebookPageCheck();
        String reconnectingPage = model.facebookPageRequiringReconnect();
        if (reconnectingPage != null) {
            binding.facebookPageStatus.setText(getString(R.string.facebook_page_reconnect, reconnectingPage));
        } else if (page == null && check == PageCheck.RECONNECT) {
            binding.facebookPageStatus.setText(R.string.facebook_reconnect);
        } else if (check == PageCheck.CHECKING) {
            binding.facebookPageStatus.setText(R.string.facebook_checking);
        } else if (page == null) {
            binding.facebookPageStatus.setText(model.restoreFailures().getValue() == null
                    ? R.string.social_connections_loading
                    : model.restoreFailed(Platform.FACEBOOK)
                            ? R.string.facebook_restore_failed : R.string.facebook_not_connected);
        } else if (check == PageCheck.VERIFIED) {
            binding.facebookPageStatus.setText(getString(R.string.facebook_verified, page));
        } else if (check == PageCheck.FAILED) {
            binding.facebookPageStatus.setText(getString(R.string.facebook_check_failed, page));
        } else if (check == PageCheck.PERMISSION_REQUIRED) {
            binding.facebookPageStatus.setText(getString(R.string.facebook_check_permission, page));
        } else {
            binding.facebookPageStatus.setText(getString(R.string.facebook_connected, page));
        }
    }

    private void handleShareIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String mime = intent.getType();
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (!"text/plain".equals(mime)) {
            Toast.makeText(this, R.string.share_invalid_type, Toast.LENGTH_LONG).show();
            return;
        }
        String url = SharedUrlParser.firstPublicWebUrl(text);
        if (url == null) {
            Toast.makeText(this, R.string.share_invalid_url, Toast.LENGTH_LONG).show();
            return;
        }
        String existing = String.valueOf(binding.sourceInput.getText()).trim();
        if (!existing.isEmpty() && (!binding.sourceUrlSwitch.isChecked()
                || !existing.equals(url))) {
            new AlertDialog.Builder(this).setMessage(R.string.share_replace_source)
                    .setPositiveButton(R.string.share_replace, (dialog, which) -> applySharedUrl(url))
                    .setNegativeButton(android.R.string.cancel, null).show();
        } else {
            applySharedUrl(url);
        }
    }

    private void applySharedUrl(String url) {
        model.clearImportState();
        binding.sourceUrlSwitch.setChecked(true);
        binding.sourceInput.setText(url);
        binding.sourceLayout.setError(null);
        Toast.makeText(this, R.string.share_ready, Toast.LENGTH_SHORT).show();
    }

    private String importFailureMessage(PublicArticleRepository.Failure failure) {
        switch (failure) {
            case INVALID_URL: return getString(R.string.source_reason_invalid);
            case PRIVATE_HOST: return getString(R.string.source_reason_private);
            case LOGIN_REQUIRED: return getString(R.string.source_reason_login);
            case PAYWALL: return getString(R.string.source_reason_paywall);
            case RATE_LIMIT: return getString(R.string.source_reason_rate);
            case NON_HTML: return getString(R.string.source_reason_non_html);
            case TOO_LARGE: return getString(R.string.source_reason_too_large);
            case EMPTY_METADATA: return getString(R.string.source_reason_empty);
            case REDIRECT_LIMIT: return getString(R.string.source_reason_redirect);
            case HTTP_ERROR: return getString(R.string.source_reason_http);
            default: return getString(R.string.source_reason_network);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        binding.networkStatus.setText(isOnline() ? R.string.online_draft_ready
                : R.string.offline_draft_ready);
        if (model != null) model.refreshChannelOptions();
    }

    private boolean isOnline() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = manager == null ? null
                : manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void saveBrief() {
        if (!pendingTargets.isEmpty()) return;
        String sourceValue = String.valueOf(binding.sourceInput.getText()).trim();
        binding.sourceLayout.setError(null);
        List<Brief.Source> sources = new ArrayList<>();
        if (!sourceValue.isEmpty()) {
            if (binding.sourceUrlSwitch.isChecked()) {
                for (String line : sourceValue.split("\\R")) {
                    String url = line.trim();
                    if (url.isEmpty()) continue;
                    if (!validHttpUrl(url)) {
                        binding.sourceLayout.setError(getString(R.string.source_url_invalid));
                        return;
                    }
                    sources.add(new Brief.Source("URL", url));
                }
            } else {
                sources.add(new Brief.Source("TEXT", sourceValue));
            }
        }
        BriefForm form = new BriefForm(String.valueOf(binding.topicInput.getText()),
                String.valueOf(binding.goalInput.getText()),
                String.valueOf(binding.audienceInput.getText()),
                String.valueOf(binding.toneInput.getText()),
                binding.contentLanguageGroup.getCheckedRadioButtonId()
                        == R.id.contentLanguageEn ? "en" : "vi",
                selectedBriefTargets(), sources);
        BriefFormValidator.Errors errors = model.validateDraft(form);
        binding.topicLayout.setError(errors.topic ? getString(R.string.field_required) : null);
        binding.goalLayout.setError(errors.goal ? getString(R.string.field_required) : null);
        binding.audienceLayout.setError(errors.audience ? getString(R.string.field_required) : null);
        binding.toneLayout.setError(errors.tone ? getString(R.string.field_required) : null);
        if (!errors.hasErrors()) model.saveBriefDraft(form,
                new ArrayList<>(selectedTargets.values()));
    }

    private List<Brief.Target> selectedBriefTargets() {
        List<Brief.Target> targets = new ArrayList<>();
        for (ChannelRepository.Snapshot channel : selectedTargets.values()) {
            targets.add(new Brief.Target(channel.info.channelId, channel.info.platform.name()));
        }
        return targets;
    }

    private void renderTargetOptions() {
        if (targetsLoaded && !pendingTargets.isEmpty()) {
            for (ChannelRepository.Snapshot option : availableTargets) {
                Scope pending = pendingTargets.get(option.info.platform);
                if (pending != null && pending.matches(option.scope) && selectable(option)) {
                    selectedTargets.put(option.info.platform, option);
                }
            }
            pendingTargets.clear();
        }
        for (Platform platform : new ArrayList<>(selectedTargets.keySet())) {
            ChannelRepository.Snapshot selected = selectedTargets.get(platform);
            boolean current = false;
            for (ChannelRepository.Snapshot option : availableTargets) {
                if (selected.scope.matches(option.scope) && selectable(option)) {
                    current = true;
                    break;
                }
            }
            if (!current) selectedTargets.remove(platform);
        }
        binding.targetList.removeAllViews();
        binding.targetStatus.setVisibility(availableTargets.isEmpty() ? View.VISIBLE : View.GONE);
        binding.targetStatus.setText(!targetsLoaded && !pendingTargets.isEmpty()
                ? R.string.social_connections_loading : R.string.no_target_available);
        binding.saveButton.setEnabled(!savingBrief && (targetsLoaded || pendingTargets.isEmpty()));
        for (ChannelRepository.Snapshot channel : availableTargets) {
            CheckBox option = new CheckBox(this);
            option.setText(channel.info.displayName + " · " + channel.info.platform.name()
                    + (selectable(channel) ? "" : " · " + getString(R.string.target_reconnect)));
            option.setEnabled(selectable(channel) && !savingBrief);
            ChannelRepository.Snapshot selected = selectedTargets.get(channel.info.platform);
            option.setChecked(selected != null && selected.scope.matches(channel.scope));
            option.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selectedTargets.put(channel.info.platform, channel);
                else if (selectedTargets.get(channel.info.platform) != null
                        && selectedTargets.get(channel.info.platform).scope.matches(channel.scope)) {
                    selectedTargets.remove(channel.info.platform);
                }
                renderTargetOptions();
            });
            binding.targetList.addView(option);
        }
    }

    private static boolean selectable(ChannelRepository.Snapshot channel) {
        return channel.connected() && channel.credentialStatus == SecretRead.Status.SUCCESS;
    }

    private static boolean validHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            return ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (URISyntaxException error) {
            return false;
        }
    }

    private void setupInstagram() {
        TextWatcher invalidateApproval = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) { model.clearInstagramApproval(); }
        };
        binding.instagramImageUrl.addTextChangedListener(invalidateApproval);
        binding.instagramCaption.addTextChangedListener(invalidateApproval);
        binding.instagramApprove.setOnClickListener(view -> {
            try {
                model.approveInstagram(String.valueOf(binding.instagramCaption.getText()),
                        String.valueOf(binding.instagramImageUrl.getText()));
            } catch (IllegalArgumentException | IllegalStateException failure) {
                binding.instagramPublishStatus.setText(R.string.instagram_invalid_preview);
            }
        });
        binding.instagramPublish.setOnClickListener(view -> model.publishInstagram());
        binding.instagramReconcile.setOnClickListener(view -> model.reconcileInstagram());
        binding.instagramOpen.setOnClickListener(view -> {
            String permalink = model.instagramPermalink();
            if (permalink != null) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(permalink))); }
                catch (RuntimeException failure) { binding.instagramPublishStatus.setText(R.string.instagram_open_error); }
            }
        });
        model.instagramApproved().observe(this, approved -> renderInstagramControls());
        model.instagramBusy().observe(this, busy -> renderInstagramControls());
        model.instagramResult().observe(this, result -> renderInstagramControls());
        renderInstagramControls();
    }

    private void renderInstagramControls() {
        MainViewModel.OAuthUiState oauth = model.oauthState().getValue();
        boolean oauthBusy = oauth != null && (oauth.status == MainViewModel.OAuthStatus.STARTING
                || oauth.status == MainViewModel.OAuthStatus.EXCHANGING);
        boolean busy = Boolean.TRUE.equals(model.instagramBusy().getValue());
        InstagramMetadata selected = model.selectedInstagramConnection();
        InstagramMetadata connection = selected != null && selected.connected ? selected : null;
        boolean connected = connection != null;
        boolean approved = model.isInstagramApprovedFor(connection);
        boolean checkingPage = model.currentFacebookPageCheck() == PageCheck.CHECKING;
        binding.connectFacebookButton.setEnabled(!oauthBusy && !busy && !checkingPage);
        binding.checkFacebookButton.setEnabled(!oauthBusy && !busy && !checkingPage
                && model.connectedPageSummary() != null);
        binding.connectInstagramButton.setEnabled(!oauthBusy && !busy && !checkingPage);
        binding.instagramImageUrl.setEnabled(connected && !busy && !oauthBusy);
        binding.instagramCaption.setEnabled(connected && !busy && !oauthBusy);
        binding.instagramApprove.setEnabled(connected && !busy && !oauthBusy);
        binding.instagramPublish.setEnabled(connected && approved && !busy && !oauthBusy);
        binding.instagramReconcile.setEnabled(connected && !busy && !oauthBusy);
        InstagramPublishRepository.Result result = model.instagramResultFor(connection);
        binding.instagramOpen.setEnabled(result != null && result.permalink != null && !busy);
        if (connected) {
            String scopes = android.text.TextUtils.join(", ", connection.grantedScopes);
            String expiry = connection.expiresAt == null ? getString(R.string.instagram_expiry_unknown) : connection.expiresAt;
            String summary = FacebookOAuthCoordinator.instagramSummary(connection.displayName, connection.externalAccountId);
            binding.instagramAccountStatus.setText(getString(R.string.instagram_connected,
                    summary, connection.linkedPageId, scopes, expiry, connection.graphApiVersion));
        } else if (selected != null) {
            String summary = FacebookOAuthCoordinator.instagramSummary(selected.displayName, selected.externalAccountId);
            binding.instagramAccountStatus.setText(getString(R.string.instagram_account_reconnect, summary));
        } else {
            binding.instagramAccountStatus.setText(model.restoreFailures().getValue() == null
                    ? R.string.social_connections_loading
                    : model.restoreFailed(Platform.INSTAGRAM)
                            ? R.string.instagram_restore_failed : R.string.instagram_not_connected);
        }
        if (busy) binding.instagramPublishStatus.setText(R.string.instagram_working);
        else if (selected != null && !connected) binding.instagramPublishStatus.setText(R.string.instagram_reconnect);
        else if (result != null && (result.status == InstagramPublishRepository.Status.PERMISSION_REQUIRED
                || result.status == InstagramPublishRepository.Status.PERMISSION_CHECK_REQUIRED)) {
            binding.instagramPublishStatus.setText(instagramStatusText(result.status));
        } else if (approved) binding.instagramPublishStatus.setText(R.string.instagram_approved);
        else if (result != null && result.status == InstagramPublishRepository.Status.PUBLISHED) {
            binding.instagramPublishStatus.setText(getString(R.string.instagram_published, result.mediaId));
        } else if (result != null) {
            binding.instagramPublishStatus.setText(instagramStatusText(result.status));
        } else binding.instagramPublishStatus.setText(R.string.instagram_publish_hint);
    }

    private int instagramStatusText(InstagramPublishRepository.Status status) {
        switch (status) {
            case READY: return R.string.instagram_connection_verified;
            case CHECK_REQUIRED: return R.string.instagram_check_required;
            case PUBLISHED_ID_UNKNOWN: return R.string.instagram_published_id_unknown;
            case PROVEN_UNPUBLISHED: return R.string.instagram_unpublished;
            case RECONNECT: return R.string.instagram_reconnect;
            case PERMISSION_REQUIRED: return R.string.instagram_permission_required;
            case PERMISSION_CHECK_REQUIRED: return R.string.instagram_permission_check_required;
            case CANCELLED: return R.string.instagram_cancelled;
            default: return R.string.instagram_publish_error;
        }
    }
}
