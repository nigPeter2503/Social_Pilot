package com.socialpilot.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {DraftEntity.class, ChannelEntity.class, ChannelSecretCleanup.class,
        BriefEntity.class, BriefSourceEntity.class, BriefTargetEntity.class},
        version = 3, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE channels (channelId TEXT NOT NULL PRIMARY KEY, platform TEXT NOT NULL,"
                    + " accountType TEXT NOT NULL, externalAccountId TEXT NOT NULL, displayName TEXT NOT NULL,"
                    + " status TEXT NOT NULL, sessionId TEXT NOT NULL, tokenType TEXT NOT NULL,"
                    + " scopesJson TEXT NOT NULL, capabilitiesJson TEXT NOT NULL, connectionRevision INTEGER NOT NULL,"
                    + " selected INTEGER NOT NULL, credentialRef TEXT, credentialRevision TEXT,"
                    + " linkedPageId TEXT, graphApiVersion TEXT, tokenExpiresAtMillis INTEGER,"
                    + " dataAccessExpiresAtMillis INTEGER, effectiveExpiresAtMillis INTEGER)");
            db.execSQL("CREATE UNIQUE INDEX index_channels_platform_accountType_externalAccountId"
                    + " ON channels (platform, accountType, externalAccountId)");
            db.execSQL("CREATE TABLE channel_secret_cleanup (credentialRef TEXT NOT NULL PRIMARY KEY)");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
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

    public abstract DraftDao drafts();
    public abstract ChannelDao channels();
    public abstract BriefDao briefs();
}
