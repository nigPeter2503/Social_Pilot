package com.socialpilot.app.data.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.ModelCapabilities;
import com.socialpilot.app.domain.contract.ai.ModelEntry;
import com.socialpilot.app.domain.contract.ai.ModelRegistry;
import com.socialpilot.app.domain.contract.ai.PriceSheet;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads {@code assets/ai_model_registry.json}. A malformed file yields an empty registry, so
 * nothing becomes dispatchable by accident. Missing fields default to the blocking value.
 */
public final class ModelRegistryParser {
    private ModelRegistryParser() { }

    public static ModelRegistry parseOrEmpty(String json) {
        try {
            return parse(json);
        } catch (RuntimeException malformed) {
            return new ModelRegistry("unavailable", new ArrayList<>());
        }
    }

    static ModelRegistry parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<ModelEntry> entries = new ArrayList<>();
        JsonArray models = root.getAsJsonArray("models");
        if (models != null) {
            for (JsonElement element : models) entries.add(entry(element.getAsJsonObject()));
        }
        return new ModelRegistry(root.get("registryRevision").getAsString(), entries);
    }

    private static ModelEntry entry(JsonObject row) {
        AiRequest.Provider provider = AiRequest.Provider.valueOf(row.get("provider").getAsString());
        String model = row.get("model").getAsString();
        ModelCapabilities capabilities = new ModelCapabilities(provider, model, support(row, "text"),
                support(row, "streamingText"), support(row, "image"), support(row, "usage"),
                row.get("maxOutputTokens").getAsInt());
        Set<AiTaskType> tasks = EnumSet.noneOf(AiTaskType.class);
        for (JsonElement task : row.getAsJsonArray("tasks")) tasks.add(AiTaskType.valueOf(task.getAsString()));
        ModelEntry.Verification verification = "VERIFIED".equals(string(row, "verification"))
                ? ModelEntry.Verification.VERIFIED : ModelEntry.Verification.UNVERIFIED;
        return new ModelEntry(capabilities, tasks, verification, price(row.get("price")), row.get("source").getAsString());
    }

    private static ModelCapabilities.Support support(JsonObject row, String name) {
        String value = string(row, name);
        return "SUPPORTED".equals(value) ? ModelCapabilities.Support.SUPPORTED
                : "UNSUPPORTED".equals(value) ? ModelCapabilities.Support.UNSUPPORTED
                : ModelCapabilities.Support.UNKNOWN;
    }

    private static PriceSheet price(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        JsonObject price = element.getAsJsonObject();
        return new PriceSheet(Currency.getInstance(price.get("currency").getAsString()),
                new BigDecimal(price.get("uncachedInputPerMillion").getAsString()),
                new BigDecimal(price.get("cachedInputPerMillion").getAsString()),
                new BigDecimal(price.get("outputPerMillion").getAsString()),
                price.has("perImage") && !price.get("perImage").isJsonNull()
                        ? new BigDecimal(price.get("perImage").getAsString()) : null,
                price.get("priceRevision").getAsString(), price.get("source").getAsString(),
                price.get("capturedAt").getAsString());
    }

    private static String string(JsonObject row, String name) {
        JsonElement value = row.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
