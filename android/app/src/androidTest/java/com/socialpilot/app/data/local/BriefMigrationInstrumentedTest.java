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
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Scope;
import java.util.Arrays;
import java.util.Collections;
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

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(name, 4, true,
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4);
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
                        AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build();
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
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT INTO channels (channelId, platform, accountType, externalAccountId, "
                            + "displayName, status, sessionId, tokenType, scopesJson, capabilitiesJson, "
                            + "connectionRevision, selected) VALUES "
                            + "('fb-internal', 'FACEBOOK', 'PAGE', 'page-1', 'Page', 'CONNECTED', "
                            + "'session-1', 'bearer', '[]', '[]', 1, 1)");
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT INTO channels (channelId, platform, accountType, externalAccountId, "
                            + "displayName, status, sessionId, tokenType, scopesJson, capabilitiesJson, "
                            + "connectionRevision, selected) VALUES "
                            + "('li-internal', 'LINKEDIN', 'PAGE', 'page-2', 'Page', 'CONNECTED', "
                            + "'session-2', 'bearer', '[]', '[]', 1, 1)");
            BriefRepository repository = new BriefRepository(database.briefs(), io);
            BriefForm form = new BriefForm("Topic", "Goal", "Audience", "Tone", "en",
                    Arrays.asList(new Brief.Target("fb-internal", "FACEBOOK"),
                            new Brief.Target("li-internal", "LINKEDIN")),
                    Arrays.asList(new Brief.Source("TEXT", "Source text"),
                            new Brief.Source("URL", "https://example.org")));
            CountDownLatch saved = new CountDownLatch(1);
            String[] result = new String[1];
            repository.saveDraft(form, Arrays.asList(new Scope("FACEBOOK", "fb-internal", 1),
                    new Scope("LINKEDIN", "li-internal", 1)), (briefId, ok) -> {
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

    @Test public void v2ChannelSurvivesBriefMigration() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "sp035-channel-migration.db";
        context.deleteDatabase(name);
        SupportSQLiteDatabase old = helper.createDatabase(name, 2);
        old.execSQL("INSERT INTO channels (channelId, platform, accountType, externalAccountId, "
                + "displayName, status, sessionId, tokenType, scopesJson, capabilitiesJson, "
                + "connectionRevision, selected) VALUES "
                + "('channel-1', 'FACEBOOK', 'PAGE', 'page-1', 'Page', 'CONNECTED', "
                + "'session-1', 'bearer', '[]', '[]', 4, 1)");
        old.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(name, 4, true,
                AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4);
        try (Cursor rows = migrated.query("SELECT channelId, connectionRevision "
                + "FROM channels WHERE externalAccountId = 'page-1'")) {
            assertTrue(rows.moveToFirst());
            assertEquals("channel-1", rows.getString(0));
            assertEquals(4, rows.getLong(1));
        } finally {
            migrated.close();
            context.deleteDatabase(name);
        }
    }

    @Test public void staleChannelRevisionCannotSaveBriefOrPartialSources() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        try {
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "INSERT INTO channels (channelId, platform, accountType, externalAccountId, "
                            + "displayName, status, sessionId, tokenType, scopesJson, capabilitiesJson, "
                            + "connectionRevision, selected) VALUES "
                            + "('channel-1', 'FACEBOOK', 'PAGE', 'page-1', 'Page', 'CONNECTED', "
                            + "'session-1', 'bearer', '[]', '[]', 2, 1)");
            BriefEntity brief = new BriefEntity("stale-brief", null, "Topic", "Goal",
                    "Audience", "Tone", "vi", 100, 100);
            BriefSourceEntity source = new BriefSourceEntity("source-1", "stale-brief",
                    "URL", "https://example.org", 0);
            BriefTargetEntity target = new BriefTargetEntity("stale-brief", "channel-1", "FACEBOOK");
            boolean rejected = false;
            try {
                database.briefs().insertScoped(brief, Collections.singletonList(source),
                        Collections.singletonList(target),
                        Collections.singletonList(new Scope("FACEBOOK", "channel-1", 1)));
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            assertTrue(rejected);
            assertNull(database.briefs().get("stale-brief"));
            assertTrue(database.briefs().sources("stale-brief").isEmpty());
            assertTrue(database.briefs().targets("stale-brief").isEmpty());
            database.briefs().insertScoped(brief, Collections.singletonList(source),
                    Collections.singletonList(target),
                    Collections.singletonList(new Scope("FACEBOOK", "channel-1", 2)));
            assertNotNull(database.briefs().get("stale-brief"));
            assertEquals(1, database.briefs().targets("stale-brief").size());
        } finally {
            database.close();
        }
    }
}
