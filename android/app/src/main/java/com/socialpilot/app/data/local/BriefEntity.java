package com.socialpilot.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "briefs", indices = {@Index(value = "legacyDraftId", unique = true)})
public final class BriefEntity {
    @PrimaryKey @NonNull public String briefId;
    public Long legacyDraftId;
    @NonNull public String topic;
    @NonNull public String goal;
    @NonNull public String audience;
    @NonNull public String tone;
    @NonNull public String contentLanguage;
    public long createdAt;
    public long updatedAt;

    public BriefEntity(@NonNull String briefId, Long legacyDraftId, @NonNull String topic,
            @NonNull String goal, @NonNull String audience, @NonNull String tone,
            @NonNull String contentLanguage, long createdAt, long updatedAt) {
        this.briefId = briefId;
        this.legacyDraftId = legacyDraftId;
        this.topic = topic;
        this.goal = goal;
        this.audience = audience;
        this.tone = tone;
        this.contentLanguage = contentLanguage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
