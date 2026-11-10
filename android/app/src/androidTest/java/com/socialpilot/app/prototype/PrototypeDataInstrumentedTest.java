package com.socialpilot.app.prototype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.TestDriver;
import androidx.work.testing.WorkManagerTestInitHelper;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.local.DraftEntity;
import com.socialpilot.app.data.prototype.PrototypeDatabase;
import com.socialpilot.app.data.prototype.SampleResultDao;
import com.socialpilot.app.data.prototype.SampleResultEntity;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PrototypeDataInstrumentedTest {
    @Test
    public void separateRoomDatabaseKeepsExistingTopicsAfterReopen() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String baselineName = "sp017-baseline-test.db";
        String prototypeName = "sp017-prototype-test.db";
        context.deleteDatabase(baselineName);
        context.deleteDatabase(prototypeName);
        AppDatabase baseline = Room.databaseBuilder(context, AppDatabase.class, baselineName).build();
        PrototypeDatabase prototype = Room.databaseBuilder(context, PrototypeDatabase.class,
                prototypeName).build();
        baseline.drafts().insert(new DraftEntity("SP017 kept topic", System.currentTimeMillis()));
        prototype.results().save(new SampleResultEntity("A", "Sample text", "COMPLETE",
                "NOT_STARTED", System.currentTimeMillis()));
        baseline.close();
        prototype.close();

        baseline = Room.databaseBuilder(context, AppDatabase.class, baselineName).build();
        prototype = Room.databaseBuilder(context, PrototypeDatabase.class, prototypeName).build();
        try (Cursor topics = baseline.getOpenHelper().getReadableDatabase()
                .query("SELECT topic FROM drafts")) {
            assertTrue(topics.moveToFirst());
            assertEquals("SP017 kept topic", topics.getString(0));
        }
        assertEquals("Sample text", prototype.results().get("A").text);
        baseline.close();
        prototype.close();
        context.deleteDatabase(baselineName);
        context.deleteDatabase(prototypeName);
    }

    @Test
    public void networkConstrainedWorkerWaitsThenWritesCheckpoint() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SampleResultDao dao = ((SocialPilotApplication) context.getApplicationContext())
                .container().prototypeDatabase.results();
        dao.save(new SampleResultEntity("A", "Worker test text", "COMPLETE",
                "NOT_STARTED", System.currentTimeMillis()));
        WorkManagerTestInitHelper.initializeTestWorkManager(context);
        WorkManager manager = WorkManager.getInstance(context);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SampleCheckpointWorker.class)
                .setInputData(new Data.Builder()
                        .putString(SampleCheckpointWorker.KEY_CHANNEL_ID, "A").build())
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        manager.enqueue(request).getResult().get(10, TimeUnit.SECONDS);
        assertEquals(WorkInfo.State.ENQUEUED,
                manager.getWorkInfoById(request.getId()).get(10, TimeUnit.SECONDS).getState());

        TestDriver driver = WorkManagerTestInitHelper.getTestDriver(context);
        assertNotNull(driver);
        driver.setAllConstraintsMet(request.getId());

        assertEquals(WorkInfo.State.SUCCEEDED,
                manager.getWorkInfoById(request.getId()).get(10, TimeUnit.SECONDS).getState());
        assertTrue(dao.get("A").checkpoint.startsWith("SHA256:"));

        dao.save(new SampleResultEntity("B", "Cancellation test", "COMPLETE",
                "NOT_STARTED", System.currentTimeMillis()));
        OneTimeWorkRequest pending = new OneTimeWorkRequest.Builder(SampleCheckpointWorker.class)
                .setInputData(new Data.Builder()
                        .putString(SampleCheckpointWorker.KEY_CHANNEL_ID, "B").build())
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        manager.enqueueUniqueWork("sp017-test-cancel", ExistingWorkPolicy.REPLACE, pending)
                .getResult().get(10, TimeUnit.SECONDS);
        assertEquals(WorkInfo.State.ENQUEUED,
                manager.getWorkInfoById(pending.getId()).get(10, TimeUnit.SECONDS).getState());
        manager.cancelUniqueWork("sp017-test-cancel").getResult().get(10, TimeUnit.SECONDS);
        assertEquals(WorkInfo.State.CANCELLED,
                manager.getWorkInfoById(pending.getId()).get(10, TimeUnit.SECONDS).getState());
        assertEquals("NOT_STARTED", dao.get("B").checkpoint);
    }
}
