package com.socialpilot.app.data.ai.pipeline;

import com.socialpilot.app.domain.agent.CheckpointPolicy;
import com.socialpilot.app.domain.agent.PipelineSnapshot;
import java.util.List;

/**
 * Where {@link ChannelPipeline} checkpoints session snapshots (SP-033). Implementations must apply
 * {@link CheckpointPolicy#check} before every write so a late callback can never overwrite a newer
 * state. {@link #save} is called under the session lock on structural changes only (not on each
 * streamed delta): it must not block on disk; a Room store enqueues to one serial writer.
 *
 * <p>Checkpoints hold no key, token or full provider response; only what the snapshot holds.
 */
public interface CheckpointStore {
    CheckpointPolicy.Write save(PipelineSnapshot snapshot);

    /** The latest accepted snapshot, or null. */
    PipelineSnapshot load(String sessionId);

    /** Every stored session, for recovery at start-up. */
    List<PipelineSnapshot> all();

    /** Channel disconnected: drops its branch everywhere; sessions left empty are deleted. */
    void forgetChannel(String channelId);

    void delete(String sessionId);

    /** For pipelines built without checkpoints: accepts and keeps nothing. */
    CheckpointStore NONE = new CheckpointStore() {
        @Override public CheckpointPolicy.Write save(PipelineSnapshot snapshot) {
            return CheckpointPolicy.Write.ACCEPTED;
        }

        @Override public PipelineSnapshot load(String sessionId) {
            return null;
        }

        @Override public List<PipelineSnapshot> all() {
            return java.util.Collections.emptyList();
        }

        @Override public void forgetChannel(String channelId) {
        }

        @Override public void delete(String sessionId) {
        }
    };
}
