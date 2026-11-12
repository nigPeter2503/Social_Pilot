package com.socialpilot.app.prototype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
import com.socialpilot.app.ui.PrototypeViewModel;
import java.util.Objects;
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
                "NOT_STARTED", "sample-run", null, System.currentTimeMillis()));
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
                "NOT_STARTED", "run-a", null, System.currentTimeMillis()));
        WorkManagerTestInitHelper.initializeTestWorkManager(context);
        WorkManager manager = WorkManager.getInstance(context);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SampleCheckpointWorker.class)
                .setInputData(new Data.Builder()
                        .putString(SampleCheckpointWorker.KEY_CHANNEL_ID, "A")
                        .putString(SampleCheckpointWorker.KEY_RUN_ID, "run-a").build())
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        dao.assignWork("A", "run-a", request.getId().toString(), System.currentTimeMillis());
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
                "NOT_STARTED", "run-b", null, System.currentTimeMillis()));
        OneTimeWorkRequest pending = new OneTimeWorkRequest.Builder(SampleCheckpointWorker.class)
                .setInputData(new Data.Builder()
                        .putString(SampleCheckpointWorker.KEY_CHANNEL_ID, "B")
                        .putString(SampleCheckpointWorker.KEY_RUN_ID, "run-b").build())
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        dao.assignWork("B", "run-b", pending.getId().toString(), System.currentTimeMillis());
        manager.enqueueUniqueWork("sp017-test-cancel", ExistingWorkPolicy.REPLACE, pending)
                .getResult().get(10, TimeUnit.SECONDS);
        assertEquals(WorkInfo.State.ENQUEUED,
                manager.getWorkInfoById(pending.getId()).get(10, TimeUnit.SECONDS).getState());
        manager.cancelUniqueWork("sp017-test-cancel").getResult().get(10, TimeUnit.SECONDS);
        dao.invalidateWork("B", "run-b", System.currentTimeMillis());
        assertEquals(WorkInfo.State.CANCELLED,
                manager.getWorkInfoById(pending.getId()).get(10, TimeUnit.SECONDS).getState());
        assertEquals("NOT_STARTED", dao.get("B").checkpoint);
    }

    @Test
    public void cancelledOrReplacedRunCannotReceiveOldCheckpoint() {
        SampleResultDao dao = ((SocialPilotApplication) InstrumentationRegistry
                .getInstrumentation().getTargetContext().getApplicationContext())
                .container().prototypeDatabase.results();
        dao.save(new SampleResultEntity("A", "old result", "COMPLETE",
                "NOT_STARTED", "old-run", "old-job", System.currentTimeMillis()));
        SampleResultEntity workerSnapshot = dao.get("A");

        assertEquals(1, dao.invalidateWork("A", "old-run", System.currentTimeMillis()));
        assertEquals(0, dao.completeCheckpoint("A", workerSnapshot.runId,
                workerSnapshot.workRequestId, "SHA256:old", System.currentTimeMillis()));

        dao.save(new SampleResultEntity("A", "new result", "COMPLETE",
                "NOT_STARTED", "new-run", "new-job", System.currentTimeMillis()));
        assertEquals(0, dao.completeCheckpoint("A", workerSnapshot.runId,
                workerSnapshot.workRequestId, "SHA256:old", System.currentTimeMillis()));
        assertEquals(0, dao.updateRun("A", "old-run", "late text", "COMPLETE",
                System.currentTimeMillis()));
        assertEquals("new result", dao.get("A").text);
        assertEquals("NOT_STARTED", dao.get("A").checkpoint);
    }

    @Test
    public void secondViewModelInSameProcessDoesNotInterruptActiveStream() throws Exception {
        SocialPilotApplication app = (SocialPilotApplication) InstrumentationRegistry
                .getInstrumentation().getTargetContext().getApplicationContext();
        SampleResultDao dao = app.container().prototypeDatabase.results();
        SampleResultEntity before = dao.get("A");
        String previousRunId = before == null ? null : before.runId;
        PrototypeViewModel first = new PrototypeViewModel(app);
        first.startA();
        String runId = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            SampleResultEntity result = dao.get("A");
            if (result != null && "RUNNING".equals(result.state)
                    && !Objects.equals(previousRunId, result.runId)) {
                runId = result.runId;
                break;
            }
            Thread.sleep(25);
        }
        assertNotNull(runId);

        new PrototypeViewModel(app);
        Thread.sleep(300);
        assertEquals(runId, dao.get("A").runId);
        assertEquals("RUNNING", dao.get("A").state);
        first.cancelA();
    }

    @Test
    public void versionOnePrototypeDatabaseMigratesWithoutLosingResult() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "sp017-migration-test.db";
        context.deleteDatabase(name);
        SQLiteDatabase old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null);
        old.execSQL("CREATE TABLE sample_results (channelId TEXT NOT NULL PRIMARY KEY, "
                + "text TEXT NOT NULL, state TEXT NOT NULL, checkpoint TEXT NOT NULL, "
                + "updatedAt INTEGER NOT NULL)");
        old.execSQL("INSERT INTO sample_results VALUES ('A', 'kept text', 'COMPLETE', "
                + "'NOT_STARTED', 100)");
        old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        old.execSQL("INSERT INTO room_master_table VALUES (42, "
                + "'a0a614ff36ad96baaf1f1e071b12d704')");
        old.setVersion(1);
        old.close();

        PrototypeDatabase migrated = Room.databaseBuilder(context, PrototypeDatabase.class, name)
                .addMigrations(PrototypeDatabase.MIGRATION_1_2).build();
        SampleResultEntity result = migrated.results().get("A");
        assertEquals("kept text", result.text);
        assertEquals("COMPLETE", result.state);
        assertEquals("NOT_STARTED", result.checkpoint);
        assertTrue(result.runId.length() > 0);
        assertEquals(null, result.workRequestId);
        migrated.close();
        context.deleteDatabase(name);
    }
}
