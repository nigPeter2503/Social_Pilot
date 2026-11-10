package com.socialpilot.app.data.prototype;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Latest result for one labelled demo channel; never a real social post. */
@Entity(tableName = "sample_results")
public final class SampleResultEntity {
    @PrimaryKey @NonNull public String channelId;
    @NonNull public String text;
    @NonNull public String state;
    @NonNull public String checkpoint;
    public long updatedAt;

    public SampleResultEntity(@NonNull String channelId, @NonNull String text,
                              @NonNull String state, @NonNull String checkpoint, long updatedAt) {
        this.channelId = channelId;
        this.text = text;
        this.state = state;
        this.checkpoint = checkpoint;
        this.updatedAt = updatedAt;
    }
}
