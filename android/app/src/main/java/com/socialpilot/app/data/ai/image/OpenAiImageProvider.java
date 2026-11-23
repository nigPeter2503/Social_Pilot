package com.socialpilot.app.data.ai.image;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.socialpilot.app.data.ai.AiCredentialSource;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiError.Code;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.ByteString;

/**
 * OpenAI image generation (one image, base64 response). The wire format is checked against
 * MockWebServer fixtures only; the live API, sizes and response fields stay NOT_RUN until a
 * verified model and price are recorded. A URL-only response is rejected, so no signed URL is
 * ever stored.
 */
public final class OpenAiImageProvider implements ImageProvider {
    /** Largest accepted response body; larger responses are a protocol error. */
    static final long MAX_RESPONSE_BYTES = 40L * 1024 * 1024;
    private final HttpUrl endpoint;
    private final AiCredentialSource credentials;
    private final OkHttpClient client;

    public OpenAiImageProvider(AiCredentialSource credentials) {
        this(HttpUrl.get("https://api.openai.com/v1/images/generations"), credentials, 120_000);
    }

    OpenAiImageProvider(HttpUrl endpoint, AiCredentialSource credentials, long readTimeoutMillis) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.credentials = Objects.requireNonNull(credentials);
        client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Attempt prepare(AiRequest request, String size) {
        if (request.task != AiRequest.Task.IMAGE) throw new IllegalArgumentException("Not an image request");
        return new HttpAttempt(request, Objects.requireNonNull(size));
    }

    private final class HttpAttempt implements Attempt {
        private final AiRequest request;
        private final String size;
        private volatile boolean cancelled;
        private volatile Call call;

        HttpAttempt(AiRequest request, String size) {
            this.request = request;
            this.size = size;
        }

        @Override
        public void cancel() {
            cancelled = true;
            Call current = call;
            if (current != null) current.cancel();
        }

        @Override
        public ImageProvider.Response run() {
            if (cancelled) return ImageProvider.Response.cancelled(false);
            String key;
            try {
                key = credentials.load();
            } catch (RuntimeException unavailable) {
                return ImageProvider.Response.failed(new AiError(Code.INVALID_CREDENTIAL, null), false);
            }
            if (key == null || !key.matches("[!-~]+")) {
                return ImageProvider.Response.failed(new AiError(Code.INVALID_CREDENTIAL, null), false);
            }
            JsonObject body = new JsonObject();
            body.addProperty("model", request.model);
            body.addProperty("prompt", request.prompt);
            body.addProperty("size", size);
            body.addProperty("n", 1);
            Request http = new Request.Builder().url(endpoint)
                    .header("Authorization", "Bearer " + key)
                    .post(RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8")))
                    .build();
            Call candidate = client.newCall(http);
            call = candidate;
            if (cancelled) {
                candidate.cancel();
                return ImageProvider.Response.cancelled(false);
            }
            try (okhttp3.Response response = candidate.execute()) {
                if (!response.isSuccessful()) return ImageProvider.Response.failed(httpError(response.code()), true);
                ResponseBody payload = response.body();
                if (payload == null || payload.contentLength() > MAX_RESPONSE_BYTES) return protocol();
                BufferedSource source = payload.source();
                if (source.request(MAX_RESPONSE_BYTES + 1)) return protocol();
                ImageProvider.Response parsed = parse(source.getBuffer().readUtf8());
                return cancelled ? ImageProvider.Response.cancelled(true) : parsed;
            } catch (InterruptedIOException timeout) {
                return cancelled ? ImageProvider.Response.cancelled(true)
                        : ImageProvider.Response.failed(new AiError(Code.TIMEOUT, null), true);
            } catch (IOException connection) {
                return cancelled ? ImageProvider.Response.cancelled(true)
                        : ImageProvider.Response.failed(new AiError(Code.CONNECTION_FAILED, null), true);
            }
        }
    }

    static ImageProvider.Response parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = root.getAsJsonArray("data");
            if (data == null || data.size() != 1) return protocol();
            JsonElement encoded = data.get(0).getAsJsonObject().get("b64_json");
            if (encoded == null || !encoded.isJsonPrimitive()) return protocol(); // URL-only: rejected.
            ByteString image = ByteString.decodeBase64(encoded.getAsString());
            if (image == null || image.size() == 0) return protocol();
            return ImageProvider.Response.succeeded(image.toByteArray(), usage(root.getAsJsonObject("usage")));
        } catch (JsonParseException | IllegalStateException | ClassCastException | NumberFormatException malformed) {
            return protocol();
        }
    }

    private static AiUsage usage(JsonObject usage) {
        if (usage == null) return AiUsage.MISSING;
        Long input = number(usage, "input_tokens");
        Long output = number(usage, "output_tokens");
        Long total = number(usage, "total_tokens");
        if (input == null && output == null && total == null) return AiUsage.MISSING;
        return new AiUsage(input, output, total, null, null);
    }

    private static Long number(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsLong();
    }

    private static ImageProvider.Response protocol() {
        return ImageProvider.Response.failed(new AiError(Code.PROTOCOL_ERROR, null), true);
    }

    private static AiError httpError(int status) {
        Code code;
        if (status == 401) code = Code.INVALID_CREDENTIAL;
        else if (status == 403) code = Code.PERMISSION_DENIED;
        else if (status == 429) code = Code.RATE_LIMITED;
        else if (status == 408 || status == 504) code = Code.TIMEOUT;
        else if (status >= 500) code = Code.PROVIDER_UNAVAILABLE;
        else if (status >= 300 && status < 400) code = Code.PROTOCOL_ERROR;
        else code = Code.INVALID_REQUEST;
        return new AiError(code, null);
    }
}
