package com.socialpilot.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.socialpilot.app.R;
import com.socialpilot.app.databinding.ActivityMainBinding;
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
    private boolean observingNetwork;
    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(android.net.Network network) {
                    postNetworkRefresh();
                }
                @Override public void onLost(android.net.Network network) {
                    postNetworkRefresh();
                }
                @Override public void onCapabilitiesChanged(android.net.Network network,
                        NetworkCapabilities capabilities) {
                    postNetworkRefresh();
                }
            };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        model = new ViewModelProvider(this).get(MainViewModel.class);
        LocaleListCompat appLocales = AppCompatDelegate.getApplicationLocales();
        String uiLanguage = appLocales.isEmpty()
                ? getResources().getConfiguration().locale.getLanguage()
                : appLocales.get(0).getLanguage();
        binding.appLanguageGroup.check("en".equals(uiLanguage)
                ? R.id.appLanguageEn : R.id.appLanguageVi);
        binding.appLanguageGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String tag = checkedId == R.id.appLanguageEn ? "en" : "vi";
            if (!tag.equals(AppCompatDelegate.getApplicationLocales().toLanguageTags())) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
            }
        });
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
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show(); model.acknowledge();
            } else if (state == MainViewModel.SaveState.FAILED) {
                Toast.makeText(this, R.string.save_error, Toast.LENGTH_SHORT).show(); model.acknowledge();
            }
        });
        binding.saveButton.setOnClickListener(view -> saveBrief());
        binding.openDashboardButton.setOnClickListener(view ->
                startActivity(new Intent(this, DashboardActivity.class)));
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

    @Override protected void onStart() {
        super.onStart();
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager != null) {
            try {
                manager.registerNetworkCallback(new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                        networkCallback);
                observingNetwork = true;
            } catch (RuntimeException ignored) {
                observingNetwork = false;
            }
        }
        refreshNetworkStatus();
    }

    @Override protected void onStop() {
        if (observingNetwork) {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager != null) manager.unregisterNetworkCallback(networkCallback);
            observingNetwork = false;
        }
        super.onStop();
    }

    private void postNetworkRefresh() {
        new Handler(Looper.getMainLooper()).post(this::refreshNetworkStatus);
    }

    private void refreshNetworkStatus() {
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
}
