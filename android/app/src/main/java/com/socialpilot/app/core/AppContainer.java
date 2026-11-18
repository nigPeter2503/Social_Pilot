package com.socialpilot.app.core;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.room.Room;
import androidx.work.WorkManager;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.prototype.PrototypeDatabase;
import com.socialpilot.app.data.prototype.SampleRepository;
import com.socialpilot.app.prototype.DemoStreamCoordinator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public final class AppContainer {
    public final ExecutorService io = Executors.newFixedThreadPool(4);
    public final AppDatabase database;
    public final DraftRepository drafts;
    public final PrototypeDatabase prototypeDatabase;
    public final SampleRepository sampleResults;
    public final DemoStreamCoordinator sampleStreams;
    public AppContainer(Context context) {
        database = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "socialpilot.db").build();
        drafts = new DraftRepository(database.drafts(), io);
        prototypeDatabase = Room.databaseBuilder(context.getApplicationContext(),
                PrototypeDatabase.class, "socialpilot-prototype.db")
                .addMigrations(PrototypeDatabase.MIGRATION_1_2,
                        PrototypeDatabase.MIGRATION_2_3,
                        PrototypeDatabase.MIGRATION_3_4).build();
        sampleResults = new SampleRepository(prototypeDatabase.results(), Executors.newSingleThreadExecutor());
        Handler main = new Handler(Looper.getMainLooper());
        sampleStreams = new DemoStreamCoordinator((action, delay) ->
                main.postDelayed(action, delay));
        sampleResults.markInterruptedOnOpen();
        sampleResults.reconcileWorkOnOpen(WorkManager.getInstance(context.getApplicationContext()));
    }
}
