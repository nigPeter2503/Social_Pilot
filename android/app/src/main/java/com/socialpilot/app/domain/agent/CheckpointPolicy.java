package com.socialpilot.app.domain.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure SP-033 rules for pipeline checkpoints, shared by every checkpoint store (memory now, Room
 * later): what a stored snapshot means after the process died, which writes a store may accept,
 * whether a checkpoint still fits the current input, and what a channel disconnect removes.
 *
 * <p>Nothing here dispatches. A stage that was RUNNING may already have been sent and charged, so
 * recovery marks it INTERRUPTED and only an explicit user retry (new requestId, new reservation)
 * runs it again. The cost of the interrupted attempt is reconciled by the budget ledger's own
 * recovery (RESERVED becomes UNCERTAIN), never by this policy.
 */
public final class CheckpointPolicy {
    /** Outcome of a write attempt against what a store already holds for the session. */
    public enum Write {
        ACCEPTED,
        /** Revision is not newer than the stored one: a late or duplicate write; dropped. */
        STALE,
        /** The incoming snapshot belongs to another session than the stored one. */
        OTHER_SESSION
    }

    /** Why a checkpoint can no longer be restored against the current input. */
    public enum Mismatch {
        BRIEF_CHANGED,
        /** A channel was added or removed since the checkpoint. */
        TARGETS_CHANGED,
        /** A channel was reconnected (new connection revision); its output is bound to the old one. */
        CONNECTION_CHANGED
    }

    private CheckpointPolicy() {
    }

    /** Store-side guard: same session and a strictly newer revision, or nothing stored yet. */
    public static Write check(PipelineSnapshot stored, PipelineSnapshot incoming) {
        if (stored == null) return Write.ACCEPTED;
        if (!stored.sessionId.equals(incoming.sessionId)) return Write.OTHER_SESSION;
        return incoming.revision > stored.revision ? Write.ACCEPTED : Write.STALE;
    }

    /** Null when the checkpoint can be restored for this input. */
    public static Mismatch mismatch(PipelineInput input, PipelineSnapshot checkpoint) {
        if (!input.briefId.equals(checkpoint.briefId)) return Mismatch.BRIEF_CHANGED;
        if (input.targets.size() != checkpoint.branches.size()) return Mismatch.TARGETS_CHANGED;
        Map<String, Long> revisions = new HashMap<>();
        for (ChannelTarget target : input.targets) revisions.put(target.channelId, target.connectionRevision);
        for (ChannelBranch branch : checkpoint.branches) {
            Long current = revisions.get(branch.target.channelId);
            if (current == null) return Mismatch.TARGETS_CHANGED;
            if (current != branch.target.connectionRevision) return Mismatch.CONNECTION_CHANGED;
        }
        return null;
    }

    /**
     * The meaning of a stored snapshot after process death. RUNNING stages become INTERRUPTED with
     * their attempt, requestId and checkpointed text kept; RUNNING branches become INTERRUPTED at
     * that stage (or at the first incomplete text stage when none had started). Finished branches
     * are unchanged. The revision moves forward so the recovered state wins over the stored one.
     */
    public static PipelineSnapshot recover(PipelineSnapshot stored, long now) {
        List<ChannelBranch> branches = new ArrayList<>();
        for (ChannelBranch branch : stored.branches) branches.add(recover(branch, now));
        return new PipelineSnapshot(stored.sessionId, stored.briefId, stored.revision + 1, branches);
    }

    private static ChannelBranch recover(ChannelBranch branch, long now) {
        if (branch.status != ChannelBranch.Status.RUNNING) return branch;
        ChannelBranch next = branch;
        PipelineStage at = null;
        for (StageResult result : branch.stages) {
            if (result.status == StageResult.Status.RUNNING) {
                next = next.withStage(result.interrupted(now));
                if (at == null) at = result.stage;
            }
        }
        if (at == null) {
            for (StageResult result : branch.stages) {
                if (result.stage.textStage && result.status != StageResult.Status.COMPLETED) {
                    at = result.stage;
                    break;
                }
            }
        }
        if (at == null) at = PipelineStage.RULE_CHECK;
        return next.withStatus(next.stage(at), ChannelBranch.Status.INTERRUPTED, at);
    }

    /**
     * Disconnect cleanup: the snapshot without that channel's branch, or null when no branch is
     * left (the store then deletes the session). Returns the same snapshot when the channel is absent.
     */
    public static PipelineSnapshot withoutChannel(PipelineSnapshot stored, String channelId) {
        if (stored.branch(channelId) == null) return stored;
        List<ChannelBranch> kept = new ArrayList<>();
        for (ChannelBranch branch : stored.branches) {
            if (!branch.target.channelId.equals(channelId)) kept.add(branch);
        }
        if (kept.isEmpty()) return null;
        return new PipelineSnapshot(stored.sessionId, stored.briefId, stored.revision + 1, kept);
    }
}
