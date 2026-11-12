package com.socialpilot.app.data.ai;

import com.google.gson.JsonObject;
import com.socialpilot.app.domain.contract.ai.AiError.Code;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.ModelCapabilities;
import java.io.IOException;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.Request;

public final class OpenAiProvider extends HttpTextProvider {
    public OpenAiProvider(AiCredentialSource credentials, List<ModelCapabilities> models) {
        this(HttpUrl.get("https://api.openai.com/v1/responses"), credentials, models, 30_000);
    }

    OpenAiProvider(HttpUrl endpoint, AiCredentialSource credentials,
                   List<ModelCapabilities> models, long readTimeoutMillis) {
        super(AiRequest.Provider.OPENAI, endpoint, credentials, models, readTimeoutMillis);
    }

    @Override
    Request request(AiRequest request, String key) {
        JsonObject body = new JsonObject();
        body.addProperty("model", request.model);
        body.addProperty("input", request.prompt);
        body.addProperty("max_output_tokens", request.maxOutputTokens);
        body.addProperty("store", false);
        body.addProperty("stream", true);
        return new Request.Builder().url(endpoint)
                .header("Authorization", "Bearer " + key)
                .header("Accept", "text/event-stream")
                .post(jsonBody(body)).build();
    }

    @Override
    void read(SseReader reader, Stream stream) throws IOException {
        String itemId = null;
        Long contentIndex = null;
        boolean refused = false;
        String data;
        while (stream.active() && (data = reader.next()) != null) {
            JsonObject event = parse(data);
            String type = string(event, "type");
            if (type == null) throw failure(Code.PROTOCOL_ERROR);
            JsonObject response = child(event, "response");
            switch (type) {
                case "response.output_text.delta":
                    String delta = string(event, "delta");
                    String nextId = string(event, "item_id");
                    Long nextContent = count(event, "content_index");
                    if (delta == null || nextId == null || nextContent == null) {
                        throw failure(Code.PROTOCOL_ERROR);
                    }
                    if (itemId != null && (!itemId.equals(nextId) || !contentIndex.equals(nextContent))) {
                        throw failure(Code.UNSUPPORTED_CAPABILITY);
                    }
                    itemId = nextId;
                    contentIndex = nextContent;
                    stream.text(delta);
                    break;
                case "response.refusal.delta":
                case "response.refusal.done":
                    refused = true;
                    break;
                case "response.completed":
                    if (!"completed".equals(string(response, "status"))) throw failure(Code.PROTOCOL_ERROR);
                    stream.usage = usage(response);
                    if (refused) throw failure(Code.CONTENT_BLOCKED);
                    return;
                case "response.incomplete":
                    stream.usage = usage(response);
                    String reason = string(child(response, "incomplete_details"), "reason");
                    throw failure("content_filter".equals(reason) || refused
                            ? Code.CONTENT_BLOCKED : Code.INCOMPLETE);
                case "response.failed":
                case "error":
                    stream.usage = usage(response);
                    Code code = providerError("error".equals(type) ? event : child(response, "error"));
                    throw failure(code == null ? Code.PROVIDER_UNAVAILABLE : code);
                default:
                    // Lifecycle and text.done events do not add deltas or finish the attempt.
                    break;
            }
        }
        if (stream.active()) throw failure(Code.INCOMPLETE);
    }

    private static AiUsage usage(JsonObject response) {
        JsonObject usage = child(response, "usage");
        return new AiUsage(count(usage, "input_tokens"), count(usage, "output_tokens"),
                count(usage, "total_tokens"), count(child(usage, "input_tokens_details"), "cached_tokens"),
                count(child(usage, "output_tokens_details"), "reasoning_tokens"));
    }

    @Override
    Code providerError(JsonObject error) {
        String code = string(error, "code");
        if (code == null) return null;
        switch (code) {
            case "invalid_api_key": return Code.INVALID_CREDENTIAL;
            case "insufficient_permissions": return Code.PERMISSION_DENIED;
            case "insufficient_quota": return Code.QUOTA_EXHAUSTED;
            case "rate_limit_exceeded": return Code.RATE_LIMITED;
            case "model_not_found": return Code.UNSUPPORTED_CAPABILITY;
            case "content_policy_violation": return Code.CONTENT_BLOCKED;
            case "server_error": return Code.PROVIDER_UNAVAILABLE;
            default: return null;
        }
    }
}
