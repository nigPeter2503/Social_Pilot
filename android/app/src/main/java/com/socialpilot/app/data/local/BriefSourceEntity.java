package com.socialpilot.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "brief_sources", foreignKeys = @ForeignKey(entity = BriefEntity.class,
        parentColumns = "briefId", childColumns = "briefId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("briefId"), @Index(value = {"briefId", "position"}, unique = true)})
public final class BriefSourceEntity {
    @PrimaryKey @NonNull public String sourceId;
    @NonNull public String briefId;
    @NonNull public String kind;
    @NonNull public String value;
    public int position;

    public BriefSourceEntity(@NonNull String sourceId, @NonNull String briefId,
            @NonNull String kind, @NonNull String value, int position) {
        this.sourceId = sourceId;
        this.briefId = briefId;
        this.kind = kind;
        this.value = value;
        this.position = position;
    }
}
