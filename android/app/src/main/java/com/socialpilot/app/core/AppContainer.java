package com.socialpilot.app.core;
import android.content.Context;
import androidx.room.Room;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.prototype.PrototypeDatabase;
import com.socialpilot.app.data.prototype.SampleRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public final class AppContainer {
    public final ExecutorService io = Executors.newFixedThreadPool(4);
    public final AppDatabase database;
    public final DraftRepository drafts;
    public final PrototypeDatabase prototypeDatabase;
    public final SampleRepository sampleResults;
    public AppContainer(Context context) {
        database = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "socialpilot.db").build();
        drafts = new DraftRepository(database.drafts(), io);
        prototypeDatabase = Room.databaseBuilder(context.getApplicationContext(),
                PrototypeDatabase.class, "socialpilot-prototype.db").build();
        sampleResults = new SampleRepository(prototypeDatabase.results(), Executors.newSingleThreadExecutor());
    }
}
