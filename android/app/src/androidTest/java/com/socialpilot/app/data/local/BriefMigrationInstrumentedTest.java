package com.socialpilot.app.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.data.repository.BriefRepository;
import com.socialpilot.app.domain.editor.Brief;
import com.socialpilot.app.domain.editor.BriefForm;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class BriefMigrationInstrumentedTest {
    @Rule public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @Test public void v1MigrationKeepsLegacyRowsAndAutoincrement() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "sp035-migration.db";
        context.deleteDatabase(name);
        SupportSQLiteDatabase old = helper.createDatabase(name, 1);
        old.execSQL("INSERT INTO drafts (id, topic, createdAt) VALUES (7, 'same', 100)");
        old.execSQL("INSERT INTO drafts (id, topic, createdAt) VALUES (11, 'same', 100)");
        old.execSQL("INSERT INTO drafts (id, topic, createdAt) VALUES (42, NULL, 200)");
        old.execSQL("INSERT INTO drafts (id, topic, createdAt) VALUES (50, 'deleted', 300)");
        old.execSQL("DELETE FROM drafts WHERE id = 50");
        old.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(name, 2, true,
                AppDatabase.MIGRATION_1_2);
        try (Cursor rows = migrated.query("SELECT id, topic, createdAt FROM drafts "
                + "ORDER BY createdAt DESC, id DESC")) {
            assertEquals(3, rows.getCount());
            assertTrue(rows.moveToFirst());
            assertEquals(42, rows.getLong(0));
            assertTrue(rows.isNull(1));
            assertEquals(200, rows.getLong(2));
            assertTrue(rows.moveToNext());
            assertEquals(11, rows.getLong(0));
            assertEquals("same", rows.getString(1));
            assertTrue(rows.moveToNext());
            assertEquals(7, rows.getLong(0));
        }
        migrated.close();

        AppDatabase reopened = Room.databaseBuilder(context, AppDatabase.class, name)
                .addMigrations(AppDatabase.MIGRATION_1_2,
                        AppDatabase.MIGRATION_2_3).build();
        try {
            assertTrue(reopened.drafts().insert(new DraftEntity("new", 400)) > 50);
            assertNull(reopened.briefs().get("missing"));
        } finally {
            reopened.close();
            context.deleteDatabase(name);
        }
    }

    @Test public void briefAndSourcesSurviveDatabaseReopen() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "sp035-reopen.db";
        context.deleteDatabase(name);
        ExecutorService io = Executors.newSingleThreadExecutor();
        String id;
        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, name).build();
        try {
            BriefRepository repository = new BriefRepository(database.briefs(), io);
            BriefForm form = new BriefForm("Topic", "Goal", "Audience", "Tone", "en",
                    Arrays.asList(new Brief.Target("fb-internal", "FACEBOOK"),
                            new Brief.Target("li-internal", "LINKEDIN")),
                    Arrays.asList(new Brief.Source("TEXT", "Source text"),
                            new Brief.Source("URL", "https://example.org")));
            CountDownLatch saved = new CountDownLatch(1);
            String[] result = new String[1];
            repository.saveDraft(form, (briefId, ok) -> {
                if (ok) result[0] = briefId;
                saved.countDown();
            });
            assertTrue(saved.await(10, TimeUnit.SECONDS));
            id = result[0];
            assertNotNull(id);
        } finally {
            database.close();
            io.shutdownNow();
        }

        database = Room.databaseBuilder(context, AppDatabase.class, name).build();
        ExecutorService readIo = Executors.newSingleThreadExecutor();
        try {
            BriefEntity row = database.briefs().get(id);
            assertEquals("en", row.contentLanguage);
            assertEquals("Goal", row.goal);
            assertNull(row.legacyDraftId);
            assertEquals(2, database.briefs().sources(id).size());
            assertEquals("Source text", database.briefs().sources(id).get(0).value);
            assertEquals(2, database.briefs().targets(id).size());
            Brief payload = new BriefRepository(database.briefs(), readIo).loadValidated(id);
            assertEquals(id, payload.briefId());
            assertEquals(2, payload.targets().size());
            assertFalse(payload.sources().isEmpty());
        } finally {
            database.close();
            readIo.shutdownNow();
            context.deleteDatabase(name);
        }
    }
}
