package com.socialpilot.app.data.ai.pipeline;

import com.socialpilot.app.domain.agent.CheckpointPolicy;
import com.socialpilot.app.domain.agent.PipelineInput;
import com.socialpilot.app.domain.agent.PipelineSnapshot;
import java.util.List;

/**
 * Where {@link ChannelPipeline} checkpoints session snapshots (SP-033). Implementations must apply
 * {@link CheckpointPolicy#check} before every write so a late callback can never overwrite a newer
 * state. {@link #save} runs on structural changes only. The initial snapshot is saved before
 * the session starts, and later saves run under its lock. A Room store must queue disk I/O on a
 * serial writer so saves do not block the session.
 *
 * <p>Checkpoints hold no key, token or full provider response; only what the snapshot holds.
 */
public interface CheckpointStore {
    CheckpointPolicy.Write save(PipelineSnapshot snapshot);

    /**
     * Atomically checks the expected session/revision against the latest stored snapshot, validates
     * its scope against the input, and replaces it with the recovered snapshot. Throws
     * IllegalArgumentException if recovery is stale, missing or incompatible.
     */
    PipelineSnapshot claimRecovery(PipelineInput input, PipelineSnapshot expected, long now);

    /** The latest accepted snapshot, or null. */
    PipelineSnapshot load(String sessionId);

    /** Every stored session, for recovery at start-up. */
    List<PipelineSnapshot> all();

    /**
     * Retires this channel through the supplied connection revision. Pruning and the guard
     * against late writes must share a transaction. Newer connections stay available.
     */
    void forgetChannel(String channelId, long retiredConnectionRevision);

    /** Deletes the checkpoint and prevents late writes from recreating the session. */
    void delete(String sessionId);

    /** For pipelines built without checkpoints: accepts and keeps nothing. */
    CheckpointStore NONE = new CheckpointStore() {
        @Override public CheckpointPolicy.Write save(PipelineSnapshot snapshot) {
            return CheckpointPolicy.Write.ACCEPTED;
        }

        @Override public PipelineSnapshot claimRecovery(PipelineInput input, PipelineSnapshot expected, long now) {
            return CheckpointPolicy.recoverFor(input, expected, now);
        }

        @Override public PipelineSnapshot load(String sessionId) {
            return null;
        }

        @Override public List<PipelineSnapshot> all() {
            return java.util.Collections.emptyList();
        }

        @Override public void forgetChannel(String channelId, long retiredConnectionRevision) {
        }

        @Override public void delete(String sessionId) {
        }
    };
}
