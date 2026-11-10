package com.socialpilot.app.domain.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory version rules for a brief. A durable queue must repeat these checks in a transaction. */
public final class VersionApprovalState {
    private final String briefId;
    private final Set<String> targetChannelIds = new HashSet<>();
    private final Map<String, ContentVersion> versions = new LinkedHashMap<>();
    private final Map<String, String> currentVersionIds = new HashMap<>();
    private final Map<String, String> approvedVersionIds = new HashMap<>();
    private final Set<String> historicallyApprovedVersionIds = new HashSet<>();

    public VersionApprovalState(Brief brief) {
        if (brief == null) throw new IllegalArgumentException("brief is required");
        briefId = brief.briefId();
        for (Brief.Target target : brief.targets()) {
            targetChannelIds.add(target.channelId());
        }
    }

    /** Saving an edit or restoration creates a new version and invalidates approval for that channel. */
    public synchronized void saveVersion(ContentVersion version) {
        if (version == null) throw new IllegalArgumentException("version is required");
        requireTarget(version.channelId());
        if (!briefId.equals(version.briefId())) {
            throw new IllegalArgumentException("version belongs to another brief");
        }
        if (versions.containsKey(version.versionId())) {
            throw new IllegalArgumentException("versionId already exists");
        }
        String currentId = currentVersionIds.get(version.channelId());
        String parentId = version.parentVersionId();
        if (currentId == null && parentId != null) {
            throw new IllegalArgumentException("initial version cannot have a parent");
        }
        if (currentId != null) {
            ContentVersion parent = versions.get(parentId);
            if (parent == null || !version.channelId().equals(parent.channelId())) {
                throw new IllegalArgumentException("parent must be an existing version of this channel");
            }
        }
        versions.put(version.versionId(), version);
        currentVersionIds.put(version.channelId(), version.versionId());
        approvedVersionIds.remove(version.channelId());
    }

    public synchronized void approve(String channelId, String versionId) {
        requireTarget(channelId);
        ContentVersion version = versions.get(versionId);
        if (version == null || !channelId.equals(version.channelId())) {
            throw new IllegalArgumentException("version does not exist for this channel");
        }
        if (!versionId.equals(currentVersionIds.get(channelId))) {
            throw new IllegalStateException("only the current version can be approved");
        }
        approvedVersionIds.put(channelId, versionId);
        historicallyApprovedVersionIds.add(versionId);
    }

    /** Returns the exact immutable version; the caller must still validate it in durable storage. */
    public synchronized ContentVersion requireEnqueueable(String channelId, String versionId) {
        requireTarget(channelId);
        ContentVersion version = versions.get(versionId);
        if (version == null || !channelId.equals(version.channelId())) {
            throw new IllegalArgumentException("version does not exist for this channel");
        }
        if (!versionId.equals(currentVersionIds.get(channelId))
                || !versionId.equals(approvedVersionIds.get(channelId))) {
            throw new IllegalStateException("version is not current and approved");
        }
        return version;
    }

    public synchronized String currentVersionId(String channelId) {
        requireTarget(channelId);
        return currentVersionIds.get(channelId);
    }

    public synchronized String approvedVersionId(String channelId) {
        requireTarget(channelId);
        return approvedVersionIds.get(channelId);
    }

    /** Old versions remain readable in creation order; this list cannot edit the session. */
    public synchronized List<ContentVersion> versionsForChannel(String channelId) {
        requireTarget(channelId);
        List<ContentVersion> result = new ArrayList<>();
        for (ContentVersion version : versions.values()) {
            if (channelId.equals(version.channelId())) result.add(version);
        }
        return Collections.unmodifiableList(result);
    }

    /** Historical approval is for display only; requireEnqueueable checks current approval. */
    public synchronized boolean wasApproved(String channelId, String versionId) {
        requireTarget(channelId);
        ContentVersion version = versions.get(versionId);
        if (version == null || !channelId.equals(version.channelId())) {
            throw new IllegalArgumentException("version does not exist for this channel");
        }
        return historicallyApprovedVersionIds.contains(versionId);
    }

    private void requireTarget(String channelId) {
        if (channelId == null || !targetChannelIds.contains(channelId)) {
            throw new IllegalArgumentException("channel is not selected by this brief");
        }
    }
}
