package com.socialpilot.app.data.remote.social;

import com.google.gson.JsonArray;
import com.socialpilot.app.domain.contract.social.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import okio.ByteString;

/** Adapter-local query. IDs are opaque; account/ownership come from the binding. */
public final class CommentPageRequest {
    public enum Account { FACEBOOK_PAGE, LINKEDIN_MEMBER, LINKEDIN_ORGANIZATION }
    public enum Query { FB_TOPLEVEL, FB_STREAM, LI_COMMENTS }
    public enum Target { POST, COMMENT }

    public final Account account;
    public final Platform platform;
    public final String channelId;
    public final long connectionRevision;
    public final String externalAccountId;
    public final String postId;
    public final String targetId;
    public final Target target;
    /** Required LinkedIn post/thread mapping; caller must supply scoped evidence in Binding. */
    public final String threadId;
    public final Query query;
    public final int pageSize;
    /** Inclusive history policy in epoch ms, bound to resume; no unverified server filter. */
    public final Long historyFrom;
    public final String apiVersion;
    public final String queryFingerprint;
    final String cursor;

    public CommentPageRequest(Account account, String channelId, long revision,
            String externalAccountId, String postId, Target target, String targetId,
            String threadId, Query query, int pageSize, Long historyFrom,
            String apiVersion, String cursor) {
        this.account = Objects.requireNonNull(account, "account");
        platform = account == Account.FACEBOOK_PAGE ? Platform.FACEBOOK : Platform.LINKEDIN;
        this.channelId = text(channelId);
        if (revision < 1 || pageSize < 1 || historyFrom != null && historyFrom < 0) invalid();
        connectionRevision = revision;
        this.externalAccountId = text(externalAccountId);
        this.postId = text(postId);
        this.target = Objects.requireNonNull(target, "target");
        this.targetId = text(targetId);
        if (target == Target.POST && !postId.equals(targetId)) invalid();
        this.threadId = threadId;
        this.query = Objects.requireNonNull(query, "query");
        this.pageSize = pageSize;
        this.historyFrom = historyFrom;
        this.apiVersion = text(apiVersion);
        if (platform == Platform.FACEBOOK) {
            if (query == Query.LI_COMMENTS || threadId != null || !apiVersion.matches("v[0-9]+\\.[0-9]+")) invalid();
        } else {
            if (query != Query.LI_COMMENTS || !apiVersion.matches("[0-9]{4}(0[1-9]|1[0-2])")
                    || !postId.matches("urn:li:(share|ugcPost):[0-9]+")
                    || threadId == null || !threadId.matches("urn:li:(activity|share|ugcPost):[0-9]+")
                    || target == Target.COMMENT && !commentOnThread(targetId, threadId)) invalid();
        }
        this.cursor = cursor;
        JsonArray key = new JsonArray();
        key.add("sp041-query-v1"); key.add(account.name()); key.add(platform.name());
        key.add(channelId); key.add(revision); key.add(externalAccountId); key.add(postId);
        key.add(target.name()); key.add(targetId); key.add(threadId); key.add(query.name());
        key.add(pageSize); key.add(historyFrom); key.add(apiVersion);
        try {
            queryFingerprint = ByteString.of(MessageDigest.getInstance("SHA-256")
                    .digest(key.toString().getBytes(StandardCharsets.UTF_8))).hex();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public CommentPageRequest resume(String nextCursor) {
        return new CommentPageRequest(account, channelId, connectionRevision, externalAccountId,
                postId, target, targetId, threadId, query, pageSize, historyFrom, apiVersion, text(nextCursor));
    }

    boolean matches(ChannelInfo c) {
        AccountType type = account == Account.FACEBOOK_PAGE ? AccountType.PAGE
                : account == Account.LINKEDIN_MEMBER ? AccountType.PERSON : AccountType.ORGANIZATION;
        return c != null && c.platform == platform && c.accountType == type
                && channelId.equals(c.channelId) && connectionRevision == c.connectionRevision
                && externalAccountId.equals(c.externalAccountId);
    }

    boolean sameQuery(CommentPageRequest other) {
        return other != null && queryFingerprint.equals(other.queryFingerprint);
    }

    static boolean commentOnThread(String id, String thread) {
        return id != null && id.matches("urn:li:comment:\\(" + java.util.regex.Pattern.quote(thread) + ",[0-9]+\\)");
    }

    static String text(String value) {
        if (value == null || value.trim().isEmpty()) invalid();
        return value;
    }

    static String reference(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}")) invalid();
        return value;
    }

    static void invalid() { throw new IllegalArgumentException("Invalid comment query or payload"); }
    @Override public String toString() { return "CommentPageRequest[redacted]"; }
}
