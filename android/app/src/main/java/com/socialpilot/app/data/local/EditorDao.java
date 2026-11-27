package com.socialpilot.app.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import com.socialpilot.app.domain.editor.ContentVersion;
import java.util.List;
import java.util.Objects;

@Dao
public abstract class EditorDao {
    @Query("SELECT * FROM brief_targets WHERE briefId = :briefId AND channelId = :channelId LIMIT 1")
    public abstract BriefTargetEntity link(String briefId, String channelId);

    @Query("SELECT * FROM content_versions WHERE versionId = :versionId LIMIT 1")
    public abstract ContentVersionEntity version(String versionId);

    @Query("SELECT updatedAt FROM briefs WHERE briefId = :briefId LIMIT 1")
    public abstract Long briefUpdatedAt(String briefId);

    @Insert protected abstract void insertVersion(ContentVersionEntity version);

    @Query("UPDATE brief_targets SET currentVersionId = :newVersionId, approvedVersionId = NULL "
            + "WHERE briefId = :briefId AND channelId = :channelId "
            + "AND currentVersionId IS :expectedCurrentVersionId")
    protected abstract int advance(String briefId, String channelId,
            String expectedCurrentVersionId, String newVersionId);

    @Query("UPDATE brief_targets SET approvedVersionId = :versionId "
            + "WHERE briefId = :briefId AND channelId = :channelId "
            + "AND currentVersionId = :versionId")
    protected abstract int approveCurrent(String briefId, String channelId, String versionId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract void recordApproval(VersionApprovalEntity approval);

    @Query("SELECT * FROM content_versions WHERE briefId = :briefId AND channelId = :channelId "
            + "ORDER BY createdAt, versionId")
    public abstract List<ContentVersionEntity> history(String briefId, String channelId);

    @Query("SELECT COUNT(*) FROM version_approvals WHERE versionId = :versionId")
    public abstract int approvalCount(String versionId);

    /** A late AI result cannot advance a different brief or current version. */
    @Transaction
    public void saveVersion(ContentVersion version, String expectedCurrentVersionId,
            long expectedBriefUpdatedAt) {
        saveVersion(version, expectedCurrentVersionId, expectedBriefUpdatedAt, false);
    }

    @Transaction
    public void restoreVersion(String briefId, String channelId, String sourceVersionId,
            String newVersionId, String expectedCurrentVersionId,
            long expectedBriefUpdatedAt, long createdAtEpochMillis) {
        ContentVersionEntity source = version(sourceVersionId);
        if (source == null || !briefId.equals(source.briefId)
                || !channelId.equals(source.channelId)) {
            throw new IllegalArgumentException("source must belong to this brief and channel");
        }
        ContentVersion restored = new ContentVersion(newVersionId, briefId, channelId,
                sourceVersionId, source.text, source.imageReference, createdAtEpochMillis);
        saveVersion(restored, expectedCurrentVersionId, expectedBriefUpdatedAt, true);
    }

    private void saveVersion(ContentVersion version, String expectedCurrentVersionId,
            long expectedBriefUpdatedAt, boolean restore) {
        if (version == null) throw new IllegalArgumentException("version is required");
        BriefTargetEntity link = link(version.briefId(), version.channelId());
        if (link == null) throw new IllegalArgumentException("channel is not selected by this brief");
        Long briefRevision = briefUpdatedAt(version.briefId());
        if (briefRevision == null || briefRevision != expectedBriefUpdatedAt) {
            throw new IllegalStateException("brief changed before this version was saved");
        }
        if (!Objects.equals(link.currentVersionId, expectedCurrentVersionId)) {
            throw new IllegalStateException("current version changed before this result was saved");
        }
        String parentId = version.parentVersionId();
        if (link.currentVersionId == null) {
            if (parentId != null || restore) {
                throw new IllegalArgumentException("initial version has no parent or restore");
            }
        } else {
            ContentVersionEntity parent = parentId == null ? null : version(parentId);
            if (parent == null || !version.briefId().equals(parent.briefId)
                    || !version.channelId().equals(parent.channelId)) {
                throw new IllegalArgumentException("parent must belong to this brief and channel");
            }
            if (!restore && !parentId.equals(link.currentVersionId)) {
                throw new IllegalArgumentException("edit must follow the current version");
            }
        }
        insertVersion(new ContentVersionEntity(version.versionId(), version.briefId(),
                version.channelId(), parentId, version.text(), version.imageReference(),
                expectedBriefUpdatedAt, version.createdAtEpochMillis()));
        if (advance(version.briefId(), version.channelId(), expectedCurrentVersionId,
                version.versionId()) != 1) {
            throw new IllegalStateException("current version changed before commit");
        }
    }

    @Transaction
    public void approve(String briefId, String channelId, String versionId, long approvedAt) {
        BriefTargetEntity link = link(briefId, channelId);
        ContentVersionEntity version = version(versionId);
        if (link == null || version == null || !briefId.equals(version.briefId)
                || !channelId.equals(version.channelId)) {
            throw new IllegalArgumentException("version does not belong to the selected channel");
        }
        if (!versionId.equals(link.currentVersionId)
                || approveCurrent(briefId, channelId, versionId) != 1) {
            throw new IllegalStateException("only the current version can be approved");
        }
        recordApproval(new VersionApprovalEntity(versionId, approvedAt));
    }

    /** The publish queue must repeat this check in its own admission/dispatch transaction. */
    @Transaction
    public ContentVersionEntity requireEnqueueable(String briefId, String channelId,
            String versionId) {
        BriefTargetEntity link = link(briefId, channelId);
        ContentVersionEntity version = version(versionId);
        if (link == null || version == null || !briefId.equals(version.briefId)
                || !channelId.equals(version.channelId)) {
            throw new IllegalArgumentException("version does not belong to the selected channel");
        }
        if (!versionId.equals(link.currentVersionId)
                || !versionId.equals(link.approvedVersionId)) {
            throw new IllegalStateException("version is not current and approved");
        }
        return version;
    }
}
