package com.socialpilot.app.data.repository.analytics;

import com.socialpilot.app.domain.analytics.AnalyticsSource;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.EvidenceSource;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery;
import com.socialpilot.app.domain.contract.analytics.DashboardQuery.ChannelScope;
import java.util.ArrayList;
import java.util.List;

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
        if (refreshed != null) cached = refreshed;
        return cached;
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
        changed();
    }
    void assertSimulated() {
        if (cached.source() != EvidenceSource.MO_PHONG) throw new AssertionError("Fixture escaped namespace");
    }
}
