package com.socialpilot.app.data.ai.ledger;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Single row (id = 1): the caps every provider, task and channel share. */
@Entity(tableName = "ai_budget_settings")
public class BudgetSettingsEntity {
    public static final int SINGLETON = 1;

    @PrimaryKey public int id = SINGLETON;
    @NonNull public String currency = "";
    public long dailyMicros;
    public long monthlyMicros;
    @NonNull public String zoneId = "";
    public long updatedAt;
}
