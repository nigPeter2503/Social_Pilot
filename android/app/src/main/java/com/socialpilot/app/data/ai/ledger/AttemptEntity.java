package com.socialpilot.app.data.ai.ledger;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One reservation per dispatch. Rows are never deleted: they are the budget trace and must
 * survive channel disconnects. Channel IDs are opaque copies, not foreign keys.
 */
@Entity(tableName = "ai_attempts", indices = {@Index("dayKey"), @Index("monthKey"), @Index("state")})
public class AttemptEntity {
    public static final String RESERVED = "RESERVED";
    public static final String QUEUED = "QUEUED";
    public static final String DISPATCHING = "DISPATCHING";
    public static final String SETTLED = "SETTLED";
    public static final String RELEASED = "RELEASED";
    public static final String UNCERTAIN = "UNCERTAIN";

    @PrimaryKey @NonNull public String requestId = "";
    /** Process that made the reservation; RESERVED rows of an older process were interrupted. */
    @NonNull public String bootId = "";
    // Scope metadata; null for reservations made through the plain BudgetManager.reserve.
    public String sessionId;
    public String channelId;
    public Long connectionRevision;
    public String stepId;
    public String taskType;
    public String provider;
    public String model;
    // Pinned at reserve.
    @NonNull public String zoneId = "";
    @NonNull public String dayKey = "";
    @NonNull public String monthKey = "";
    @NonNull public String currency = "";
    @NonNull public String priceRevision = "";
    public long reservedMicros;
    @NonNull public String state = RESERVED;
    public Long settledMicros;
    // Provider usage as reported; null usageState means no terminal record yet.
    public String usageState;
    public Long inputTokens;
    public Long outputTokens;
    public Long totalTokens;
    public Long cachedInputTokens;
    public Long reasoningTokens;
    public boolean interrupted;
    public long createdAt;
    public long updatedAt;
}
