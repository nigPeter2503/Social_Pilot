package com.socialpilot.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Content is immutable; approval history is stored separately. */
@Entity(tableName = "content_versions",
        foreignKeys = @ForeignKey(entity = BriefTargetEntity.class,
                parentColumns = {"briefId", "channelId"},
                childColumns = {"briefId", "channelId"}, onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"briefId", "channelId"}),
                @Index(value = {"briefId", "channelId", "createdAt"})})
public final class ContentVersionEntity {
    @PrimaryKey @NonNull public String versionId;
    @NonNull public String briefId;
    @NonNull public String channelId;
    public String parentVersionId;
    @NonNull public String text;
    public String imageReference;
    public long briefUpdatedAt;
    public long createdAt;

    public ContentVersionEntity(@NonNull String versionId, @NonNull String briefId,
            @NonNull String channelId, String parentVersionId, @NonNull String text,
            String imageReference, long briefUpdatedAt, long createdAt) {
        this.versionId = versionId;
        this.briefId = briefId;
        this.channelId = channelId;
        this.parentVersionId = parentVersionId;
        this.text = text;
        this.imageReference = imageReference;
        this.briefUpdatedAt = briefUpdatedAt;
        this.createdAt = createdAt;
    }
}
