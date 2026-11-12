package com.socialpilot.app.data.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.socialpilot.app.domain.contract.ai.AiError.Code;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.ModelCapabilities;
import java.io.IOException;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.Request;

public final class GeminiAiProvider extends HttpTextProvider {
    public GeminiAiProvider(AiCredentialSource credentials, List<ModelCapabilities> models) {
        this(HttpUrl.get("https://generativelanguage.googleapis.com/v1beta/models/"),
                credentials, models, 30_000);
    }

    GeminiAiProvider(HttpUrl endpoint, AiCredentialSource credentials,
                     List<ModelCapabilities> models, long readTimeoutMillis) {
        super(AiRequest.Provider.GEMINI, endpoint, credentials, models, readTimeoutMillis);
    }

    @Override
    Request request(AiRequest request, String key) {
        JsonObject text = new JsonObject();
        text.addProperty("text", request.prompt);
        JsonArray parts = new JsonArray();
        parts.add(text);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject config = new JsonObject();
        config.addProperty("candidateCount", 1);
        config.addProperty("maxOutputTokens", request.maxOutputTokens);
        JsonObject body = new JsonObject();
        body.add("contents", contents);
        body.add("generationConfig", config);
        HttpUrl url = endpoint.newBuilder().addPathSegment(request.model + ":streamGenerateContent")
                .addQueryParameter("alt", "sse").build();
        return new Request.Builder().url(url)
                .header("x-goog-api-key", key)
                .header("Accept", "text/event-stream")
                .post(jsonBody(body)).build();
    }

    @Override
    void read(SseReader reader, Stream stream) throws IOException {
        boolean stopped = false;
        AiUsage latestUsage = AiUsage.MISSING;
        String data;
        while (stream.active() && (data = reader.next()) != null) {
            JsonObject frame = parse(data);
            JsonObject metadata = child(frame, "usageMetadata");
            if (metadata != null) {
                latestUsage = usage(metadata);
                stream.usage = latestUsage.asPartial();
            }
            JsonObject error = child(frame, "error");
            if (error != null) {
                Code code = providerError(error);
                throw failure(code == null ? Code.PROVIDER_UNAVAILABLE : code);
            }
            String block = string(child(frame, "promptFeedback"), "blockReason");
            if (block != null && !"BLOCK_REASON_UNSPECIFIED".equals(block)) throw failure(Code.CONTENT_BLOCKED);
            JsonArray candidates = array(frame, "candidates");
            if (candidates == null || candidates.size() == 0) continue;
            if (stopped || candidates.size() != 1 || !candidates.get(0).isJsonObject()) {
                throw failure(Code.PROTOCOL_ERROR);
            }
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            Long index = count(candidate, "index");
            if (candidate.has("index") && (index == null || index != 0)) throw failure(Code.PROTOCOL_ERROR);
            String reason = string(candidate, "finishReason");
            if ("SAFETY".equals(reason) || "RECITATION".equals(reason)
                    || "BLOCKLIST".equals(reason) || "PROHIBITED_CONTENT".equals(reason)
                    || "SPII".equals(reason)) {
                throw failure(Code.CONTENT_BLOCKED);
            }
            JsonArray parts = array(child(candidate, "content"), "parts");
            if (parts != null) {
                for (JsonElement part : parts) {
                    if (!part.isJsonObject()) throw failure(Code.PROTOCOL_ERROR);
                    JsonObject value = part.getAsJsonObject();
                    JsonElement thought = value.get("thought");
                    if (thought != null && thought.isJsonPrimitive()
                            && thought.getAsJsonPrimitive().isBoolean() && thought.getAsBoolean()) continue;
                    String text = string(value, "text");
                    if (text == null) throw failure(Code.UNSUPPORTED_CAPABILITY);
                    stream.text(text);
                }
            }
            if (reason == null || "FINISH_REASON_UNSPECIFIED".equals(reason)) continue;
            if ("STOP".equals(reason)) {
                stopped = true;
            } else {
                if ("MAX_TOKENS".equals(reason)) stream.usage = latestUsage;
                throw failure(Code.INCOMPLETE);
            }
        }
        if (stream.active()) {
            if (!stopped) throw failure(Code.INCOMPLETE);
            stream.usage = latestUsage;
        }
    }

    private static JsonArray array(JsonObject parent, String name) throws IOException {
        JsonElement element = parent == null ? null : parent.get(name);
        if (element == null || element.isJsonNull()) return null;
        if (!element.isJsonArray()) throw failure(Code.PROTOCOL_ERROR);
        return element.getAsJsonArray();
    }

    private static AiUsage usage(JsonObject metadata) {
        return new AiUsage(count(metadata, "promptTokenCount"), count(metadata, "candidatesTokenCount"),
                count(metadata, "totalTokenCount"), count(metadata, "cachedContentTokenCount"),
                count(metadata, "thoughtsTokenCount"));
    }

    @Override
    Code providerError(JsonObject error) {
        if (error == null) return null;
        JsonElement details = error.get("details");
        if (details != null && details.isJsonArray()) {
            for (JsonElement item : details.getAsJsonArray()) {
                if (item.isJsonObject() && "API_KEY_INVALID".equals(string(item.getAsJsonObject(), "reason"))) {
                    return Code.INVALID_CREDENTIAL;
                }
            }
        }
        String status = string(error, "status");
        if (status == null) return null;
        switch (status) {
            case "UNAUTHENTICATED": return Code.INVALID_CREDENTIAL;
            case "PERMISSION_DENIED": return Code.PERMISSION_DENIED;
            case "RESOURCE_EXHAUSTED": return Code.RATE_LIMITED;
            case "DEADLINE_EXCEEDED": return Code.TIMEOUT;
            case "NOT_FOUND": return Code.UNSUPPORTED_CAPABILITY;
            case "UNAVAILABLE":
            case "INTERNAL": return Code.PROVIDER_UNAVAILABLE;
            case "INVALID_ARGUMENT": return Code.INVALID_REQUEST;
            default: return null;
        }
    }
}
