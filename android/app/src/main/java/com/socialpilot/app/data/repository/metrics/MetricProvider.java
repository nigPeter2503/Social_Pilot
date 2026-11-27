package com.socialpilot.app.data.repository.metrics;

import com.socialpilot.app.data.remote.social.FollowerMetricAdapter;
import com.socialpilot.app.data.remote.social.FollowerRequest;
import com.socialpilot.app.data.remote.social.PostMetricAdapter;
import com.socialpilot.app.data.remote.social.PostRequest;
import com.socialpilot.app.domain.contract.Cancelable;

/** Admission, credential lookup and response normalization remain with the operation-specific adapters. */
public interface MetricProvider {
    Cancelable read(FollowerRequest request, FollowerMetricAdapter.ResultCallback callback);
    Cancelable read(PostRequest request, PostMetricAdapter.ResultCallback callback);

    /** Resolve a post adapter bound to this exact query's capability and per-metric grants. */
    interface PostAdapters { PostMetricAdapter forRequest(PostRequest request); }

    static MetricProvider adapters(FollowerMetricAdapter followers, PostAdapters posts) {
        return new MetricProvider() {
            @Override public Cancelable read(FollowerRequest r, FollowerMetricAdapter.ResultCallback cb) {
                return followers.read(r, cb);
            }
            @Override public Cancelable read(PostRequest r, PostMetricAdapter.ResultCallback cb) {
                return posts.forRequest(r).read(r, cb);
            }
        };
    }
}
