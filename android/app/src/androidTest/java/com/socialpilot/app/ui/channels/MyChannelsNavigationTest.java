package com.socialpilot.app.ui.channels;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.R;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.ui.MainActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MyChannelsNavigationTest {
    @Test public void opensFromHomeAndReturnsToHome() {
        try (ActivityScenario<MainActivity> home = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.my_channels_button)).perform(click());
            onView(withId(R.id.channels_title)).check(matches(withText(R.string.my_channels)));
            pressBack();
            onView(withId(R.id.my_channels_button)).check(matches(isDisplayed()));
        }
    }

    @Test public void opensAiKeysFromTheSameHomeScreen() {
        try (ActivityScenario<MainActivity> home = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.openAiKeysButton)).perform(scrollTo(), click());
            onView(withText(R.string.ai_keys_title)).check(matches(isDisplayed()));
            pressBack();
            onView(withId(R.id.openAiKeysButton)).check(matches(isDisplayed()));
        }
    }

    @Test public void connectionShortcutsReachTheirProvidersWithoutLaunchingOAuthOnRecreation() {
        Instrumentation device = InstrumentationRegistry.getInstrumentation();
        IntentFilter browser = new IntentFilter(Intent.ACTION_VIEW);
        browser.addDataScheme("https");
        Instrumentation.ActivityMonitor monitor = device.addMonitor(browser, null, true);
        Intent launch = new Intent(device.getTargetContext(), MainActivity.class);
        try (ActivityScenario<MainActivity> home = ActivityScenario.launch(launch)) {
            home.onActivity(activity -> MainActivity.openConnection(activity, Platform.INSTAGRAM));
            onView(withId(R.id.connectInstagramButton)).check(matches(isDisplayed()));
            home.onActivity(activity -> MainActivity.openConnection(activity, Platform.FACEBOOK));
            onView(withId(R.id.connectFacebookButton)).check(matches(isDisplayed()));
            home.recreate();
            onView(withId(R.id.connectFacebookButton)).check(matches(isDisplayed()));
            assertEquals(0, monitor.getHits());
        } finally {
            device.removeMonitor(monitor);
        }
    }

    @Test public void staleCallbackWithoutPendingSessionKeepsHomeAvailable() {
        Instrumentation device = InstrumentationRegistry.getInstrumentation();
        Intent callback = new Intent(Intent.ACTION_VIEW,
                Uri.parse("socialpilot://oauth/callback?sessionId=old-session"),
                device.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        device.getTargetContext().startActivity(callback);
        device.waitForIdleSync();
        onView(withId(R.id.my_channels_button)).check(matches(isDisplayed()));
    }

    @Test public void remainsOpenAfterActivityRecreation() {
        try (ActivityScenario<MyChannelsActivity> screen = ActivityScenario.launch(MyChannelsActivity.class)) {
            screen.recreate();
            onView(withId(R.id.channels_title)).check(matches(isDisplayed()));
        }
    }
}
