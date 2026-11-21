package com.socialpilot.app.data.remote.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.*;
import java.io.IOException;
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
import static com.socialpilot.app.data.remote.social.FollowerResult.*;

/** Read-only, asynchronous follower adapter. No capability mutation or persistence. */
public final class FollowerMetricAdapter {
    public interface ConnectionSource { Binding current(String channelId); }
    public interface CredentialSource { String accessToken(ChannelInfo channel); }
    public interface Clock { long nowMillis(); }
    public interface ResultCallback { void complete(FollowerResult result); }

    /** Caller must load capability for this exact channel/account/revision, not a global grant. */
    public static final class Binding {
        public final ChannelInfo channel;
        public final FollowerRequest capabilityScope;
        public final ChannelCapability capability;
        public final PermissionEvidence permissionEvidence;

        public Binding(ChannelInfo channel, FollowerRequest capabilityScope,
                ChannelCapability capability, PermissionEvidence evidence) {
            this.channel = Objects.requireNonNull(channel, "channel");
            this.capabilityScope = Objects.requireNonNull(capabilityScope, "capabilityScope");
            this.capability = Objects.requireNonNull(capability, "capability");
            this.permissionEvidence = evidence;
        }
    }

    /** Explicit evidence for a provider error; never inferred from message text or HTTP 403. */
    public static final class PermissionEvidence {
        private final FollowerRequest scope;
        private final int providerCode;
        public final String evidenceRef;

        public PermissionEvidence(FollowerRequest scope, int providerCode, String evidenceRef) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.providerCode = providerCode;
            this.evidenceRef = reference(evidenceRef);
        }

        boolean matches(FollowerRequest request, int code) {
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

    public FollowerMetricAdapter(OkHttpClient client, ConnectionSource connections,
            CredentialSource credentials, Clock clock, String sourceRef) {
        this(client, connections, credentials, clock, HttpUrl.get("https://graph.facebook.com/"),
                HttpUrl.get("https://api.linkedin.com/rest/"), Source.API_THAT, sourceRef);
    }

    /** Package-private endpoint/provenance injection for offline MockWebServer fixtures. */
    FollowerMetricAdapter(OkHttpClient client, ConnectionSource connections,
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
        this.sourceRef = reference(sourceRef);
    }

    /** Callback runs in the background; cancel suppresses delivery and cancels HTTP. */
    public Cancelable read(FollowerRequest request, ResultCallback callback) {
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

    private void start(FollowerRequest request, Pending pending) {
        if (pending.stopped()) return;
        Binding binding = connections.current(request.channelId);
        FollowerResult blocked = admission(request, binding);
        if (blocked != null) { pending.deliver(blocked); return; }
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
        Request.Builder wire = new Request.Builder().url(url(request)).get()
                .header("Authorization", "Bearer " + token);
        if (request.platform == Platform.LINKEDIN) {
            wire.header("LinkedIn-Version", request.apiVersion).header("X-Restli-Protocol-Version", "2.0.0");
        }
        Call call = client.newCall(wire.build());
        if (!pending.attach(call)) return;
        call.enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException exception) {
                FollowerResult guard = responseGuard(request, binding);
                pending.deliver(guard != null ? guard : failed(request, MissingReason.NETWORK_ERROR,
                        Failure.NETWORK_ERROR, error(SocialError.Code.NETWORK_ERROR,
                                SocialError.NextAction.RETRY_WHEN_SAFE), null, clock.nowMillis()));
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                FollowerResult result;
                try (Response received = response) {
                    String body = received.body() == null ? "" : received.body().string();
                    long receivedAt = clock.nowMillis();
                    FollowerResult guard = responseGuard(request, binding);
                    result = guard != null ? guard : normalize(request, binding, received.code(), body,
                            received.header("Retry-After"), receivedAt);
                } catch (IOException exception) {
                    FollowerResult guard = responseGuard(request, binding);
                    result = guard != null ? guard : failed(request, MissingReason.NETWORK_ERROR,
                            Failure.NETWORK_ERROR, error(SocialError.Code.NETWORK_ERROR,
                                    SocialError.NextAction.RETRY_WHEN_SAFE), null, clock.nowMillis());
                }
                pending.deliver(result);
            }
        });
    }

    private HttpUrl url(FollowerRequest r) {
        if (r.platform != Platform.LINKEDIN) {
            return facebook.newBuilder().addPathSegment(r.apiVersion).addPathSegment(r.externalAccountId)
                    .addQueryParameter("fields", "id,followers_count").build();
        }
        if (r.account == FollowerRequest.Account.LINKEDIN_MEMBER) {
            return linkedin.newBuilder().addPathSegment("memberFollowersCount").addQueryParameter("q", "me").build();
        }
        return linkedin.newBuilder().addPathSegment("networkSizes").addPathSegment(r.externalAccountId)
                .addQueryParameter("edgeType", "COMPANY_FOLLOWED_BY_MEMBER").build();
    }

    private FollowerResult responseGuard(FollowerRequest request, Binding sent) {
        Binding current = connections.current(request.channelId);
        FollowerResult blocked = admission(request, current);
        if (blocked != null) return blocked;
        return sent.channel.tokenReference.equals(current.channel.tokenReference)
                ? null : rejected(Failure.SCOPE_MISMATCH, null);
    }

    private FollowerResult admission(FollowerRequest r, Binding b) {
        if (b == null || !r.matches(b.channel)) return rejected(Failure.SCOPE_MISMATCH, null);
        if (b.channel.connectionStatus != ConnectionStatus.CONNECTED) {
            SocialError.Code code = b.channel.connectionStatus == ConnectionStatus.REAUTH_REQUIRED
                    ? SocialError.Code.AUTH_REQUIRED : SocialError.Code.CHANNEL_DISCONNECTED;
            return rejected(Failure.CHANNEL_DISCONNECTED, error(code, SocialError.NextAction.RECONNECT));
        }
        if (!r.sameTarget(b.capabilityScope)
                || b.capability.operation != SocialOperation.READ_FOLLOWER_COUNT) {
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

    private FollowerResult normalize(FollowerRequest r, Binding binding, int status, String raw,
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
                    code = body.getAsJsonObject("error").get("code").getAsInt();
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
            } else if (binding.permissionEvidence != null && binding.permissionEvidence.matches(r, code)) {
                reason = MissingReason.PERMISSION_REQUIRED;
                social = error(SocialError.Code.PERMISSION_REQUIRED, SocialError.NextAction.RESOLVE_PERMISSION);
            }
            return failed(r, reason, Failure.PROVIDER_ERROR, social,
                    reason == MissingReason.RATE_LIMITED ? retryAt(retryAfter, receivedAt) : null, receivedAt);
        }
        try {
            if (body == null) throw new IllegalArgumentException();
            JsonElement value;
            switch (r.account) {
                case INSTAGRAM_PROFESSIONAL:
                case FACEBOOK_PAGE:
                    if (!body.has("id") || !body.get("id").isJsonPrimitive()
                            || !body.get("id").getAsJsonPrimitive().isString()
                            || !r.externalAccountId.equals(body.get("id").getAsString())) {
                        return rejected(Failure.SCOPE_MISMATCH, null);
                    }
                    value = body.get("followers_count"); break;
                case LINKEDIN_MEMBER:
                    if (!body.has("elements") || !body.get("elements").isJsonArray()
                            || body.getAsJsonArray("elements").size() > 1) throw new IllegalArgumentException();
                    if (body.getAsJsonArray("elements").size() == 0) value = null;
                    else {
                        JsonObject row = body.getAsJsonArray("elements").get(0).getAsJsonObject();
                        if (row.has("dateRange")) throw new IllegalArgumentException();
                        value = row.get("memberFollowersCount");
                    }
                    break;
                default:
                    value = body.get("firstDegreeSize");
            }
            Long count = null;
            if (value != null && !value.isJsonNull()) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
                count = new BigDecimal(value.getAsString()).longValueExact();
                if (count < 0) throw new IllegalArgumentException();
            }
            return sample(r, Status.SUCCESS, count, count == null ? MissingReason.PROVIDER_OMITTED : null,
                    null, null, null, receivedAt);
        } catch (RuntimeException ignored) {
            return failed(r, MissingReason.UNVERIFIED, Failure.INVALID_RESPONSE, null, null, receivedAt);
        }
    }

    private FollowerResult failed(FollowerRequest r, MissingReason reason, Failure failure,
            SocialError error, Long retryAt, long at) {
        return sample(r, Status.FAILURE, null, reason, failure, error, retryAt, at);
    }

    private FollowerResult sample(FollowerRequest r, Status status, Long value, MissingReason reason,
            Failure failure, SocialError error, Long retryAt, long at) {
        MetricDefinition definition = new MetricDefinition(r);
        return new FollowerResult(status, definition,
                new MetricSample(r, definition, value, reason, at, source, sourceRef), failure, error, retryAt);
    }

    private static FollowerResult rejected(Failure reason, SocialError error) {
        return new FollowerResult(Status.REJECTED, null, null, reason, error, null);
    }

    private static SocialError error(SocialError.Code code, SocialError.NextAction action) {
        return new SocialError(code, action);
    }

    private static String reference(String value) {
        FollowerRequest.text(value);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}")) {
            throw new IllegalArgumentException("Use a redacted evidence label");
        }
        return value;
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
        synchronized void deliver(FollowerResult result) {
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
