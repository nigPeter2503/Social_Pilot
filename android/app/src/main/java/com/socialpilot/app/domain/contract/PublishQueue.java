package com.socialpilot.app.domain.contract;

import com.socialpilot.app.domain.contract.social.PublishRequest;

/** Durable queue contract only; SP-009 does not implement Room or WorkManager processing. */
public interface PublishQueue {
    /**
     * Called off the main thread. Persist transactionally before returning.
     * Repeated identical requestId is idempotent; a conflicting payload must be rejected.
     * Also prevent duplicate active/published channel + approvedVersionId entries.
     * Validate approval here and again before dispatch. UNKNOWN must never be auto-resubmitted.
     */
    void enqueueApprovedVersion(PublishRequest request);

    /**
     * Invalidate this revision, cancel unsent work and cancel active HTTP calls where possible.
     * Keep only the agreed minimal reconciliation record for requests already sent.
     * Must not touch shared drafts, another channel, or a newer connection revision.
     */
    void cancelForConnection(String channelId, long connectionRevision);
}
