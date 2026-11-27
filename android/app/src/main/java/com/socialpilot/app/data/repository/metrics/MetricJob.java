package com.socialpilot.app.data.repository.metrics;

import com.socialpilot.app.data.local.snapshot.Snapshot;
import com.socialpilot.app.data.remote.social.FollowerRequest;
import com.socialpilot.app.data.remote.social.PostRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Frozen collection input. A post query measures one period per collection. */
public record MetricJob(String collectionId, long collectionStartedAt, Snapshot.Scope scope,
        String apiVersion, boolean follower, List<PostTarget> posts) {
    public static final int MAX_TARGETS = 8;

    public record PostTarget(String postId, PostRequest.Query query, PostRequest.Period period) {}
    private record PostOperation(String postId, PostRequest.Query query) {}

    public MetricJob {
        Objects.requireNonNull(scope);
        FollowerRequest account = new FollowerRequest(scope.account(), scope.channelId(),
                scope.connectionRevision(), scope.externalAccountId(), apiVersion, collectionId);
        int count = posts.size() + (follower ? 1 : 0);
        if (count == 0 || count > MAX_TARGETS || account.platform != scope.platform() || collectionStartedAt < 0) {
            throw new IllegalArgumentException("invalid metric job");
        }
        posts = Collections.unmodifiableList(new ArrayList<>(posts));
        Set<PostOperation> unique = new HashSet<>();
        for (PostTarget post : posts) {
            request(account, post);
            if (!unique.add(new PostOperation(post.postId(), post.query()))) {
                throw new IllegalArgumentException("duplicate post operation");
            }
        }
    }

    public static MetricJob fresh(long now, Snapshot.Scope scope, String version, boolean follower, List<PostTarget> posts) {
        return new MetricJob(UUID.randomUUID().toString(), now, scope, version, follower, posts);
    }

    FollowerRequest followerRequest() {
        return new FollowerRequest(scope.account(), scope.channelId(), scope.connectionRevision(),
                scope.externalAccountId(), apiVersion, collectionId);
    }

    PostRequest postRequest(PostTarget target) { return request(followerRequest(), target); }

    private static PostRequest request(FollowerRequest a, PostTarget target) {
        return new PostRequest(a.account, a.channelId, a.connectionRevision, a.externalAccountId,
                target.postId(), a.apiVersion, a.collectionId, target.query(), target.period());
    }
}
