package com.socialpilot.app.data.ai.pipeline;

import com.socialpilot.app.domain.agent.ChannelBranch;
import com.socialpilot.app.domain.agent.CheckpointPolicy;
import com.socialpilot.app.domain.agent.PipelineInput;
import com.socialpilot.app.domain.agent.PipelineSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory checkpoint store with the same write guard the Room store must use. It does not survive
 * process death; it is the reference behaviour for tests until the Room schema is agreed (SP-033).
 */
public final class MemoryCheckpointStore implements CheckpointStore {
    private final Map<String, PipelineSnapshot> sessions = new LinkedHashMap<>();
    private final Map<String, Long> retiredRevisions = new HashMap<>();
    private final Set<String> deletedSessions = new HashSet<>();

    @Override
    public synchronized CheckpointPolicy.Write save(PipelineSnapshot snapshot) {
        if (deletedSessions.contains(snapshot.sessionId)) return CheckpointPolicy.Write.INVALIDATED;
        CheckpointPolicy.Write write = CheckpointPolicy.check(sessions.get(snapshot.sessionId), snapshot);
        if (write != CheckpointPolicy.Write.ACCEPTED) return write;

        PipelineSnapshot kept = snapshot;
        for (ChannelBranch branch : snapshot.branches) {
            Long retired = retiredRevisions.get(branch.target.channelId);
            if (retired != null && branch.target.connectionRevision <= retired) {
                kept = CheckpointPolicy.withoutChannel(kept, branch.target.channelId);
                if (kept == null) return CheckpointPolicy.Write.INVALIDATED;
            }
        }
        sessions.put(kept.sessionId, kept);
        return write;
    }

    @Override
    public synchronized PipelineSnapshot claimRecovery(PipelineInput input, PipelineSnapshot expected, long now) {
        PipelineSnapshot stored = sessions.get(expected.sessionId);
        if (stored == null || stored.revision != expected.revision) {
            throw new IllegalArgumentException("checkpoint missing or stale");
        }
        PipelineSnapshot recovered = CheckpointPolicy.recoverFor(input, stored, now);
        sessions.put(recovered.sessionId, recovered);
        return recovered;
    }

    @Override
    public synchronized PipelineSnapshot load(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public synchronized List<PipelineSnapshot> all() {
        return new ArrayList<>(sessions.values());
    }

    @Override
    public synchronized void forgetChannel(String channelId, long retiredConnectionRevision) {
        if (channelId == null || channelId.trim().isEmpty() || retiredConnectionRevision <= 0) {
            throw new IllegalArgumentException("channel and positive revision required");
        }
        Long previous = retiredRevisions.get(channelId);
        if (previous != null && previous >= retiredConnectionRevision) return;
        retiredRevisions.put(channelId, retiredConnectionRevision);
        for (String sessionId : new ArrayList<>(sessions.keySet())) {
            PipelineSnapshot stored = sessions.get(sessionId);
            ChannelBranch branch = stored.branch(channelId);
            if (branch == null || branch.target.connectionRevision > retiredConnectionRevision) continue;
            PipelineSnapshot kept = CheckpointPolicy.withoutChannel(stored, channelId);
            if (kept == null) delete(sessionId);
            else sessions.put(sessionId, kept);
        }
    }

    @Override
    public synchronized void delete(String sessionId) {
        deletedSessions.add(sessionId);
        sessions.remove(sessionId);
    }
}
