package com.socialpilot.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "channels", indices = {
        @Index(value = {"platform", "accountType", "externalAccountId"}, unique = true)
})
public final class ChannelEntity {
    @PrimaryKey @NonNull public String channelId = "";
    @NonNull public String platform = "";
    @NonNull public String accountType = "";
    @NonNull public String externalAccountId = "";
    @NonNull public String displayName = "";
    @NonNull public String status = "";
    @NonNull public String sessionId = "";
    @NonNull public String tokenType = "";
    @NonNull public String scopesJson = "[]";
    @NonNull public String capabilitiesJson = "[]";
    public long connectionRevision;
    public boolean selected;
    public String credentialRef;
    public String credentialRevision;
    public String linkedPageId;
    public String graphApiVersion;
    public Long tokenExpiresAtMillis;
    public Long dataAccessExpiresAtMillis;
    public Long effectiveExpiresAtMillis;
}
