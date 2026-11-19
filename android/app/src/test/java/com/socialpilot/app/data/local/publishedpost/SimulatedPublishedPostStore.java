package com.socialpilot.app.data.local.publishedpost;

import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** MO_PHONG: in-memory transaction/rollback harness. Does NOT test Room/SQLite/FK or durability. */
final class SimulatedPublishedPostStore implements PublishedPostStore {
    private static final class Key {
        final String channel;
        final String external;
        Key(String channel, String external) { this.channel = channel; this.external = external; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return channel.equals(key.channel) && external.equals(key.external);
        }
        @Override public int hashCode() { return Objects.hash(channel, external); }
    }

    private Map<Key, PublishedPost> posts = new HashMap<>();
    private Map<String, PublishedPost> ids = new HashMap<>();
    private final Map<String, ChannelState> channels = new HashMap<>();
    boolean failRead;
    boolean failCommit;
    int failWriteAt;
    private int writes;
    Runnable beforeWork;
    Runnable afterWrite;
    CountDownLatch writeEntered;
    CountDownLatch allowWrite;

    synchronized void connect(PublishedPostScope scope) {
        channels.put(scope.channelId, new ChannelState(scope, ConnectionStatus.CONNECTED));
    }

    synchronized void state(PublishedPostScope scope, ConnectionStatus status) {
        channels.put(scope.channelId, new ChannelState(scope, status));
    }

    synchronized void disconnectAndClean(PublishedPostScope next) {
        channels.put(next.channelId, new ChannelState(next, ConnectionStatus.DISCONNECTED));
        Map<Key, PublishedPost> kept = new HashMap<>();
        for (Map.Entry<Key, PublishedPost> entry : posts.entrySet()) {
            if (entry.getKey().channel.equals(next.channelId)) ids.remove(entry.getValue().id);
            else kept.put(entry.getKey(), entry.getValue());
        }
        posts = kept;
    }

    synchronized void seed(PublishedPost post) {
        posts.put(new Key(post.scope.channelId, post.externalPostId), post);
        ids.put(post.id, post);
    }

    synchronized int size() { return posts.size(); }

    @Override public synchronized <T> T transaction(Work<T> work) {
        if (beforeWork != null) { Runnable hook = beforeWork; beforeWork = null; hook.run(); }
        Map<Key, PublishedPost> original = new HashMap<>(posts);
        Map<String, PublishedPost> originalIds = new HashMap<>(ids);
        Map<String, ChannelState> originalChannels = new HashMap<>(channels);
        writes = 0;
        try {
            T result = work.run(new Transaction() {
                @Override public ChannelState channel(String id) { checkRead(); return channels.get(id); }
                @Override public PublishedPost byExternalId(String channel, String external) {
                    checkRead(); return posts.get(new Key(channel, external));
                }
                @Override public PublishedPost byLocalId(String id) { checkRead(); return ids.get(id); }
                @Override public void insert(PublishedPost post) {
                    Key key = new Key(post.scope.channelId, post.externalPostId);
                    if (posts.containsKey(key) || ids.containsKey(post.id)) throw new IllegalStateException("duplicate");
                    posts.put(key, post); ids.put(post.id, post); checkWrite();
                }
                @Override public void update(PublishedPost post) {
                    Key key = new Key(post.scope.channelId, post.externalPostId);
                    PublishedPost old = posts.get(key);
                    if (old == null || !old.id.equals(post.id)) throw new IllegalStateException("missing update");
                    posts.put(key, post); ids.put(post.id, post); checkWrite();
                }
            });
            if (failCommit) throw new IllegalStateException("MOCK_SECRET commit detail");
            return result;
        } catch (RuntimeException | Error failure) {
            posts = original; ids = originalIds;
            channels.clear(); channels.putAll(originalChannels);
            throw failure;
        }
    }

    private void checkRead() {
        if (failRead) throw new IllegalStateException("MOCK_SECRET read detail");
    }

    private void checkWrite() {
        if (writeEntered != null) writeEntered.countDown();
        if (allowWrite != null) {
            try {
                if (!allowWrite.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("test interrupted");
            }
        }
        if (++writes == failWriteAt) throw new IllegalStateException("MOCK_SECRET write detail");
        if (afterWrite != null) { Runnable hook = afterWrite; afterWrite = null; hook.run(); }
    }
}
