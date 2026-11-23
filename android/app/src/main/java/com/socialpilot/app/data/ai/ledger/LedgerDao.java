package com.socialpilot.app.data.ai.ledger;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface LedgerDao {
    @Query("SELECT * FROM ai_attempts WHERE requestId = :requestId")
    AttemptEntity find(String requestId);

    /** ABORT: a duplicate requestId is a bug the ledger already filtered inside the transaction. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(AttemptEntity row);

    @Update
    void update(AttemptEntity row);

    @Query("SELECT COALESCE(SUM(CASE WHEN state = 'SETTLED' THEN settledMicros "
            + "WHEN state IN ('RESERVED', 'UNCERTAIN') THEN reservedMicros ELSE 0 END), 0) "
            + "FROM ai_attempts WHERE dayKey = :dayKey AND currency = :currency")
    long committedForDay(String dayKey, String currency);

    @Query("SELECT COALESCE(SUM(CASE WHEN state = 'SETTLED' THEN settledMicros "
            + "WHEN state IN ('RESERVED', 'UNCERTAIN') THEN reservedMicros ELSE 0 END), 0) "
            + "FROM ai_attempts WHERE monthKey = :monthKey AND currency = :currency")
    long committedForMonth(String monthKey, String currency);

    @Query("SELECT COUNT(*) FROM ai_attempts WHERE monthKey = :monthKey AND state = 'UNCERTAIN'")
    int uncertainForMonth(String monthKey);

    @Query("SELECT * FROM ai_budget_settings WHERE id = 1")
    BudgetSettingsEntity settings();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveSettings(BudgetSettingsEntity settings);

    @Query("UPDATE ai_attempts SET state = 'UNCERTAIN', interrupted = 1, updatedAt = :now "
            + "WHERE state = 'RESERVED' AND bootId != :currentBootId")
    int markInterrupted(String currentBootId, long now);

    @Query("UPDATE ai_attempts SET channelId = NULL, sessionId = NULL, stepId = NULL, connectionRevision = NULL, "
            + "updatedAt = :now WHERE channelId = :channelId")
    int forgetChannel(String channelId, long now);

    @Query("SELECT * FROM ai_attempts WHERE monthKey = :monthKey ORDER BY createdAt, requestId")
    List<AttemptEntity> forMonth(String monthKey);
}
