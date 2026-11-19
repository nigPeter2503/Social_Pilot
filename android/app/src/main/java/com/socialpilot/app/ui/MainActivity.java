package com.socialpilot.app.ui;

import android.content.Intent;
import androidx.appcompat.app.AlertDialog;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.socialpilot.app.R;
import com.socialpilot.app.databinding.ActivityMainBinding;
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
        handleOAuthIntent(getIntent());
        if (savedInstanceState == null) handleShareIntent(getIntent());
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
        Toast.makeText(this, getString(R.string.oauth_error, code), Toast.LENGTH_LONG).show();
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
}
