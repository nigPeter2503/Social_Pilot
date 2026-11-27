package com.socialpilot.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.app.Activity;
import android.widget.Spinner;
import android.widget.TextView;
import com.socialpilot.app.R;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class DashboardInstrumentedTest {
    @Test public void fixtureIsLabelledAndMissingMetricSurvivesRecreation() {
        try (ActivityScenario<DashboardActivity> activity =
                     ActivityScenario.launch(DashboardActivity.class)) {
            activity.onActivity(screen -> {
                assertTrue(((TextView) screen.findViewById(R.id.fixtureNotice))
                        .getText().toString().contains("MO_PHONG"));
                assertEquals("100", ((TextView) screen.findViewById(R.id.metricValue))
                        .getText().toString());
                String observed = observedAtLabel(screen);
                assertEquals(screen.getString(R.string.dashboard_observed_at, observed),
                        ((TextView) screen.findViewById(R.id.metricDetail)).getText().toString());
                assertEquals(screen.getString(R.string.dashboard_sample_at, observed, 100),
                        ((TextView) screen.findViewById(R.id.timelineEnd)).getText().toString());
                ((Spinner) screen.findViewById(R.id.metricSelector))
                        .setSelection(DashboardViewModel.ENGAGEMENT);
            });
            activity.onActivity(screen -> {
                assertEquals("—", ((TextView) screen.findViewById(R.id.metricValue))
                        .getText().toString());
                assertEquals(DashboardViewModel.ENGAGEMENT,
                        ((Spinner) screen.findViewById(R.id.metricSelector)).getSelectedItemPosition());
                assertEquals(screen.getString(R.string.dashboard_missing_reason,
                                screen.getString(R.string.dashboard_reason_not_synced)),
                        ((TextView) screen.findViewById(R.id.metricDetail)).getText().toString());
            });
            activity.recreate();
            activity.onActivity(screen -> {
                assertEquals("—", ((TextView) screen.findViewById(R.id.metricValue))
                        .getText().toString());
                assertEquals(DashboardViewModel.ENGAGEMENT,
                        ((Spinner) screen.findViewById(R.id.metricSelector)).getSelectedItemPosition());
            });
        }
    }

    private static String observedAtLabel(Activity screen) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                    screen.getResources().getConfiguration().locale)
                    .format(input.parse("2026-11-08T17:00:00Z"));
        } catch (java.text.ParseException error) {
            throw new AssertionError(error);
        }
    }
}
