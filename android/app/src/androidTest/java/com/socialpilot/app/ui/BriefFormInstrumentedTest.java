package com.socialpilot.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.local.BriefDao;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class BriefFormInstrumentedTest {
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
