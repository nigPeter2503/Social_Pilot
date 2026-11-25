package com.socialpilot.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.net.URI;
import java.net.URISyntaxException;

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
        String page = model.connectedPageSummary();
        if (page != null) binding.facebookPageStatus.setText(getString(R.string.facebook_connected, page));
        binding.connectFacebookButton.setOnClickListener(view -> model.connectFacebook());
        binding.openAiKeysButton.setOnClickListener(view -> startActivity(new Intent(this, AiKeysActivity.class)));
        binding.connectInstagramButton.setOnClickListener(view -> model.connectInstagram());
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
            binding.saveButton.setEnabled(state != MainViewModel.SaveState.SAVING);
            if (state == MainViewModel.SaveState.SAVED) {
                binding.topicInput.setText("");
                binding.goalInput.setText("");
                binding.audienceInput.setText("");
                binding.toneInput.setText("");
                binding.sourceInput.setText("");
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show(); model.acknowledge();
            } else if (state == MainViewModel.SaveState.FAILED) {
                Toast.makeText(this, R.string.save_error, Toast.LENGTH_SHORT).show(); model.acknowledge();
            }
        });
        binding.saveButton.setOnClickListener(view -> saveBrief());
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
        Toast.makeText(this, getString(R.string.oauth_error, code), Toast.LENGTH_LONG).show();
    }

    @Override protected void onResume() {
        super.onResume();
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = manager == null ? null
                : manager.getNetworkCapabilities(manager.getActiveNetwork());
        boolean online = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        binding.networkStatus.setText(online ? R.string.online_draft_ready
                : R.string.offline_draft_ready);
    }

    private void saveBrief() {
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
                Collections.emptyList(), sources);
        BriefFormValidator.Errors errors = model.validateDraft(form);
        binding.topicLayout.setError(errors.topic ? getString(R.string.field_required) : null);
        binding.goalLayout.setError(errors.goal ? getString(R.string.field_required) : null);
        binding.audienceLayout.setError(errors.audience ? getString(R.string.field_required) : null);
        binding.toneLayout.setError(errors.tone ? getString(R.string.field_required) : null);
        if (!errors.hasErrors()) model.saveBriefDraft(form);
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
        OAuthExchangeResponse connection = model.instagramConnection();
        boolean connected = connection != null;
        boolean approved = model.isInstagramApprovedFor(connection);
        binding.connectFacebookButton.setEnabled(!oauthBusy && !busy);
        binding.connectInstagramButton.setEnabled(!oauthBusy && !busy);
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
        } else {
            binding.instagramAccountStatus.setText(R.string.instagram_not_connected);
        }
        if (busy) binding.instagramPublishStatus.setText(R.string.instagram_working);
        else if (approved) binding.instagramPublishStatus.setText(R.string.instagram_approved);
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
            case CANCELLED: return R.string.instagram_cancelled;
            default: return R.string.instagram_publish_error;
        }
    }
}
