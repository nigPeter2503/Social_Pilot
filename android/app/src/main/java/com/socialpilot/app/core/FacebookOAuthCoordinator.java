package com.socialpilot.app.core;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import com.google.gson.Gson;
import com.socialpilot.app.data.remote.oauth.OAuthApi;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedOAuthStore.Pending;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthFailure;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthHandoffExchangeRequest;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthStartRequest;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthStartResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class FacebookOAuthCoordinator {
    public interface Listener {
        void onAuthorizationUrl(String url);
        void onConnected(String provider, String displayName, String accountId);
        void onFailure(String code);
    }

    private final OAuthApi api;
    private final EncryptedOAuthStore store;
    private final ExecutorService io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final SecureRandom random = new SecureRandom();
    private final Gson gson = new Gson();

    public FacebookOAuthCoordinator(EncryptedOAuthStore store, ExecutorService io, String baseUrl) {
        this.io = io;
        this.store = store;
        URI uri = URI.create(baseUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !baseUrl.endsWith("/")) throw new IllegalArgumentException("OAuth API must use an HTTPS base URL");
        OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build();
        api = new Retrofit.Builder().baseUrl(baseUrl).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(OAuthApi.class);
    }

    public void start(String provider, Listener listener) {
        if (!busy.compareAndSet(false, true)) return;
        final String verifier = randomVerifier();
        final String challenge;
        try { challenge = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }
        catch (Exception exception) { busy.set(false); postFailure(listener, "PROVIDER_ERROR"); return; }
        io.execute(() -> {
            try {
                Response<OAuthStartResponse> response = api.start(provider.toLowerCase(Locale.ROOT),
                        new OAuthStartRequest(provider, "android-dev", challenge)).execute();
                if (!response.isSuccessful() || response.body() == null) throw new OAuthRequestException(errorCode(response));
                OAuthStartResponse result = response.body();
                result.validate();
                URI authorizationUri = URI.create(result.authorizationUrl);
                if (!"www.facebook.com".equalsIgnoreCase(authorizationUri.getHost())
                        || (authorizationUri.getPort() != -1 && authorizationUri.getPort() != 443)) {
                    throw new OAuthRequestException("INVALID_REDIRECT");
                }
                store.savePending(result.sessionId, verifier, provider);
                main.post(() -> { busy.set(false); listener.onAuthorizationUrl(result.authorizationUrl); });
            } catch (Exception exception) {
                busy.set(false); postFailure(listener, exception instanceof OAuthRequestException
                        ? ((OAuthRequestException) exception).code : "PROVIDER_ERROR");
            }
        });
    }

    public void handleCallback(Uri callback, Listener listener) {
        if (callback == null || !"socialpilot".equals(callback.getScheme())
                || !"oauth".equals(callback.getHost()) || !"/callback".equals(callback.getPath())) {
            postFailure(listener, "INVALID_REDIRECT"); return;
        }
        String sessionId = callback.getQueryParameter("sessionId");
        String error = callback.getQueryParameter("error");
        Pending pending = store.getPending();
        if (pending == null || sessionId == null || !pending.sessionId.equals(sessionId)) {
            postFailure(listener, "INVALID_SESSION"); return;
        }
        if (error != null) {
            store.clearPending(); postFailure(listener, allowedError(error)); return;
        }
        String handoff = callback.getQueryParameter("handoffCode");
        if (handoff == null || !handoff.matches("[A-Za-z0-9_-]{43}")) {
            store.clearPending(); postFailure(listener, "INVALID_SESSION"); return;
        }
        if (!busy.compareAndSet(false, true)) return;
        store.clearPending();
        io.execute(() -> {
            try {
                Response<OAuthExchangeResponse> response = api.exchange(
                        new OAuthHandoffExchangeRequest(sessionId, handoff, pending.verifier)).execute();
                if (!response.isSuccessful() || response.body() == null) throw new OAuthRequestException(errorCode(response));
                OAuthExchangeResponse result = response.body(); result.validate();
                if (!sessionId.equals(result.sessionId) || !pending.provider.equals(result.provider)) {
                    throw new OAuthRequestException("PROVIDER_ERROR");
                }
                store.saveConnected(result);
                main.post(() -> {
                    busy.set(false); listener.onConnected(result.provider, result.displayName, result.externalAccountId);
                });
            } catch (Exception exception) {
                busy.set(false); postFailure(listener, exception instanceof OAuthRequestException
                        ? ((OAuthRequestException) exception).code : "PROVIDER_ERROR");
            }
        });
    }

    public String connectedPageSummary() { return store.pageSummary(); }

    public String connectedInstagramSummary() {
        OAuthExchangeResponse connection = store.instagramConnection();
        return connection == null ? null : instagramSummary(connection.displayName, connection.externalAccountId);
    }

    public static String instagramSummary(String username, String accountId) {
        return "@" + username + " (" + accountId + ")";
    }

    private String randomVerifier() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private String errorCode(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                OAuthFailure failure = gson.fromJson(response.errorBody().string(), OAuthFailure.class);
                if (failure != null) { failure.validate(); return failure.code; }
            }
        } catch (Exception ignored) { }
        return "PROVIDER_ERROR";
    }

    private static String allowedError(String value) {
        try { OAuthFailure failure = new OAuthFailure(value); failure.validate(); return failure.code; }
        catch (IllegalArgumentException ignored) { return "PROVIDER_ERROR"; }
    }

    private void postFailure(Listener listener, String code) { main.post(() -> listener.onFailure(code)); }

    private static final class OAuthRequestException extends Exception {
        private final String code;
        private OAuthRequestException(String code) { this.code = code; }
    }
}
