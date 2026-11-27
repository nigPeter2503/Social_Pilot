package com.socialpilot.app.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.core.AppContainer;
import com.socialpilot.app.data.ai.ModelSelectionStore;
import com.socialpilot.app.domain.ai.BudgetInput;
import com.socialpilot.app.domain.ai.UsageSummary;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.BudgetPeriod;
import com.socialpilot.app.domain.contract.ai.ModelEntry;
import com.socialpilot.app.domain.contract.ai.ModelRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;

/** Model choice per task, budget caps and the usage summary; selection never touches drafts or usage. */
public final class AiModelsViewModel extends AndroidViewModel {
    /** Output cap used only to show whether a model is blocked for reasons other than size. */
    private static final int PREVIEW_OUTPUT_TOKENS = 1;

    public static final class TaskRow {
        public final AiTaskType task;
        public final List<ModelEntry> options;
        public final ModelEntry selected;
        public final ModelEntry.Block block;

        TaskRow(AiTaskType task, List<ModelEntry> options, ModelEntry selected) {
            this.task = task;
            this.options = options;
            this.selected = selected;
            this.block = selected == null ? null : selected.blockFor(task, PREVIEW_OUTPUT_TOKENS);
        }
    }

    public static final class State {
        public final List<TaskRow> rows;
        public final boolean dispatchEnabled;
        public final String registryRevision;
        /** Null when no usage ledger is configured. */
        public final List<UsageSummary.Row> today;
        public final List<UsageSummary.Row> month;
        public final boolean saveFailed;
        /** Null until caps are set; without caps nothing is admitted. */
        public final AttemptLedger.Limits limits;
        public final AttemptLedger.Spend spend;
        public final BudgetNotice budgetNotice;

        State(List<TaskRow> rows, boolean dispatchEnabled, String registryRevision,
              List<UsageSummary.Row> today, List<UsageSummary.Row> month, boolean saveFailed,
              AttemptLedger.Limits limits, AttemptLedger.Spend spend, BudgetNotice budgetNotice) {
            this.rows = rows;
            this.dispatchEnabled = dispatchEnabled;
            this.registryRevision = registryRevision;
            this.today = today;
            this.month = month;
            this.saveFailed = saveFailed;
            this.limits = limits;
            this.spend = spend;
            this.budgetNotice = budgetNotice;
        }
    }

    private final ModelRegistry registry;
    private final ModelSelectionStore selections;
    private final AttemptLedger ledger;
    private final boolean dispatchEnabled;
    private final ExecutorService io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<>();

    public AiModelsViewModel(@NonNull Application application) {
        super(application);
        AppContainer container = ((SocialPilotApplication) application).container();
        registry = container.modelRegistry;
        selections = container.modelSelections;
        ledger = container.ledger;
        dispatchEnabled = container.ai.dispatchEnabled();
        io = container.io;
        reload(false);
    }

    public enum BudgetNotice { NONE, SAVED, INVALID_AMOUNT, DAILY_ABOVE_MONTHLY, SAVE_FAILED }

    public LiveData<State> state() {
        return state;
    }

    public void select(AiTaskType task, ModelEntry entry) {
        io.execute(() -> {
            boolean saved;
            try {
                saved = selections.select(task, entry);
            } catch (IllegalArgumentException notRegistered) {
                saved = false;
            }
            load(!saved);
        });
    }

    /** Caps in USD, evaluated in the device zone at the moment they are saved. */
    public void setLimits(String dailyText, String monthlyText) {
        Long daily = BudgetInput.parseMicros(dailyText);
        Long monthly = BudgetInput.parseMicros(monthlyText);
        if (daily == null || monthly == null) {
            reload(false, BudgetNotice.INVALID_AMOUNT);
            return;
        }
        if (daily > monthly) {
            reload(false, BudgetNotice.DAILY_ABOVE_MONTHLY);
            return;
        }
        io.execute(() -> {
            BudgetNotice notice;
            try {
                ledger.setLimits(new AttemptLedger.Limits("USD", daily, monthly, TimeZone.getDefault().getID()));
                notice = BudgetNotice.SAVED;
            } catch (RuntimeException storage) {
                notice = BudgetNotice.SAVE_FAILED;
            }
            load(false, notice);
        });
    }

    private void reload(boolean saveFailed) {
        reload(saveFailed, BudgetNotice.NONE);
    }

    private void reload(boolean saveFailed, BudgetNotice notice) {
        io.execute(() -> load(saveFailed, notice));
    }

    private void load(boolean saveFailed) {
        load(saveFailed, BudgetNotice.NONE);
    }

    /** Worker thread. */
    private void load(boolean saveFailed, BudgetNotice notice) {
        List<TaskRow> rows = new ArrayList<>();
        for (AiTaskType task : AiTaskType.values()) {
            rows.add(new TaskRow(task, registry.forTask(task), selections.selected(task)));
        }
        List<UsageSummary.Row> today = null;
        List<UsageSummary.Row> month = null;
        AttemptLedger.Limits limits = null;
        AttemptLedger.Spend spend = null;
        if (ledger != null) {
            limits = ledger.limits();
            spend = ledger.currentSpend();
            String zone = limits == null ? TimeZone.getDefault().getID() : limits.zoneId;
            BudgetPeriod now = BudgetPeriod.of(System.currentTimeMillis(), zone);
            List<com.socialpilot.app.domain.contract.ai.UsageRecord> records = ledger.forMonth(now.monthKey);
            today = UsageSummary.summarize(records, UsageSummary.Scope.DAY, now.dayKey);
            month = UsageSummary.summarize(records, UsageSummary.Scope.MONTH, now.monthKey);
        }
        State next = new State(Collections.unmodifiableList(rows), dispatchEnabled, registry.revision,
                today, month, saveFailed, limits, spend, notice);
        main.post(() -> state.setValue(next));
    }
}
