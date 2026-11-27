package com.socialpilot.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.database.Cursor;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.local.BriefDao;
import com.socialpilot.app.data.repository.ChannelRepository;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class BriefFormInstrumentedTest {
    @Test public void inputsStayLockedUntilSavedSnapshotCompletes() throws Exception {
        SocialPilotApplication app = (SocialPilotApplication) InstrumentationRegistry
                .getInstrumentation().getTargetContext().getApplicationContext();
        String topic = "SP035 slow save " + UUID.randomUUID();
        ExecutorService blocker = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            activity.onActivity(screen -> {
                ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.topicInput))
                        .setText(topic);
                ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.goalInput))
                        .setText("Goal");
                ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.audienceInput))
                        .setText("Audience");
                ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.toneInput))
                        .setText("Tone");
            });
            Future<?> hold = blocker.submit(() -> {
                app.container().database.beginTransaction();
                locked.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                finally { app.container().database.endTransaction(); }
            });
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            activity.onActivity(screen -> {
                assertTrue(screen.findViewById(com.socialpilot.app.R.id.saveButton).performClick());
                assertFalse(screen.findViewById(com.socialpilot.app.R.id.topicInput).isEnabled());
                assertFalse(screen.findViewById(com.socialpilot.app.R.id.sourceInput).isEnabled());
            });
            release.countDown();
            hold.get(10, TimeUnit.SECONDS);
            for (int attempt = 0; attempt < 100 && app.container().database.briefs()
                    .countByTopic(topic) == 0; attempt++) Thread.sleep(20);
            assertEquals(1, app.container().database.briefs().countByTopic(topic));
            boolean[] cleared = new boolean[1];
            for (int attempt = 0; attempt < 100 && !cleared[0]; attempt++) {
                activity.onActivity(screen -> {
                    android.widget.EditText input = screen.findViewById(com.socialpilot.app.R.id.topicInput);
                    cleared[0] = input.isEnabled() && input.getText().length() == 0;
                });
                if (!cleared[0]) Thread.sleep(20);
            }
            assertTrue(cleared[0]);
        } finally {
            release.countDown();
            blocker.shutdownNow();
            app.container().database.getOpenHelper().getWritableDatabase().execSQL(
                    "DELETE FROM briefs WHERE topic = ?", new Object[] {topic});
        }
    }

    @Test public void selectedChannelSurvivesRotationBeforeSave() throws Exception {
        SocialPilotApplication app = (SocialPilotApplication) InstrumentationRegistry
                .getInstrumentation().getTargetContext().getApplicationContext();
        String accountId = String.valueOf(System.currentTimeMillis());
        String topic = "SP035 selected " + UUID.randomUUID();
        ChannelRepository.Snapshot channel = app.container().channels.save(new OAuthExchangeResponse(
                UUID.randomUUID().toString(), "FACEBOOK", accountId, "PAGE", "SP035 Page " + accountId,
                "fixture-token", null, "bearer", null));
        try {
            try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
                CheckBox[] selected = new CheckBox[1];
                for (int attempt = 0; attempt < 100 && selected[0] == null; attempt++) {
                    activity.onActivity(screen -> selected[0] = findTarget(screen, accountId));
                    if (selected[0] == null) Thread.sleep(20);
                }
                assertTrue(selected[0] != null);
                activity.onActivity(screen -> {
                    findTarget(screen, accountId).performClick();
                    assertTrue(findTarget(screen, accountId).isChecked());
                });
                activity.recreate();
                activity.onActivity(screen -> {
                    CheckBox target = findTarget(screen, accountId);
                    assertTrue(target != null && target.isChecked());
                    ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.topicInput))
                            .setText(topic);
                    ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.goalInput))
                            .setText("Goal");
                    ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.audienceInput))
                            .setText("Audience");
                    ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.toneInput))
                            .setText("Tone");
                    screen.findViewById(com.socialpilot.app.R.id.saveButton).performClick();
                });
                for (int attempt = 0; attempt < 100 && app.container().database.briefs()
                        .countByTopic(topic) == 0; attempt++) Thread.sleep(20);
                assertEquals(1, app.container().database.briefs().countByTopic(topic));
                try (Cursor rows = app.container().database.getOpenHelper().getReadableDatabase().query(
                        "SELECT t.channelId FROM brief_targets t JOIN briefs b ON b.briefId = t.briefId "
                                + "WHERE b.topic = ?", new Object[] {topic})) {
                    assertTrue(rows.moveToFirst());
                    assertEquals(channel.info.channelId, rows.getString(0));
                    assertFalse(rows.moveToNext());
                }
            }
        } finally {
            app.container().database.getOpenHelper().getWritableDatabase().execSQL(
                    "DELETE FROM briefs WHERE topic = ?", new Object[] {topic});
            app.container().database.getOpenHelper().getWritableDatabase().execSQL(
                    "DELETE FROM channels WHERE channelId = ?", new Object[] {channel.info.channelId});
            app.container().socialSecrets.delete(SecretRef.parse(channel.info.tokenReference));
            app.container().channels.restore();
        }
    }

    private static CheckBox findTarget(MainActivity screen, String accountId) {
        LinearLayout list = screen.findViewById(com.socialpilot.app.R.id.targetList);
        for (int index = 0; index < list.getChildCount(); index++) {
            CheckBox box = (CheckBox) list.getChildAt(index);
            if (box.getText().toString().contains(accountId)) return box;
        }
        return null;
    }

    @Test public void savingThenRotatingDoesNotDuplicateBrief() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BriefDao dao = ((SocialPilotApplication) context.getApplicationContext())
                .container().database.briefs();
        String topic = "SP035 rotation " + UUID.randomUUID();
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            activity.onActivity(screen -> {
                ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.topicInput))
                        .setText(topic);
                ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.goalInput))
                        .setText("Goal");
                ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.audienceInput))
                        .setText("Audience");
                ((android.widget.EditText) screen.findViewById(com.socialpilot.app.R.id.toneInput))
                        .setText("Tone");
                screen.findViewById(com.socialpilot.app.R.id.saveButton).performClick();
            });
            for (int attempt = 0; attempt < 100 && dao.countByTopic(topic) == 0; attempt++) {
                Thread.sleep(20);
            }
            assertEquals(1, dao.countByTopic(topic));
            String[] visible = new String[1];
            for (int attempt = 0; attempt < 100; attempt++) {
                activity.onActivity(screen -> visible[0] = ((android.widget.TextView)
                        screen.findViewById(com.socialpilot.app.R.id.draftList))
                        .getText().toString());
                if (visible[0].contains(topic) && visible[0].contains("Goal")
                        && visible[0].contains("Audience")) break;
                Thread.sleep(20);
            }
            assertTrue(visible[0].contains(topic) && visible[0].contains("Goal")
                    && visible[0].contains("Audience"));
            activity.recreate();
            assertEquals(1, dao.countByTopic(topic));
        }
    }
}
