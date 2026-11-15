package com.socialpilot.app.core;

import android.content.Context;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OAuthCoordinator {
    public interface Listener {
        void onAuthorizationUrl(String url);
        void onConnected(String pageName, String pageId);
        void onFailure(String code);
    }

    private final OAuthApi api;
    private final EncryptedOAuthStore store;
    private final ExecutorService io;
    private final java.util.concurrent.Executor main;
    private final java.util.ArrayDeque<CallbackWork> callbackQueue = new java.util.ArrayDeque<>();
    private final java.util.Set<String> queuedSessions = new java.util.HashSet<>();
    private boolean drainingCallbacks;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final SecureRandom random = new SecureRandom();
    private final Gson gson = new Gson();

    public OAuthCoordinator(Context context, ExecutorService io, String baseUrl) {
        this.io = io;
        this.store = EncryptedOAuthStore.shared(context);
        Handler handler = new Handler(Looper.getMainLooper());
        this.main = task -> handler.post(task);
        URI uri = URI.create(baseUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !baseUrl.endsWith("/")) throw new IllegalArgumentException("OAuth API must use an HTTPS base URL");
        OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build();
        api = new Retrofit.Builder().baseUrl(baseUrl).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(OAuthApi.class);
    }

    OAuthCoordinator(OAuthApi api, EncryptedOAuthStore store, ExecutorService io,
            java.util.concurrent.Executor main) {
        this.api=api; this.store=store; this.io=io; this.main=main;
    }

    public void start(String provider, Listener listener) {
        if (!"FACEBOOK".equals(provider) && !"LINKEDIN".equals(provider)) { postFailure(listener,"INVALID_REDIRECT"); return; }
        if (!busy.compareAndSet(false, true)) { postFailure(listener,"PROVIDER_ERROR"); return; }
        final String verifier = randomVerifier();
        final String challenge;
        try { challenge = Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)),
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }
        catch (Exception exception) { busy.set(false); postFailure(listener, "PROVIDER_ERROR"); return; }
        io.execute(() -> {
            try {
                Response<OAuthStartResponse> response = api.start(provider.toLowerCase(java.util.Locale.ROOT),
                        new OAuthStartRequest(provider, "android-dev", challenge)).execute();
                if (!response.isSuccessful() || response.body() == null) throw new OAuthRequestException(errorCode(response));
                OAuthStartResponse result = response.body(); result.validate();
                validateAuthorizationUrl(provider,result.authorizationUrl);
                store.savePending(provider,result.sessionId,verifier,result.expiresAt);
                main.execute(() -> { busy.set(false); listener.onAuthorizationUrl(result.authorizationUrl); });
            } catch (Exception exception) {
                busy.set(false); postFailure(listener, exception instanceof OAuthRequestException
                        ? ((OAuthRequestException) exception).code : "PROVIDER_ERROR");
            }
        });
    }

    public boolean handleCallback(Uri callback, Listener listener) {
        if (callback == null || !"socialpilot".equals(callback.getScheme())
                || !"oauth".equals(callback.getHost()) || !"/callback".equals(callback.getPath())) {
            postFailure(listener,"INVALID_REDIRECT"); return true;
        }
        return acceptCallback(callback.getQueryParameter("sessionId"),
                callback.getQueryParameter("handoffCode"), callback.getQueryParameter("error"), listener);
    }

    boolean acceptCallback(String sessionId, String handoff, String error, Listener listener) {
        if (sessionId == null) { postFailure(listener,"INVALID_SESSION"); return true; }
        synchronized (callbackQueue) {
            if (queuedSessions.contains(sessionId)) return true;
            CallbackWork work=new CallbackWork(sessionId,handoff,error,listener);
            callbackQueue.add(work);
            queuedSessions.add(sessionId);
            if (drainingCallbacks) return true;
            drainingCallbacks=true;
            try { io.execute(this::drainCallbacks); }
            catch (java.util.concurrent.RejectedExecutionException e) {
                callbackQueue.remove(work); queuedSessions.remove(sessionId); drainingCallbacks=false;
                postFailure(listener,"PROVIDER_ERROR"); return false;
            }
            return true;
        }
    }

    private void drainCallbacks() {
        while (true) {
            CallbackWork work;
            synchronized (callbackQueue) {
                work=callbackQueue.poll();
                if (work==null) { drainingCallbacks=false; return; }
            }
            try { exchangeCallback(work); }
            finally { synchronized (callbackQueue) { queuedSessions.remove(work.sessionId); } }
        }
    }

    private void exchangeCallback(CallbackWork work) {
        try {
            Pending pending=store.findPending(work.sessionId);
            if (pending==null || (pending.expiresAt!=null && EncryptedOAuthStore.expired(pending.expiresAt))) {
                if (pending!=null) store.clearPending(pending.provider,pending.sessionId);
                postFailure(work.listener,"INVALID_SESSION"); return;
            }
            if (work.error!=null) {
                store.clearPending(pending.provider,pending.sessionId);
                postFailure(work.listener,allowedError(work.error)); return;
            }
            if (work.handoff==null || !work.handoff.matches("[A-Za-z0-9_-]{43}")) {
                store.clearPending(pending.provider,pending.sessionId);
                postFailure(work.listener,"INVALID_SESSION"); return;
            }
            store.clearPending(pending.provider,pending.sessionId);
            Response<OAuthExchangeResponse> response=api.exchange(pending.provider.toLowerCase(java.util.Locale.ROOT),
                    new OAuthHandoffExchangeRequest(work.sessionId,work.handoff,pending.verifier)).execute();
            if (!response.isSuccessful() || response.body()==null) throw new OAuthRequestException(errorCode(response));
            OAuthExchangeResponse result=response.body();
            validateExchange(pending.provider,pending.sessionId,result);
            store.saveConnected(result);
            main.execute(() -> work.listener.onConnected(result.displayName,result.externalAccountId));
        } catch (Exception e) {
            postFailure(work.listener,e instanceof OAuthRequestException ? ((OAuthRequestException)e).code : "PROVIDER_ERROR");
        }
    }

    private static final class CallbackWork {
        final String sessionId, handoff, error;
        final Listener listener;
        CallbackWork(String sessionId,String handoff,String error,Listener listener) {
            this.sessionId=sessionId; this.handoff=handoff; this.error=error; this.listener=listener;
        }
    }

    public String summary(String provider) { return store.summary(provider); }
    public boolean needsReauth(String provider) {
        var c=store.channel(provider);
        return c!=null && c.connectionStatus==com.socialpilot.app.domain.contract.social.ConnectionStatus.REAUTH_REQUIRED;
    }
    public static void validateExchange(String provider,String sessionId,OAuthExchangeResponse result){
        result.validate();
        if(!sessionId.equals(result.sessionId)||!provider.equals(result.provider)
            ||!("FACEBOOK".equals(provider)?"PAGE":"PERSON").equals(result.accountType))throw new IllegalArgumentException("Unexpected OAuth identity");
    }
    public static void validateAuthorizationUrl(String provider,String url) {
        URI u=URI.create(url);
        boolean allowed="FACEBOOK".equals(provider)
            ? "www.facebook.com".equals(u.getHost()) && u.getPath().endsWith("/dialog/oauth")
            : "LINKEDIN".equals(provider) && "www.linkedin.com".equals(u.getHost()) && "/oauth/v2/authorization".equals(u.getPath());
        if(!allowed || !"https".equals(u.getScheme()) || u.getUserInfo()!=null || u.getFragment()!=null || (u.getPort()!=-1&&u.getPort()!=443)) throw new IllegalArgumentException("Invalid authorization URL");
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

    private void postFailure(Listener listener, String code) { main.execute(() -> listener.onFailure(code)); }

    private static final class OAuthRequestException extends Exception {
        private final String code;
        private OAuthRequestException(String code) { this.code = code; }
    }
}
