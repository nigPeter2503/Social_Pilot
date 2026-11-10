package com.socialpilot.app.data.prototype;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/** Separate demo database: the baseline socialpilot.db schema and topics stay untouched. */
@Database(entities = {SampleResultEntity.class}, version = 1, exportSchema = true)
public abstract class PrototypeDatabase extends RoomDatabase {
    public abstract SampleResultDao results();
}
