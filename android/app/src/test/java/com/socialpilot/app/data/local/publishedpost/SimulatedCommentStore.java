package com.socialpilot.app.data.local.publishedpost;

import com.socialpilot.app.data.local.comment.*;
import com.socialpilot.app.data.remote.social.CommentPageRequest;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.*;

/** MO_PHONG. Copy-on-write comment tables inside the SP-041 fixture transaction. */
public final class SimulatedCommentStore implements CommentStore {
    private final SimulatedPublishedPostStore posts = new SimulatedPublishedPostStore();
    private Map<List<Object>, StoredComment> rows = new HashMap<>();
    private Map<CommentCheckpoint.Key, CommentCheckpoint> progress = new HashMap<>();
    private Map<CommentCheckpoint.Key, Set<String>> membership = new HashMap<>();
    private Map<CommentCheckpoint.Key, CommentAttempt> attempts = new HashMap<>();
    private Map<String, CommentPageRequest> queries = new HashMap<>();
    private record HandledKey(Platform platform, String channel, String id, long revision) {}
    private Set<HandledKey> handled = new HashSet<>();
    public boolean failCleanup;
    public boolean failCheckpoint;
    public boolean failCommit;
    public Runnable afterCheckpoint;

    public synchronized void seed(PublishedPostScope scope, String post) {
        posts.connect(scope);
        PublishedPostInput input = new PublishedPostInput(scope, post,
                new PublishedPostInput.Ownership(scope, post, "fixture-ownership"),
                null, null, null, null, 1, PublishedPostInput.Source.MO_PHONG, "fixture-post");
        // The root owner supplies today's binding independently of comment cleanup.
        PublishedPost previous = posts.transaction(tx -> tx.byExternalId(scope.channelId, post));
        if (previous != null && previous.scope.connectionRevision < scope.connectionRevision) {
            posts.seed(new PublishedPost(UUID.randomUUID().toString(), input, null));
            return;
        }
        if (PublishedPostRepository.forSimulation(posts).upsert(input).status
                != PublishedPostRepository.Status.COMMITTED) throw new AssertionError("fixture root");
    }

    public synchronized void state(PublishedPostScope scope, ConnectionStatus status) {
        posts.state(scope, status);
    }

    public record Counts(long rows, long members, long checkpoints, long attempts, long queries, long handled) {}
    public synchronized Counts counts(String channel, long revision) {
        long members = 0;
        for (var entry : membership.entrySet())
            if (channel.equals(entry.getKey().channelId) && revision == entry.getKey().revision)
                members += entry.getValue().size();
        return new Counts(rows.values().stream().filter(r -> channel.equals(r.observation.scope.channelId)
                        && r.observation.scope.connectionRevision == revision).count(), members,
                progress.keySet().stream().filter(k -> channel.equals(k.channelId) && k.revision == revision).count(),
                attempts.keySet().stream().filter(k -> channel.equals(k.channelId) && k.revision == revision).count(),
                queries.values().stream().filter(q -> channel.equals(q.channelId) && q.connectionRevision == revision).count(),
                handled.stream().filter(k -> channel.equals(k.channel()) && revision == k.revision()).count());
    }

    public synchronized int size() { return rows.size(); }
    public synchronized boolean markHandled(CommentPageRequest q, String id) {
        var channel = posts.transaction(tx -> tx.channel(q.channelId));
        StoredComment row = row(q.platform, q.channelId, id);
        if (channel == null || channel.status != ConnectionStatus.CONNECTED
                || channel.scope.connectionRevision != q.connectionRevision || row == null
                || row.observation.scope.connectionRevision != q.connectionRevision) return false;
        return handled.add(new HandledKey(q.platform, q.channelId, id, q.connectionRevision));
    }
    public synchronized int checkpoints() { return progress.size(); }
    public synchronized StoredComment row(Platform platform, String channel, String id) {
        return rows.get(Arrays.asList(platform, channel, id));
    }

    @Override public synchronized <T> T transaction(Work<T> work) {
        Map<List<Object>, StoredComment> nextRows = new HashMap<>(rows);
        Map<CommentCheckpoint.Key, CommentCheckpoint> nextProgress = new HashMap<>(progress);
        Map<CommentCheckpoint.Key, Set<String>> nextMembership = new HashMap<>();
        for (Map.Entry<CommentCheckpoint.Key, Set<String>> e : membership.entrySet())
            nextMembership.put(e.getKey(), new HashSet<>(e.getValue()));
        Map<CommentCheckpoint.Key, CommentAttempt> nextAttempts = new HashMap<>(attempts);
        Map<String, CommentPageRequest> nextQueries = new HashMap<>(queries);
        Set<HandledKey> nextHandled = new HashSet<>(handled);
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
                public void include(CommentCheckpoint.Key key, String id) {
                    if (!nextMembership.containsKey(key)) nextMembership.put(key, new HashSet<>());
                    nextMembership.get(key).add(id);
                }
                public List<StoredComment> comments(CommentPageRequest q, Handled filter) {
                    CommentCheckpoint.Key key = new CommentCheckpoint.Key(q);
                    List<StoredComment> found = new ArrayList<>();
                    Set<String> ids = nextMembership.get(key);
                    if (ids == null) return found;
                    Platform platform = root.channel(key.channelId).scope.platform;
                    for (String id : ids) {
                        List<Object> identity = Arrays.asList(platform, key.channelId, id);
                        StoredComment row = nextRows.get(identity);
                        if (row == null || row.observation.scope.connectionRevision != key.revision) continue;
                        boolean done = nextHandled.contains(new HandledKey(platform, key.channelId, id, key.revision));
                        if (q.historyFrom != null && row.createdAt != null && row.createdAt < q.historyFrom) continue;
                        if (filter == Handled.ALL || done == (filter == Handled.HANDLED)) found.add(row);
                    }
                    return found;
                }
                public CommentAttempt attempt(CommentCheckpoint.Key key) { return nextAttempts.get(key); }
                public Long rateLimitedUntil(String channel, long revision, long now) {
                    Long until = null;
                    for (Map.Entry<CommentCheckpoint.Key, CommentAttempt> entry : nextAttempts.entrySet()) {
                        CommentAttempt a = entry.getValue();
                        if (entry.getKey().channelId.equals(channel) && entry.getKey().revision == revision
                                && a.error() != null && a.error().code == com.socialpilot.app.domain.contract.social.SocialError.Code.RATE_LIMITED
                                && a.retryAt() != null && a.retryAt() > now && (until == null || a.retryAt() > until))
                            until = a.retryAt();
                    }
                    return until;
                }
                public void putAttempt(CommentCheckpoint.Key key, CommentAttempt value) { nextAttempts.put(key, value); }
                public void remember(CommentPageRequest q) { nextQueries.put(q.queryFingerprint, q); }
                public CommentPageRequest query(String fingerprint) { return nextQueries.get(fingerprint); }
                public List<String> workFingerprints(String channel, long revision) {
                    List<String> found = new ArrayList<>();
                    for (CommentPageRequest q : nextQueries.values())
                        if (q.channelId.equals(channel) && q.connectionRevision == revision) found.add(q.queryFingerprint);
                    return found;
                }
                public void deleteRevision(String channel, long revision) {
                    nextRows.values().removeIf(row -> channel.equals(row.observation.scope.channelId)
                            && row.observation.scope.connectionRevision == revision);
                    if (failCleanup) throw new IllegalStateException("interrupted comment cleanup");
                    nextProgress.keySet().removeIf(key -> key.channelId.equals(channel) && key.revision == revision);
                    nextMembership.keySet().removeIf(key -> key.channelId.equals(channel) && key.revision == revision);
                    nextAttempts.keySet().removeIf(key -> key.channelId.equals(channel) && key.revision == revision);
                    nextQueries.values().removeIf(q -> q.channelId.equals(channel) && q.connectionRevision == revision);
                    nextHandled.removeIf(key -> channel.equals(key.channel()) && revision == key.revision());
                }
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
        membership = nextMembership;
        attempts = nextAttempts;
        queries = nextQueries;
        handled = nextHandled;
        return result;
    }
}
