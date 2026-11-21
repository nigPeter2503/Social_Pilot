package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.*;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.nio.charset.StandardCharsets;
import okhttp3.*;
import static com.socialpilot.app.data.remote.social.CommentPageResult.Failure;

/** Shared lifecycle only; provider mapping and continuation rules remain separate. */
public abstract class CommentAdapter {
    public interface ConnectionSource { Binding current(String channelId); }
    /** Resolve only this exact immutable channel subject/revision/token reference. */
    public interface CredentialSource { String accessToken(ChannelInfo channel); }
    public interface Clock { long nowMillis(); }
    public interface ResultCallback { void complete(CommentPageResult result); }

    /** Explicit external evidence, never inferred from absent response fields. */
    public static final class ItemEvidence {
        public final boolean root;
        public final boolean deleted;
        public final String evidenceRef;
        public ItemEvidence(boolean root, boolean deleted, String evidenceRef) {
            this.root = root; this.deleted = deleted;
            this.evidenceRef = CommentPageRequest.reference(evidenceRef);
        }
    }

    /** Provider error code classification needs evidence for this exact query/version/subject. */
    public static final class PermissionEvidence {
        final CommentPageRequest scope;
        final int providerCode;
        public final String evidenceRef;
        public PermissionEvidence(CommentPageRequest scope, int providerCode, String evidenceRef) {
            this.scope = Objects.requireNonNull(scope, "scope"); this.providerCode = providerCode;
            this.evidenceRef = CommentPageRequest.reference(evidenceRef);
        }
        boolean matches(CommentPageRequest request, Integer code) {
            return scope.sameQuery(request) && code != null && code == providerCode;
        }
    }

    /** Snapshot from current Channel/capability lookup. Change bindingId on binding/evidence changes.
     * Scope asserts verified post ownership and endpoint target relation, not ID parsing.
     * maxPageSize must be established for this account/query/version (fixture limit in tests).
     */
    public static final class Binding {
        public final ChannelInfo channel;
        public final CommentPageRequest scope;
        public final ChannelCapability readCapability;
        public final ChannelCapability replyCapability;
        public final String bindingId;
        public final String postThreadEvidenceRef;
        public final int maxPageSize;
        public final Map<String, ItemEvidence> itemEvidence;
        public final PermissionEvidence permissionEvidence;

        public Binding(ChannelInfo channel, CommentPageRequest scope, ChannelCapability read,
                ChannelCapability reply, String bindingId, String postThreadEvidenceRef,
                int maxPageSize, Map<String, ItemEvidence> itemEvidence) {
            this(channel, scope, read, reply, bindingId, postThreadEvidenceRef, maxPageSize, itemEvidence, null);
        }

        public Binding(ChannelInfo channel, CommentPageRequest scope, ChannelCapability read,
                ChannelCapability reply, String bindingId, String postThreadEvidenceRef,
                int maxPageSize, Map<String, ItemEvidence> itemEvidence, PermissionEvidence permissionEvidence) {
            this.channel = Objects.requireNonNull(channel, "channel");
            this.scope = Objects.requireNonNull(scope, "scope");
            readCapability = Objects.requireNonNull(read, "read");
            replyCapability = Objects.requireNonNull(reply, "reply");
            this.bindingId = CommentPageRequest.reference(bindingId);
            this.postThreadEvidenceRef = postThreadEvidenceRef == null ? null
                    : CommentPageRequest.reference(postThreadEvidenceRef);
            if (maxPageSize < 1) CommentPageRequest.invalid();
            this.maxPageSize = maxPageSize;
            Map<String, ItemEvidence> copy = new HashMap<>();
            if (itemEvidence != null) {
                for (Map.Entry<String, ItemEvidence> entry : itemEvidence.entrySet()) {
                    copy.put(CommentPageRequest.text(entry.getKey()), Objects.requireNonNull(entry.getValue(), "fact"));
                }
            }
            this.itemEvidence = Collections.unmodifiableMap(copy);
            this.permissionEvidence = permissionEvidence;
        }
    }

    private final OkHttpClient client;
    private final ConnectionSource connections;
    private final CredentialSource credentials;
    private final Clock clock;
    private final Executor executor;
    private final HttpUrl endpoint;
    private final Platform platform;
    private final CommentCursorCodec cursors;
    private final CommentRecord.Source source;
    private final String sourceRef;

    CommentAdapter(Platform platform, OkHttpClient client, ConnectionSource connections,
            CredentialSource credentials, Clock clock, Executor backgroundExecutor, HttpUrl endpoint,
            CommentCursorCodec cursors, CommentRecord.Source source, String sourceRef) {
        this.platform = platform;
        this.client = client.newBuilder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).build();
        this.connections = Objects.requireNonNull(connections, "connections");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.clock = Objects.requireNonNull(clock, "clock");
        // An injected executor must execute off the Android main thread.
        executor = backgroundExecutor == null ? client.dispatcher().executorService() : backgroundExecutor;
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
        this.source = Objects.requireNonNull(source, "source");
        this.sourceRef = CommentPageRequest.reference(sourceRef);
    }

    public final Cancelable read(CommentPageRequest request, ResultCallback callback) {
        Objects.requireNonNull(request, "request");
        Pending pending = new Pending(Objects.requireNonNull(callback, "callback"));
        executor.execute(() -> run(request, pending));
        return pending;
    }

    private void run(CommentPageRequest r, Pending p) {
        if (p.stopped()) return;
        try {
            Binding sent = connections.current(r.channelId);
            CommentPageResult denied = admission(r, sent);
            if (denied != null) { p.deliver(denied); return; }
            String continuation;
            try {
                continuation = cursors.decode(r);
                if (r.platform == Platform.LINKEDIN && continuation != null
                        && !continuation.matches("[1-9][0-9]{0,9}")) CommentPageRequest.invalid();
                if (r.platform == Platform.LINKEDIN && continuation != null
                        && Long.parseLong(continuation) > Integer.MAX_VALUE - r.pageSize) CommentPageRequest.invalid();
            } catch (RuntimeException ignored) { p.deliver(fail(Failure.INVALID_CURSOR)); return; }
            if (p.stopped()) return;
            String token = credentials.accessToken(sent.channel);
            denied = guard(r, sent);
            if (denied != null) { p.deliver(denied); return; }
            if (p.stopped()) return;
            if (token == null || token.trim().isEmpty()) {
                p.deliver(fail(Failure.PROVIDER_ERROR, SocialError.Code.AUTH_REQUIRED, SocialError.NextAction.RECONNECT)); return;
            }
            Request.Builder wire = new Request.Builder().url(url(r, continuation)).get()
                    .header("Authorization", "Bearer " + token);
            if (r.platform == Platform.LINKEDIN) wire.header("LinkedIn-Version", r.apiVersion)
                    .header("X-Restli-Protocol-Version", "2.0.0");
            Call call = client.newCall(wire.build());
            if (!p.attach(call)) return;
            CommentPageResult result;
            try (Response response = call.execute()) {
                String body = response.body() == null ? "" : response.body().string();
                long at = clock.nowMillis();
                result = CommentResponseMapper.map(r, sent, response.code(), body,
                        response.header("Retry-After"), at, continuation, cursors, source, sourceRef, token);
            } catch (IOException ignored) {
                result = fail(Failure.NETWORK_ERROR, SocialError.Code.NETWORK_ERROR, SocialError.NextAction.RETRY_WHEN_SAFE);
            }
            // Check after body read AND mapping, immediately before terminal delivery.
            denied = guard(r, sent);
            p.deliver(denied == null ? result : denied);
        } catch (RuntimeException ignored) {
            // Never retain exception text, URL, credential or provider body.
            p.deliver(fail(Failure.LOCAL_ERROR));
        }
    }

    private HttpUrl url(CommentPageRequest r, String continuation) {
        HttpUrl.Builder url = endpoint.newBuilder();
        if (platform == Platform.INSTAGRAM) {
            String edge = r.target == CommentPageRequest.Target.POST ? "comments" : "replies";
            url.addPathSegment(r.apiVersion).addPathSegment(r.targetId).addPathSegment(edge)
                    .addQueryParameter("fields", "id,text,timestamp,username")
                    .addQueryParameter("limit", String.valueOf(r.pageSize));
            if (continuation != null) url.addQueryParameter("after", continuation);
        } else if (platform == Platform.FACEBOOK) {
            url.addPathSegment(r.apiVersion).addPathSegment(r.targetId).addPathSegment("comments")
                    .addQueryParameter("fields", "id,message,created_time,from,parent")
                    .addQueryParameter("filter", r.query == CommentPageRequest.Query.FB_TOPLEVEL ? "toplevel" : "stream")
                    .addQueryParameter("limit", String.valueOf(r.pageSize));
            if (continuation != null) url.addQueryParameter("after", continuation);
        } else {
            url.addPathSegment("socialActions").addEncodedPathSegment(restliPathId(r.targetId)).addPathSegment("comments")
                    .addQueryParameter("start", continuation == null ? "0" : continuation)
                    .addQueryParameter("count", String.valueOf(r.pageSize));
        }
        return url.build();
    }

    private static String restliPathId(String value) {
        StringBuilder encoded = new StringBuilder();
        char[] hex = "0123456789ABCDEF".toCharArray();
        for (byte part : value.getBytes(StandardCharsets.UTF_8)) {
            int c = part & 255;
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c == '-' || c == '.' || c == '_' || c == '~') encoded.append((char) c);
            else encoded.append('%').append(hex[c >>> 4]).append(hex[c & 15]);
        }
        return encoded.toString();
    }

    private CommentPageResult admission(CommentPageRequest r, Binding b) {
        if (r.platform != platform || b == null || !r.matches(b.channel) || !r.sameQuery(b.scope)) return fail(Failure.SCOPE_MISMATCH);
        if (b.channel.connectionStatus != ConnectionStatus.CONNECTED) {
            return fail(Failure.CHANNEL_DISCONNECTED,
                    b.channel.connectionStatus == ConnectionStatus.REAUTH_REQUIRED ? SocialError.Code.AUTH_REQUIRED
                            : SocialError.Code.CHANNEL_DISCONNECTED, SocialError.NextAction.RECONNECT);
        }
        if (b.readCapability.operation != SocialOperation.READ_COMMENTS
                || b.replyCapability.operation != SocialOperation.REPLY_COMMENT) return fail(Failure.SCOPE_MISMATCH);
        CapabilityAvailability availability = b.readCapability.availability;
        if (availability != CapabilityAvailability.AVAILABLE) {
            SocialError error = availability == CapabilityAvailability.PERMISSION_REQUIRED
                    ? new SocialError(SocialError.Code.PERMISSION_REQUIRED, SocialError.NextAction.RESOLVE_PERMISSION)
                    : availability == CapabilityAvailability.UNSUPPORTED
                    ? new SocialError(SocialError.Code.UNSUPPORTED_OPERATION, SocialError.NextAction.NONE) : null;
            return CommentPageResult.failure(Failure.CAPABILITY_BLOCKED, error, availability, null);
        }
        if (r.platform != Platform.FACEBOOK && b.postThreadEvidenceRef == null) return fail(Failure.MAPPING_UNVERIFIED);
        if (r.pageSize > b.maxPageSize) return fail(Failure.SCOPE_MISMATCH);
        return null;
    }

    private CommentPageResult guard(CommentPageRequest r, Binding sent) {
        Binding current = connections.current(r.channelId);
        CommentPageResult denied = admission(r, current);
        if (denied != null) return denied;
        if (!Objects.equals(sent.channel.tokenReference, current.channel.tokenReference)
                || !sent.bindingId.equals(current.bindingId)
                || !Objects.equals(sent.postThreadEvidenceRef, current.postThreadEvidenceRef)
                || sent.maxPageSize != current.maxPageSize
                || !sameCapability(sent.readCapability, current.readCapability)
                || !sameCapability(sent.replyCapability, current.replyCapability)
                || sent.permissionEvidence != current.permissionEvidence
                || !sent.itemEvidence.equals(current.itemEvidence)) return fail(Failure.SCOPE_MISMATCH);
        return null;
    }

    private static boolean sameCapability(ChannelCapability a, ChannelCapability b) {
        return a.operation == b.operation && a.availability == b.availability
                && Objects.equals(a.reasonCode, b.reasonCode) && Objects.equals(a.verifiedAt, b.verifiedAt);
    }
    static CommentPageResult fail(Failure failure) { return CommentPageResult.failure(failure, null, null, null); }
    static CommentPageResult fail(Failure failure, SocialError.Code code, SocialError.NextAction action) {
        return CommentPageResult.failure(failure, new SocialError(code, action), null, null);
    }

    private static final class Pending implements Cancelable {
        private final ResultCallback callback;
        private Call call;
        private boolean stopped;
        Pending(ResultCallback callback) { this.callback = callback; }
        synchronized boolean stopped() { return stopped; }
        synchronized boolean attach(Call call) {
            if (stopped) { call.cancel(); return false; }
            this.call = call; return true;
        }
        synchronized void deliver(CommentPageResult result) {
            if (stopped) return;
            stopped = true; callback.complete(result);
        }
        @Override public synchronized void cancel() {
            stopped = true;
            if (call != null) call.cancel();
        }
    }
}
