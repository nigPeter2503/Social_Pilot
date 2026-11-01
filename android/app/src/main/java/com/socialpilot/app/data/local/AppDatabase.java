package com.socialpilot.app.data.local;
import androidx.room.Database;
import androidx.room.RoomDatabase;
@Database(entities = {DraftEntity.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase { public abstract DraftDao drafts(); }
