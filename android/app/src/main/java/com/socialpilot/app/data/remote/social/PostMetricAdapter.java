package com.socialpilot.app.data.remote.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import static com.socialpilot.app.data.remote.social.FollowerResult.Source;
import static com.socialpilot.app.data.remote.social.PostResult.*;

/** Read-only, asynchronous post metric adapter. No capability mutation or persistence. */
public final class PostMetricAdapter {
    public interface ConnectionSource { Binding current(String channelId); }
    public interface CredentialSource { String accessToken(ChannelInfo channel); }
    public interface Clock { long nowMillis(); }
    public interface ResultCallback { void complete(PostResult result); }

    /** Caller supplies verified post ownership, operation capability and independent submetric
     * grants for capabilityScope's exact account/post/query/version/revision. No global grant.
     */
    public static final class Binding {
        public final ChannelInfo channel;
        public final PostRequest capabilityScope;
        public final ChannelCapability capability;
        public final PermissionEvidence permissionEvidence;
        public final Map<Metric, ChannelCapability> metricGrants;

        public Binding(ChannelInfo channel, PostRequest capabilityScope,
                ChannelCapability capability, PermissionEvidence evidence, Map<Metric, ChannelCapability> metricGrants) {
            this.channel = Objects.requireNonNull(channel, "channel");
            this.capabilityScope = Objects.requireNonNull(capabilityScope, "capabilityScope");
            this.capability = Objects.requireNonNull(capability, "capability");
            this.permissionEvidence = evidence;
            EnumMap<Metric, ChannelCapability> grants = new EnumMap<>(Metric.class);
            grants.putAll(Objects.requireNonNull(metricGrants, "metricGrants"));
            this.metricGrants = Collections.unmodifiableMap(grants);
        }
    }

    /** Explicit evidence for a provider error; never inferred from message text or HTTP 403. */
    public static final class PermissionEvidence {
        private final PostRequest scope;
        private final int providerCode;
        public final String evidenceRef;

        public PermissionEvidence(PostRequest scope, int providerCode, String evidenceRef) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.providerCode = providerCode;
            this.evidenceRef = PostRequest.reference(evidenceRef);
        }

        boolean matches(PostRequest request, int code) {
            return scope.sameTarget(request) && providerCode == code;
        }
    }

    private final OkHttpClient client;
    private final ConnectionSource connections;
    private final CredentialSource credentials;
    private final Clock clock;
    private final HttpUrl facebook;
    private final HttpUrl linkedin;
    private final Source source;
    private final String sourceRef;

    public PostMetricAdapter(OkHttpClient client, ConnectionSource connections,
            CredentialSource credentials, Clock clock, String sourceRef) {
        this(client, connections, credentials, clock, HttpUrl.get("https://graph.facebook.com/"),
                HttpUrl.get("https://api.linkedin.com/rest/"), Source.API_THAT, sourceRef);
    }

    /** Package-private endpoint/provenance injection for offline MockWebServer fixtures. */
    PostMetricAdapter(OkHttpClient client, ConnectionSource connections,
            CredentialSource credentials, Clock clock, HttpUrl facebook, HttpUrl linkedin,
            Source source, String sourceRef) {
        // Do not follow a redirect carrying Authorization to an unapproved endpoint.
        this.client = client.newBuilder().followRedirects(false).followSslRedirects(false).build();
        this.connections = Objects.requireNonNull(connections, "connections");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.facebook = Objects.requireNonNull(facebook, "facebook");
        this.linkedin = Objects.requireNonNull(linkedin, "linkedin");
        this.source = Objects.requireNonNull(source, "source");
        this.sourceRef = PostRequest.reference(sourceRef);
    }

    /** Callback runs in the background; cancel suppresses delivery and cancels HTTP. */
    public Cancelable read(PostRequest request, ResultCallback callback) {
        Objects.requireNonNull(request, "request");
        Pending pending = new Pending(Objects.requireNonNull(callback, "callback"));
        client.dispatcher().executorService().execute(() -> {
            try { start(request, pending); }
            catch (RuntimeException ignored) {
                // Connection/credential lookup failures are local, not provider evidence.
                pending.deliver(rejected(Failure.LOCAL_ERROR, null));
            }
        });
        return pending;
    }

    private void start(PostRequest request, Pending pending) {
        if (pending.stopped()) return;
        Binding binding = connections.current(request.channelId);
        PostResult blocked = admission(request, binding);
        if (blocked != null) { pending.deliver(blocked); return; }
        Map<Metric, MissingReason> blockedMetrics = blockedMetrics(request, binding);
        boolean needsWire = false;
        for (MetricDefinition definition : PostMetricMapping.definitions(request)) {
            if (definition.providerMetricName != null && !blockedMetrics.containsKey(definition.metricKey)) needsWire = true;
        }
        if (!needsWire) { pending.deliver(localMissing(request, blockedMetrics, clock.nowMillis())); return; }
        String token = credentials.accessToken(binding.channel);
        if (token == null || token.trim().isEmpty()) {
            pending.deliver(failed(request, MissingReason.AUTH_REQUIRED, Failure.PROVIDER_ERROR,
                    error(SocialError.Code.AUTH_REQUIRED, SocialError.NextAction.RECONNECT), null, clock.nowMillis()));
            return;
        }
        // Credential access can block; verify the connection again before sending.
        Binding current = connections.current(request.channelId);
        blocked = admission(request, current);
        if (blocked != null) { pending.deliver(blocked); return; }
        if (!binding.channel.tokenReference.equals(current.channel.tokenReference)) {
            pending.deliver(rejected(Failure.SCOPE_MISMATCH, null)); return;
        }
        blockedMetrics = blockedMetrics(request, current);
        needsWire = false;
        for (MetricDefinition definition : PostMetricMapping.definitions(request)) {
            if (definition.providerMetricName != null && !blockedMetrics.containsKey(definition.metricKey)) needsWire = true;
        }
        if (!needsWire) { pending.deliver(localMissing(request, blockedMetrics, clock.nowMillis())); return; }
        Request.Builder wire = new Request.Builder().url(url(request)).get()
                .header("Authorization", "Bearer " + token);
        if (request.platform == Platform.LINKEDIN) {
            wire.header("LinkedIn-Version", request.apiVersion).header("X-Restli-Protocol-Version", "2.0.0");
        }
        Call call = client.newCall(wire.build());
        if (!pending.attach(call)) return;
        call.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException exception) {
                PostResult result;
                try {
                    PostResult guard = responseGuard(request, binding);
                    result = guard != null ? guard : failed(request, MissingReason.NETWORK_ERROR,
                            Failure.NETWORK_ERROR, error(SocialError.Code.NETWORK_ERROR,
                                    SocialError.NextAction.RETRY_WHEN_SAFE), null, clock.nowMillis());
                } catch (RuntimeException ignored) { result = rejected(Failure.LOCAL_ERROR, null); }
                pending.deliver(result);
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                PostResult result;
                try (Response received = response) {
                    String body = received.body() == null ? "" : received.body().string();
                    long receivedAt = clock.nowMillis();
                    Binding receivedBinding = connections.current(request.channelId);
                    PostResult guard = responseGuard(request, binding, receivedBinding);
                    result = guard != null ? guard : normalize(request, receivedBinding, received.code(), body,
                            received.header("Retry-After"), receivedAt);
                } catch (IOException exception) {
                    onFailure(call, exception); return;
                } catch (RuntimeException ignored) { result = rejected(Failure.LOCAL_ERROR, null); }
                pending.deliver(result);
            }
        });
    }

    private HttpUrl url(PostRequest r) {
        if (r.platform == Platform.INSTAGRAM) {
            if (r.query != PostRequest.Query.IG_REACH) throw new IllegalArgumentException("Unverified Instagram query");
            return facebook.newBuilder().addPathSegment(r.apiVersion).addPathSegment(r.postId)
                    .addPathSegment("insights").addQueryParameter("metric", "reach").build();
        }
        if (r.platform == Platform.FACEBOOK) {
            HttpUrl.Builder url = facebook.newBuilder().addPathSegment(r.apiVersion).addPathSegment(r.postId);
            switch (r.query) {
                case FB_REACTIONS: case FB_LIKES:
                    url.addPathSegment("reactions").addQueryParameter("summary", "true").addQueryParameter("limit", "1");
                    if (r.query == PostRequest.Query.FB_LIKES) url.addQueryParameter("type", "LIKE");
                    break;
                case FB_COMMENTS_TOPLEVEL: case FB_COMMENTS_STREAM:
                    url.addPathSegment("comments").addQueryParameter("summary", "true").addQueryParameter("filter",
                            r.query == PostRequest.Query.FB_COMMENTS_TOPLEVEL ? "toplevel" : "stream").addQueryParameter("limit", "1");
                    break;
                case FB_SHARES: url.addQueryParameter("fields", "id,shares"); break;
                default: url.addPathSegment("insights").addQueryParameter("metric",
                        r.query == PostRequest.Query.FB_MEDIA_VIEWS ? "post_media_view" : "post_total_media_view_unique")
                        .addQueryParameter("period", "lifetime");
            }
            return url.build();
        }
        // Encode values inside Rest.li structures, preserving union/List syntax.
        String encodedPost = new HttpUrl.Builder().scheme("https").host("example.invalid")
                .addQueryParameter("v", r.postId).build().encodedQuery().substring(2);
        if (r.account == FollowerRequest.Account.LINKEDIN_MEMBER) {
            HttpUrl.Builder url = linkedin.newBuilder().addPathSegment("memberCreatorPostAnalytics")
                    .addQueryParameter("q", "entity").addEncodedQueryParameter("entity", "("
                            + (r.postId.startsWith("urn:li:share:") ? "share" : "ugc") + ":" + encodedPost + ")")
                    .addQueryParameter("queryType", PostMetricMapping.memberMetric(r)).addQueryParameter("aggregation", "TOTAL");
            if (r.period != null) url.addEncodedQueryParameter("dateRange", r.period.wire());
            return url.build();
        }
        return linkedin.newBuilder().addPathSegment("organizationalEntityShareStatistics")
                .addQueryParameter("q", "organizationalEntity").addQueryParameter("organizationalEntity", r.externalAccountId)
                .addEncodedQueryParameter(r.postId.startsWith("urn:li:share:") ? "shares" : "ugcPosts", "List(" + encodedPost + ")").build();
    }

    private PostResult responseGuard(PostRequest request, Binding sent) {
        return responseGuard(request, sent, connections.current(request.channelId));
    }

    private PostResult responseGuard(PostRequest request, Binding sent, Binding current) {
        PostResult blocked = admission(request, current);
        if (blocked != null) return blocked;
        return sent.channel.tokenReference.equals(current.channel.tokenReference)
                ? null : rejected(Failure.SCOPE_MISMATCH, null);
    }

    private PostResult admission(PostRequest r, Binding b) {
        if (b == null || !r.matches(b.channel)) return rejected(Failure.SCOPE_MISMATCH, null);
        if (b.channel.connectionStatus != ConnectionStatus.CONNECTED) {
            SocialError.Code code = b.channel.connectionStatus == ConnectionStatus.REAUTH_REQUIRED
                    ? SocialError.Code.AUTH_REQUIRED : SocialError.Code.CHANNEL_DISCONNECTED;
            return rejected(Failure.CHANNEL_DISCONNECTED, error(code, SocialError.NextAction.RECONNECT));
        }
        if (!r.sameTarget(b.capabilityScope)
                || b.capability.operation != SocialOperation.READ_POST_METRICS) {
            return rejected(Failure.CAPABILITY_BLOCKED, null);
        }
        switch (b.capability.availability) {
            case AVAILABLE: return null;
            case PERMISSION_REQUIRED:
                return failed(r, MissingReason.PERMISSION_REQUIRED, Failure.CAPABILITY_BLOCKED,
                        error(SocialError.Code.PERMISSION_REQUIRED, SocialError.NextAction.RESOLVE_PERMISSION), null, clock.nowMillis());
            case UNSUPPORTED:
                return failed(r, MissingReason.UNSUPPORTED, Failure.CAPABILITY_BLOCKED,
                        error(SocialError.Code.UNSUPPORTED_OPERATION, SocialError.NextAction.NONE), null, clock.nowMillis());
            default:
                return failed(r, MissingReason.UNVERIFIED, Failure.CAPABILITY_BLOCKED, null, null, clock.nowMillis());
        }
    }

    private PostResult normalize(PostRequest r, Binding binding, int status, String raw,
            String retryAfter, long receivedAt) {
        JsonObject body = null;
        try {
            JsonElement json = JsonParser.parseString(raw);
            if (json.isJsonObject()) body = json.getAsJsonObject();
        } catch (RuntimeException ignored) { /* Never retain a provider body or parser message. */ }
        int code = -1;
        boolean providerFailure = false;
        if (body != null) {
            providerFailure = body.has("error") || body.has("serviceErrorCode");
            try {
                if (r.platform != Platform.LINKEDIN && body.has("error")) {
                    JsonElement wireCode = body.getAsJsonObject("error").get("code");
                    if (wireCode.isJsonPrimitive() && wireCode.getAsJsonPrimitive().isNumber()) {
                        code = new BigDecimal(wireCode.getAsString()).intValueExact();
                    }
                } else if (body.has("status") && body.get("status").getAsInt() >= 400) {
                    providerFailure = true;
                }
            } catch (RuntimeException ignored) { providerFailure = true; }
        }
        if (status < 200 || status >= 300 || providerFailure) {
            MissingReason reason = MissingReason.UNVERIFIED;
            SocialError social = null;
            if (status == 401 || r.platform != Platform.LINKEDIN && code == 190) {
                reason = MissingReason.AUTH_REQUIRED;
                social = error(SocialError.Code.AUTH_REQUIRED, SocialError.NextAction.RECONNECT);
            } else if (status == 429 || r.platform != Platform.LINKEDIN
                    && (code == 4 || code == 17 || code == 32 || code == 613)) {
                reason = MissingReason.RATE_LIMITED;
                social = error(SocialError.Code.RATE_LIMITED, SocialError.NextAction.WAIT);
            } else if (status == 503) {
                reason = MissingReason.NETWORK_ERROR;
                social = error(SocialError.Code.NETWORK_ERROR,
                        retryAfter != null ? SocialError.NextAction.WAIT : SocialError.NextAction.RETRY_WHEN_SAFE);
            } else if (binding.permissionEvidence != null && binding.permissionEvidence.matches(r, code)) {
                reason = MissingReason.PERMISSION_REQUIRED;
                social = error(SocialError.Code.PERMISSION_REQUIRED, SocialError.NextAction.RESOLVE_PERMISSION);
            }
            boolean hasRetryHeader = reason == MissingReason.RATE_LIMITED || status == 503;
            return failed(r, reason, Failure.PROVIDER_ERROR, social,
                    hasRetryHeader ? retryAt(retryAfter, receivedAt) : null, receivedAt);
        }
        List<MetricDefinition> definitions = PostMetricMapping.definitions(r);
        PostMetricMapping.Parsed parsed = PostMetricMapping.parse(r, body, definitions,
                blockedMetrics(r, binding), receivedAt, source, sourceRef);
        if (parsed.rejected) return rejected(Failure.SCOPE_MISMATCH, null);
        int good = 0;
        for (MetricSample sample : parsed.samples) if (sample.value != null) good++;
        Status resultStatus = parsed.invalid && good == 0 ? Status.FAILURE
                : good == parsed.samples.size() ? Status.SUCCESS : Status.PARTIAL;
        return new PostResult(resultStatus, definitions, parsed.samples,
                parsed.invalid ? Failure.INVALID_RESPONSE : null, null, null);
    }

    private Map<Metric, MissingReason> blockedMetrics(PostRequest request, Binding binding) {
        Map<Metric, MissingReason> blocked = new EnumMap<>(Metric.class);
        for (MetricDefinition definition : PostMetricMapping.definitions(request)) {
            ChannelCapability grant = binding.metricGrants.get(definition.metricKey);
            MissingReason reason = null;
            if (grant == null || grant.operation != SocialOperation.READ_POST_METRICS) reason = MissingReason.UNVERIFIED;
            else switch (grant.availability) {
                case AVAILABLE: break;
                case PERMISSION_REQUIRED: reason = MissingReason.PERMISSION_REQUIRED; break;
                case UNSUPPORTED: reason = MissingReason.UNSUPPORTED; break;
                default: reason = MissingReason.UNVERIFIED;
            }
            if (reason != null) blocked.put(definition.metricKey, reason);
        }
        return blocked;
    }

    private PostResult localMissing(PostRequest r, Map<Metric, MissingReason> blocked, long at) {
        List<MetricDefinition> definitions = PostMetricMapping.definitions(r);
        List<MetricSample> samples = new ArrayList<>();
        for (MetricDefinition definition : definitions) samples.add(new MetricSample(r, definition, null,
                blocked.containsKey(definition.metricKey) ? blocked.get(definition.metricKey) : MissingReason.UNVERIFIED,
                at, source, sourceRef, false));
        return new PostResult(Status.UNAVAILABLE, definitions, samples, null, null, null);
    }

    private PostResult failed(PostRequest r, MissingReason reason, Failure failure,
            SocialError error, Long retryAt, long at) {
        List<MetricDefinition> definitions = PostMetricMapping.definitions(r);
        List<MetricSample> samples = new ArrayList<>();
        for (MetricDefinition definition : definitions) samples.add(new MetricSample(r, definition, null,
                reason, at, source, sourceRef, false));
        return new PostResult(Status.FAILURE, definitions, samples, failure, error, retryAt);
    }

    private static PostResult rejected(Failure reason, SocialError error) {
        return new PostResult(Status.REJECTED, Collections.emptyList(), Collections.emptyList(), reason, error, null);
    }

    private static SocialError error(SocialError.Code code, SocialError.NextAction action) {
        return new SocialError(code, action);
    }

    private static Long retryAt(String header, long receivedAt) {
        if (header == null) return null;
        try {
            long seconds = Long.parseLong(header);
            return seconds < 0 ? null : Math.addExact(receivedAt, Math.multiplyExact(seconds, 1000));
        } catch (NumberFormatException ignored) {
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
            format.setLenient(false);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            try { return format.parse(header).getTime(); }
            catch (ParseException ignoredDate) { return null; }
        } catch (ArithmeticException ignored) { return null; }
    }

    private static final class Pending implements Cancelable {
        private final ResultCallback callback;
        private Call call;
        private boolean stopped;
        Pending(ResultCallback callback) { this.callback = callback; }
        synchronized boolean stopped() { return stopped; }
        synchronized boolean attach(Call call) {
            if (stopped) { call.cancel(); return false; }
            this.call = call;
            return true;
        }
        synchronized void deliver(PostResult result) {
            if (stopped) return;
            stopped = true;
            callback.complete(result);
        }
        @Override public synchronized void cancel() {
            stopped = true;
            if (call != null) call.cancel();
        }
    }
}
