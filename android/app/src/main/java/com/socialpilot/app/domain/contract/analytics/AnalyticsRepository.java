package com.socialpilot.app.domain.contract.analytics;

import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.DashboardError;
import java.util.List;

import static com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.immutable;

/** SP-046.01 draft 0.1.0. Lifecycle and delivery semantics are pinned in the HO-DTO handoff. */
public interface AnalyticsRepository {
    /**
     * Subscribe to the exact query's local projection. Emit initial state and subsequent changes
     * serially on a worker thread. Subscribing never schedules a refresh.
     */
    Cancelable observeCachedDashboard(DashboardQuery query, DashboardObserver observer);

    /**
     * Explicit refresh. Deliver at most one terminal callback on a worker thread after publishing
     * the attempt to the cache projection. Cancellation suppresses future callback delivery.
     */
    Cancelable refresh(DashboardQuery query, RefreshCallback callback);

    interface DashboardObserver {
        void onChanged(DashboardData data);
    }

    interface RefreshCallback {
        void onComplete(RefreshResult result);
    }

    enum RefreshOutcome { SUCCESS, PARTIAL, FAILED }

    record RefreshResult(DashboardQuery query, RefreshOutcome outcome, List<DashboardError> errors) {
        public RefreshResult {
            errors = immutable(errors);
        }
    }
}
