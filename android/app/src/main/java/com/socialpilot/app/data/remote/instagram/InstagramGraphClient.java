package com.socialpilot.app.data.remote.instagram;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import java.io.IOException;
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
    private final OkHttpClient http;

    public InstagramGraphClient() {
        this(new OkHttpClient());
    }

    InstagramGraphClient(OkHttpClient client) {
        http = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build();
    }

    public String createContainer(OAuthExchangeResponse connection, String imageUrl, String caption) throws IOException {
        return id(call(connection.accessToken, url(connection, connection.externalAccountId).addPathSegment("media").build(),
                new FormBody.Builder().add("image_url", imageUrl).add("caption", caption).build()));
    }

    public String publishContainer(OAuthExchangeResponse connection, String containerId) throws IOException {
        return id(call(connection.accessToken, url(connection, connection.externalAccountId).addPathSegment("media_publish").build(),
                new FormBody.Builder().add("creation_id", containerId).build()));
    }

    public String containerStatus(OAuthExchangeResponse connection, String containerId) throws IOException {
        JsonObject data = call(connection.accessToken,
                url(connection, containerId).addQueryParameter("fields", "status_code").build(), null);
        return data.has("status_code") ? data.get("status_code").getAsString() : "";
    }

    public void validateConnection(OAuthExchangeResponse connection) throws IOException {
        String accountId = connection.externalAccountId;
        JsonObject data = call(connection.accessToken, url(connection, accountId).addQueryParameter("fields", "id").build(), null);
        if (!accountId.equals(id(data))) throw new IOException("Unexpected Instagram account");
    }

    public String permalink(OAuthExchangeResponse connection, String mediaId) throws IOException {
        JsonObject data = call(connection.accessToken,
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
        return new HttpUrl.Builder().scheme("https").host("graph.facebook.com")
                .addPathSegment(connection.graphApiVersion).addPathSegment(id);
    }

    private JsonObject call(String token, HttpUrl url, RequestBody body) throws IOException {
        Request.Builder request = new Request.Builder().url(url).header("Authorization", "Bearer " + token);
        if (body != null) {
            request.post(new RequestBody() {
                @Override public MediaType contentType() { return body.contentType(); }
                @Override public long contentLength() throws IOException { return body.contentLength(); }
                @Override public boolean isOneShot() { return true; }
                @Override public void writeTo(BufferedSink sink) throws IOException { body.writeTo(sink); }
            });
        }
        try (Response response = http.newCall(request.build()).execute()) {
            if (response.code() == 401) throw new AuthorizationExpired();
            if (response.body() == null) throw new IOException("Missing Graph response");
            JsonObject data = JsonParser.parseString(response.body().string()).getAsJsonObject();
            if (data.has("error")) {
                JsonObject error = data.getAsJsonObject("error");
                if (error.has("code") && error.get("code").getAsInt() == 190) throw new AuthorizationExpired();
                throw new IOException("Instagram operation failed");
            }
            if (!response.isSuccessful()) throw new IOException("Instagram operation failed");
            return data;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid Graph response");
        }
    }

    private static String id(JsonObject data) throws IOException {
        String id = data.has("id") && data.get("id").isJsonPrimitive() ? data.get("id").getAsString() : "";
        if (!id.matches("[0-9]{5,30}")) throw new IOException("Missing Instagram ID");
        return id;
    }

    public static final class AuthorizationExpired extends IOException {}
}
