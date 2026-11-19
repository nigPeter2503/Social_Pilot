package com.socialpilot.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.widget.Spinner;
import android.widget.TextView;
import com.socialpilot.app.R;
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
                ((Spinner) screen.findViewById(R.id.metricSelector))
                        .setSelection(DashboardViewModel.ENGAGEMENT);
            });
            activity.onActivity(screen -> {
                assertEquals("—", ((TextView) screen.findViewById(R.id.metricValue))
                        .getText().toString());
                assertEquals(DashboardViewModel.ENGAGEMENT,
                        ((Spinner) screen.findViewById(R.id.metricSelector)).getSelectedItemPosition());
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
}
