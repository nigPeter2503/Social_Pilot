package com.socialpilot.app.core;

import android.content.Context;
import androidx.work.*;
import com.google.gson.Gson;
import com.socialpilot.app.data.local.snapshot.Snapshot;
import com.socialpilot.app.data.remote.social.FollowerRequest;
import com.socialpilot.app.data.remote.social.PostRequest;
import com.socialpilot.app.data.repository.metrics.MetricCollectionCoordinator;
import com.socialpilot.app.data.repository.metrics.MetricJob;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Manual and scheduled callers enqueue the same immutable one-shot collection through this entry point. */
public final class MetricCollectionWork {
    private final Context context;
    final MetricCollectionCoordinator coordinator;
    private final MetricCollectionCoordinator.Clock clock;

    public MetricCollectionWork(Context context, MetricCollectionCoordinator coordinator, MetricCollectionCoordinator.Clock clock) {
        this.context = context.getApplicationContext();
        this.coordinator = coordinator;
        this.clock = clock;
    }

    public record Ticket(MetricJob job, UUID workId, Operation enqueue) {}

    public Ticket manualRefresh(Snapshot.Scope scope, String version, boolean follower, List<MetricJob.PostTarget> posts) {
        return enqueue(MetricJob.fresh(clock.now(), scope, version, follower, posts));
    }

    public Ticket enqueue(MetricJob job) {
        OneTimeWorkRequest request = request(job);
        // Queued manual refreshes each retain their own ID. The active retry chain owns the scope until terminal.
        Operation op = WorkManager.getInstance(context).enqueueUniqueWork(tag(job.scope().channelId(),
                job.scope().connectionRevision()), ExistingWorkPolicy.APPEND_OR_REPLACE, request);
        return new Ticket(job, request.getId(), op);
    }

    public Operation cancel(String channelId, long revision) {
        if (coordinator != null) coordinator.cancel(channelId, revision);
        return WorkManager.getInstance(context).cancelAllWorkByTag(tag(channelId, revision));
    }

    private static String tag(String channelId, long revision) { return "sp045/" + revision + "/" + channelId; }

    static Data encode(MetricJob job) {
        Snapshot.Scope s = job.scope();
        List<String[]> rows = new ArrayList<>();
        for (MetricJob.PostTarget post : job.posts()) {
            PostRequest.Period p = post.period();
            rows.add(p == null ? new String[]{post.postId(), post.query().name()}
                    : new String[]{post.postId(), post.query().name(), p.startDate, p.endDate, p.verifiedZoneId, p.evidenceRef});
        }
        return new Data.Builder().putString("collection", job.collectionId()).putLong("started", job.collectionStartedAt())
                .putString("channel", s.channelId()).putString("account", s.account().name())
                .putString("platform", s.platform().name()).putString("external", s.externalAccountId())
                .putLong("revision", s.connectionRevision()).putString("version", job.apiVersion())
                .putBoolean("follower", job.follower()).putString("posts", new Gson().toJson(rows)).build();
    }

    static OneTimeWorkRequest request(MetricJob job) {
        Snapshot.Scope s = job.scope();
        return new OneTimeWorkRequest.Builder(MetricCollectionWorker.class).setInputData(encode(job))
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MetricCollectionCoordinator.BACKOFF_MS, TimeUnit.MILLISECONDS)
                .addTag(tag(s.channelId(), s.connectionRevision())).build();
    }

    static MetricJob decode(Data d) {
        Snapshot.Scope scope = new Snapshot.Scope(Platform.valueOf(d.getString("platform")),
                FollowerRequest.Account.valueOf(d.getString("account")), d.getString("channel"), d.getString("external"),
                d.getLong("revision", -1));
        List<MetricJob.PostTarget> posts = new ArrayList<>();
        for (String[] row : new Gson().fromJson(d.getString("posts"), String[][].class)) {
            if (row.length != 2 && row.length != 6) throw new IllegalArgumentException("post target fields");
            PostRequest.Period period = row.length == 2 ? null : new PostRequest.Period(row[2], row[3], row[4], row[5]);
            posts.add(new MetricJob.PostTarget(row[0], PostRequest.Query.valueOf(row[1]), period));
        }
        return new MetricJob(d.getString("collection"), d.getLong("started", -1), scope, d.getString("version"),
                d.getBoolean("follower", false), posts);
    }
}
