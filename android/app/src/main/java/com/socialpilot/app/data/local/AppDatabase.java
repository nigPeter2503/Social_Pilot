package com.socialpilot.app.data.local;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

@Database(entities = {DraftEntity.class, BriefEntity.class, BriefSourceEntity.class,
        BriefTargetEntity.class, ContentVersionEntity.class,
        VersionApprovalEntity.class}, version = 3, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS briefs (briefId TEXT NOT NULL PRIMARY KEY, "
                    + "legacyDraftId INTEGER, topic TEXT NOT NULL, goal TEXT NOT NULL, "
                    + "audience TEXT NOT NULL, tone TEXT NOT NULL, contentLanguage TEXT NOT NULL, "
                    + "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_briefs_legacyDraftId "
                    + "ON briefs (legacyDraftId)");
            database.execSQL("CREATE TABLE IF NOT EXISTS brief_sources (sourceId TEXT NOT NULL PRIMARY KEY, "
                    + "briefId TEXT NOT NULL, kind TEXT NOT NULL, value TEXT NOT NULL, "
                    + "position INTEGER NOT NULL, FOREIGN KEY(briefId) REFERENCES briefs(briefId) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_brief_sources_briefId "
                    + "ON brief_sources (briefId)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_brief_sources_briefId_position "
                    + "ON brief_sources (briefId, position)");
            database.execSQL("CREATE TABLE IF NOT EXISTS brief_targets (briefId TEXT NOT NULL, "
                    + "channelId TEXT NOT NULL, platform TEXT NOT NULL, "
                    + "PRIMARY KEY(briefId, channelId), FOREIGN KEY(briefId) REFERENCES briefs(briefId) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_brief_targets_briefId_platform "
                    + "ON brief_targets (briefId, platform)");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE brief_targets ADD COLUMN currentVersionId TEXT");
            database.execSQL("ALTER TABLE brief_targets ADD COLUMN approvedVersionId TEXT");
            database.execSQL("CREATE TABLE IF NOT EXISTS content_versions ("
                    + "versionId TEXT NOT NULL PRIMARY KEY, briefId TEXT NOT NULL, "
                    + "channelId TEXT NOT NULL, parentVersionId TEXT, text TEXT NOT NULL, "
                    + "imageReference TEXT, briefUpdatedAt INTEGER NOT NULL, "
                    + "createdAt INTEGER NOT NULL, FOREIGN KEY(briefId, channelId) "
                    + "REFERENCES brief_targets(briefId, channelId) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_content_versions_briefId_channelId "
                    + "ON content_versions (briefId, channelId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_content_versions_briefId_channelId_createdAt "
                    + "ON content_versions (briefId, channelId, createdAt)");
            database.execSQL("CREATE TABLE IF NOT EXISTS version_approvals ("
                    + "versionId TEXT NOT NULL PRIMARY KEY, approvedAt INTEGER NOT NULL, "
                    + "FOREIGN KEY(versionId) REFERENCES content_versions(versionId) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
        }
    };

    public abstract DraftDao drafts();
    public abstract BriefDao briefs();
    public abstract EditorDao editor();
}
