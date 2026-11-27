package com.socialpilot.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.socialpilot.app.R;
import com.socialpilot.app.core.FacebookOAuthCoordinator;
import com.socialpilot.app.databinding.ActivityMainBinding;
import com.socialpilot.app.data.repository.InstagramPublishRepository;
import com.socialpilot.app.data.repository.ChannelRepository.PageCheck;
import com.socialpilot.app.data.repository.ChannelRepository.Snapshot;
import com.socialpilot.app.domain.DraftValidator;
import com.socialpilot.app.data.security.EncryptedOAuthStore.InstagramMetadata;
import com.socialpilot.app.domain.contract.social.Platform;

public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private MainViewModel model;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        model = new ViewModelProvider(this).get(MainViewModel.class);
        binding.connectFacebookButton.setOnClickListener(view -> model.connectFacebook());
        binding.checkFacebookButton.setOnClickListener(view -> model.checkFacebookPage());
        model.facebookPageCheck().observe(this, result -> {
            renderFacebookConnection();
            renderInstagramControls();
        });
        binding.openAiKeysButton.setOnClickListener(view -> startActivity(new Intent(this, AiKeysActivity.class)));
        binding.connectInstagramButton.setOnClickListener(view -> model.connectInstagram());
        binding.disconnectFacebookButton.setOnClickListener(view -> confirmDisconnect(Platform.FACEBOOK));
        binding.disconnectInstagramButton.setOnClickListener(view -> confirmDisconnect(Platform.INSTAGRAM));
        model.disconnectState().observe(this, state -> {
            renderFacebookConnection();
            renderInstagramControls();
            int message;
            switch (state) {
                case DONE: message = R.string.channel_disconnected; break;
                case CLEANUP_PENDING: message = R.string.channel_cleanup_pending; break;
                case FAILED: message = R.string.channel_disconnect_failed; break;
                default: return;
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            model.acknowledgeDisconnect();
        });
        model.restoreFailures().observe(this, failures -> {
            renderFacebookConnection();
            renderInstagramControls();
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
            for (com.socialpilot.app.data.local.DraftEntity draft : drafts) text.append("• ").append(draft.topic).append("\n\n");
            binding.draftList.setText(text.length() == 0 ? getString(R.string.empty) : text.toString());
        });
        model.saveState().observe(this, state -> {
            binding.saveButton.setEnabled(state != MainViewModel.SaveState.SAVING);
            if (state == MainViewModel.SaveState.SAVED) {
                binding.topicInput.setText(""); Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show(); model.acknowledge();
            } else if (state == MainViewModel.SaveState.FAILED) {
                Toast.makeText(this, R.string.save_error, Toast.LENGTH_SHORT).show(); model.acknowledge();
            }
        });
        binding.saveButton.setOnClickListener(view -> {
            try { model.save(DraftValidator.normalize(String.valueOf(binding.topicInput.getText()))); }
            catch (IllegalArgumentException error) { binding.topicInput.setError(getString(R.string.required)); }
        });
        binding.openPrototype.setOnClickListener(view ->
                startActivity(new Intent(this, PrototypeActivity.class)));
        handleOAuthIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleOAuthIntent(intent);
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

    private void confirmDisconnect(Platform platform) {
        Snapshot channel = model.selectedChannel(platform);
        if (channel == null) return;
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.channel_disconnect_title, channel.info.displayName))
                .setMessage(platform == Platform.INSTAGRAM
                        ? R.string.instagram_disconnect_summary : R.string.channel_disconnect_summary)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.channel_disconnect, (window, action) -> model.disconnect(channel.scope, false));
        if (platform == Platform.INSTAGRAM) {
            dialog.setNeutralButton(R.string.channel_disconnect_erase,
                    (window, action) -> model.disconnect(channel.scope, true));
        }
        dialog.show();
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
        binding.instagramConfirmPrior.setOnClickListener(view -> new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.instagram_confirm_prior_prompt)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.instagram_confirm_prior, (dialog, action) -> model.confirmPriorInstagramPostChecked())
                .show());
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
        boolean disconnecting = model.disconnectState().getValue() == MainViewModel.DisconnectState.WORKING;
        binding.disconnectFacebookButton.setVisibility(model.selectedChannel(Platform.FACEBOOK) == null
                ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.disconnectInstagramButton.setVisibility(model.selectedChannel(Platform.INSTAGRAM) == null
                ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.disconnectFacebookButton.setEnabled(!disconnecting && !oauthBusy);
        binding.disconnectInstagramButton.setEnabled(!disconnecting && !oauthBusy);
        binding.connectFacebookButton.setEnabled(!disconnecting && !oauthBusy && !busy && !checkingPage);
        binding.checkFacebookButton.setEnabled(!oauthBusy && !busy && !checkingPage
                && model.connectedPageSummary() != null);
        binding.connectInstagramButton.setEnabled(!disconnecting && !oauthBusy && !busy && !checkingPage);
        binding.instagramImageUrl.setEnabled(connected && !busy && !oauthBusy);
        binding.instagramCaption.setEnabled(connected && !busy && !oauthBusy);
        binding.instagramApprove.setEnabled(connected && !busy && !oauthBusy);
        binding.instagramPublish.setEnabled(connected && approved && !busy && !oauthBusy && !disconnecting);
        binding.instagramReconcile.setEnabled(connected && !busy && !oauthBusy && !disconnecting);
        InstagramPublishRepository.Result result = model.instagramResultFor(connection);
        boolean needsPriorCheck = result != null && result.status == InstagramPublishRepository.Status.CONFIRM_PRIOR_POST;
        binding.instagramConfirmPrior.setVisibility(needsPriorCheck ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.instagramConfirmPrior.setEnabled(needsPriorCheck && !busy && !disconnecting);
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
            case CONFIRM_PRIOR_POST: return R.string.instagram_confirm_prior_prompt;
            case PRIOR_VERSION_BLOCKED: return R.string.instagram_prior_version_blocked;
            default: return R.string.instagram_publish_error;
        }
    }
}
