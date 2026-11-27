package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.data.local.comment.*;
import com.socialpilot.app.data.local.publishedpost.*;
import com.socialpilot.app.data.repository.comment.*;
import com.socialpilot.app.domain.contract.social.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.*;

/** Recreated synthetic root and query catalog. The WorkManager database alone survives restart. */
public final class DeviceCommentFixture {
    public static CommentPageRequest query(String channel, long revision, CommentPageRequest.Query filter) {
        return new CommentPageRequest(CommentPageRequest.Account.FACEBOOK_PAGE, channel, revision,
                "account-" + channel, "device-post", CommentPageRequest.Target.POST, "device-post",
                null, filter, 2, 1L, "v27.0", null);
    }
    public final CommentPageRequest query = query("device-channel", 7, CommentPageRequest.Query.FB_STREAM);
    public static CommentPageRequest instagram(long revision, CommentPageRequest.Target target) {
        return new CommentPageRequest(CommentPageRequest.Account.INSTAGRAM_PROFESSIONAL, "ig-channel", revision,
                "ig-account", "device-post", target, target == CommentPageRequest.Target.POST ? "device-post" : "device-id",
                null, CommentPageRequest.Query.IG_COMMENTS, 2, 1L, "v27.0", null);
    }
    public final SimulatedCommentStore store = new SimulatedCommentStore();
    public PublishedPostScope scope(CommentPageRequest q) {
        return new PublishedPostScope(q.platform, q.account.type, q.channelId, q.connectionRevision, q.externalAccountId);
    }
    public void seed(CommentPageRequest q) { store.seed(scope(q), q.postId); }
    public void invalidate(long revision) {
        store.state(scope(query(query.channelId, revision, query.query)), ConnectionStatus.DISCONNECTED);
    }
    public final AtomicInteger calls = new AtomicInteger();
    public volatile int status = 200;
    public volatile boolean twoPages;
    public volatile boolean holdResponse = true;
    public volatile boolean resumedPosition;
    public final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1),
            cancelled = new CountDownLatch(1);
    public final CommentRepository repo;

    public DeviceCommentFixture(CommentSyncCoordinator.Network network) {
        seed(query);
        CommentCursorCodec codec = new CommentCursorCodec(new SecretKeySpec(new byte[16], "AES"));
        ChannelCapability read = new ChannelCapability(SocialOperation.READ_COMMENTS, CapabilityAvailability.AVAILABLE,
                null, "2026-11-19T00:00:00Z");
        ChannelCapability reply = new ChannelCapability(SocialOperation.REPLY_COMMENT, CapabilityAvailability.UNVERIFIED,
                "fixture", null);
        OkHttpClient client = new OkHttpClient.Builder().eventListener(new EventListener() {
            @Override public void canceled(Call call) { cancelled.countDown(); }
        }).addInterceptor(chain -> {
            int request = calls.incrementAndGet();
            String body = "{\"data\":[{\"id\":\"device-id\"}]}";
            if (twoPages && request == 1) {
                body = "{\"data\":[{\"id\":\"first\"}],\"paging\":{\"cursors\":{\"after\":\"fixture-page\"},\"next\":\"fixture\"}}";
            } else if (holdResponse) {
                if (twoPages) resumedPosition = "fixture-page".equals(chain.request().url().queryParameter("after"));
                entered.countDown();
                try {
                    if (!release.await(45, TimeUnit.SECONDS)) throw new java.io.IOException("fixture timeout");
                } catch (InterruptedException e) { throw new java.io.IOException("fixture interrupted"); }
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                    .header("Retry-After", "3600")
                    .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
        }).build();
        CommentSyncCoordinator.Adapters adapters = q -> {
            ChannelInfo channel = new ChannelInfo(q.channelId, q.platform, q.externalAccountId, q.account.type,
                    "fixture", ConnectionStatus.CONNECTED, q.connectionRevision, "fixture-ref");
            CommentAdapter.Binding binding = new CommentAdapter.Binding(channel, q, read, reply, "device",
                    "fixture-post-ownership", 2, null);
            if (q.platform == Platform.INSTAGRAM)
                return new InstagramCommentAdapter(client, ignored -> binding, ignored -> "fixture-only",
                        System::currentTimeMillis, null, codec, HttpUrl.get("https://fixture.invalid/"),
                        CommentRecord.Source.MO_PHONG, "device");
            return new FacebookCommentAdapter(client, ignored -> binding, ignored -> "fixture-only",
                    System::currentTimeMillis, null, codec, HttpUrl.get("https://fixture.invalid/"),
                    CommentRecord.Source.MO_PHONG, "device");
        };
        repo = new CommentRepository(new CommentSyncCoordinator(new CommentCache(CommentPageWriter.forSimulation(store, codec)),
                adapters, System::currentTimeMillis, network, Executors.newSingleThreadExecutor(), 2), 60_000);
        repo.prepareWork(query);
    }
}
