package com.socialpilot.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {DraftEntity.class, ChannelEntity.class, ChannelSecretCleanup.class},
        version = 2, exportSchema = true)
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

    public abstract DraftDao drafts();
    public abstract ChannelDao channels();
}
