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
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Scope;
import com.socialpilot.app.domain.editor.ContentVersion;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** Stores one feed test attempt per connected Instagram account, including its reconciliation result. */
public final class InstagramPublishRepository {
    public enum Status { READY, PUBLISHED, CHECK_REQUIRED, PUBLISHED_ID_UNKNOWN, PROVEN_UNPUBLISHED,
        RECONNECT, PERMISSION_REQUIRED, PERMISSION_CHECK_REQUIRED, CONFIRM_PRIOR_POST, PRIOR_VERSION_BLOCKED,
        CANCELLED, ERROR }

    private static final long RETENTION_MILLIS = TimeUnit.DAYS.toMillis(30);
    private static final String RETAINED = "retained:";
    private static final String NEEDS_CHECK = "needs-check:";
    private static final String ERASE = "erase:";

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
    private final Clock clock;
    private final Gson gson = new Gson();

    interface Clock {
        long nowMillis();
    }

    public InstagramPublishRepository(Context context, EncryptedOAuthStore connections) {
        this(context, connections, new InstagramGraphClient());
    }

    public InstagramPublishRepository(Context context, EncryptedOAuthStore connections, InstagramGraphClient graph) {
        this(context, connections, graph, System::currentTimeMillis);
    }

    InstagramPublishRepository(Context context, EncryptedOAuthStore connections, InstagramGraphClient graph, Clock clock) {
        this.connections = connections;
        this.graph = graph;
        this.clock = clock;
        attempts = preferences(context);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences("instagram_feed_spike", Context.MODE_PRIVATE);
    }

    public void setDisconnectChoice(Scope scope, boolean erase) {
        synchronized (attempts) {
            SharedPreferences.Editor edit = attempts.edit();
            String key = eraseKey(scope);
            if (erase) edit.putBoolean(key, true);
            else edit.remove(key);
            if (!edit.commit()) throw new IllegalStateException("Could not store disconnect choice");
        }
    }

    private static String eraseKey(Scope scope) {
        return ERASE + scope.channelId + ":" + scope.connectionRevision;
    }

    /** Durable Room retirement supplies the old account and the time the connection ended. */
    public static void retireAttempt(Context context, Scope scope, String accountId, long retiredAtMillis) {
        SharedPreferences store = preferences(context);
        synchronized (store) {
            Gson gson = new Gson();
            String json = store.getString(accountId, null);
            boolean erase = store.getBoolean(eraseKey(scope), false);
            SharedPreferences.Editor edit = store.edit().remove(accountId).remove(eraseKey(scope));
            if (json != null) {
                Attempt attempt;
                try {
                    attempt = gson.fromJson(json, Attempt.class);
                } catch (RuntimeException malformed) {
                    attempt = null;
                }
                // Legacy attempts did not record dispatch. Treat those and unreadable rows as uncertain.
                if (attempt == null || !Boolean.FALSE.equals(attempt.dispatched)) {
                    edit.putBoolean(NEEDS_CHECK + scope.channelId, true);
                    long expiresAt = retiredAtMillis + RETENTION_MILLIS;
                    if (!erase && attempt != null && attempt.versionId != null
                            && System.currentTimeMillis() < expiresAt) {
                        edit.putString(retainedKey(scope, attempt.versionId), gson.toJson(new RetainedAttempt(
                                attempt.versionId, attempt.mediaId, scope.connectionRevision, expiresAt)));
                    }
                }
            }
            if (erase) {
                for (String key : store.getAll().keySet()) {
                    if (!key.startsWith(retainedPrefix(scope))) continue;
                    edit.putBoolean(NEEDS_CHECK + scope.channelId, true).remove(key);
                }
            }
            if (!edit.commit()) throw new IllegalStateException("Could not clean the retired publish attempt");
        }
    }

    private static String retainedPrefix(Scope scope) {
        return RETAINED + scope.channelId + ":";
    }

    private static String retainedKey(Scope scope, String versionId) {
        return retainedPrefix(scope) + versionId;
    }

    public void purgeExpiredEvidence() {
        synchronized (attempts) {
            expireEvidenceLocked();
        }
    }

    private boolean expireEvidenceLocked() {
        long now = clock.nowMillis();
        SharedPreferences.Editor edit = null;
        for (String key : attempts.getAll().keySet()) {
            if (!key.startsWith(RETAINED)) continue;
            RetainedAttempt evidence = readRetained(key);
            if (evidence != null && now < evidence.expiresAtMillis) continue;
            if (edit == null) edit = attempts.edit();
            edit.remove(key);
            int separator = key.indexOf(':', RETAINED.length());
            if (separator > RETAINED.length()) {
                // The earlier acknowledgement does not cover a newly expired record.
                edit.putBoolean(NEEDS_CHECK + key.substring(RETAINED.length(), separator), true);
            }
        }
        return edit == null || edit.commit();
    }

    public boolean confirmPriorPostChecked(InstagramSession session) {
        Scope scope = session.scope();
        synchronized (connections) {
            if (scope == null || !connections.isCurrentInstagramSession(session)) return false;
            synchronized (attempts) {
                return expireEvidenceLocked() && attempts.edit().remove(NEEDS_CHECK + scope.channelId).commit();
            }
        }
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
            Status blocked = publishBlock(session, version.versionId());
            if (blocked != null) {
                return currentResult(session, result(session, blocked));
            }
            if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
            if (connection.expiresAt == null) {
                graph.validateConnection(connection);
                if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
            }
            String container = graph.createContainer(connection, version.imageReference(), version.text());
            Attempt attempt = new Attempt(version.versionId(), container, null, null, false);
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
        if (attempt == null) {
            Status blocked = publishBlock(session, null);
            if (blocked != null) return result(session, blocked);
        }
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
        try {
            synchronized (connections) {
                if (!connections.isCurrentInstagramSession(session)) return cancelled(session);
                save(session.accountId, new Attempt(attempt.versionId, attempt.containerId, null, null, true));
            }
            String mediaId = graph.publishContainer(connection, attempt.containerId);
            Attempt published = new Attempt(attempt.versionId, attempt.containerId, mediaId, null, true);
            boolean stillTracked;
            synchronized (attempts) {
                Attempt saved = load(session.accountId);
                stillTracked = saved != null && saved.versionId.equals(attempt.versionId)
                        && saved.containerId.equals(attempt.containerId);
                // Normal reconnect can retain the result. A retired attempt has been removed.
                if (stillTracked) save(session.accountId, published);
            }
            if (!stillTracked) return currentResult(session, result(session, Status.CHECK_REQUIRED));
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
        Attempt resolved = new Attempt(attempt.versionId, attempt.containerId, attempt.mediaId, link, true);
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

    private Status publishBlock(InstagramSession session, String versionId) {
        Scope scope = session.scope();
        if (scope == null) return null;
        synchronized (attempts) {
            if (!expireEvidenceLocked()) return Status.ERROR;
            if (attempts.getBoolean(NEEDS_CHECK + scope.channelId, false)) return Status.CONFIRM_PRIOR_POST;
            if (versionId == null) return null;
            return attempts.contains(retainedKey(scope, versionId)) ? Status.PRIOR_VERSION_BLOCKED : null;
        }
    }

    private RetainedAttempt readRetained(String key) {
        try {
            return gson.fromJson(attempts.getString(key, null), RetainedAttempt.class);
        } catch (RuntimeException malformed) {
            return null;
        }
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
        private final Boolean dispatched;

        private Attempt(String versionId, String containerId, String mediaId, String permalink, boolean dispatched) {
            this.versionId = versionId;
            this.containerId = containerId;
            this.mediaId = mediaId;
            this.permalink = permalink;
            this.dispatched = dispatched;
        }
    }

    private static final class RetainedAttempt {
        private final String versionId;
        private final String mediaId;
        private final long retiredRevision;
        private final long expiresAtMillis;

        private RetainedAttempt(String versionId, String mediaId, long retiredRevision, long expiresAtMillis) {
            this.versionId = versionId;
            this.mediaId = mediaId;
            this.retiredRevision = retiredRevision;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
