package com.socialpilot.app.domain.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable state of a pipeline session; {@link #revision} increases with every change. */
public final class PipelineSnapshot {
    public final String sessionId;
    public final String briefId;
    /** A missing or unknown fingerprint cannot authorize recovery. */
    public final String briefFingerprint;
    public final long revision;
    public final List<ChannelBranch> branches;

    public PipelineSnapshot(String sessionId, String briefId, String briefFingerprint, long revision,
                            List<ChannelBranch> branches) {
        this.sessionId = sessionId;
        this.briefId = briefId;
        this.briefFingerprint = briefFingerprint;
        this.revision = revision;
        this.branches = Collections.unmodifiableList(new ArrayList<>(branches));
    }

    public ChannelBranch branch(String channelId) {
        for (ChannelBranch branch : branches) if (branch.target.channelId.equals(channelId)) return branch;
        return null;
    }

    /** No branch is still running. */
    public boolean settled() {
        for (ChannelBranch branch : branches) if (branch.status == ChannelBranch.Status.RUNNING) return false;
        return true;
    }
}
