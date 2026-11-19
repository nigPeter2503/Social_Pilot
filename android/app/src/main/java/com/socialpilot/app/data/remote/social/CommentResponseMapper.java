package com.socialpilot.app.data.remote.social;

import com.google.gson.*;
import com.socialpilot.app.domain.contract.social.*;
import java.math.BigDecimal;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;
import java.util.*;
import okhttp3.HttpUrl;
import static com.socialpilot.app.data.remote.social.CommentPageResult.Failure;
import static com.socialpilot.app.data.remote.social.CommentRecord.*;

/** All-or-failure mapping: an invalid item never silently disappears from a successful page. */
final class CommentResponseMapper {
    private CommentResponseMapper() {}

    static CommentPageResult map(CommentPageRequest r, CommentAdapter.Binding binding, int status,
            String raw, String retryAfter, long at, String continuation, CommentCursorCodec cursors,
            Source source, String sourceRef, String token) {
        JsonObject body = null;
        try { body = object(JsonParser.parseString(raw)); }
        catch (RuntimeException ignored) { /* Error classification may still use HTTP evidence. */ }
        Integer code = null;
        boolean providerError = body != null && (present(body, "error") || present(body, "serviceErrorCode"));
        try {
            if (body != null && present(body, "status") && number(body.get("status")) >= 400) providerError = true;
            if (r.platform == Platform.FACEBOOK && body != null && present(body, "error")) {
                code = Math.toIntExact(number(object(body.get("error")).get("code")));
            } else if (r.platform == Platform.LINKEDIN && body != null && present(body, "serviceErrorCode")) {
                code = Math.toIntExact(number(body.get("serviceErrorCode")));
            }
        } catch (RuntimeException ignored) { providerError = true; }
        if (status < 200 || status >= 300 || providerError) {
            SocialError error = null;
            if (status == 401 || r.platform == Platform.FACEBOOK && Objects.equals(code, 190)) {
                error = new SocialError(SocialError.Code.AUTH_REQUIRED, SocialError.NextAction.RECONNECT);
            } else if (status == 429 || r.platform == Platform.FACEBOOK
                    && Arrays.asList(4, 17, 32, 613).contains(code)) {
                error = new SocialError(SocialError.Code.RATE_LIMITED, SocialError.NextAction.WAIT);
            } else if (binding.permissionEvidence != null && binding.permissionEvidence.matches(r, code)) {
                // Explicit scoped evidence, not a generic 403 or prototype heuristic.
                error = new SocialError(SocialError.Code.PERMISSION_REQUIRED, SocialError.NextAction.RESOLVE_PERMISSION);
            }
            return CommentPageResult.failure(Failure.PROVIDER_ERROR, error, null,
                    error != null && error.code == SocialError.Code.RATE_LIMITED ? retryAt(retryAfter, at) : null);
        }
        try {
            if (body == null) CommentPageRequest.invalid();
            checkScope(body, r);
            String arrayName = r.platform == Platform.FACEBOOK ? "data" : "elements";
            JsonElement rows = body.get(arrayName);
            if (rows == null || !rows.isJsonArray()) CommentPageRequest.invalid();
            List<CommentRecord> items = new ArrayList<>();
            for (JsonElement row : rows.getAsJsonArray()) {
                JsonObject item = object(row);
                checkScope(item, r);
                items.add(r.platform == Platform.FACEBOOK
                        ? facebook(item, r, binding, at, source, sourceRef)
                        : linkedin(item, r, binding, at, source, sourceRef));
            }
            Paging paging = r.platform == Platform.FACEBOOK ? facebookPaging(body, token)
                    : linkedinPaging(body, r, continuation);
            String next = paging.continuation == null ? null : cursors.encode(r, paging.continuation);
            return CommentPageResult.success(new CommentPage(r, items, next, paging.more,
                    paging.reason, at, source, sourceRef));
        } catch (RuntimeException ignored) { return CommentAdapter.fail(Failure.INVALID_RESPONSE); }
    }

    private static CommentRecord facebook(JsonObject raw, CommentPageRequest r, CommentAdapter.Binding b,
            long at, Source source, String ref) {
        String id = required(raw, "id");
        String parent = present(raw, "parent") ? required(object(raw.get("parent")), "id") : null;
        if (r.target == CommentPageRequest.Target.COMMENT && parent != null && !r.targetId.equals(parent)) CommentPageRequest.invalid();
        String message = optional(raw, "message");
        String author = present(raw, "from") ? optional(object(raw.get("from")), "name") : null;
        Long created = timestamp(optional(raw, "created_time"));
        Long updated = timestamp(optional(raw, "updated_time"));
        return record(r, b, id, parent, message, author, created, updated, at, source, ref);
    }

    private static CommentRecord linkedin(JsonObject raw, CommentPageRequest r, CommentAdapter.Binding b,
            long at, Source source, String ref) {
        String nativeId = required(raw, "id");
        if (!nativeId.matches("[0-9]+") || !r.threadId.equals(required(raw, "object"))) CommentPageRequest.invalid();
        String id = "urn:li:comment:(" + r.threadId + "," + nativeId + ")";
        if (present(raw, "commentUrn") && !id.equals(required(raw, "commentUrn"))) CommentPageRequest.invalid();
        String parent = optional(raw, "parentComment");
        if (parent != null && (!CommentPageRequest.commentOnThread(parent, r.threadId)
                || r.target == CommentPageRequest.Target.COMMENT && !r.targetId.equals(parent))) CommentPageRequest.invalid();
        if (present(raw, "actor") && !required(raw, "actor").matches("urn:li:(person|organization|organizationBrand):[A-Za-z0-9_-]+")) CommentPageRequest.invalid();
        String message = present(raw, "message") ? optional(object(raw.get("message")), "text") : null;
        Long created = present(raw, "created") ? epoch(object(raw.get("created")).get("time")) : null;
        Long updated = present(raw, "lastModified") ? epoch(object(raw.get("lastModified")).get("time")) : null;
        // No actor URN/profile is invented as a display name.
        return record(r, b, id, parent, message, null, created, updated, at, source, ref);
    }

    private static CommentRecord record(CommentPageRequest r, CommentAdapter.Binding b, String id,
            String parent, String message, String author, Long created, Long updated, long at, Source source, String ref) {
        if (id.equals(parent)) CommentPageRequest.invalid();
        ParentState parentState = parent == null ? ParentState.UNKNOWN : ParentState.KNOWN;
        ContentState content = message == null ? ContentState.OMITTED : ContentState.AVAILABLE;
        CommentAdapter.ItemEvidence fact = b.itemEvidence.get(id);
        if (fact != null) {
            if (fact.root) {
                if (parent != null || r.target == CommentPageRequest.Target.COMMENT) CommentPageRequest.invalid();
                parentState = ParentState.ROOT;
            }
            if (fact.deleted) {
                if (message != null) CommentPageRequest.invalid();
                content = ContentState.DELETED;
            }
        }
        return new CommentRecord(r, id, parentState, parent, content, message, author, created,
                updated, at, source, ref, b.replyCapability);
    }

    private static Paging facebookPaging(JsonObject body, String token) {
        JsonObject paging = present(body, "paging") ? object(body.get("paging")) : null;
        JsonObject positions = paging != null && present(paging, "cursors") ? object(paging.get("cursors")) : null;
        String next = paging == null ? null : optional(paging, "next");
        if (next == null) return new Paging(false, null, null);
        CommentPageRequest.text(next);
        String after = positions == null ? null : required(positions, "after");
        CommentPageRequest.text(after);
        // The next URL is only a signal; only an encrypted, validated continuation survives.
        if (after.getBytes(StandardCharsets.UTF_8).length > 16000 || after.contains("://") || after.contains("access_token")
                || after.contains("Bearer ") || token != null && after.contains(token)) CommentPageRequest.invalid();
        return new Paging(true, after, null);
    }

    private static Paging linkedinPaging(JsonObject body, CommentPageRequest r, String continuation) {
        if (!present(body, "paging")) return new Paging(null, null, "NOT_ESTABLISHED");
        JsonObject p = object(body.get("paging"));
        long start = number(p.get("start"));
        long count = number(p.get("count"));
        Long total = present(p, "total") ? number(p.get("total")) : null;
        if (start < 0 || count <= 0 || start > Integer.MAX_VALUE - count || total != null && total < 0
                || p.get("links") == null || !p.get("links").isJsonArray()) CommentPageRequest.invalid();
        long requestedStart = continuation == null ? 0 : Long.parseLong(continuation);
        long nextStart = requestedStart + r.pageSize;
        boolean conflict = start != requestedStart || count != r.pageSize;
        int nextLinks = 0;
        for (JsonElement entry : p.getAsJsonArray("links")) {
            JsonObject link = object(entry);
            if (!required(link, "rel").equals("next")) continue;
            nextLinks++;
            String href = optional(link, "href");
            HttpUrl url = href == null ? null : HttpUrl.get("https://example.invalid/").resolve(href);
            if (url == null || url.queryParameterValues("start").size() != 1
                    || url.queryParameterValues("count").size() != 1
                    || !String.valueOf(nextStart).equals(url.queryParameter("start"))
                    || !String.valueOf(r.pageSize).equals(url.queryParameter("count"))) conflict = true;
        }
        Boolean byTotal = total == null ? null : nextStart < total;
        if (nextLinks > 0 && Boolean.FALSE.equals(byTotal)) conflict = true;
        if (conflict) return new Paging(null, null, "PAGING_CONFLICT");
        Boolean more = nextLinks > 0 ? Boolean.TRUE : byTotal;
        return new Paging(more, Boolean.TRUE.equals(more) ? String.valueOf(nextStart) : null,
                more == null ? "NOT_ESTABLISHED" : null);
    }

    /** Echoes, if present, must agree; no attempt to derive ownership from ID prefixes. */
    private static void checkScope(JsonObject value, CommentPageRequest r) {
        checkEcho(value, "postId", r.postId); checkEcho(value, "postUrn", r.postId);
        checkEcho(value, "channelId", r.channelId); checkEcho(value, "externalAccountId", r.externalAccountId);
        if (present(value, "post")) {
            JsonElement post = value.get("post");
            String id = post.isJsonObject() ? required(post.getAsJsonObject(), "id") : string(post);
            if (!r.postId.equals(id)) CommentPageRequest.invalid();
        }
        if (present(value, "connectionRevision") && number(value.get("connectionRevision")) != r.connectionRevision) CommentPageRequest.invalid();
    }
    private static void checkEcho(JsonObject value, String field, String expected) {
        if (present(value, field) && !expected.equals(required(value, field))) CommentPageRequest.invalid();
    }
    private static boolean present(JsonObject value, String key) { return value.has(key) && !value.get(key).isJsonNull(); }
    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) CommentPageRequest.invalid();
        return value.getAsJsonObject();
    }
    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) CommentPageRequest.invalid();
        return value.getAsString();
    }
    private static String required(JsonObject value, String key) { return CommentPageRequest.text(string(value.get(key))); }
    private static String optional(JsonObject value, String key) { return present(value, key) ? string(value.get(key)) : null; }
    private static long number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) CommentPageRequest.invalid();
        return new BigDecimal(value.getAsString()).longValueExact();
    }
    private static Long epoch(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        long time = number(value);
        if (time < 0 || time > 253402300799999L) CommentPageRequest.invalid();
        return time;
    }
    private static Long timestamp(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(Z|[+-][0-9]{2}:?[0-9]{2})")) CommentPageRequest.invalid();
        String normalized = value.endsWith("Z") ? value.substring(0, value.length() - 1) + "+0000"
                : value.substring(0, 19) + value.substring(19).replace(":", "");
        int hours = Integer.parseInt(normalized.substring(20, 22));
        int minutes = Integer.parseInt(normalized.substring(22));
        if (hours > 14 || minutes > 59 || hours == 14 && minutes != 0) CommentPageRequest.invalid();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(normalized, position);
        if (parsed == null || position.getIndex() != normalized.length()) CommentPageRequest.invalid();
        return parsed.getTime();
    }
    private static Long retryAt(String value, long now) {
        if (value == null) return null;
        try {
            long seconds = Long.parseLong(value);
            return seconds < 0 ? null : Math.addExact(now, Math.multiplyExact(seconds, 1000));
        } catch (NumberFormatException ignored) {
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
            format.setLenient(false); format.setTimeZone(TimeZone.getTimeZone("GMT"));
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(value, position);
            return date != null && position.getIndex() == value.length() ? date.getTime() : null;
        } catch (ArithmeticException ignored) { return null; }
    }
    private static final class Paging {
        final Boolean more;
        final String continuation;
        final String reason;
        Paging(Boolean more, String continuation, String reason) {
            this.more = more; this.continuation = continuation; this.reason = reason;
        }
    }
}
