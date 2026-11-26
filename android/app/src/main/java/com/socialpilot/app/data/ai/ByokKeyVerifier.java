package com.socialpilot.app.data.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.ai.AiRequest.Provider;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Checks a BYOK key with the provider's model-listing endpoint, which does not generate tokens.
 * The key is sent only in the provider's auth header to its fixed HTTPS host, never in the URL,
 * never through the OAuth backend, and redirects are refused.
 */
public final class ByokKeyVerifier {
    /**
     * Only VALID, INVALID_KEY and PERMISSION_DENIED say something about the key itself. The
     * verifier returns the network outcomes. {@link AiKeyRepository} adds key availability,
     * connectivity, supersession and status storage outcomes.
     */
    public enum Outcome {
        VALID, INVALID_KEY, PERMISSION_DENIED, RATE_LIMITED, PROVIDER_UNAVAILABLE,
        TIMEOUT, NETWORK_ERROR, UNEXPECTED_RESPONSE, CANCELLED,
        OFFLINE, NO_KEY, KEY_UNREADABLE, SUPERSEDED, STATUS_STORAGE_FAILED;

        public boolean describesKey() {
            return this == VALID || this == INVALID_KEY || this == PERMISSION_DENIED;
        }
    }

    private static final long MAX_BODY_BYTES = 256 * 1024;
    private final Map<Provider, HttpUrl> endpoints;
    private final OkHttpClient client;

    public ByokKeyVerifier() {
        this(defaultEndpoints(), 15_000);
    }

    // Package-private for MockWebServer fixtures only.
    ByokKeyVerifier(Map<Provider, HttpUrl> endpoints, long readTimeoutMillis) {
        this.endpoints = new EnumMap<>(endpoints);
        client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .addNetworkInterceptor(chain -> {
                    Response response = chain.proceed(chain.request());
                    // OkHttp can follow a 503 + Retry-After: 0 even with connection retries off.
                    return response.code() == 503 ? response.newBuilder().removeHeader("Retry-After").build()
                            : response;
                })
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    private static Map<Provider, HttpUrl> defaultEndpoints() {
        Map<Provider, HttpUrl> result = new EnumMap<>(Provider.class);
        result.put(Provider.OPENAI, HttpUrl.get("https://api.openai.com/v1/models"));
        result.put(Provider.GEMINI, HttpUrl.get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1"));
        return result;
    }

    /** Prepares one check without I/O. Run it on a background worker; cancel from any thread. */
    public Check prepare(Provider provider, String key) {
        HttpUrl url = Objects.requireNonNull(endpoints.get(Objects.requireNonNull(provider)));
        Request.Builder request = new Request.Builder().url(url).get().header("Accept", "application/json");
        if (provider == Provider.OPENAI) request.header("Authorization", "Bearer " + key);
        else request.header("x-goog-api-key", key);
        return new Check(provider, client.newCall(request.build()));
    }

    public static final class Check implements Cancelable {
        private final Provider provider;
        private final Call call;

        private Check(Provider provider, Call call) {
            this.provider = provider;
            this.call = call;
        }

        @Override
        public void cancel() {
            call.cancel();
        }

        /** For fixtures: the exact request that would be sent. */
        Request request() {
            return call.request();
        }

        public Outcome run() {
            if (call.isCanceled()) return Outcome.CANCELLED;
            try (Response response = call.execute()) {
                return classify(provider, response);
            } catch (InterruptedIOException timeout) {
                return call.isCanceled() ? Outcome.CANCELLED : Outcome.TIMEOUT;
            } catch (IOException failure) {
                return call.isCanceled() ? Outcome.CANCELLED : Outcome.NETWORK_ERROR;
            }
        }
    }

    static Outcome classify(Provider provider, Response response) throws IOException {
        int status = response.code();
        if (status == 401) return Outcome.INVALID_KEY;
        if (status == 403) return Outcome.PERMISSION_DENIED;
        if (status == 429) return Outcome.RATE_LIMITED;
        if (status == 408 || status == 504) return Outcome.TIMEOUT;
        if (status >= 500 && status < 600) return Outcome.PROVIDER_UNAVAILABLE;

        if (status == 200) {
            JsonObject body = json(response);
            String list = provider == Provider.OPENAI ? "data" : "models";
            JsonElement items = body == null ? null : body.get(list);
            // A captive portal or proxy page is not proof that the provider accepted the key.
            return items instanceof JsonArray ? Outcome.VALID : Outcome.UNEXPECTED_RESPONSE;
        }
        if (status == 400 && provider == Provider.GEMINI
                && hasReason(json(response), "API_KEY_INVALID", "API_KEY_EXPIRED")) {
            return Outcome.INVALID_KEY;
        }
        return Outcome.UNEXPECTED_RESPONSE;
    }

    private static JsonObject json(Response response) throws IOException {
        ResponseBody body = response.body();
        MediaType type = body == null ? null : body.contentType();
        if (type == null || !"json".equals(type.subtype())) return null;
        try {
            JsonElement parsed = JsonParser.parseString(response.peekBody(MAX_BODY_BYTES).string());
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (JsonParseException malformed) {
            return null;
        }
    }

    private static boolean hasReason(JsonObject body, String... reasons) {
        JsonElement error = body == null ? null : body.get("error");
        JsonElement details = error != null && error.isJsonObject() ? error.getAsJsonObject().get("details") : null;
        if (!(details instanceof JsonArray)) return false;
        for (JsonElement detail : details.getAsJsonArray()) {
            if (!detail.isJsonObject()) continue;
            JsonElement reason = detail.getAsJsonObject().get("reason");
            if (reason == null || !reason.isJsonPrimitive()) continue;
            for (String expected : reasons) {
                if (expected.equals(reason.getAsString())) return true;
            }
        }
        return false;
    }
}
