package com.socialpilot.app.data.repository.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.EvidenceSource;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.ObservedAtBasis;
import com.socialpilot.app.domain.contract.analytics.DashboardData.PostMetadata;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.ChannelScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MO_PHONG raw in-memory source. Does not return any precomputed DashboardData. */
final class SimulatedAnalyticsSource implements AnalyticsSource {
    Batch cached = AnalyticsFixture.batch(List.of(), List.of());
    Batch refreshed;
    SourceFailure readFailure;
    SourceFailure refreshFailure;
    RuntimeException storageFailure;
    List<ChannelScope> connected = new ArrayList<>(List.of(AnalyticsFixture.SCOPE));
    final List<Runnable> listeners = new ArrayList<>();
    int refreshes;
    @Override public synchronized Batch readCached(DashboardQuery query) throws SourceFailure {
        if (storageFailure != null) throw storageFailure;
        if (readFailure != null) throw readFailure;
        return cached;
    }
    @Override public synchronized Batch refreshProjection(DashboardQuery query) throws SourceFailure {
        refreshes++;
        if (refreshFailure != null) throw refreshFailure;
        if (refreshed == null) return cached;
        Batch attempt = refreshed;
        if (attempt.available()) cached = commit(cached, attempt);
        return attempt;
    }
    private record PostKey(ChannelScope scope, String id) {}
    private record SampleKey(ChannelScope scope, Subject subject, String entityId,
                             String definitionId, String snapshotId, EvidenceSource source,
                             long observedAt, ObservedAtBasis basis, Long periodStart,
                             Long periodEnd, String apiVersion, String alignmentId) {}
    private static Batch commit(Batch previous, Batch attempt) {
        if (!previous.available() || previous.source() != attempt.source()) return attempt;
        Map<String, Definition> definitions = new LinkedHashMap<>();
        for (Definition d : previous.definitions()) definitions.put(d.id(), d);
        for (Definition d : attempt.definitions()) definitions.put(d.id(), d);
        Map<ChannelScope, Channel> channels = new LinkedHashMap<>();
        for (Channel c : previous.channels()) channels.put(c.scope(), c);
        for (Channel c : attempt.channels()) channels.put(c.scope(), c);
        Map<PostKey, PostMetadata> posts = new LinkedHashMap<>();
        for (PostMetadata p : previous.posts()) posts.put(new PostKey(p.scope(), p.postId()), p);
        for (PostMetadata p : attempt.posts()) posts.put(new PostKey(p.scope(), p.postId()), p);
        Map<SampleKey, Sample> samples = new LinkedHashMap<>();
        for (Sample s : previous.samples()) {
            if (s.definition().equals(definitions.get(s.definition().id())))
                samples.put(key(s), s);
        }
        for (Sample s : attempt.samples()) samples.put(key(s), s);
        return new Batch(true, attempt.source(), new ArrayList<>(definitions.values()),
                new ArrayList<>(channels.values()), new ArrayList<>(posts.values()),
                new ArrayList<>(samples.values()), attempt.attempt());
    }
    private static SampleKey key(Sample s) {
        return new SampleKey(s.provenance().scope(), s.subject(), s.provenance().entityId(),
                s.definition().id(), s.provenance().snapshotId(), s.provenance().source(),
                s.provenance().observedAt(), s.provenance().observedAtBasis(),
                s.provenance().providerPeriodStart(), s.provenance().providerPeriodEnd(),
                s.provenance().providerApiVersion(), s.alignmentId());
    }
    @Override public synchronized Cancelable watch(Runnable invalidated) {
        listeners.add(invalidated);
        return () -> { synchronized (this) { listeners.remove(invalidated); } };
    }
    @Override public synchronized boolean withCurrentScopes(List<ChannelScope> scopes, Runnable action) {
        if (!connected.containsAll(scopes)) return false;
        action.run();
        return true;
    }
    synchronized void changed() {
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }
    synchronized void revision(ChannelScope scope) {
        connected = new ArrayList<>(List.of(scope));
        cached = AnalyticsFixture.batch(List.of(), List.of());
        changed();
    }
    void assertSimulated() {
        if (cached.source() != EvidenceSource.MO_PHONG) throw new AssertionError("Fixture escaped namespace");
    }
}
