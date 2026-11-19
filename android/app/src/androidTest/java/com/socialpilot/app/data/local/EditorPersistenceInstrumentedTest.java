package com.socialpilot.app.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.domain.editor.ContentVersion;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class EditorPersistenceInstrumentedTest {
    @Rule public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @Test public void editingApprovedVersionInvalidatesOnlyItsChannel() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        try {
            seedBrief(database);
            EditorDao editor = database.editor();
            editor.saveVersion(version("fb-v1", "fb", null, "Facebook text"), null, 100);
            editor.saveVersion(version("li-v1", "li", null, "LinkedIn text"), null, 100);
            editor.approve("brief-1", "fb", "fb-v1", 101);
            editor.approve("brief-1", "li", "li-v1", 101);

            editor.saveVersion(version("fb-v2", "fb", "fb-v1", "Edited text"), "fb-v1", 100);
            assertNull(editor.link("brief-1", "fb").approvedVersionId);
            assertEquals("li-v1", editor.requireEnqueueable("brief-1", "li", "li-v1").versionId);
            assertThrows(IllegalStateException.class,
                    () -> editor.requireEnqueueable("brief-1", "fb", "fb-v1"));
            assertThrows(IllegalStateException.class,
                    () -> editor.requireEnqueueable("brief-1", "fb", "fb-v2"));
            assertEquals(1, editor.approvalCount("fb-v1"));
            editor.approve("brief-1", "fb", "fb-v2", 102);
            assertEquals("fb-v2", editor.requireEnqueueable("brief-1", "fb", "fb-v2").versionId);
        } finally {
            database.close();
        }
    }

    @Test public void staleCallbackAndWrongChannelCannotReplaceApproval() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        try {
            seedBrief(database);
            EditorDao editor = database.editor();
            editor.saveVersion(version("fb-v1", "fb", null, "First"), null, 100);
            editor.saveVersion(version("fb-v2", "fb", "fb-v1", "Current"), "fb-v1", 100);
            editor.approve("brief-1", "fb", "fb-v2", 101);
            assertThrows(IllegalStateException.class,
                    () -> editor.saveVersion(version("fb-late", "fb", "fb-v1", "Late"),
                            "fb-v1", 100));
            assertThrows(IllegalArgumentException.class,
                    () -> editor.approve("brief-1", "li", "fb-v2", 102));
            assertThrows(IllegalStateException.class,
                    () -> editor.saveVersion(version("fb-old-brief", "fb", "fb-v2", "Late"),
                            "fb-v2", 99));
            assertNull(editor.version("fb-late"));
            assertNull(editor.version("fb-old-brief"));
            assertEquals(2, editor.history("brief-1", "fb").size());
            assertEquals("fb-v2", editor.requireEnqueueable("brief-1", "fb", "fb-v2").versionId);
        } finally {
            database.close();
        }
    }

    @Test public void restoreCreatesNewUnapprovedVersion() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDatabase database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        try {
            seedBrief(database);
            EditorDao editor = database.editor();
            editor.saveVersion(version("fb-v1", "fb", null, "Original"), null, 100);
            editor.saveVersion(version("fb-v2", "fb", "fb-v1", "Edited"), "fb-v1", 100);
            editor.approve("brief-1", "fb", "fb-v2", 101);
            editor.restoreVersion(version("fb-v3", "fb", "fb-v1", "Original"), "fb-v2", 100);
            assertEquals("fb-v3", editor.link("brief-1", "fb").currentVersionId);
            assertNull(editor.link("brief-1", "fb").approvedVersionId);
            assertEquals(1, editor.approvalCount("fb-v2"));
            assertThrows(IllegalStateException.class,
                    () -> editor.requireEnqueueable("brief-1", "fb", "fb-v2"));
        } finally {
            database.close();
        }
    }

    @Test public void v2MigratesLinksThenKeepsApprovalAfterReopen() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "sp036-migration.db";
        context.deleteDatabase(name);
        SupportSQLiteDatabase old = helper.createDatabase(name, 2);
        old.execSQL("INSERT INTO briefs VALUES ('brief-1', NULL, 'Topic', 'Goal', "
                + "'Audience', 'Tone', 'vi', 100, 100)");
        old.execSQL("INSERT INTO brief_targets VALUES ('brief-1', 'fb', 'FACEBOOK')");
        old.close();
        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(name, 3, true,
                AppDatabase.MIGRATION_2_3);
        migrated.close();

        AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, name)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build();
        try {
            assertNull(database.editor().link("brief-1", "fb").currentVersionId);
            database.editor().saveVersion(version("fb-v1", "fb", null, "Text"), null, 100);
            database.editor().approve("brief-1", "fb", "fb-v1", 101);
        } finally {
            database.close();
        }
        database = Room.databaseBuilder(context, AppDatabase.class, name)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build();
        try {
            assertEquals("fb-v1", database.editor()
                    .requireEnqueueable("brief-1", "fb", "fb-v1").versionId);
            assertEquals(1, database.editor().approvalCount("fb-v1"));
        } finally {
            database.close();
            context.deleteDatabase(name);
        }
    }

    private static void seedBrief(AppDatabase database) {
        database.briefs().insert(new BriefEntity("brief-1", null, "Topic", "Goal",
                "Audience", "Tone", "vi", 100, 100), Collections.emptyList(),
                Arrays.asList(new BriefTargetEntity("brief-1", "fb", "FACEBOOK"),
                        new BriefTargetEntity("brief-1", "li", "LINKEDIN")));
    }

    private static ContentVersion version(String id, String channelId, String parentId,
            String text) {
        return new ContentVersion(id, "brief-1", channelId, parentId, text, null, 200);
    }
}
