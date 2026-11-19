package com.socialpilot.app.data.local.publishedpost;

import com.socialpilot.app.data.local.comment.*;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.*;

/** MO_PHONG. Copy-on-write comment tables inside the SP-041.01 fixture transaction. */
public final class SimulatedCommentStore implements CommentStore {
    private final SimulatedPublishedPostStore posts = new SimulatedPublishedPostStore();
    private Map<List<Object>, StoredComment> rows = new HashMap<>();
    private Map<CommentCheckpoint.Key, CommentCheckpoint> progress = new HashMap<>();
    public boolean failCheckpoint;
    public boolean failCommit;
    public Runnable afterCheckpoint;

    public synchronized void seed(PublishedPostScope scope, String post) {
        posts.connect(scope);
        PublishedPostInput input = new PublishedPostInput(scope, post,
                new PublishedPostInput.Ownership(scope, post, "fixture-ownership"),
                null, null, null, null, 1, PublishedPostInput.Source.MO_PHONG, "fixture-post");
        if (PublishedPostRepository.forSimulation(posts).upsert(input).status
                != PublishedPostRepository.Status.COMMITTED) throw new AssertionError("fixture root");
    }

    public synchronized void state(PublishedPostScope scope, ConnectionStatus status) {
        posts.state(scope, status);
    }

    public synchronized void invalidate(PublishedPostScope next) {
        posts.disconnectAndClean(next);
        rows.entrySet().removeIf(entry -> next.channelId.equals(entry.getValue().observation.scope.channelId));
        progress.keySet().removeIf(key -> next.channelId.equals(key.channelId));
    }

    public synchronized int size() { return rows.size(); }
    public synchronized int checkpoints() { return progress.size(); }
    public synchronized StoredComment row(Platform platform, String channel, String id) {
        return rows.get(Arrays.asList(platform, channel, id));
    }

    @Override public synchronized <T> T transaction(Work<T> work) {
        Map<List<Object>, StoredComment> nextRows = new HashMap<>(rows);
        Map<CommentCheckpoint.Key, CommentCheckpoint> nextProgress = new HashMap<>(progress);
        T result = posts.transaction(root -> {
            T value = work.run(new Transaction() {
                public PublishedPostStore.ChannelState channel(String id) { return root.channel(id); }
                public PublishedPost byExternalId(String channel, String id) { return root.byExternalId(channel, id); }
                public StoredComment comment(Platform platform, String channel, String id) {
                    return nextRows.get(Arrays.asList(platform, channel, id));
                }
                public void putComment(StoredComment row) {
                    nextRows.put(Arrays.asList(row.observation.scope.platform, row.observation.scope.channelId,
                            row.observation.commentId), row);
                }
                public CommentCheckpoint checkpoint(CommentCheckpoint.Key key) { return nextProgress.get(key); }
                public void putCheckpoint(CommentCheckpoint checkpoint) {
                    if (failCheckpoint) throw new IllegalStateException("simulated IO failure");
                    nextProgress.put(checkpoint.key, checkpoint);
                    if (afterCheckpoint != null) afterCheckpoint.run();
                }
            });
            if (failCommit) throw new IllegalStateException("simulated commit failure");
            return value;
        });
        rows = nextRows;
        progress = nextProgress;
        return result;
    }
}
