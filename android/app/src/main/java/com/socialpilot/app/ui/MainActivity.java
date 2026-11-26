package com.socialpilot.app.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.socialpilot.app.R;
import com.socialpilot.app.core.FacebookOAuthCoordinator;
import com.socialpilot.app.databinding.ActivityMainBinding;
import com.socialpilot.app.data.repository.InstagramPublishRepository;
import com.socialpilot.app.domain.DraftValidator;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.ui.channels.MyChannelsActivity;

public final class MainActivity extends AppCompatActivity {
    private static final String OPEN_CONNECTION_PLATFORM = "open_connection_platform";
    private ActivityMainBinding binding;
    private MainViewModel model;

    public static void openConnection(Context context, Platform platform) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(OPEN_CONNECTION_PLATFORM, platform.name());
        context.startActivity(intent);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        model = new ViewModelProvider(this).get(MainViewModel.class);
        binding.myChannelsButton.setOnClickListener(view ->
                startActivity(new Intent(this, MyChannelsActivity.class)));
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
        showConnection(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleOAuthIntent(intent);
        showConnection(intent);
    }

    private void showConnection(Intent intent) {
        String platform = intent.getStringExtra(OPEN_CONNECTION_PLATFORM);
        intent.removeExtra(OPEN_CONNECTION_PLATFORM);
        View button;
        if (Platform.FACEBOOK.name().equals(platform)) {
            button = binding.connectFacebookButton;
        } else if (Platform.INSTAGRAM.name().equals(platform)) {
            button = binding.connectInstagramButton;
        } else {
            return;
        }
        button.post(() -> {
            Rect bounds = new Rect();
            button.getDrawingRect(bounds);
            button.requestRectangleOnScreen(bounds);
        });
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
