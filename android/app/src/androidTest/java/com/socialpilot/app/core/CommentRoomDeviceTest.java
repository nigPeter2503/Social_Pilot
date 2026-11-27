package com.socialpilot.app.core;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.room.Room;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;
import com.socialpilot.app.data.local.comment.*;
import com.socialpilot.app.data.local.commentsync.*;
import com.socialpilot.app.data.local.publishedpost.*;
import com.socialpilot.app.data.remote.social.*;
import com.socialpilot.app.data.repository.ChannelRepository;
import com.socialpilot.app.data.repository.comment.*;
import com.socialpilot.app.data.security.*;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.social.*;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.junit.*;

/** Real on-disk Room and Keystore, with every provider GET intercepted on-device. */
public class CommentRoomDeviceTest {
    private static final String MEDIA = "00074";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger calls = new AtomicInteger();
    private final BlockingQueue<Reply> replies = new LinkedBlockingQueue<>();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile boolean holdResponse;
    private String name;
    private Context context;
    private CommentCandidateDatabase db;
    private ChannelRepository channels;
    private ChannelCommentAccess access;
    private CommentRepository repo;
    private PublishedPostRepository posts;
    private boolean online = true;
    private String phase;
    private boolean keepDatabase;
    private record Reply(String path, String bearer, int status, String body, String after) {}

    @Before public void setUp() {
        phase = InstrumentationRegistry.getArguments().getString("roomPhase");
        name = phase == null ? "sp041-fixture-" + UUID.randomUUID() : "sp041-worker-recovery";
        context = new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String key, int mode) {
                return super.getSharedPreferences(name + key, mode);
            }
        };
        open();
    }

    private void open() {
        db = Room.databaseBuilder(context, CommentCandidateDatabase.class, name).build();
        channels = new ChannelRepository(db, new EncryptedSecretStore(context, SecretNamespace.SOCIAL_TOKEN));
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            calls.incrementAndGet();
            Reply reply = replies.poll();
            if (reply == null || !request.method().equals("GET") || !request.url().host().equals("graph.facebook.com")
                    || !request.url().encodedPath().equals("/v27.0/" + reply.path())
                    || !("Bearer " + reply.bearer()).equals(request.header("Authorization"))
                    || !java.util.Objects.equals(reply.after(), request.url().queryParameter("after")))
                throw new IOException("Unexpected fixture request");
            if (holdResponse) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Fixture response timed out");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Fixture interrupted");
                }
            }
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(reply.status()).message("fixture").body(ResponseBody.create(reply.body(),
                            MediaType.get("application/json"))).build();
        }).build();
        access = new ChannelCommentAccess(channels, http);
        CommentRoomStore store = new CommentRoomStore(db);
        posts = PublishedPostRepository.forProduction(store.posts);
        repo = access.repository(store.comments, KeystoreRecordCipher.commentCursors(), () -> online, io, 60_000);
    }

    @After public void close() {
        db.close();
        if (!keepDatabase) {
            context.deleteDatabase(name);
            context.getSharedPreferences("secrets_social_v1", Context.MODE_PRIVATE).edit().clear().commit();
        }
        io.shutdownNow();
    }

    private ChannelRepository.Snapshot connect(boolean ig) {
        OAuthExchangeResponse grant = ig ? new OAuthExchangeResponse("ig-session", "INSTAGRAM", "00017",
                "PROFESSIONAL", "IG fixture", "fixture-ig", null, "bearer", null, "00012",
                Collections.singletonList("instagram_manage_comments"), "v27.0")
                : new OAuthExchangeResponse("fb-session", "FACEBOOK", "00012", "PAGE", "Page fixture",
                        "fixture-fb", null, "bearer", null);
        var channel = channels.save(grant);
        assertTrue(channels.recordCapability(channel.scope, new ChannelCapability(SocialOperation.READ_COMMENTS,
                CapabilityAvailability.AVAILABLE, null, "2026-11-01T00:00:00Z")));
        return channel;
    }

    private void reply(String path, String token, String body) {
        replies.add(new Reply(path, token, 200, body, null));
    }

    private PublishedPost importOwned(ChannelRepository.Snapshot channel) throws IOException {
        boolean ig = channel.info.platform == Platform.INSTAGRAM;
        reply(MEDIA, ig ? "fixture-ig" : "fixture-fb", "{\"id\":\"" + MEDIA + "\",\""
                + (ig ? "owner" : "from") + "\":{\"id\":\"" + channel.info.externalAccountId + "\"}}");
        var result = access.importPost(posts, PublishedPostScope.from(channel.info), MEDIA);
        assertEquals(PublishedPostRepository.Status.COMMITTED, result.status);
        return result.entries.get(0).post;
    }

    private CommentPageRequest query(ChannelRepository.Snapshot channel, String id) {
        boolean ig = channel.info.platform == Platform.INSTAGRAM;
        return new CommentPageRequest(ig ? CommentPageRequest.Account.INSTAGRAM_PROFESSIONAL
                : CommentPageRequest.Account.FACEBOOK_PAGE, channel.info.channelId, channel.info.connectionRevision,
                channel.info.externalAccountId, MEDIA, id == null ? CommentPageRequest.Target.POST
                : CommentPageRequest.Target.COMMENT, id == null ? MEDIA : id, null,
                ig ? CommentPageRequest.Query.IG_COMMENTS : CommentPageRequest.Query.FB_TOPLEVEL, 1, null, "v27.0", null);
    }

    private CommentSyncCoordinator.Result pull(CommentPageRequest q) throws Exception {
        BlockingQueue<CommentSyncCoordinator.Result> done = new ArrayBlockingQueue<>(1);
        repo.refresh(q, done::offer);
        var result = done.poll(15, TimeUnit.SECONDS);
        assertNotNull(result);
        return result;
    }

    @Test public void importedOwnerAndParentStayBoundToChannelAndRevision() throws Exception {
        var ig = connect(true);
        var fb = connect(false);
        reply(MEDIA, "fixture-ig", "{\"id\":\"00074\",\"owner\":{\"id\":\"00012\"}}");
        assertThrows(IOException.class, () -> access.importPost(posts, PublishedPostScope.from(ig.info), MEDIA));
        assertEquals(PublishedPostRepository.Status.MISSING,
                posts.lookupByExternalId(PublishedPostScope.from(ig.info), MEDIA).status);
        var post = importOwned(ig);
        assertEquals(post.id, importOwned(ig).id);
        assertNotEquals(post.id, importOwned(fb).id);
        var q = query(ig, null);
        reply(MEDIA + "/comments", "fixture-ig", "{\"data\":[{\"id\":\"p\"}]}");
        assertEquals(CommentSyncCoordinator.State.COMPLETE, pull(q).state());
        var child = query(ig, "p");
        reply("p/replies", "fixture-ig", "{\"data\":[{\"id\":\"r\"}]}");
        assertEquals(CommentSyncCoordinator.State.COMPLETE, pull(child).state());
        assertEquals("p", repo.loadCached(child, CommentStore.Handled.ALL).rows().get(0).parentCommentId);
        int sent = calls.get();
        assertEquals("POST_CONFLICT", pull(query(fb, "p")).reason());
        assertEquals(sent, calls.get());
        String otherMedia = "0075";
        reply(otherMedia, "fixture-ig", "{\"id\":\"0075\",\"owner\":{\"id\":\"00017\"}}");
        assertEquals(PublishedPostRepository.Status.COMMITTED,
                access.importPost(posts, PublishedPostScope.from(ig.info), otherMedia).status);
        var wrongMedia = new CommentPageRequest(q.account, q.channelId, q.connectionRevision,
                q.externalAccountId, otherMedia, CommentPageRequest.Target.COMMENT, "p", null,
                q.query, q.pageSize, null, q.apiVersion, null);
        assertEquals("POST_CONFLICT", pull(wrongMedia).reason());
        var wrongVersion = new CommentPageRequest(q.account, q.channelId, q.connectionRevision,
                q.externalAccountId, MEDIA, q.target, MEDIA, null, q.query, q.pageSize, null, "v26.0", null);
        assertEquals("SCOPE_MISMATCH", pull(wrongVersion).reason());
        assertEquals(sent + 1, calls.get());
        replies.add(new Reply(MEDIA + "/comments", "fixture-ig", 401, "{}", null));
        assertEquals(SocialError.Code.AUTH_REQUIRED, pull(q).error().code);
        assertEquals(ConnectionStatus.REAUTH_REQUIRED, channels.find(ig.info.channelId).info.connectionStatus);
        assertEquals(1, repo.loadCached(q, CommentStore.Handled.ALL).rows().size());
        connect(true);
        assertEquals("RETIRED_REVISION", pull(q).reason());
        assertTrue(channels.find(fb.info.channelId).connected());
    }

    @Test public void reconnectDuringGetCannotCommitOrRetireTheOtherChannel() throws Exception {
        var ig = connect(true);
        var fb = connect(false);
        importOwned(ig);
        var fbPost = importOwned(fb);
        var pageQuery = query(fb, null);
        reply(MEDIA + "/comments", "fixture-fb", "{\"data\":[{\"id\":\"same\"}]}");
        assertEquals(CommentSyncCoordinator.State.COMPLETE, pull(pageQuery).state());
        db.getOpenHelper().getWritableDatabase().execSQL("INSERT INTO drafts(topic,createdAt) VALUES ('fixture',1)");
        var stale = query(ig, null);
        reply(MEDIA + "/comments", "fixture-ig", "{\"data\":[{\"id\":\"same\"}]}");
        holdResponse = true;
        var done = new ArrayBlockingQueue<CommentSyncCoordinator.Result>(1);
        repo.refresh(stale, done::offer);
        ChannelRepository.Snapshot replacement;
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            replacement = connect(true);
        } finally {
            holdResponse = false;
            release.countDown();
        }
        assertEquals("RETIRED_REVISION", done.poll(10, TimeUnit.SECONDS).reason());
        assertNull(db.commentSync().checkpoint(stale.queryFingerprint));
        assertNull(db.commentSync().comment("INSTAGRAM", ig.info.channelId, "same"));
        assertEquals("2026-11-01T00:00:00Z", channels.find(replacement.info.channelId)
                .capability(SocialOperation.READ_COMMENTS).verifiedAt);
        repo.afterInvalidation(stale.channelId, stale.connectionRevision, fingerprint -> {});
        assertNull(db.commentSync().definition(stale.queryFingerprint));
        assertEquals(fbPost.id, posts.lookupByExternalId(PublishedPostScope.from(fb.info), MEDIA).post.id);
        assertEquals(1, repo.loadCached(pageQuery, CommentStore.Handled.ALL).rows().size());
        try (var drafts = db.query("SELECT topic FROM drafts", null)) {
            assertTrue(drafts.moveToFirst());
            assertEquals("fixture", drafts.getString(0));
        }
    }

    @Test public void checkpointFailureRollsBackCommentAndMembership() throws Exception {
        var fb = connect(false);
        importOwned(fb);
        var q = query(fb, null);
        db.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER reject_page BEFORE UPDATE OF checkpoint"
                + " ON comment_queries BEGIN SELECT RAISE(ABORT,'fixture'); END");
        reply(MEDIA + "/comments", "fixture-fb", "{\"data\":[{\"id\":\"c\"}]}");
        assertEquals("STORAGE", pull(q).reason());
        db.close();
        open();
        var view = repo.loadCached(q, CommentStore.Handled.ALL);
        assertTrue(view.rows().isEmpty());
        assertNull(view.paging());
        assertEquals("STORAGE", view.failure());
        assertNull(db.commentSync().comment("FACEBOOK", fb.info.channelId, "c"));
    }

    private void finishQueuedWork() throws Exception {
        var app = (CommentDeviceRunner.FixtureApp) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        var mark = context.getSharedPreferences("recovery", 0);
        assertNotEquals(mark.getInt("pid", -1), android.os.Process.myPid());
        var ig = channels.list().get(0);
        var q = query(ig, null);
        online = false;
        assertEquals(1, repo.loadCached(q, CommentStore.Handled.ALL).rows().size());
        assertEquals(mark.getString("post", null), posts.lookupByExternalId(PublishedPostScope.from(ig.info), MEDIA).post.id);
        var wm = androidx.work.WorkManager.getInstance(app);
        var id = UUID.fromString(mark.getString("work", null));
        assertEquals(androidx.work.WorkInfo.State.ENQUEUED, wm.getWorkInfoById(id).get().getState());
        replies.add(new Reply(MEDIA + "/comments", "fixture-ig", 200,
                "{\"data\":[{\"id\":\"c1\"},{\"id\":\"c2\"}]}", "opaque-fixture"));
        app.roomRepository = repo;
        online = true;
        try {
            CommentWorkDeviceTest.shell("svc wifi enable");
            CommentWorkDeviceTest.shell("svc data enable");
            long end = android.os.SystemClock.elapsedRealtime() + 90_000;
            androidx.work.WorkInfo info;
            do {
                info = wm.getWorkInfoById(id).get(5, TimeUnit.SECONDS);
                if (info.getState().isFinished()) break;
                android.os.SystemClock.sleep(150);
            } while (android.os.SystemClock.elapsedRealtime() < end);
            assertEquals(androidx.work.WorkInfo.State.SUCCEEDED, info.getState());
            assertEquals("COMPLETE", info.getOutputData().getString("state"));
            assertEquals(1, calls.get());
            online = false;
            var view = repo.loadCached(q, CommentStore.Handled.ALL);
            assertEquals(CommentRepository.State.OFFLINE_CACHED, view.state());
            assertEquals(2, view.rows().size());
            assertNotNull(view.lastCompleteSyncAt());
        } finally {
            app.roomRepository = null;
            mark.edit().clear().commit();
        }
    }

    private class WorkerContext extends ContextWrapper implements CommentSyncWorker.Provider {
        WorkerContext() { super(context); }
        @Override public Context getApplicationContext() { return this; }
        @Override public CommentRepository comments() { return repo; }
    }

    @Test public void reopenedWorkerUsesEncryptedCursorAndKeepsOfflineRows() throws Exception {
        if ("resume".equals(phase)) {
            finishQueuedWork();
            return;
        }
        var ig = connect(true);
        var post = importOwned(ig);
        var q = query(ig, null);
        var work = CommentSyncWorker.encode(repo.prepareWork(q));
        String edge = MEDIA + "/comments";
        reply(edge, "fixture-ig", "{\"data\":[{\"id\":\"c1\"}],\"paging\":{\"next\":\"redacted-next\","
                + "\"cursors\":{\"after\":\"opaque-fixture\"}}}");
        replies.add(new Reply(edge, "fixture-ig", 403, "{}", "opaque-fixture"));
        assertEquals(CommentSyncCoordinator.State.FAILED, pull(q).state());
        var partial = repo.loadCached(q, CommentStore.Handled.ALL);
        assertEquals(CommentCheckpoint.State.MORE, partial.paging());
        assertNull(partial.lastCompleteSyncAt());
        assertEquals(1, partial.rows().size());
        String cp = db.commentSync().checkpoint(q.queryFingerprint);
        assertFalse(cp.contains("opaque-fixture"));
        assertFalse(cp.contains("redacted-next"));
        if ("queue".equals(phase)) {
            var wm = androidx.work.WorkManager.getInstance(context);
            CommentSyncWorker.enqueue(context, repo, q).getResult().get(10, TimeUnit.SECONDS);
            var workInfo = wm.getWorkInfosForUniqueWork(CommentSyncWorker.uniqueName(q.queryFingerprint))
                    .get(10, TimeUnit.SECONDS).get(0);
            assertEquals(androidx.work.WorkInfo.State.ENQUEUED, workInfo.getState());
            assertTrue(context.getSharedPreferences("recovery", 0).edit()
                    .putString("work", workInfo.getId().toString()).putString("post", post.id)
                    .putInt("pid", android.os.Process.myPid()).commit());
            keepDatabase = true;
            return;
        }
        db.close();
        open();
        assertEquals(post.id, posts.lookupByExternalId(PublishedPostScope.from(ig.info), MEDIA).post.id);
        replies.add(new Reply(edge, "fixture-ig", 200, "{\"data\":[{\"id\":\"c1\"},{\"id\":\"c2\"}]}", "opaque-fixture"));
        var worker = TestListenableWorkerBuilder.from(new WorkerContext(), CommentSyncWorker.class)
                .setInputData(work).build();
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Success);
        db.close();
        open();
        online = false;
        var view = repo.loadCached(q, CommentStore.Handled.ALL);
        assertEquals(CommentRepository.State.OFFLINE_CACHED, view.state());
        assertEquals(2, view.rows().size());
        assertEquals(CommentCheckpoint.State.COMPLETE, view.paging());
        assertNotNull(view.lastCompleteSyncAt());
        assertTrue(replies.isEmpty());
    }
}
