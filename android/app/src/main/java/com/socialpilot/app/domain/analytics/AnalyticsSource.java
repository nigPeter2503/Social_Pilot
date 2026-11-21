package com.socialpilot.app.domain.analytics;

import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import com.socialpilot.app.domain.contract.analytics.DashboardData.PostMetadata;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.ChannelScope;
import java.util.List;
import java.util.Objects;

/** SP-046 local projection port, not a Room DAO or a provider adapter. */
public interface AnalyticsSource {
    enum Key { FOLLOWER, REACTION, COMMENT, SHARE, IMPRESSION, REACH, ENGAGEMENT, ENGAGEMENT_RATE }
    enum Kind { GAUGE, CUMULATIVE, PERIOD, UNVERIFIED }
    enum Subject { CHANNEL, POST }

    /** IDs pin provider/filter/unit/aggregation semantics; components are ordered registry IDs. */
    record Definition(String id, Key key, Kind kind, Subject subject, String unit,
                      List<String> components) {
        public Definition {
            Objects.requireNonNull(id);
            Objects.requireNonNull(key);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(subject);
            Objects.requireNonNull(unit);
            components = List.copyOf(components);
        }
    }

    /** alignmentId is reviewed measurement-window evidence, never merely collectionId. */
    record Sample(Definition definition, Subject subject, Long value, MissingReason missingReason,
                  MetricSource provenance, String alignmentId) {
        public Sample {
            Objects.requireNonNull(definition);
            Objects.requireNonNull(subject);
            Objects.requireNonNull(provenance);
            if (!definition.id().equals(provenance.definitionId()) || subject != definition.subject()
                    || (value == null) != (missingReason != null)) {
                throw new IllegalArgumentException("Invalid normalized sample");
            }
        }
    }

    record Channel(ChannelScope scope, String displayName, String followerDefinitionId) {}
    record Attempt(Long lastSuccessAt, Long lastAttemptAt, SyncState state,
                   boolean offline, List<DashboardError> errors) {
        public Attempt { errors = List.copyOf(errors); }
    }
    /** One consistent local read, including ownership-checked LOCAL PublishedPost metadata. */
    record Batch(boolean available, EvidenceSource source, List<Definition> definitions,
                 List<Channel> channels, List<PostMetadata> posts, List<Sample> samples,
                 Attempt attempt) {
        public Batch {
            Objects.requireNonNull(source);
            Objects.requireNonNull(attempt);
            definitions = List.copyOf(definitions);
            channels = List.copyOf(channels);
            posts = List.copyOf(posts);
            samples = List.copyOf(samples);
        }
    }

    /** Blocking local IO. Failures must throw, never return empty success. */
    Batch readCached(DashboardQuery query) throws SourceFailure;

    /** Delegate to a future sync coordinator; this module contains no network/collector. */
    Batch refreshProjection(DashboardQuery query) throws SourceFailure;

    /** Notify after local projection/connection changes. Registration does not refresh. */
    Cancelable watch(Runnable invalidated);

    /**
     * Serialize revision/connected/ownership checks and action against disconnect/reconnect.
     * Implement using the same coordination boundary as Channel invalidation. False drops work.
     * No credential fields cross this port. This is a required integration gate, not a preflight.
     */
    boolean withCurrentScopes(List<ChannelScope> scopes, Runnable action);

    final class SourceFailure extends Exception {
        public final List<DashboardError> errors;
        public final boolean offline;
        public SourceFailure(List<DashboardError> errors, boolean offline) {
            super("Analytics source failed");
            if (errors.isEmpty()) throw new IllegalArgumentException("Failure needs normalized error");
            this.errors = List.copyOf(errors);
            this.offline = offline;
        }
    }
}
