package com.socialpilot.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Intent;
import android.widget.EditText;
import android.widget.CompoundButton;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.R;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SharedUrlInstrumentedTest {
    @Test public void sharesheetFillsSourceButDoesNotSaveAndSurvivesRecreation() {
        Intent share = new Intent(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), MainActivity.class);
        share.setAction(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "Article: https://example.com/story");
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(share)) {
            activity.onActivity(screen -> {
                assertEquals("https://example.com/story",
                        ((EditText) screen.findViewById(R.id.sourceInput)).getText().toString());
                assertTrue(((CompoundButton) screen.findViewById(R.id.sourceUrlSwitch)).isChecked());
                assertEquals("", ((EditText) screen.findViewById(R.id.topicInput))
                        .getText().toString());
                assertEquals(Intent.ACTION_SEND, screen.getIntent().getAction());
            });
            activity.recreate();
            activity.onActivity(screen -> assertEquals("https://example.com/story",
                    ((EditText) screen.findViewById(R.id.sourceInput)).getText().toString()));
        }
    }

    @Test public void unsupportedMimeDoesNotFillBrief() {
        Intent share = new Intent(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), MainActivity.class);
        share.setAction(Intent.ACTION_SEND);
        share.setType("image/png");
        share.putExtra(Intent.EXTRA_TEXT, "https://example.com/story");
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(share)) {
            activity.onActivity(screen -> assertEquals("",
                    ((EditText) screen.findViewById(R.id.sourceInput)).getText().toString()));
        }
    }

    @Test public void cancellingReplacementKeepsExistingSource() {
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            activity.onActivity(screen -> {
                ((EditText) screen.findViewById(R.id.sourceInput)).setText("Existing source");
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, "https://example.com/new");
                screen.onNewIntent(share);
            });
            onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click());
            activity.onActivity(screen -> assertEquals("Existing source",
                    ((EditText) screen.findViewById(R.id.sourceInput)).getText().toString()));
        }
    }
}
