package com.socialpilot.app.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

/** Fixture-only presentation state until SP-046 supplies the analytics repository. */
public final class DashboardViewModel extends ViewModel {
    public static final int FOLLOWERS = 0;
    public static final int ENGAGEMENT = 1;
    private static final String METRIC_KEY = "dashboard_metric";
    private final SavedStateHandle savedState;

    public static final class PreviewValue {
        public final Integer value;
        public final String observedAt;
        public final String missingReason;
        public final String gapAt;
        public final String gapReason;

        private PreviewValue(Integer value, String observedAt, String missingReason,
                String gapAt, String gapReason) {
            this.value = value;
            this.observedAt = observedAt;
            this.missingReason = missingReason;
            this.gapAt = gapAt;
            this.gapReason = gapReason;
        }
    }

    public DashboardViewModel(SavedStateHandle savedState) {
        this.savedState = savedState;
    }

    public LiveData<Integer> selectedMetric() {
        return savedState.getLiveData(METRIC_KEY, FOLLOWERS);
    }

    public void selectMetric(int metric) {
        if (metric != FOLLOWERS && metric != ENGAGEMENT) {
            throw new IllegalArgumentException("unknown dashboard metric");
        }
        Integer current = selectedMetric().getValue();
        if (current == null || current != metric) {
            savedState.set(METRIC_KEY, metric);
        }
    }

    public PreviewValue previewValue(int metric) {
        if (metric == FOLLOWERS) {
            // docs/contracts/analytics-examples.json, mock-snapshot-1 (MO_PHONG).
            return new PreviewValue(100, "2026-11-08T17:00:00Z", null,
                    "2026-11-01T17:00:00Z", "NOT_SYNCED");
        }
        if (metric == ENGAGEMENT) return new PreviewValue(null, null, "NOT_SYNCED",
                "2026-11-01T17:00:00Z", "NOT_SYNCED");
        throw new IllegalArgumentException("unknown dashboard metric");
    }
}
