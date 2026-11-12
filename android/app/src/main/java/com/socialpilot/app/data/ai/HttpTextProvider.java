package com.socialpilot.app.data.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.AiProvider;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiError.Code;
import com.socialpilot.app.domain.contract.ai.AiEvent;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.ModelCapabilities;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

abstract class HttpTextProvider implements AiProvider {
    private static final int MAX_OUTPUT_CHARS = 1024 * 1024;
    private static final long MAX_ERROR_BYTES = 16 * 1024;
    private final AiRequest.Provider provider;
    private final AiCredentialSource credentials;
    private final List<ModelCapabilities> models;
    private final OkHttpClient client;
    final HttpUrl endpoint;

    HttpTextProvider(AiRequest.Provider provider, HttpUrl endpoint, AiCredentialSource credentials,
                     List<ModelCapabilities> models, long readTimeoutMillis) {
        this.provider = provider;
        this.endpoint = endpoint;
        this.credentials = Objects.requireNonNull(credentials);
        this.models = Collections.unmodifiableList(new ArrayList<>(models));
        client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public final Attempt prepare(AiRequest request, Listener listener) {
        return new HttpAttempt(Objects.requireNonNull(request), Objects.requireNonNull(listener));
    }

    abstract Request request(AiRequest request, String key);

    abstract void read(SseReader reader, Stream stream) throws IOException;

    abstract Code providerError(JsonObject error);

    private boolean supports(AiRequest request) {
        if (request.provider != provider) return false;
        for (ModelCapabilities model : models) {
            if (model.allowsTextStream(request)) return true;
        }
        return false;
    }

    private final class HttpAttempt implements Attempt {
        private final AiRequest request;
        private final Listener listener;
        private boolean started;
        private volatile boolean cancelled;
        private boolean terminal;
        private boolean dispatched;
        private Call call;

        HttpAttempt(AiRequest request, Listener listener) {
            this.request = request;
            this.listener = listener;
        }

        @Override
        public void cancel() {
            Call attached;
            synchronized (this) {
                if (terminal || cancelled) return;
                cancelled = true;
                attached = call;
            }
            if (attached != null) attached.cancel();
        }

        private synchronized boolean attach(Call candidate) {
            if (cancelled) return false;
            call = candidate;
            // From this point onwards a caller must account for possible provider work.
            dispatched = true;
            return true;
        }

        @Override
        public void run() {
            synchronized (this) {
                if (started) return;
                started = true;
            }
            Stream stream = new Stream(this);
            AiError error = null;
            try {
                if (!cancelled) {
                    if (!supports(request)) throw failure(Code.UNSUPPORTED_CAPABILITY);
                    listener.onEvent(AiEvent.started(request, ++stream.sequence));
                    execute(stream);
                    if (stream.outputChars == 0 && !cancelled) throw failure(Code.INCOMPLETE);
                }
            } catch (StreamFailure failure) {
                error = failure.error;
            } catch (JsonParseException | ProtocolException failure) {
                error = new AiError(Code.PROTOCOL_ERROR, null);
            } catch (InterruptedIOException failure) {
                error = new AiError(Code.TIMEOUT, null);
            } catch (IOException failure) {
                error = new AiError(Code.CONNECTION_FAILED, null);
            }
            AiEvent result;
            synchronized (this) {
                terminal = true;
                AiEvent.Type type = cancelled ? AiEvent.Type.CANCELLED
                        : error == null ? AiEvent.Type.COMPLETED : AiEvent.Type.FAILED;
                result = AiEvent.terminal(request, ++stream.sequence, type,
                        cancelled ? null : error, stream.usage,
                        type != AiEvent.Type.COMPLETED && stream.outputChars > 0, dispatched);
                call = null;
            }
            listener.onEvent(result);
        }

        private void execute(Stream stream) throws IOException {
            if (cancelled) return;
            String key;
            try {
                key = credentials.load();
            } catch (RuntimeException unavailable) {
                throw failure(Code.INVALID_CREDENTIAL);
            }
            if (key == null || key.isEmpty()) throw failure(Code.INVALID_CREDENTIAL);
            for (int i = 0; i < key.length(); i++) {
                char character = key.charAt(i);
                if (character <= ' ' || character > '~') throw failure(Code.INVALID_CREDENTIAL);
            }
            Call candidate = client.newCall(request(request, key));
            if (!attach(candidate)) {
                candidate.cancel();
                return;
            }
            try (Response response = candidate.execute()) {
                if (!response.isSuccessful()) throw httpError(response);
                ResponseBody body = response.body();
                MediaType type = body == null ? null : body.contentType();
                if (type == null || !"text".equals(type.type()) || !"event-stream".equals(type.subtype())) {
                    throw failure(Code.PROTOCOL_ERROR);
                }
                read(new SseReader(body.source()), stream);
            }
        }
    }

    final class Stream {
        private final HttpAttempt attempt;
        long sequence;
        int outputChars;
        AiUsage usage = AiUsage.MISSING;

        private Stream(HttpAttempt attempt) {
            this.attempt = attempt;
        }

        boolean active() {
            return !attempt.cancelled;
        }

        void text(String text) throws IOException {
            if (text.isEmpty() || !active()) return;
            if (text.length() > MAX_OUTPUT_CHARS - outputChars) throw failure(Code.PROTOCOL_ERROR);
            outputChars += text.length();
            attempt.listener.onEvent(AiEvent.text(attempt.request, ++sequence, text));
        }
    }

    private StreamFailure httpError(Response response) {
        int status = response.code();
        Code wireCode = null;
        try (ResponseBody preview = response.peekBody(MAX_ERROR_BYTES)) {
            wireCode = providerError(child(parse(preview.string()), "error"));
        } catch (IOException | JsonParseException ignored) {
            // HTTP status remains available even when the provider body is absent or malformed.
        }
        Code code;
        if (status == 401 || wireCode == Code.INVALID_CREDENTIAL) code = Code.INVALID_CREDENTIAL;
        else if (status == 403) code = Code.PERMISSION_DENIED;
        else if (status == 429) code = wireCode == Code.QUOTA_EXHAUSTED ? wireCode : Code.RATE_LIMITED;
        else if (status == 408 || status == 504) code = Code.TIMEOUT;
        else if (status >= 500) code = Code.PROVIDER_UNAVAILABLE;
        else if (status >= 300 && status < 400) code = Code.PROTOCOL_ERROR;
        else if (wireCode != null) code = wireCode;
        else if (status >= 400) code = Code.INVALID_REQUEST;
        else code = Code.PROTOCOL_ERROR;
        Long retryAfter = code == Code.RATE_LIMITED || code == Code.PROVIDER_UNAVAILABLE
                ? retryAfter(response.header("Retry-After")) : null;
        return new StreamFailure(new AiError(code, retryAfter));
    }

    private static Long retryAfter(String raw) {
        if (raw == null || !raw.matches("[0-9]+")) return null;
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static RequestBody jsonBody(JsonObject value) {
        RequestBody delegate = RequestBody.create(value.toString(), MediaType.get("application/json; charset=utf-8"));
        return new RequestBody() {
            @Override public MediaType contentType() { return delegate.contentType(); }
            @Override public long contentLength() throws IOException { return delegate.contentLength(); }
            @Override public boolean isOneShot() { return true; }
            @Override public void writeTo(BufferedSink sink) throws IOException { delegate.writeTo(sink); }
        };
    }

    static JsonObject parse(String data) {
        JsonElement element = JsonParser.parseString(data);
        if (!element.isJsonObject()) throw new JsonParseException("Expected a JSON object");
        return element.getAsJsonObject();
    }

    static JsonObject child(JsonObject parent, String name) {
        JsonElement element = parent == null ? null : parent.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    static String string(JsonObject parent, String name) {
        JsonElement element = parent == null ? null : parent.get(name);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    static Long count(JsonObject parent, String name) {
        JsonElement element = parent == null ? null : parent.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return null;
        try {
            long value = element.getAsBigDecimal().longValueExact();
            return value < 0 ? null : value;
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }

    static StreamFailure failure(Code code) {
        return new StreamFailure(new AiError(code, null));
    }

    static final class StreamFailure extends IOException {
        final AiError error;

        StreamFailure(AiError error) {
            super(error.code.name());
            this.error = error;
        }
    }
}
