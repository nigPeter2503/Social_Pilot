package com.socialpilot.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "version_approvals", foreignKeys = @ForeignKey(
        entity = ContentVersionEntity.class, parentColumns = "versionId",
        childColumns = "versionId", onDelete = ForeignKey.CASCADE))
public final class VersionApprovalEntity {
    @PrimaryKey @NonNull public String versionId;
    public long approvedAt;

    public VersionApprovalEntity(@NonNull String versionId, long approvedAt) {
        this.versionId = versionId;
        this.approvedAt = approvedAt;
    }
}
