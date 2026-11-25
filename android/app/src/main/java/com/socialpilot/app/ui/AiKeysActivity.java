package com.socialpilot.app.ui;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.socialpilot.app.R;
import com.socialpilot.app.data.ai.AiKeyRepository.KeyStatus;
import com.socialpilot.app.data.ai.ByokKeyVerifier.Outcome;
import com.socialpilot.app.databinding.ActivityAiKeysBinding;
import com.socialpilot.app.databinding.ItemAiKeyBinding;
import com.socialpilot.app.databinding.ItemTaskModelBinding;
import com.socialpilot.app.domain.ai.UsageSummary;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.ModelEntry;
import com.socialpilot.app.domain.contract.ai.AiRequest.Provider;
import com.socialpilot.app.ui.AiKeysViewModel.Busy;
import com.socialpilot.app.ui.AiKeysViewModel.Notice;
import com.socialpilot.app.ui.AiKeysViewModel.ProviderState;

/** BYOK settings. The full key is only ever in the input field until it is saved. */
public final class AiKeysActivity extends AppCompatActivity {
    private AiKeysViewModel model;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keeps typed keys out of screenshots and the recent-apps thumbnail.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        ActivityAiKeysBinding binding = ActivityAiKeysBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        model = new ViewModelProvider(this).get(AiKeysViewModel.class);
        bind(binding.openAiKey, Provider.OPENAI, R.string.provider_openai);
        bind(binding.geminiKey, Provider.GEMINI, R.string.provider_gemini);
        AiModelsViewModel models = new ViewModelProvider(this).get(AiModelsViewModel.class);
        models.state().observe(this, state -> renderModels(binding, models, state));
    }

    private void renderModels(ActivityAiKeysBinding binding, AiModelsViewModel models, AiModelsViewModel.State state) {
        binding.dispatchStatus.setText(getString(R.string.ai_dispatch_status,
                getString(state.dispatchEnabled ? R.string.ai_dispatch_enabled : R.string.ai_dispatch_locked),
                getString(R.string.ai_registry_revision, state.registryRevision)));
        binding.taskModels.removeAllViews();
        for (AiModelsViewModel.TaskRow row : state.rows) {
            ItemTaskModelBinding item = ItemTaskModelBinding.inflate(getLayoutInflater(), binding.taskModels, true);
            item.taskTitle.setText(taskTitle(row.task));
            java.util.List<String> labels = new java.util.ArrayList<>();
            labels.add(getString(R.string.ai_model_none));
            int selected = 0;
            for (int i = 0; i < row.options.size(); i++) {
                ModelEntry option = row.options.get(i);
                labels.add(getString(R.string.ai_model_option, providerName(option.provider()), option.model()));
                if (option == row.selected) selected = i + 1;
            }
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            item.modelSpinner.setAdapter(adapter);
            item.modelSpinner.setSelection(selected, false);
            item.modelSpinner.setEnabled(!row.options.isEmpty());
            final int current = selected;
            item.modelSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position != current) models.select(row.task, position == 0 ? null : row.options.get(position - 1));
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
            item.modelStatus.setText(modelStatus(row));
        }
        if (state.saveFailed) Toast.makeText(this, R.string.ai_model_save_error, Toast.LENGTH_SHORT).show();
        binding.usageSummary.setText(usageText(state));
    }

    private String modelStatus(AiModelsViewModel.TaskRow row) {
        if (row.options.isEmpty()) return getString(R.string.ai_model_no_options);
        if (row.selected == null) return getString(R.string.ai_model_not_selected);
        switch (row.block) {
            case NONE: return getString(R.string.ai_model_ready, row.selected.price.priceRevision);
            case UNVERIFIED: return getString(R.string.ai_model_block_unverified);
            case CAPABILITY_UNKNOWN: return getString(R.string.ai_model_block_capability_unknown);
            case CAPABILITY_UNSUPPORTED: return getString(R.string.ai_model_block_capability_unsupported);
            case TASK_NOT_ALLOWED: return getString(R.string.ai_model_block_task);
            case OUTPUT_CAP_EXCEEDED: return getString(R.string.ai_model_block_output_cap);
            default: return getString(R.string.ai_model_block_price);
        }
    }

    private String usageText(AiModelsViewModel.State state) {
        if (state.today == null) return getString(R.string.ai_usage_unavailable);
        StringBuilder text = new StringBuilder();
        for (java.util.List<UsageSummary.Row> rows : java.util.Arrays.asList(state.today, state.month)) {
            for (UsageSummary.Row row : rows) {
                text.append(getString(R.string.ai_usage_row, providerName(row.provider), row.periodKey,
                        java.math.BigDecimal.valueOf(row.estimatedMicros, 6).stripTrailingZeros().toPlainString(),
                        row.currency, row.settledRequests, row.uncertainRequests, row.releasedRequests)).append("\n");
            }
        }
        if (text.length() == 0) return getString(R.string.ai_usage_empty);
        return text.append(getString(R.string.ai_usage_note)).toString();
    }

    private String providerName(Provider provider) {
        return getString(provider == Provider.OPENAI ? R.string.provider_openai : R.string.provider_gemini);
    }

    private static int taskTitle(AiTaskType task) {
        switch (task) {
            case COMMENT_CLASSIFICATION: return R.string.ai_task_comment_classification;
            case COMMENT_REPLY: return R.string.ai_task_comment_reply;
            case ANALYTICS_REPORT: return R.string.ai_task_analytics_report;
            default: return R.string.ai_task_content_text;
        }
    }

    private void bind(ItemAiKeyBinding card, Provider provider, int title) {
        card.providerTitle.setText(title);
        card.saveKeyButton.setOnClickListener(view -> {
            card.keyInputLayout.setError(null);
            if (model.save(provider, String.valueOf(card.keyInput.getText()))) card.keyInput.setText("");
        });
        card.checkKeyButton.setOnClickListener(view -> model.check(provider));
        card.deleteKeyButton.setOnClickListener(view -> model.delete(provider));
        model.state(provider).observe(this, state -> render(card, state));
    }

    private void render(ItemAiKeyBinding card, ProviderState state) {
        boolean busy = state.busy != Busy.NONE;
        KeyStatus status = state.status;
        boolean hasKey = status != null && status.state != com.socialpilot.app.data.ai.AiKeyRepository.KeyState.NOT_SET;
        card.keyProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        card.saveKeyButton.setEnabled(state.busy != Busy.SAVING && state.busy != Busy.DELETING && state.busy != Busy.LOADING);
        card.checkKeyButton.setEnabled(!busy && hasKey);
        card.deleteKeyButton.setEnabled(state.busy != Busy.DELETING && state.busy != Busy.LOADING && hasKey);
        card.keyStatus.setText(busy ? busyText(state.busy) : statusText(status));
        int outcome = outcomeText(state.outcome);
        card.keyOutcome.setVisibility(outcome == 0 || busy ? View.GONE : View.VISIBLE);
        if (outcome != 0) card.keyOutcome.setText(outcome);
        if (state.notice == Notice.FORMAT_ERROR) {
            card.keyInputLayout.setError(getString(R.string.ai_key_format_error));
        } else if (state.notice == Notice.STORE_ERROR) {
            Toast.makeText(this, R.string.ai_key_store_error, Toast.LENGTH_LONG).show();
        } else if (state.notice == Notice.DELETE_ERROR) {
            Toast.makeText(this, R.string.ai_key_delete_error, Toast.LENGTH_LONG).show();
        } else if (state.notice == Notice.DELETED) {
            Toast.makeText(this, R.string.ai_key_deleted, Toast.LENGTH_SHORT).show();
        }
        if (state.notice != Notice.NONE && state.notice != Notice.FORMAT_ERROR) model.acknowledgeNotice(state.provider);
    }

    private String busyText(Busy busy) {
        switch (busy) {
            case SAVING: return getString(R.string.ai_key_saving);
            case CHECKING: return getString(R.string.ai_key_checking);
            case DELETING: return getString(R.string.ai_key_deleting);
            default: return getString(R.string.ai_key_loading);
        }
    }

    private String statusText(KeyStatus status) {
        if (status == null) return getString(R.string.ai_key_loading);
        switch (status.state) {
            case UNVERIFIED: return getString(R.string.ai_key_state_unverified, status.maskedHint);
            case VALID:
                return getString(R.string.ai_key_state_valid, status.maskedHint, DateUtils.formatDateTime(this,
                        status.checkedAtMillis == null ? 0 : status.checkedAtMillis,
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));
            case INVALID: return getString(R.string.ai_key_state_invalid, status.maskedHint);
            case PERMISSION_DENIED: return getString(R.string.ai_key_state_permission, status.maskedHint);
            case UNREADABLE: return getString(R.string.ai_key_state_unreadable);
            default: return getString(R.string.ai_key_state_not_set);
        }
    }

    private static int outcomeText(Outcome outcome) {
        if (outcome == null) return 0;
        switch (outcome) {
            case RATE_LIMITED: return R.string.ai_key_outcome_rate_limited;
            case PROVIDER_UNAVAILABLE: return R.string.ai_key_outcome_provider_unavailable;
            case TIMEOUT: return R.string.ai_key_outcome_timeout;
            case NETWORK_ERROR: return R.string.ai_key_outcome_network;
            case UNEXPECTED_RESPONSE: return R.string.ai_key_outcome_unexpected;
            case OFFLINE: return R.string.ai_key_outcome_offline;
            case SUPERSEDED: return R.string.ai_key_outcome_superseded;
            case STATUS_STORAGE_FAILED: return R.string.ai_key_outcome_status_storage_failed;
            default: return 0; // Definite outcomes are already shown by the key state.
        }
    }
}
