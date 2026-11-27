package com.socialpilot.app.core;

import android.app.Application;
import android.content.Context;
import androidx.test.runner.AndroidJUnitRunner;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.remote.social.DeviceMetricFixture;

/** Alternate runner supplies the same test-only bindings on both sides of a real process restart. */
public class MetricDeviceRunner extends AndroidJUnitRunner {
    @Override public Application newApplication(ClassLoader cl, String name, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.newApplication(getClass().getClassLoader(), FixtureApplication.class.getName(), context);
    }

    public static class FixtureApplication extends SocialPilotApplication {
        final DeviceMetricFixture fixture = new DeviceMetricFixture();
        @Override protected AppContainer createContainer() { return new AppContainer(this, fixture.coordinator); }
    }
}
