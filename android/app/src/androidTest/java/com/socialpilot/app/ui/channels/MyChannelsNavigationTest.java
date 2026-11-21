package com.socialpilot.app.ui.channels;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.socialpilot.app.R;
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

    @Test public void remainsOpenAfterActivityRecreation() {
        try (ActivityScenario<MyChannelsActivity> screen = ActivityScenario.launch(MyChannelsActivity.class)) {
            screen.recreate();
            onView(withId(R.id.channels_title)).check(matches(isDisplayed()));
        }
    }
}
