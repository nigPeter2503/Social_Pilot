package com.socialpilot.app.data.ai.pipeline;

import com.socialpilot.app.domain.agent.CheckpointPolicy;
import com.socialpilot.app.domain.agent.PipelineSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory checkpoint store with the same write guard the Room store must use. It does not survive
 * process death; it is the reference behaviour for tests until the Room schema is agreed (SP-033).
 */
public final class MemoryCheckpointStore implements CheckpointStore {
    private final Map<String, PipelineSnapshot> sessions = new LinkedHashMap<>();

    @Override
    public synchronized CheckpointPolicy.Write save(PipelineSnapshot snapshot) {
        CheckpointPolicy.Write write = CheckpointPolicy.check(sessions.get(snapshot.sessionId), snapshot);
        if (write == CheckpointPolicy.Write.ACCEPTED) sessions.put(snapshot.sessionId, snapshot);
        return write;
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
    public synchronized void forgetChannel(String channelId) {
        for (String sessionId : new ArrayList<>(sessions.keySet())) {
            PipelineSnapshot kept = CheckpointPolicy.withoutChannel(sessions.get(sessionId), channelId);
            if (kept == null) sessions.remove(sessionId);
            else sessions.put(sessionId, kept);
        }
    }

    @Override
    public synchronized void delete(String sessionId) {
        sessions.remove(sessionId);
    }
}
