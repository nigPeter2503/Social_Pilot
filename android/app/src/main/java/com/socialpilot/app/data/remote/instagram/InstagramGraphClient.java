package com.socialpilot.app.data.remote.instagram;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/** Graph requests for the Instagram feed spike. Provider payloads and credentials stay out of logs. */
public final class InstagramGraphClient {
    private static final String DEFAULT_PAGE_GRAPH_VERSION = "v27.0";
    private final OkHttpClient http;
    private final GrantCheck grants;
    private final Set<Call> inFlight = new HashSet<>();

    public interface GrantCheck {
        boolean current(OAuthExchangeResponse connection);
    }

    public InstagramGraphClient() {
        this(new OkHttpClient());
    }

    InstagramGraphClient(OkHttpClient client) {
        this(client, connection -> true);
    }

    public InstagramGraphClient(GrantCheck grants) {
        this(new OkHttpClient(), grants);
    }

    private InstagramGraphClient(OkHttpClient client, GrantCheck grants) {
        this.grants = grants;
        http = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
    }

    public String createContainer(OAuthExchangeResponse connection, String imageUrl, String caption) throws IOException {
        return id(call(connection, url(connection, connection.externalAccountId).addPathSegment("media").build(),
                new FormBody.Builder().add("image_url", imageUrl).add("caption", caption).build()));
    }

    public String publishContainer(OAuthExchangeResponse connection, String containerId) throws IOException {
        return id(call(connection, url(connection, connection.externalAccountId).addPathSegment("media_publish").build(),
                new FormBody.Builder().add("creation_id", containerId).build()));
    }

    public String containerStatus(OAuthExchangeResponse connection, String containerId) throws IOException {
        JsonObject data = call(connection,
                url(connection, containerId).addQueryParameter("fields", "status_code").build(), null);
        return data.has("status_code") ? data.get("status_code").getAsString() : "";
    }

    public void validateConnection(OAuthExchangeResponse connection) throws IOException {
        validateIdentity(connection, connection.graphApiVersion);
    }

    public void validatePageConnection(OAuthExchangeResponse connection) throws IOException {
        if (!"FACEBOOK".equals(connection.provider) || !"PAGE".equals(connection.accountType)) {
            throw new IllegalArgumentException("Facebook Page credential required");
        }
        validateIdentity(connection, connection.graphApiVersion == null
                ? DEFAULT_PAGE_GRAPH_VERSION : connection.graphApiVersion);
    }

    private void validateIdentity(OAuthExchangeResponse connection, String version) throws IOException {
        String accountId = connection.externalAccountId;
        HttpUrl identity = graphUrl(version, accountId).addQueryParameter("fields", "id").build();
        JsonObject data = call(connection, identity, null);
        if (!accountId.equals(id(data))) throw new IOException("Unexpected Graph account");
    }

    public String permalink(OAuthExchangeResponse connection, String mediaId) throws IOException {
        JsonObject data = call(connection,
                url(connection, mediaId).addQueryParameter("fields", "id,permalink").build(), null);
        if (!mediaId.equals(id(data)) || !data.has("permalink")) throw new IOException("Missing Instagram permalink");
        HttpUrl link = HttpUrl.parse(data.get("permalink").getAsString());
        if (link == null || !link.isHttps() || link.port() != 443
                || (!"www.instagram.com".equals(link.host()) && !"instagram.com".equals(link.host()))
                || !link.username().isEmpty() || !link.password().isEmpty()
                || !link.encodedPath().matches("/p/[A-Za-z0-9_-]+/?")) {
            throw new IOException("Unexpected Instagram permalink");
        }
        return link.newBuilder().query(null).fragment(null).build().toString();
    }

    private static HttpUrl.Builder url(OAuthExchangeResponse connection, String id) {
        return graphUrl(connection.graphApiVersion, id);
    }

    private static HttpUrl.Builder graphUrl(String version, String id) {
        return new HttpUrl.Builder().scheme("https").host("graph.facebook.com")
                .addPathSegment(version).addPathSegment(id);
    }

    public void cancelAccount(String provider, String accountId) {
        String account = provider + ":" + accountId;
        synchronized (inFlight) {
            for (Call running : inFlight) {
                if (account.equals(running.request().tag(String.class))) running.cancel();
            }
        }
    }

    private JsonObject call(OAuthExchangeResponse connection, HttpUrl url, RequestBody body) throws IOException {
        Request.Builder request = new Request.Builder().url(url)
                .tag(String.class, connection.provider + ":" + connection.externalAccountId)
                .header("Authorization", "Bearer " + connection.accessToken);
        if (body != null) {
            request.post(new RequestBody() {
                @Override public MediaType contentType() { return body.contentType(); }
                @Override public long contentLength() throws IOException { return body.contentLength(); }
                @Override public boolean isOneShot() { return true; }
                @Override public void writeTo(BufferedSink sink) throws IOException { body.writeTo(sink); }
            });
        }
        Call call;
        synchronized (inFlight) {
            if (!grants.current(connection)) throw new IOException("Stale Graph connection");
            call = http.newCall(request.build());
            inFlight.add(call);
        }
        try (Response response = call.execute()) {
            if (response.code() == 401) throw new AuthorizationExpired();
            if (response.body() == null) throw new IOException("Missing Graph response");
            JsonObject data = JsonParser.parseString(response.body().string()).getAsJsonObject();
            if (data.has("error")) {
                JsonObject error = data.getAsJsonObject("error");
                int code = error.has("code") ? error.get("code").getAsInt() : 0;
                if (code == 190) throw new AuthorizationExpired();
                if (code == 10 || (code >= 200 && code <= 299)) throw new PermissionDenied();
                throw new IOException("Graph operation failed");
            }
            if (!response.isSuccessful()) throw new IOException("Graph operation failed");
            return data;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid Graph response");
        } finally {
            synchronized (inFlight) {
                inFlight.remove(call);
            }
        }
    }

    private static String id(JsonObject data) throws IOException {
        String id = data.has("id") && data.get("id").isJsonPrimitive() ? data.get("id").getAsString() : "";
        if (!id.matches("[0-9]{5,30}")) throw new IOException("Missing Graph ID");
        return id;
    }

    public static final class AuthorizationExpired extends IOException {}
    public static final class PermissionDenied extends IOException {}
}
