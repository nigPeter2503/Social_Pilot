package com.socialpilot.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** An unpromoted or superseded reference requiring cleanup from the external secret store. */
@Entity(tableName = "channel_secret_cleanup")
public final class ChannelSecretCleanup {
    @PrimaryKey @NonNull public final String credentialRef;

    public ChannelSecretCleanup(@NonNull String credentialRef) {
        this.credentialRef = credentialRef;
    }
}
