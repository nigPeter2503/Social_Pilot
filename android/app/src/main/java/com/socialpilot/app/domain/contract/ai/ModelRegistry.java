package com.socialpilot.app.domain.contract.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable capability registry. Unknown provider/model pairs are never dispatchable. */
public final class ModelRegistry {
    public final String revision;
    public final List<ModelEntry> entries;

    public ModelRegistry(String revision, List<ModelEntry> entries) {
        if (revision == null || revision.trim().isEmpty()) throw new IllegalArgumentException("Registry revision required");
        this.revision = revision;
        List<ModelEntry> copy = new ArrayList<>();
        for (ModelEntry entry : entries) {
            if (find(copy, entry.provider(), entry.model()) != null) {
                throw new IllegalArgumentException("Duplicate model entry");
            }
            copy.add(Objects.requireNonNull(entry));
        }
        this.entries = Collections.unmodifiableList(copy);
    }

    public ModelEntry find(AiRequest.Provider provider, String model) {
        return find(entries, provider, model);
    }

    /** All rows listed for a task, including blocked ones, so the UI can show why. */
    public List<ModelEntry> forTask(AiTaskType task) {
        List<ModelEntry> result = new ArrayList<>();
        for (ModelEntry entry : entries) if (entry.tasks.contains(task)) result.add(entry);
        return result;
    }

    /** Capabilities for one provider's adapter; the adapter re-checks every request. */
    public List<ModelCapabilities> capabilities(AiRequest.Provider provider) {
        List<ModelCapabilities> result = new ArrayList<>();
        for (ModelEntry entry : entries) if (entry.provider() == provider) result.add(entry.capabilities);
        return result;
    }

    private static ModelEntry find(List<ModelEntry> rows, AiRequest.Provider provider, String model) {
        for (ModelEntry entry : rows) {
            if (entry.provider() == provider && entry.model().equals(model)) return entry;
        }
        return null;
    }
}
