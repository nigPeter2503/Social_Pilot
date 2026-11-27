package com.socialpilot.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.widget.RadioButton;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.R;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AppLanguageInstrumentedTest {
    @Test public void changingAppLanguageKeepsContentLanguageSelection() throws Exception {
        try {
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
                String[] expected = new String[1];
                activity.onActivity(screen -> {
                    ((RadioButton) screen.findViewById(R.id.contentLanguageEn)).setChecked(true);
                    boolean currentlyVi = ((RadioButton)
                            screen.findViewById(R.id.appLanguageVi)).isChecked();
                    expected[0] = currentlyVi ? "en" : "vi";
                    ((RadioButton) screen.findViewById(currentlyVi
                            ? R.id.appLanguageEn : R.id.appLanguageVi)).performClick();
                });
                for (int attempt = 0; attempt < 100
                        && !expected[0].equals(AppCompatDelegate.getApplicationLocales()
                                .toLanguageTags()); attempt++) {
                    Thread.sleep(20);
                }
                assertEquals(expected[0], AppCompatDelegate.getApplicationLocales().toLanguageTags());
                activity.onActivity(screen -> {
                    assertTrue(((RadioButton) screen.findViewById("vi".equals(expected[0])
                            ? R.id.appLanguageVi : R.id.appLanguageEn)).isChecked());
                    assertTrue(((RadioButton)
                            screen.findViewById(R.id.contentLanguageEn)).isChecked());
                    assertEquals("vi".equals(expected[0]) ? "Bắt đầu một ý tưởng mới"
                            : "Start a new idea", screen.getString(R.string.title));
                });
                activity.recreate();
                activity.onActivity(screen -> {
                    assertTrue(((RadioButton) screen.findViewById("vi".equals(expected[0])
                            ? R.id.appLanguageVi : R.id.appLanguageEn)).isChecked());
                    assertTrue(((RadioButton)
                            screen.findViewById(R.id.contentLanguageEn)).isChecked());
                });
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()));
        }
    }
}
