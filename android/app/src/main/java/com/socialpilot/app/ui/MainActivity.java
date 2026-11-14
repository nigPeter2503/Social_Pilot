package com.socialpilot.app.ui;

import android.content.Intent;
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

public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private MainViewModel model;
    private boolean oauthBusy,publishBusy;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        model = new ViewModelProvider(this).get(MainViewModel.class);
        model.connections().observe(this,c->{
            binding.facebookPageStatus.setText(c.facebook==null?getString(R.string.facebook_not_connected):
                getString(c.facebookReauth?R.string.social_reauth_required:R.string.facebook_connected,c.facebook));
            binding.linkedinStatus.setText(c.linkedin==null?getString(R.string.linkedin_not_connected):
                getString(c.linkedinReauth?R.string.social_reauth_required:R.string.linkedin_connected,c.linkedin));
        });
        refreshConnectionSummaries();
        binding.connectLinkedinButton.setOnClickListener(view -> model.connectLinkedIn());
        binding.connectFacebookButton.setOnClickListener(view -> model.connectFacebook());
        model.oauthState().observe(this, state -> {
            oauthBusy=state.status==MainViewModel.OAuthStatus.STARTING||state.status==MainViewModel.OAuthStatus.EXCHANGING;
            refreshActionButtons();
            if (state.status == MainViewModel.OAuthStatus.AUTHORIZATION_READY) {
                String url = state.value;
                model.consumeAuthorizationUrl();
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (RuntimeException exception) { showOAuthError("PROVIDER_ERROR"); }
            } else if (state.status == MainViewModel.OAuthStatus.CONNECTED) {
                refreshConnectionSummaries();
                Toast.makeText(this, R.string.social_connected_short, Toast.LENGTH_SHORT).show();
                model.acknowledgeOAuth();
            } else if (state.status == MainViewModel.OAuthStatus.FAILED) {
                showOAuthError(state.value == null ? "PROVIDER_ERROR" : state.value);
                model.acknowledgeOAuth();
            }
        });
        binding.publishLinkedinButton.setOnClickListener(view -> {
            final String text=String.valueOf(binding.linkedinText.getText()).trim();
            if(text.isEmpty()){binding.linkedinText.setError(getString(R.string.required));return;}
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.linkedin_approve_title)
                .setMessage(getString(R.string.linkedin_approve_message,text))
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.linkedin_approve_send,(dialog,which)->model.publishApprovedLinkedInText(text)).show();
        });
        binding.confirmLinkedinUrlButton.setOnClickListener(view->{
            final String url=String.valueOf(binding.linkedinPostUrl.getText()).trim();
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.linkedin_verify_title)
                .setMessage(R.string.linkedin_verify_message).setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.linkedin_verify_confirm,(dialog,which)->model.confirmOpenedLinkedInUrl(url)).show();
        });
        model.publishing().observe(this,busy->{publishBusy=Boolean.TRUE.equals(busy);refreshActionButtons();});
        model.publishState().observe(this,result->{
            if(result==null)return;
            int label=result.startsWith("PUBLISHED_URL_PENDING")?R.string.linkedin_publish_pending:
                result.startsWith("UNKNOWN")?R.string.linkedin_publish_unknown:
                result.startsWith("SUCCESS")?R.string.linkedin_publish_success:R.string.linkedin_publish_other;
            binding.linkedinPublishStatus.setText(getString(label,result));
            refreshConnectionSummaries();
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
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleOAuthIntent(intent);
    }

    private void handleOAuthIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if (!"socialpilot".equals(data.getScheme())) return;
        intent.setData(null);
        model.handleOAuthCallback(data);
    }

    @Override protected void onResume() { super.onResume(); if(model!=null) refreshConnectionSummaries(); }
    private void refreshConnectionSummaries() { model.refreshConnections(); }

    private void refreshActionButtons(){
        boolean enabled=!oauthBusy&&!publishBusy;
        binding.connectFacebookButton.setEnabled(enabled);binding.connectLinkedinButton.setEnabled(enabled);
        binding.publishLinkedinButton.setEnabled(enabled);binding.confirmLinkedinUrlButton.setEnabled(enabled);
    }
    private void showOAuthError(String code) {
        Toast.makeText(this, getString(R.string.oauth_error, code), Toast.LENGTH_LONG).show();
    }
}
