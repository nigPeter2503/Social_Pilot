package com.socialpilot.app.data.repository.social;

import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.socialpilot.app.domain.contract.social.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Durable reservations keyed by provider account and approved content. */
final class LinkedInAttemptLedger {
    interface Clock { long now(); }
    static final class Target {
        final String key, requestId, postId;
        Target(String key, PublishResult result) {
            this.key = key; this.requestId = result.requestId; this.postId = result.postId;
        }
    }
    private final SharedPreferences preferences;
    private final Clock clock;
    private final Gson gson = new Gson();
    LinkedInAttemptLedger(SharedPreferences preferences, Clock clock) {
        this.preferences = preferences; this.clock = clock;
    }
    String key(ChannelInfo channel, String version) {
        synchronized (preferences) {
            String key = "account_attempt_" + hash(channel.externalAccountId + ":" + version);
            String oldChannel = "LINKEDIN:" + channel.externalAccountId;
            java.util.Set<String> candidates = new java.util.LinkedHashSet<>();
            candidates.add(key);
            candidates.add("attempt_" + hash(channel.channelId + ":" + version));
            candidates.add("attempt_" + hash(oldChannel + ":" + version));
            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                if (!entry.getKey().startsWith("attempt_")) continue;
                PublishResult result = parse(String.valueOf(entry.getValue()));
                if (result != null && (result.channelId.equals(oldChannel)
                        || result.channelId.startsWith(oldChannel + ":"))
                        && entry.getKey().equals("attempt_" + hash(result.channelId + ":" + version))) {
                    candidates.add(entry.getKey());
                }
            }
            String source = null;
            for (String candidate : candidates) {
                if (!preferences.contains(candidate)) continue;
                if (source == null) source = candidate;
                PublishResult result = parse(preferences.getString(candidate, ""));
                if (result == null || result.outcome != PublishResult.Outcome.REJECTED) {
                    source = candidate;
                    break;
                }
            }
            if (source != null && !source.equals(key)) {
                SharedPreferences.Editor editor = preferences.edit().putString(key, preferences.getString(source, "UNKNOWN"));
                if (source.equals(preferences.getString("last_key", null))) editor.putString("last_key", key);
                requireCommit(editor);
            }
            return key;
        }
    }
    PublishResult existing(String key, PublishRequest request) {
        synchronized (preferences) {
            String raw = preferences.getString(key, "");
            PublishResult result = parse(raw);
            if (result == null) return PublishResult.unknown(request);
            return restore(request, result);
        }
    }
    boolean begin(String key, PublishRequest request, String confirmedRequestId) {
        synchronized (preferences) {
            if (preferences.contains(key)) {
                PublishResult old = parse(preferences.getString(key, ""));
                if (old == null || old.outcome != PublishResult.Outcome.REJECTED) return false;
                if (!old.requestId.equals(confirmedRequestId)) return false;
                if (clock.now() < retryAt(key)) return false;
                if (old.error.code == SocialError.Code.AUTH_REQUIRED
                        && old.channelId.equals(request.channelId)
                        && request.connectionRevision <= old.connectionRevision) return false;
                if (old.error.code != SocialError.Code.AUTH_REQUIRED
                        && old.error.code != SocialError.Code.PERMISSION_REQUIRED
                        && old.error.code != SocialError.Code.RATE_LIMITED) return false;
            }
            PublishResult pending = PublishResult.unknown(request);
            requireCommit(preferences.edit().putString(key, gson.toJson(pending))
                    .putString("last_key", key).putString("last_payload", gson.toJson(pending))
                    .putString("last_result", summary(pending)).remove("retry_after_" + key));
            return true;
        }
    }
    void record(String key, PublishResult result, long retryAt) {
        synchronized (preferences) {
            PublishResult reserved = parse(preferences.getString(key, ""));
            if (reserved == null || !reserved.requestId.equals(result.requestId)) throw new IllegalStateException("Attempt replaced");
            SharedPreferences.Editor editor = preferences.edit().putString(key, gson.toJson(result))
                    .putString("retry_after_" + key, Long.toString(retryAt));
            if (key.equals(preferences.getString("last_key", null))) {
                editor.putString("last_payload", gson.toJson(result)).putString("last_result", summary(result));
            }
            requireCommit(editor);
        }
    }
    Target target() {
        synchronized (preferences) {
            String key = preferences.getString("last_key", null);
            PublishResult result = parse(preferences.getString("last_payload", ""));
            if (key == null || result == null || result.postId == null) return null;
            return new Target(key, result);
        }
    }
    Target target(String key) {
        synchronized (preferences) {
            PublishResult result = parse(preferences.getString(key,""));
            return result == null || result.postId == null ? null : new Target(key,result);
        }
    }
    String confirm(Target target, String url) {
        synchronized (preferences) {
            if (target == null) throw new IllegalStateException("Open the post again");
            String raw = preferences.getString(target.key, "");
            PublishResult current = parse(raw);
            if (current == null || !current.requestId.equals(target.requestId)
                    || !target.postId.equals(current.postId)) throw new IllegalStateException("Attempt replaced");
            PublishResult verified = LinkedInPostVerification.ownerConfirmed(raw, url);
            SharedPreferences.Editor editor = preferences.edit().putString(target.key, gson.toJson(verified));
            if (target.key.equals(preferences.getString("last_key", null))) {
                editor.putString("last_payload", gson.toJson(verified)).putString("last_result", summary(verified))
                        .putString("url_verification_source", "OWNER_CONFIRMED");
            }
            requireCommit(editor);
            return preferences.getString("last_result", null);
        }
    }
    String confirmedRetry(String key) {
        synchronized (preferences) {
            PublishResult previous = parse(preferences.getString(key, ""));
            return previous != null && previous.outcome == PublishResult.Outcome.REJECTED ? previous.requestId : null;
        }
    }
    long retryAt(String key) {
        try { return Long.parseLong(preferences.getString("retry_after_" + key, "0")); }
        catch (RuntimeException e) { return Long.MAX_VALUE; }
    }
    static PublishResult restore(PublishRequest request, PublishResult old) {
        switch (old.outcome) {
            case SUCCESS: return PublishResult.success(request, old.postId, old.postUrl);
            case PUBLISHED_URL_PENDING: return PublishResult.publishedUrlPending(request, old.postId);
            case REJECTED: return PublishResult.rejected(request, new SocialError(old.error.code, old.error.nextAction));
            default: return PublishResult.unknown(request);
        }
    }
    private PublishResult parse(String raw) {
        try {
            PublishResult result = gson.fromJson(raw, PublishResult.class);
            PublishRequest request = new PublishRequest(result.requestId, result.channelId, result.connectionRevision, "stored-attempt");
            return restore(request, result);
        } catch (RuntimeException e) { return null; }
    }
    static String summary(PublishResult result) {
        return result.outcome.name() + (result.error == null ? "" : "\n" + result.error.code + "\n" + result.error.nextAction)
                + (result.postId == null ? "" : "\n" + result.postId)
                + (result.postUrl == null ? "" : "\n" + result.postUrl);
    }
    private static void requireCommit(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("Cannot save publish attempt");
    }
    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (byte b : digest) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return text.toString();
        } catch (Exception e) { throw new IllegalStateException("Hash unavailable"); }
    }
}
