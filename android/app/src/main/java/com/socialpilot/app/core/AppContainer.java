package com.socialpilot.app.core;

import android.content.Context;
import androidx.room.Room;
import com.socialpilot.app.BuildConfig;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.repository.metrics.MetricCollectionCoordinator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppContainer {
    public final ExecutorService io = Executors.newFixedThreadPool(4);
    public final AppDatabase database;
    public final DraftRepository drafts;
    public final MetricCollectionWork metrics;
    public final FacebookOAuthCoordinator facebookOAuth;
    public AppContainer(Context context) { this(context, null); }

    public AppContainer(Context context, MetricCollectionCoordinator collector) {
        database = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "socialpilot.db").build();
        drafts = new DraftRepository(database.drafts(), io);
        // Wire the production coordinator once the Channel and Snapshot Room ports are available.
        metrics = new MetricCollectionWork(context, collector, System::currentTimeMillis);
        facebookOAuth = new FacebookOAuthCoordinator(context, io, BuildConfig.OAUTH_API_BASE_URL);
    }
}
