package com.socialpilot.app.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.socialpilot.app.R;
import com.socialpilot.app.databinding.ActivityMainBinding;
import com.socialpilot.app.domain.DraftValidator;
import com.socialpilot.app.ui.channels.MyChannelsActivity;

public final class MainActivity extends AppCompatActivity {
    private static final String OPEN_FACEBOOK_CONNECTION = "open_facebook_connection";
    private ActivityMainBinding binding;
    private MainViewModel model;

    public static void openFacebookConnection(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(OPEN_FACEBOOK_CONNECTION, true);
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
        model.oauthState().observe(this, state -> {
            binding.connectFacebookButton.setEnabled(state.status != MainViewModel.OAuthStatus.STARTING
                    && state.status != MainViewModel.OAuthStatus.EXCHANGING);
            if (state.status == MainViewModel.OAuthStatus.AUTHORIZATION_READY) {
                String url = state.value;
                model.consumeAuthorizationUrl();
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (RuntimeException exception) { showOAuthError("PROVIDER_ERROR"); }
            } else if (state.status == MainViewModel.OAuthStatus.CONNECTED) {
                binding.facebookPageStatus.setText(getString(R.string.facebook_connected, state.value));
                Toast.makeText(this, R.string.facebook_connected_short, Toast.LENGTH_SHORT).show();
                model.acknowledgeOAuth();
            } else if (state.status == MainViewModel.OAuthStatus.FAILED) {
                showOAuthError(state.value == null ? "PROVIDER_ERROR" : state.value);
                model.acknowledgeOAuth();
            }
        });
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
        handleOAuthIntent(getIntent());
        showFacebookConnection(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleOAuthIntent(intent);
        showFacebookConnection(intent);
    }

    private void showFacebookConnection(Intent intent) {
        if (!intent.getBooleanExtra(OPEN_FACEBOOK_CONNECTION, false)) return;
        intent.removeExtra(OPEN_FACEBOOK_CONNECTION);
        binding.connectFacebookButton.post(() -> {
            Rect bounds = new Rect();
            binding.connectFacebookButton.getDrawingRect(bounds);
            binding.connectFacebookButton.requestRectangleOnScreen(bounds);
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
}
