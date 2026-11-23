package com.socialpilot.app.prototype;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic sample streams. No AI provider or paid request is called here. */
public final class DemoStreamCoordinator {
    public enum Phase { RUNNING, COMPLETE, CANCELLED, INTERRUPTED, ERROR }

    public interface Scheduler {
        void schedule(Runnable action, long delayMillis);
    }

    public interface Listener {
        void onUpdate(String channel, Phase phase, String accumulatedText);
    }

    private final Scheduler scheduler;
    private final Map<String, Stream> streams = new HashMap<>();
    private long nextGeneration;

    public DemoStreamCoordinator(Scheduler scheduler) {
        if (scheduler == null) throw new IllegalArgumentException("scheduler is required");
        this.scheduler = scheduler;
    }

    public synchronized boolean start(String channel, List<String> chunks, Listener listener) {
        if (channel == null || channel.trim().isEmpty() || chunks == null || chunks.isEmpty()
                || chunks.contains(null) || listener == null) {
            throw new IllegalArgumentException("channel, chunks and listener are required");
        }
        Stream previous = streams.get(channel);
        if (previous != null && previous.phase == Phase.RUNNING) return false;
        Stream stream = new Stream(++nextGeneration, listener);
        streams.put(channel, stream);
        listener.onUpdate(channel, Phase.RUNNING, "");
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            boolean last = index == chunks.size() - 1;
            long delay = (index + 1L) * 2000L;
            scheduler.schedule(() -> deliver(channel, stream.generation, chunk, last), delay);
        }
        return true;
    }

    public synchronized void cancel(String channel) {
        finish(channel, Phase.CANCELLED);
    }

    public synchronized void fail(String channel) {
        finish(channel, Phase.ERROR);
    }

    public synchronized void interruptAll() {
        for (String channel : streams.keySet()) finish(channel, Phase.INTERRUPTED);
    }

    private void deliver(String channel, long generation, String chunk, boolean last) {
        synchronized (this) {
            Stream stream = streams.get(channel);
            if (stream == null || stream.generation != generation || stream.phase != Phase.RUNNING) {
                return;
            }
            if (stream.text.length() > 0) stream.text.append(' ');
            stream.text.append(chunk.trim());
            if (last) stream.phase = Phase.COMPLETE;
            stream.listener.onUpdate(channel, stream.phase, stream.text.toString());
        }
    }

    private void finish(String channel, Phase phase) {
        Stream stream = streams.get(channel);
        if (stream == null || stream.phase != Phase.RUNNING) return;
        stream.phase = phase;
        stream.listener.onUpdate(channel, phase, stream.text.toString());
    }

    private static final class Stream {
        final long generation;
        final Listener listener;
        final StringBuilder text = new StringBuilder();
        Phase phase = Phase.RUNNING;

        Stream(long generation, Listener listener) {
            this.generation = generation;
            this.listener = listener;
        }
    }
}
