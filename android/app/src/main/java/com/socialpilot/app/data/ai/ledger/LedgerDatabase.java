package com.socialpilot.app.data.ai.ledger;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * AI budget ledger in its own file ({@code ai_ledger.db}), owned by the AI platform. It has no
 * foreign keys into {@code socialpilot.db}, so it never competes for the shared schema version
 * slot and channel cleanup cannot delete budget evidence. No destructive migration fallback.
 */
@Database(entities = {AttemptEntity.class, BudgetSettingsEntity.class}, version = 1, exportSchema = true)
public abstract class LedgerDatabase extends RoomDatabase {
    public static final String FILE_NAME = "ai_ledger.db";

    public abstract LedgerDao ledger();

    public static LedgerDatabase open(Context context, String fileName) {
        return Room.databaseBuilder(context.getApplicationContext(), LedgerDatabase.class, fileName).build();
    }

    /** Room-backed store; Room transactions take SQLite's write lock before the first read. */
    public LedgerStore store() {
        LedgerDao dao = ledger();
        LedgerDatabase database = this;
        return new LedgerStore() {
            @Override public <T> T inTransaction(Callable<T> work) { return database.runInTransaction(work); }
            @Override public AttemptEntity find(String requestId) { return dao.find(requestId); }
            @Override public void insert(AttemptEntity row) { dao.insert(row); }
            @Override public void update(AttemptEntity row) { dao.update(row); }
            @Override public void releaseQueued(long now) { dao.releaseQueued(now); }
            @Override public long committedForDay(String dayKey, String currency) { return dao.committedForDay(dayKey, currency); }
            @Override public long committedForMonth(String monthKey, String currency) { return dao.committedForMonth(monthKey, currency); }
            @Override public int uncertainForMonth(String monthKey) { return dao.uncertainForMonth(monthKey); }
            @Override public BudgetSettingsEntity settings() { return dao.settings(); }
            @Override public void saveSettings(BudgetSettingsEntity settings) { dao.saveSettings(settings); }
            @Override public int markInterrupted(String currentBootId, long now) { return dao.markInterrupted(currentBootId, now); }
            @Override public int forgetChannel(String channelId, long retiredRevision, long now) {
                return dao.forgetChannel(channelId, retiredRevision, now);
            }
            @Override public List<AttemptEntity> forMonth(String monthKey) { return dao.forMonth(monthKey); }
        };
    }
}
