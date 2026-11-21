package com.socialpilot.app.data.remote.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static com.socialpilot.app.data.remote.social.PostResult.*;

/** Strict provider shapes, not a generic count extractor. No summing unknown breakdowns. */
final class PostMetricMapping {
    static List<MetricDefinition> definitions(PostRequest r) {
        List<MetricDefinition> result = new ArrayList<>();
        if (r.query == PostRequest.Query.ORGANIZATION_ORGANIC) {
            for (Metric m : new Metric[] {Metric.IMPRESSION_COUNT, Metric.COMMENT_COUNT,
                    Metric.SHARE_COUNT, Metric.PROVIDER_LIKE_COUNT, Metric.REACTION_COUNT}) {
                String field = m == Metric.IMPRESSION_COUNT ? "impressionCount" : m == Metric.COMMENT_COUNT
                        ? "commentCount" : m == Metric.SHARE_COUNT ? "shareCount" : m == Metric.PROVIDER_LIKE_COUNT ? "likeCount" : null;
                result.add(new MetricDefinition(r, m, field == null ? null : "totalShareStatistics." + field,
                        "single-post;organic;noTimeIntervals", "ROLLING_12_MONTHS_ORGANIC",
                        MeasurementKind.PROVIDER_ROLLING_12_MONTHS_ORGANIC, Aggregation.TOTAL, m == Metric.PROVIDER_LIKE_COUNT));
            }
            return result;
        }
        Metric m;
        String field;
        String filter;
        String window = "CUMULATIVE_COUNTER";
        switch (r.query) {
            case IG_REACH: m = Metric.REACH_COUNT; field = "reach"; filter = "period=lifetime"; window = "LIFETIME"; break;
            case IG_LIKES: m = Metric.LIKE_COUNT; field = null; filter = "unverified"; window = "UNVERIFIED"; break;
            case IG_COMMENTS: m = Metric.COMMENT_COUNT; field = null; filter = "unverified"; window = "UNVERIFIED"; break;
            case IG_SHARES: m = Metric.SHARE_COUNT; field = null; filter = "unverified"; window = "UNVERIFIED"; break;
            case IG_IMPRESSIONS: m = Metric.IMPRESSION_COUNT; field = null; filter = "unverified"; window = "UNVERIFIED"; break;
            case FB_REACTIONS: m = Metric.REACTION_COUNT; field = "reactions.summary.total_count"; filter = "all"; break;
            case FB_LIKES: m = Metric.LIKE_COUNT; field = "reactions.summary.total_count"; filter = "type=LIKE"; break;
            case FB_COMMENTS_TOPLEVEL: m = Metric.COMMENT_COUNT; field = "comments.summary.total_count"; filter = "filter=toplevel"; break;
            case FB_COMMENTS_STREAM: m = Metric.COMMENT_COUNT; field = "comments.summary.total_count"; filter = "filter=stream"; break;
            case FB_SHARES: m = Metric.SHARE_COUNT; field = "shares.count"; filter = "fields=id,shares"; break;
            case FB_MEDIA_VIEWS: m = Metric.MEDIA_VIEW_COUNT; field = "post_media_view"; filter = "period=lifetime"; window = "LIFETIME"; break;
            case FB_UNIQUE_VIEWERS: m = Metric.UNIQUE_MEDIA_VIEWER_COUNT; field = "post_total_media_view_unique"; filter = "period=lifetime"; window = "LIFETIME"; break;
            case FB_IMPRESSIONS: m = Metric.IMPRESSION_COUNT; field = null; filter = "unverified"; window = "UNVERIFIED"; break;
            default:
                m = r.query == PostRequest.Query.MEMBER_IMPRESSION ? Metric.IMPRESSION_COUNT
                        : r.query == PostRequest.Query.MEMBER_REACTION ? Metric.REACTION_COUNT
                        : r.query == PostRequest.Query.MEMBER_COMMENT ? Metric.COMMENT_COUNT
                        : r.query == PostRequest.Query.MEMBER_RESHARE ? Metric.SHARE_COUNT : Metric.MEMBERS_REACHED_COUNT;
                field = "memberCreatorPostAnalytics.count";
                filter = "queryType=" + memberMetric(r) + ";aggregation=TOTAL";
                window = r.period == null ? "LIFETIME" : "VERIFIED_DATE_RANGE";
        }
        MeasurementKind kind = field == null ? MeasurementKind.UNVERIFIED
                : r.period == null ? MeasurementKind.CUMULATIVE : MeasurementKind.PERIOD;
        Aggregation aggregation = field == null ? Aggregation.UNVERIFIED
                : m == Metric.REACH_COUNT || m == Metric.UNIQUE_MEDIA_VIEWER_COUNT || m == Metric.MEMBERS_REACHED_COUNT
                ? Aggregation.DISTINCT_TOTAL : Aggregation.TOTAL;
        return Collections.singletonList(new MetricDefinition(r, m, field, filter, window, kind, aggregation, false));
    }

    static String memberMetric(PostRequest r) {
        return r.query == PostRequest.Query.MEMBER_REACHED ? "MEMBERS_REACHED" : r.query.name().substring(7);
    }

    static final class Parsed {
        final List<MetricSample> samples = new ArrayList<>();
        boolean invalid;
        boolean rejected;
    }

    static Parsed parse(PostRequest r, JsonObject body, List<MetricDefinition> definitions,
            Map<Metric, MissingReason> blocked, long at, FollowerResult.Source source, String ref) {
        Parsed parsed = new Parsed();
        JsonObject row = null;
        boolean bounds = false;
        try {
            if (body == null) throw new IllegalArgumentException();
            // Optional echoed identities must agree even on endpoints which normally omit them.
            if (!optionalText(body, "postId", r.postId) || !optionalText(body, "account", r.externalAccountId)
                    || !optionalText(body, "author", r.externalAccountId)) { parsed.rejected = true; return parsed; }
            switch (r.account) {
                case INSTAGRAM_PROFESSIONAL:
                    if (!optionalText(body, "media_id", r.postId)
                            || !optionalText(body, "id", r.postId)) { parsed.rejected = true; return parsed; }
                    if (body.has("dateRange") || body.has("timeRange")) throw new IllegalArgumentException();
                    row = body;
                    break;
                case FACEBOOK_PAGE:
                    if (body.has("dateRange") || body.has("timeRange")) throw new IllegalArgumentException("unexpected period");
                    if (r.query == PostRequest.Query.FB_SHARES) {
                        if (!textEquals(body.get("id"), r.postId)) { parsed.rejected = true; return parsed; }
                    } else if (!optionalText(body, "id", r.postId)) { parsed.rejected = true; return parsed; }
                    if (body.has("from") && !textEquals(object(body.get("from")).get("id"), r.externalAccountId)) {
                        parsed.rejected = true; return parsed;
                    }
                    row = body;
                    break;
                case LINKEDIN_MEMBER:
                    row = singleRow(body);
                    if (row != null) {
                        JsonObject target = object(row.get("targetEntity"));
                        String key = r.postId.startsWith("urn:li:share:") ? "share" : "ugc";
                        if (target.size() != 1 || !textEquals(target.get(key), r.postId)
                                || !optionalText(row, "author", r.externalAccountId)) { parsed.rejected = true; return parsed; }
                        JsonElement metric = row.get("metricType");
                        // The documented versioned enum union and plain string are distinct accepted wire shapes.
                        if (metric != null && metric.isJsonObject()) {
                            JsonObject union = metric.getAsJsonObject();
                            if (union.size() != 1) throw new IllegalArgumentException();
                            metric = union.get("com.linkedin.adsexternalapi.memberanalytics.v1.CreatorPostAnalyticsMetricTypeV1");
                        }
                        if (!textEquals(metric, memberMetric(r))) throw new IllegalArgumentException();
                        if (r.period == null) {
                            if (row.has("dateRange")) throw new IllegalArgumentException("total is not period");
                        } else {
                            JsonObject range = object(row.get("dateRange"));
                            if (!r.period.startDate.equals(providerDate(range.get("start")))
                                    || !r.period.endDate.equals(providerDate(range.get("end")))) throw new IllegalArgumentException();
                            bounds = true;
                        }
                    }
                    break;
                default:
                    row = singleRow(body);
                    if (row != null) {
                        String key = r.postId.startsWith("urn:li:share:") ? "share" : "ugcPost";
                        String other = key.equals("share") ? "ugcPost" : "share";
                        if (!textEquals(row.get("organizationalEntity"), r.externalAccountId)
                                || !textEquals(row.get(key), r.postId) || row.has(other)) { parsed.rejected = true; return parsed; }
                        if (row.has("timeRange")) throw new IllegalArgumentException("organization period is not single post");
                        row = row.has("totalShareStatistics") && !row.get("totalShareStatistics").isJsonNull()
                                ? object(row.get("totalShareStatistics")) : new JsonObject();
                    }
            }
        } catch (RuntimeException ignored) { parsed.invalid = true; }
        boolean invalidEnvelope = parsed.invalid;
        for (MetricDefinition d : definitions) {
            Long value = null;
            MissingReason reason = blocked.get(d.metricKey);
            if (reason == null && d.providerMetricName == null) reason = MissingReason.UNVERIFIED;
            if (reason == null) {
                if (invalidEnvelope) reason = MissingReason.UNVERIFIED;
                else {
                    try {
                        JsonElement raw = row == null ? null : value(r, row, d);
                        value = count(raw, d.permitsNegative);
                        if (value == null) reason = MissingReason.PROVIDER_OMITTED;
                    } catch (WrongSubject ignored) {
                        parsed.rejected = true; parsed.samples.clear(); return parsed;
                    } catch (RuntimeException ignored) {
                        parsed.invalid = true;
                        reason = MissingReason.UNVERIFIED;
                    }
                }
            }
            parsed.samples.add(new MetricSample(r, d, value, reason, at, source, ref, bounds));
        }
        return parsed;
    }

    private static JsonElement value(PostRequest r, JsonObject row, MetricDefinition d) {
        switch (r.query) {
            case FB_REACTIONS: case FB_LIKES: case FB_COMMENTS_TOPLEVEL: case FB_COMMENTS_STREAM:
                return path(row, "summary", "total_count");
            case FB_SHARES: return path(row, "shares", "count");
            case IG_REACH: case FB_MEDIA_VIEWS: case FB_UNIQUE_VIEWERS:
                JsonObject insight = singleData(row);
                if (insight == null) return null;
                if (!textEquals(insight.get("name"), d.providerMetricName)
                        || !textEquals(insight.get("period"), "lifetime")) throw new IllegalArgumentException();
                if ((r.query == PostRequest.Query.IG_REACH || insight.has("id")) && !textEquals(insight.get("id"), r.postId + "/insights/"
                        + d.providerMetricName + "/lifetime")) throw new WrongSubject();
                JsonArray values = array(insight.get("values"));
                if (values.size() > 1) throw new IllegalArgumentException();
                return values.size() == 0 ? null : object(values.get(0)).get("value");
            case ORGANIZATION_ORGANIC:
                return row.get(d.providerMetricName.substring("totalShareStatistics.".length()));
            default: return row.get("count");
        }
    }

    private static JsonObject singleRow(JsonObject body) { return single(array(body.get("elements"))); }
    private static JsonObject singleData(JsonObject body) { return single(array(body.get("data"))); }
    private static JsonObject single(JsonArray rows) {
        if (rows.size() > 1) throw new IllegalArgumentException();
        return rows.size() == 0 ? null : object(rows.get(0));
    }
    private static JsonElement path(JsonObject row, String parent, String field) {
        JsonElement p = row.get(parent);
        return p == null || p.isJsonNull() ? null : object(p).get(field);
    }
    private static JsonObject object(JsonElement e) {
        if (e == null || !e.isJsonObject()) throw new IllegalArgumentException();
        return e.getAsJsonObject();
    }
    private static JsonArray array(JsonElement e) {
        if (e == null || !e.isJsonArray()) throw new IllegalArgumentException();
        return e.getAsJsonArray();
    }
    private static boolean textEquals(JsonElement e, String expected) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && expected.equals(e.getAsString());
    }
    private static boolean optionalText(JsonObject row, String key, String expected) {
        return !row.has(key) || textEquals(row.get(key), expected);
    }
    private static Long count(JsonElement e, boolean signed) {
        if (e == null || e.isJsonNull()) return null;
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        long count = new BigDecimal(e.getAsString()).longValueExact();
        if (!signed && count < 0) throw new IllegalArgumentException();
        return count;
    }
    private static String providerDate(JsonElement e) {
        JsonObject date = object(e);
        Long year = count(date.get("year"), false), month = count(date.get("month"), false), day = count(date.get("day"), false);
        if (year == null || month == null || day == null) throw new IllegalArgumentException();
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
    }
    private static final class WrongSubject extends RuntimeException { private static final long serialVersionUID = 1L; }
}
