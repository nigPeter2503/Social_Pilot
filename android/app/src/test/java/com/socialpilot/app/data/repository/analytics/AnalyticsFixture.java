package com.socialpilot.app.data.repository.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource.*;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import com.socialpilot.app.domain.contract.analytics.DashboardData.PostMetadata;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.*;
import com.socialpilot.app.domain.contract.social.Platform;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Raw MO_PHONG input builder; never constructs calculated dashboard outputs. */
public final class AnalyticsFixture {
    public static final ChannelScope SCOPE = new ChannelScope(Platform.FACEBOOK, "Channel:00A", 1);
    public static final long START = Instant.parse("2026-11-01T17:00:00Z").toEpochMilli();
    public static final long END = Instant.parse("2026-11-08T17:00:00Z").toEpochMilli();
    public static final long STALE_POLICY = 48L * 3600 * 1000;
    public static final Definition REACTION = def("reaction-v1", Key.REACTION, Kind.CUMULATIVE, Subject.POST);
    public static final Definition COMMENT = def("comment-v1", Key.COMMENT, Kind.CUMULATIVE, Subject.POST);
    public static final Definition SHARE = def("share-v1", Key.SHARE, Kind.CUMULATIVE, Subject.POST);
    public static final Definition IMPRESSION = def("impression-v1", Key.IMPRESSION, Kind.CUMULATIVE, Subject.POST);
    public static final Definition FOLLOWER = def("follower-v1", Key.FOLLOWER, Kind.GAUGE, Subject.CHANNEL);
    public static final Definition ENGAGEMENT = new Definition("engagement-v1", Key.ENGAGEMENT, Kind.CUMULATIVE,
            Subject.POST, "COUNT", List.of(REACTION.id(), COMMENT.id(), SHARE.id()));
    public static final Definition RATE = new Definition("rate-v1", Key.ENGAGEMENT_RATE, Kind.CUMULATIVE,
            Subject.POST, "PERCENT", List.of(REACTION.id(), COMMENT.id(), SHARE.id(), IMPRESSION.id()));
    public static final List<Definition> DEFINITIONS = List.of(REACTION, COMMENT, SHARE, IMPRESSION, FOLLOWER, ENGAGEMENT, RATE);
    private AnalyticsFixture() {}
    public static Definition def(String id, Key key, Kind kind, Subject subject) {
        return new Definition(id, key, kind, subject, "COUNT", List.of());
    }
    public static DashboardQuery query(String definition, String... ids) {
        return new DashboardQuery(List.of(SCOPE), definition, START, END, "Asia/Ho_Chi_Minh",
                new Cohort(List.of(ids)), Grouping.TOPIC, 10);
    }
    public static PostMetadata post(String id, Long published, String topic, String format) {
        return new PostMetadata(SCOPE, id, null, published, topic, format);
    }
    public static Sample sample(Definition d, String id, long at, Long value) {
        return sample(d, id, at, value, SCOPE, EvidenceSource.MO_PHONG, "window-" + at, null, null, ObservedAtBasis.COLLECTION);
    }
    public static Sample sample(Definition d, String id, long at, Long value, ChannelScope scope,
                                EvidenceSource source, String alignment, Long periodStart, Long periodEnd, ObservedAtBasis basis) {
        MetricSource provenance = new MetricSource("snapshot:" + id + ":" + d.id() + ":" + at,
                "collection:" + id + ":" + at, scope, id, d.id(), source, "synthetic-reviewed-window",
                at, basis, at + 7, periodStart, periodEnd, "mock-v1");
        return new Sample(d, d.subject(), value, value == null ? MissingReason.PROVIDER_OMITTED : null, provenance, alignment);
    }
    public static List<Sample> counters(String id, long at, long r, long c, long s, long i) {
        return new ArrayList<>(List.of(sample(REACTION, id, at, r), sample(COMMENT, id, at, c),
                sample(SHARE, id, at, s), sample(IMPRESSION, id, at, i)));
    }
    public static List<Sample> pair(String id, long r, long c, long s, long i) {
        List<Sample> out = counters(id, START, 0, 0, 0, 0);
        out.addAll(counters(id, END, r, c, s, i));
        return out;
    }
    public static Batch batch(List<PostMetadata> posts, List<Sample> samples) {
        return new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS, List.of(), posts, samples,
                new Attempt(END + 7, END + 7, SyncState.IDLE, false, List.of()));
    }
    public static Batch followers(List<Sample> samples) {
        return new Batch(true, EvidenceSource.MO_PHONG, DEFINITIONS,
                List.of(new Channel(SCOPE, "MO_PHONG", FOLLOWER.id())), List.of(), samples,
                new Attempt(END + 7, END + 7, SyncState.IDLE, false, List.of()));
    }
}
