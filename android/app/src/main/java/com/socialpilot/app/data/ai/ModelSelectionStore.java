package com.socialpilot.app.data.ai;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.ModelEntry;
import com.socialpilot.app.domain.contract.ai.ModelRegistry;
import java.util.Objects;

/**
 * Provider/model chosen per task. Settings only: changing a selection never touches drafts,
 * keys or recorded usage. A selection that is no longer in the registry reads as none.
 */
// commit() is deliberate: called from a worker, and the caller reports a failed save.
@SuppressLint("ApplySharedPref")
public final class ModelSelectionStore implements AiGateway.Selections {
    private final SharedPreferences preferences;
    private final ModelRegistry registry;

    public ModelSelectionStore(SharedPreferences preferences, ModelRegistry registry) {
        this.preferences = Objects.requireNonNull(preferences);
        this.registry = Objects.requireNonNull(registry);
    }

    @Override
    public synchronized ModelEntry selected(AiTaskType task) {
        String raw = preferences.getString(name(task), null);
        if (raw == null) return null;
        int separator = raw.indexOf('|');
        if (separator <= 0) return null;
        try {
            ModelEntry entry = registry.find(AiRequest.Provider.valueOf(raw.substring(0, separator)),
                    raw.substring(separator + 1));
            return entry != null && entry.tasks.contains(task) ? entry : null;
        } catch (IllegalArgumentException unknownProvider) {
            return null;
        }
    }

    /** Selecting a blocked model is allowed so the UI can show why it cannot run yet. */
    public synchronized boolean select(AiTaskType task, ModelEntry entry) {
        if (entry == null) return preferences.edit().remove(name(task)).commit();
        if (registry.find(entry.provider(), entry.model()) != entry || !entry.tasks.contains(task)) {
            throw new IllegalArgumentException("Model is not registered for this task");
        }
        return preferences.edit().putString(name(task), entry.provider().name() + "|" + entry.model()).commit();
    }

    private static String name(AiTaskType task) {
        return "task." + task.name();
    }
}
