package com.socialpilot.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "brief_targets", primaryKeys = {"briefId", "channelId"},
        foreignKeys = @ForeignKey(entity = BriefEntity.class, parentColumns = "briefId",
                childColumns = "briefId", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"briefId", "platform"}, unique = true)})
public final class BriefTargetEntity {
    @NonNull public String briefId;
    @NonNull public String channelId;
    @NonNull public String platform;

    public BriefTargetEntity(@NonNull String briefId, @NonNull String channelId,
            @NonNull String platform) {
        this.briefId = briefId;
        this.channelId = channelId;
        this.platform = platform;
    }
}
