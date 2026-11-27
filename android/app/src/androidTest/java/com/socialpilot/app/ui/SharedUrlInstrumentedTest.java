package com.socialpilot.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Intent;
import android.database.Cursor;
import android.widget.EditText;
import android.widget.CompoundButton;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.R;
import com.socialpilot.app.SocialPilotApplication;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SharedUrlInstrumentedTest {
    @Test public void sharedPublicUrlIsPersistedAsBriefSourceOnlyAfterSave() throws Exception {
        SocialPilotApplication app = (SocialPilotApplication) InstrumentationRegistry
                .getInstrumentation().getTargetContext().getApplicationContext();
        String topic = "SP037 shared " + UUID.randomUUID();
        String url = "https://example.com/story?source=sharesheet";
        Intent share = new Intent(app, MainActivity.class);
        share.setAction(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "Read this: " + url);
        try {
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(share)) {
                activity.onActivity(screen -> {
                    assertEquals(url, ((EditText) screen.findViewById(R.id.sourceInput))
                            .getText().toString());
                    ((EditText) screen.findViewById(R.id.topicInput)).setText(topic);
                    ((EditText) screen.findViewById(R.id.goalInput)).setText("Goal");
                    ((EditText) screen.findViewById(R.id.audienceInput)).setText("Audience");
                    ((EditText) screen.findViewById(R.id.toneInput)).setText("Tone");
                    screen.findViewById(R.id.saveButton).performClick();
                });
                for (int attempt = 0; attempt < 100 && app.container().database.briefs()
                        .countByTopic(topic) == 0; attempt++) Thread.sleep(20);
                assertEquals(1, app.container().database.briefs().countByTopic(topic));
                try (Cursor rows = app.container().database.getOpenHelper().getReadableDatabase()
                        .query("SELECT s.kind, s.value FROM brief_sources s JOIN briefs b "
                                        + "ON b.briefId = s.briefId WHERE b.topic = ?",
                                new Object[] {topic})) {
                    assertTrue(rows.moveToFirst());
                    assertEquals("URL", rows.getString(0));
                    assertEquals(url, rows.getString(1));
                    assertTrue(!rows.moveToNext());
                }
            }
        } finally {
            app.container().database.getOpenHelper().getWritableDatabase().execSQL(
                    "DELETE FROM briefs WHERE topic = ?", new Object[] {topic});
        }
    }

    @Test public void closingSharedUrlWithoutSavingCreatesNoBrief() {
        SocialPilotApplication app = (SocialPilotApplication) InstrumentationRegistry
                .getInstrumentation().getTargetContext().getApplicationContext();
        String topic = "SP037 cancelled " + UUID.randomUUID();
        Intent share = new Intent(app, MainActivity.class);
        share.setAction(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, "https://example.com/cancelled");
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(share)) {
            activity.onActivity(screen -> {
                assertEquals("https://example.com/cancelled",
                        ((EditText) screen.findViewById(R.id.sourceInput)).getText().toString());
                ((EditText) screen.findViewById(R.id.topicInput)).setText(topic);
                screen.finish();
            });
        }
        assertEquals(0, app.container().database.briefs().countByTopic(topic));
    }

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
