package com.socialpilot.app.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.LocaleManager;
import android.os.Build;
import android.os.LocaleList;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.R;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.core.AppContainer;
import com.socialpilot.app.data.repository.ChannelRepository.Snapshot;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MainActivityConnectionDeviceTest {
    private static final String ACCOUNT = "9876543210";

    @Test public void coldActivityShowsInstagramReconnectInVietnameseAndEnglish() throws Exception {
        SocialPilotApplication app = (SocialPilotApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        // This test exercises the real AppContainer on the isolated SP-022 QA package only.
        assumeTrue(app.getPackageName().endsWith(".sp022review") && Build.VERSION.SDK_INT >= 33);
        AppContainer container = app.container();
        assertTrue(container.channels.list().isEmpty());
        LocaleManager locales = app.getSystemService(LocaleManager.class);
        LocaleList original = locales.getApplicationLocales();
        try {
            locales.setApplicationLocales(LocaleList.forLanguageTags("vi"));
            assertInstagramState("Chưa kết nối Instagram.");
            Snapshot expired = container.channels.save(connection("expired", "2020-01-01T00:00:00Z"));
            assertInstagramState("Cần kết nối lại Instagram @fixture (" + ACCOUNT + ").");

            Snapshot revoked = container.channels.save(connection("revoked", null));
            assertTrue(container.channels.requireReconnect(revoked.scope));
            locales.setApplicationLocales(LocaleList.forLanguageTags("en"));
            assertInstagramState("Reconnect Instagram account @fixture (" + ACCOUNT + ").");
        } finally {
            Snapshot selected = container.channels.selected(Platform.INSTAGRAM);
            if (selected != null) {
                container.database.getOpenHelper().getWritableDatabase().execSQL(
                        "DELETE FROM channels WHERE channelId = ?", new Object[] {selected.scope.channelId});
                container.socialSecrets.delete(SecretRef.parse(selected.info.tokenReference));
                container.channels.restore();
            }
            locales.setApplicationLocales(original);
        }
    }

    private static void assertInstagramState(String expected) throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<String> text = new AtomicReference<>();
            for (int i = 0; i < 30; i++) {
                scenario.onActivity(activity -> text.set(
                        ((TextView) activity.findViewById(R.id.instagramAccountStatus)).getText().toString()));
                if (expected.equals(text.get())) break;
                Thread.sleep(50);
            }
            onView(withId(R.id.instagramAccountStatus)).check(matches(withText(expected)));
            onView(withId(R.id.instagramPublish)).check(matches(not(isEnabled())));
            onView(withId(R.id.connectInstagramButton)).check(matches(isEnabled()));
        }
    }

    private static OAuthExchangeResponse connection(String session, String expiresAt) {
        return new OAuthExchangeResponse(session, "INSTAGRAM", ACCOUNT, "PROFESSIONAL", "fixture",
                "test-token", null, "bearer", expiresAt, "1234567890",
                Collections.singletonList("instagram_basic"), "v27.0");
    }
}
