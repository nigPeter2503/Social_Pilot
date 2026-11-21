package com.socialpilot.app.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.socialpilot.app.data.remote.instagram.InstagramGraphClient;
import com.socialpilot.app.data.remote.instagram.InstagramGraphClient.AuthorizationExpired;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.editor.ContentVersion;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Stores one feed test attempt per connected Instagram account, including its reconciliation result. */
public final class InstagramPublishRepository {
    public enum Status { READY, PUBLISHED, CHECK_REQUIRED, PUBLISHED_ID_UNKNOWN, PROVEN_UNPUBLISHED,
        RECONNECT, CANCELLED, ERROR }

    public static final class Result {
        public final Status status;
        public final String mediaId;
        public final String permalink;

        private Result(Status status, String mediaId, String permalink) {
            this.status = status;
            this.mediaId = mediaId;
            this.permalink = permalink;
        }
    }

    private final EncryptedOAuthStore connections;
    private final SharedPreferences attempts;
    private final InstagramGraphClient graph;
    private final Gson gson = new Gson();

    public InstagramPublishRepository(Context context, EncryptedOAuthStore connections) {
        this(context, connections, new InstagramGraphClient());
    }

    InstagramPublishRepository(Context context, EncryptedOAuthStore connections, InstagramGraphClient graph) {
        this.connections = connections;
        this.graph = graph;
        attempts = context.getApplicationContext().getSharedPreferences("instagram_feed_spike", Context.MODE_PRIVATE);
    }

    public synchronized Result publish(ContentVersion version, String approvedSessionId) {
        OAuthExchangeResponse connection = connections.instagramConnection();
        if (!isCurrent(connection, approvedSessionId)) return result(Status.CANCELLED);
        if (isExpired(connection)) return reconnect(connection);
        String accountId = connection.externalAccountId;
        if (!version.channelId().equals(accountId)) return result(Status.CANCELLED);
        try {
            Attempt previous = load(accountId);
            if (previous != null) {
                if (previous.permalink == null) return result(Status.CHECK_REQUIRED);
                if (previous.versionId.equals(version.versionId())) return published(previous);
            }
            String container = graph.createContainer(connection, version.imageReference(), version.text());
            if (!isCurrent(connections.instagramConnection(), approvedSessionId)) return result(Status.CANCELLED);
            Attempt attempt = new Attempt(version.versionId(), container, null, null);
            save(accountId, attempt);
            return publishSavedContainer(connection, attempt);
        } catch (AuthorizationExpired failure) {
            return reconnect(connection);
        } catch (IOException | RuntimeException failure) {
            return result(Status.ERROR);
        }
    }

    public synchronized Result reconcile() {
        OAuthExchangeResponse connection = connections.instagramConnection();
        if (connection == null) return result(Status.RECONNECT);
        if (isExpired(connection)) return reconnect(connection);
        try {
            String accountId = connection.externalAccountId;
            Attempt attempt = load(accountId);
            if (attempt == null) {
                graph.validateConnection(connection);
                return result(Status.READY);
            }
            if (attempt.permalink != null) {
                graph.validateConnection(connection);
                return published(attempt);
            }
            if (attempt.mediaId != null) return resolvePermalink(connection, attempt);
            switch (graph.containerStatus(connection, attempt.containerId)) {
                case "PUBLISHED": return result(Status.PUBLISHED_ID_UNKNOWN);
                case "EXPIRED":
                case "ERROR":
                    if (!attempts.edit().remove(accountId).commit()) return result(Status.ERROR);
                    return result(Status.PROVEN_UNPUBLISHED);
                default: return result(Status.CHECK_REQUIRED);
            }
        } catch (AuthorizationExpired failure) {
            return reconnect(connection);
        } catch (IOException | RuntimeException failure) {
            return result(Status.ERROR);
        }
    }

    private Result publishSavedContainer(OAuthExchangeResponse connection, Attempt attempt) {
        try {
            String mediaId = graph.publishContainer(connection, attempt.containerId);
            Attempt published = new Attempt(attempt.versionId, attempt.containerId, mediaId, null);
            save(connection.externalAccountId, published);
            return resolvePermalink(connection, published);
        } catch (AuthorizationExpired failure) {
            return reconnect(connection);
        } catch (IOException | RuntimeException failure) {
            // The provider may already have published. The container remains available for read-only reconciliation.
            return result(Status.CHECK_REQUIRED);
        }
    }

    private Result resolvePermalink(OAuthExchangeResponse connection, Attempt attempt) throws IOException {
        String link = graph.permalink(connection, attempt.mediaId);
        Attempt resolved = new Attempt(attempt.versionId, attempt.containerId, attempt.mediaId, link);
        save(connection.externalAccountId, resolved);
        return published(resolved);
    }

    private Attempt load(String accountId) throws IOException {
        String json = attempts.getString(accountId, null);
        if (json == null) return null;
        Attempt attempt = gson.fromJson(json, Attempt.class);
        if (attempt == null || attempt.versionId == null || attempt.containerId == null) {
            throw new IOException("Cannot reconcile the saved attempt");
        }
        return attempt;
    }

    private void save(String accountId, Attempt attempt) throws IOException {
        if (!attempts.edit().putString(accountId, gson.toJson(attempt)).commit()) {
            throw new IOException("Could not save the Instagram publish attempt");
        }
    }

    private static boolean isCurrent(OAuthExchangeResponse connection, String sessionId) {
        return connection != null && connection.sessionId.equals(sessionId);
    }

    private static boolean isExpired(OAuthExchangeResponse connection) {
        if (connection.expiresAt == null) return false;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date expiry = format.parse(connection.expiresAt);
            return expiry != null && expiry.getTime() <= System.currentTimeMillis();
        } catch (java.text.ParseException failure) {
            return true;
        }
    }

    private Result reconnect(OAuthExchangeResponse connection) {
        connections.invalidateInstagram(connection.sessionId);
        return result(Status.RECONNECT);
    }

    private static Result result(Status status) { return new Result(status, null, null); }

    private static Result published(Attempt attempt) {
        return new Result(Status.PUBLISHED, attempt.mediaId, attempt.permalink);
    }

    private static final class Attempt {
        private final String versionId;
        private final String containerId;
        private final String mediaId;
        private final String permalink;

        private Attempt(String versionId, String containerId, String mediaId, String permalink) {
            this.versionId = versionId;
            this.containerId = containerId;
            this.mediaId = mediaId;
            this.permalink = permalink;
        }
    }
}
