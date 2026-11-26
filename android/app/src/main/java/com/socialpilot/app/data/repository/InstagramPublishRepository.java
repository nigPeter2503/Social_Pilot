package com.socialpilot.app.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.socialpilot.app.data.remote.instagram.InstagramGraphClient;
import com.socialpilot.app.data.remote.instagram.InstagramGraphClient.AuthorizationExpired;
import com.socialpilot.app.data.remote.instagram.InstagramGraphClient.PermissionDenied;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedOAuthStore.InstagramSession;
import com.socialpilot.app.data.security.EncryptedOAuthStore.InstagramMetadata;
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
        RECONNECT, PERMISSION_REQUIRED, PERMISSION_CHECK_REQUIRED, CANCELLED, ERROR }

    public static final class Result {
        public final Status status;
        public final String mediaId;
        public final String permalink;
        public final InstagramSession session;
        private final InstagramSession visibleSession;

        private Result(InstagramSession session, InstagramSession visibleSession, Status status,
                String mediaId, String permalink) {
            this.session = session;
            this.visibleSession = visibleSession;
            this.status = status;
            this.mediaId = mediaId;
            this.permalink = permalink;
        }

        public boolean isCurrent(EncryptedOAuthStore connections) {
            return connections.isCurrentInstagramSession(visibleSession);
        }

        public boolean matchesConnection(InstagramMetadata connection) {
            return visibleSession.matches(connection);
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

    public synchronized Result publish(ContentVersion version, InstagramSession session) {
        OAuthExchangeResponse connection = connections.instagramConnection(session);
        if (connection == null || !version.channelId().equals(session.accountId)) return cancelled(session);
        if (isExpired(connection)) return reconnect(session);
        String accountId = connection.externalAccountId;
        try {
            Attempt previous = load(accountId);
            if (previous != null) {
                if (previous.permalink == null) return currentResult(session, result(session, Status.CHECK_REQUIRED));
                if (previous.versionId.equals(version.versionId())) return currentResult(session, published(session, previous));
            }
            if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
            if (connection.expiresAt == null) {
                graph.validateConnection(connection);
                if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
            }
            String container = graph.createContainer(connection, version.imageReference(), version.text());
            Attempt attempt = new Attempt(version.versionId(), container, null, null);
            synchronized (connections) {
                if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
                save(accountId, attempt);
            }
            return publishSavedContainer(session, connection, attempt);
        } catch (AuthorizationExpired failure) {
            return reconnect(session);
        } catch (PermissionDenied failure) {
            return currentResult(session, result(session, Status.PERMISSION_REQUIRED));
        } catch (IOException | RuntimeException failure) {
            return currentResult(session, result(session, Status.ERROR));
        }
    }

    public synchronized Result reconcile(InstagramSession session) {
        OAuthExchangeResponse connection = connections.instagramConnection(session);
        if (connection == null) {
            return session.sessionId == null ? currentResult(session, result(session, Status.RECONNECT)) : cancelled(session);
        }
        if (isExpired(connection)) return reconnect(session);
        try {
            return currentResult(session, reconcileAttempt(session, connection, load(session.accountId)));
        } catch (AuthorizationExpired failure) {
            return reconnect(session);
        } catch (PermissionDenied failure) {
            return currentResult(session, result(session, Status.PERMISSION_REQUIRED));
        } catch (IOException | RuntimeException failure) {
            return currentResult(session, result(session, Status.ERROR));
        }
    }

    private Result reconcileAttempt(InstagramSession session, OAuthExchangeResponse connection, Attempt attempt)
            throws IOException {
        if (attempt == null || attempt.permalink != null) {
            graph.validateConnection(connection);
            return attempt == null ? result(session, Status.READY) : published(session, attempt);
        }
        if (attempt.mediaId != null) return resolvePermalink(session, connection, attempt);
        switch (graph.containerStatus(connection, attempt.containerId)) {
            case "PUBLISHED": return result(session, Status.PUBLISHED_ID_UNKNOWN);
            case "EXPIRED":
            case "ERROR":
                synchronized (connections) {
                    if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
                    boolean removed = attempts.edit().remove(session.accountId).commit();
                    return result(session, removed ? Status.PROVEN_UNPUBLISHED : Status.ERROR);
                }
            default: return result(session, Status.CHECK_REQUIRED);
        }
    }

    private Result publishSavedContainer(InstagramSession session, OAuthExchangeResponse connection, Attempt attempt) {
        if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
        try {
            String mediaId = graph.publishContainer(connection, attempt.containerId);
            Attempt saved = load(session.accountId);
            if (saved == null || !saved.versionId.equals(attempt.versionId)
                    || !saved.containerId.equals(attempt.containerId)) {
                return currentResult(session, result(session, Status.CHECK_REQUIRED));
            }
            // The POST may have succeeded during reconnect. Keep its ID on the original attempt for readback.
            Attempt published = new Attempt(attempt.versionId, attempt.containerId, mediaId, null);
            save(session.accountId, published);
            if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
            return currentResult(session, resolvePermalink(session, connection, published));
        } catch (AuthorizationExpired failure) {
            return reconnect(session);
        } catch (PermissionDenied failure) {
            return currentResult(session, result(session, Status.PERMISSION_CHECK_REQUIRED));
        } catch (IOException | RuntimeException failure) {
            // The provider may already have published. The container remains available for read-only reconciliation.
            return currentResult(session, result(session, Status.CHECK_REQUIRED));
        }
    }

    private Result resolvePermalink(InstagramSession session, OAuthExchangeResponse connection, Attempt attempt)
            throws IOException {
        String link = graph.permalink(connection, attempt.mediaId);
        Attempt resolved = new Attempt(attempt.versionId, attempt.containerId, attempt.mediaId, link);
        synchronized (connections) {
            if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
            save(session.accountId, resolved);
        }
        return published(session, resolved);
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

    private Result reconnect(InstagramSession session) {
        synchronized (connections) {
            if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
            connections.invalidateInstagram(session.sessionId);
            return new Result(session, connections.instagramSession(), Status.RECONNECT, null, null);
        }
    }

    private Result currentResult(InstagramSession session, Result candidate) {
        return connections.isCurrentInstagramSession(session) ? candidate : cancelled(session);
    }

    private static Result cancelled(InstagramSession session) { return result(session, Status.CANCELLED); }

    private static Result result(InstagramSession session, Status status) {
        return new Result(session, session, status, null, null);
    }

    private static Result published(InstagramSession session, Attempt attempt) {
        return new Result(session, session, Status.PUBLISHED, attempt.mediaId, attempt.permalink);
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
