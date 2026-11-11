package com.socialpilot.app.data.prototype;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

/** Separate demo database: the baseline socialpilot.db schema and topics stay untouched. */
@Database(entities = {SampleResultEntity.class}, version = 2, exportSchema = true)
public abstract class PrototypeDatabase extends RoomDatabase {
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE sample_results ADD COLUMN runId TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE sample_results ADD COLUMN workRequestId TEXT");
            database.execSQL("UPDATE sample_results SET runId = lower(hex(randomblob(16)))");
        }
    };

    public abstract SampleResultDao results();
}
