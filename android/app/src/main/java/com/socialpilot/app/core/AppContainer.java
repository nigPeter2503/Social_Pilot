package com.socialpilot.app.core;

import android.content.Context;
import androidx.room.Room;
import com.socialpilot.app.BuildConfig;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.repository.DraftRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppContainer {
    public final ExecutorService io = Executors.newFixedThreadPool(4);
    public final AppDatabase database;
    public final DraftRepository drafts;
    public final FacebookOAuthCoordinator facebookOAuth;
    public AppContainer(Context context) {
        database = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "socialpilot.db").build();
        drafts = new DraftRepository(database.drafts(), io);
        facebookOAuth = new FacebookOAuthCoordinator(context, io, BuildConfig.OAUTH_API_BASE_URL);
    }
}
