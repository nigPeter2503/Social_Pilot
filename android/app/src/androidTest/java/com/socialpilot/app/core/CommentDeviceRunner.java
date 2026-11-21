package com.socialpilot.app.core;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import androidx.test.runner.AndroidJUnitRunner;
import com.socialpilot.app.data.remote.social.DeviceCommentFixture;
import com.socialpilot.app.data.repository.comment.CommentRepository;

public final class CommentDeviceRunner extends AndroidJUnitRunner {
    @Override public Application newApplication(ClassLoader cl, String name, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return super.newApplication(getClass().getClassLoader(), FixtureApp.class.getName(), context);
    }

    public static final class FixtureApp extends Application implements CommentSyncWorker.Provider {
        private DeviceCommentFixture fixture;
        synchronized void resetFixture() { fixture = null; }
        synchronized DeviceCommentFixture fixture() {
            ConnectivityManager net = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (fixture == null) fixture = new DeviceCommentFixture(() -> net.getActiveNetwork() != null);
            return fixture;
        }
        @Override public CommentRepository comments() { return fixture().repo; }
    }
}
