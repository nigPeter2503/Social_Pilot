package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.data.local.comment.*;
import com.socialpilot.app.data.local.publishedpost.*;
import com.socialpilot.app.data.repository.comment.*;
import com.socialpilot.app.data.repository.comment.CommentSyncCoordinator.Result;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.*;
import java.net.InetAddress;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import static org.junit.Assert.*;
import static com.socialpilot.app.data.repository.comment.CommentSyncCoordinator.State.*;
import static com.socialpilot.app.data.repository.comment.CommentRepository.State.*;
import static com.socialpilot.app.data.remote.social.CommentPageRequest.*;

/** Actual adapter, HTTP and writer. Only ownership, transactions, network status and time are fixtures. */
public final class CommentSyncTest {
    private static final String EMPTY_PAGE = "{\"data\":[]}";
    private static String row(String id) { return "{\"id\":\"" + id + "\"}"; }
    private static String page(String items, boolean more) {
        return page(items, more ? "fixture-position" : null);
    }
    private static String page(String items, String after) {
        return "{\"data\":[" + items + "]" + (after == null ? ""
                : ",\"paging\":{\"cursors\":{\"after\":\"" + after + "\"},\"next\":\"fixture-next\"}") + "}";
    }

    private final SimulatedCommentStore store = new SimulatedCommentStore();
    private final CommentCursorCodec codec = new CommentCursorCodec(new SecretKeySpec(new byte[16], "AES"));
    private final CommentPageWriter writer = CommentPageWriter.forSimulation(store, codec);
    private final CommentCache cache = new CommentCache(writer);
    private final AtomicLong clock = new AtomicLong(10_000);
    private final AtomicBoolean online = new AtomicBoolean(true);
    private final CountDownLatch httpCancelled = new CountDownLatch(1);
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final MockWebServer server = new MockWebServer();
    private HttpUrl endpoint;
    private OkHttpClient client = new OkHttpClient.Builder().eventListener(new okhttp3.EventListener() {
        @Override public void canceled(Call call) { httpCancelled.countDown(); }
    }).build();
    private CommentCursorCodec adapterKey = codec;
    private CommentRepository repo;
    private final CommentPageRequest q = query(Query.FB_STREAM, Target.POST, "post", 7);

    private static CommentPageRequest query(Query filter, Target target, String id, long revision) {
        return new CommentPageRequest(Account.FACEBOOK_PAGE, "chan", revision, "account", "post",
                target, id, null, filter, 2, 1_000L, "v27.0", null);
    }
    private static CommentPageRequest instagram(String channel, String media, Long historyFrom) {
        return new CommentPageRequest(Account.INSTAGRAM_PROFESSIONAL, channel, 7, "ig-" + channel, media,
                Target.POST, media, null, Query.IG_COMMENTS, 2, historyFrom, "v27.0", null);
    }
    private PublishedPostScope scope(CommentPageRequest r) {
        return new PublishedPostScope(r.platform, r.account.type,
                r.channelId, r.connectionRevision, r.externalAccountId);
    }
    @Before public void setup() throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server.start(loopback, 0);
        // After a cancelled call OkHttp can try another localhost address. Only this one is listening.
        endpoint = server.url("/").newBuilder().host(loopback.getHostAddress()).build();
        store.seed(scope(q), q.postId);
        repo = repository(3);
    }
    @After public void close() throws Exception {
        io.shutdownNow();
        client.dispatcher().executorService().shutdownNow();
        client.connectionPool().evictAll();
        server.shutdown();
    }
    private CommentRepository repository(int budget) {
        return new CommentRepository(new CommentSyncCoordinator(cache, this::adapter, clock::get, online::get, io, budget), 1_000);
    }
    private CommentAdapter adapter(CommentPageRequest r) {
        ChannelInfo ch = new ChannelInfo(r.channelId, r.platform, r.externalAccountId, scope(r).accountType, "fixture",
                ConnectionStatus.CONNECTED, r.connectionRevision, "fixture-ref");
        ChannelCapability read = new ChannelCapability(SocialOperation.READ_COMMENTS, CapabilityAvailability.AVAILABLE,
                null, "2026-11-19T00:00:00Z");
        ChannelCapability reply = new ChannelCapability(SocialOperation.REPLY_COMMENT, CapabilityAvailability.UNVERIFIED,
                "fixture", null);
        CommentAdapter.Binding binding = new CommentAdapter.Binding(ch, r, read, reply, "fixture", "fixture", 2,
                null, new CommentAdapter.PermissionEvidence(r, 200, "fixture-403"));
        if (r.platform == Platform.INSTAGRAM)
            return new InstagramCommentAdapter(client, ignored -> binding, ignored -> "fixture-token",
                    clock::get, null, adapterKey, endpoint, CommentRecord.Source.MO_PHONG, "fixture");
        return r.platform == Platform.FACEBOOK ? new FacebookCommentAdapter(client, ignored -> binding, ignored -> "fixture-token",
                clock::get, null, adapterKey, endpoint, CommentRecord.Source.MO_PHONG, "fixture")
                : new LinkedInCommentAdapter(client, ignored -> binding, ignored -> "fixture-token", clock::get, null,
                        adapterKey, endpoint, CommentRecord.Source.MO_PHONG, "fixture");
    }

    private Result pull(CommentPageRequest r) throws Exception {
        CompletableFuture<Result> done = new CompletableFuture<>();
        repo.refresh(r, done::complete);
        return done.get(5, TimeUnit.SECONDS);
    }
    private Result resume(CommentCache.WorkKey key) throws Exception {
        CompletableFuture<Result> done = new CompletableFuture<>();
        repo.coordinator.resume(key, done::complete);
        return done.get(5, TimeUnit.SECONDS);
    }
    private CommentRepository.View view(CommentPageRequest r) { return repo.loadCached(r, CommentStore.Handled.ALL); }
    private void respond(String body) { server.enqueue(new MockResponse().setBody(body)); }
    private String position() throws Exception {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        return request.getRequestUrl().queryParameter("after");
    }

    private final CountDownLatch release = new CountDownLatch(1);
    private void blockFirst(String first, String later) {
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            private final AtomicInteger count = new AtomicInteger();
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (count.incrementAndGet() == 1) {
                    if (!release.await(5, TimeUnit.SECONDS)) return new MockResponse().setResponseCode(500);
                    return new MockResponse().setBody(first);
                }
                return new MockResponse().setBody(later);
            }
        });
    }

    @Test public void workerPullAndRotationShareHttpAndLateWorkerSeesCommittedChain() throws Exception {
        CommentCache.WorkKey queued = repo.prepareWork(q);
        blockFirst(page(row("a"), false), EMPTY_PAGE);
        CompletableFuture<Result> worker = new CompletableFuture<>(), oldView = new CompletableFuture<>(),
                rotated = new CompletableFuture<>();
        repo.coordinator.resume(queued, worker::complete);
        assertNull(position());
        Cancelable old = repo.refresh(q, oldView::complete);
        repo.refresh(q, rotated::complete);
        old.cancel();
        assertEquals(LOADING, view(q).state());
        release.countDown();
        assertEquals(COMPLETE, worker.get(3, TimeUnit.SECONDS).state());
        assertEquals(worker.get(), rotated.get(3, TimeUnit.SECONDS));
        assertFalse(oldView.isDone());
        assertEquals(READY, view(q).state());
        assertEquals(1, view(q).rows().size());
        assertEquals(COMPLETE, resume(queued).state());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void differentFiltersAndTargetsQueueAndRetainOverlappingMembership() throws Exception {
        CommentPageRequest top = query(Query.FB_TOPLEVEL, Target.POST, "post", 7);
        CommentPageRequest child = query(Query.FB_STREAM, Target.COMMENT, "parent", 7);
        blockFirst(page(row("a") + "," + row("shared"), false), page(row("b") + "," + row("shared"), false));
        CompletableFuture<Result> first = new CompletableFuture<>(), second = new CompletableFuture<>(), third = new CompletableFuture<>();
        repo.refresh(q, first::complete);
        assertNull(position());
        repo.refresh(top, second::complete);
        repo.refresh(child, third::complete);
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
        assertNull(writer.prepare(top).previous);
        release.countDown();
        for (CompletableFuture<Result> done : java.util.Arrays.asList(first, second, third))
            assertEquals(COMPLETE, done.get(3, TimeUnit.SECONDS).state());
        assertEquals("toplevel", server.takeRequest().getRequestUrl().queryParameter("filter"));
        assertTrue(server.takeRequest().getPath().contains("/parent/comments"));
        assertEquals(3, store.checkpoints());
        assertEquals(3, store.size());
        assertEquals("a", view(q).rows().get(0).observation.commentId);
        assertEquals("b", view(top).rows().get(0).observation.commentId);
        assertEquals(2, view(child).rows().size());
        assertTrue(store.markHandled(q, "shared"));
        assertEquals("shared", repo.loadCached(top, CommentStore.Handled.HANDLED).rows().get(0).observation.commentId);
        assertEquals(1, repo.loadCached(q, CommentStore.Handled.UNHANDLED).rows().size());
    }

    @Test public void budgetAndHistoryResumeAcrossCoordinatorReconstruction() throws Exception {
        repo = repository(1);
        CommentCache.WorkKey key = repo.prepareWork(q);
        String old = "{\"id\":\"old\",\"created_time\":\"1970-01-01T00:00:00Z\"}";
        String edge = "{\"id\":\"edge\",\"created_time\":\"1970-01-01T00:00:01Z\"}";
        respond(page(old + "," + edge, true));
        assertEquals(PAGE_LIMIT, resume(key).state());
        CommentRepository.View partial = view(q);
        assertEquals(PARTIAL, partial.state());
        assertEquals(Long.valueOf(1000), partial.oldestObservedAt());
        assertNull(partial.lastCompleteSyncAt());
        assertNull(position());
        assertNull(store.row(q.platform, q.channelId, "old"));
        repo = repository(1);
        clock.set(15_000);
        respond(page(row("undated"), false));
        assertEquals(COMPLETE, resume(key).state());
        assertEquals("fixture-position", position());
        assertEquals(2, view(q).rows().size());
        assertEquals(Long.valueOf(15_000), view(q).lastCompleteSyncAt());
        assertEquals(2, server.getRequestCount());
    }

    @Test public void instagramWorkerResumesEachQueryAndDeduplicatesOverlappingPages() throws Exception {
        repo = repository(1);
        CommentPageRequest[] queries = {
                instagram("one", "media", null), instagram("two", "media", null),
                instagram("one", "other", null), instagram("one", "media", 1_000L)
        };
        CommentCache.WorkKey[] jobs = new CommentCache.WorkKey[queries.length];
        for (int i = 0; i < queries.length; i++) {
            CommentPageRequest query = queries[i];
            store.seed(scope(query), query.postId);
            jobs[i] = repo.prepareWork(query);
            respond(page(row("id-" + i), "cursor-" + i));
            assertEquals(PAGE_LIMIT, pull(query).state());
            assertNull(position());
        }
        repo = repository(1);
        assertEquals(OBSOLETE, resume(new CommentCache.WorkKey(jobs[0].fingerprint(), 8, 0)).state());
        for (int i = queries.length - 1; i >= 0; i--) {
            respond(page(row("id-" + i), false));
            assertEquals(COMPLETE, resume(jobs[i]).state());
            assertEquals("cursor-" + i, position());
            var cached = view(queries[i]);
            assertEquals(CommentCheckpoint.State.COMPLETE, cached.paging());
            assertEquals(1, cached.rows().size());
            assertEquals("id-" + i, cached.rows().get(0).observation.commentId);
        }
        assertEquals(4, store.size());
        assertEquals(8, server.getRequestCount());
    }

    @Test public void instagramPageTwoNetworkLossKeepsFirstPageThroughWorkerRetry() throws Exception {
        CommentPageRequest ig = instagram("one", "media", null);
        store.seed(scope(ig), ig.postId);
        var job = repo.prepareWork(ig);
        respond(page(row("shared"), true));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        Result failed = pull(ig);
        assertEquals(WAIT, failed.state());
        assertNull(position());
        assertEquals("fixture-position", position());
        online.set(false);
        var cached = view(ig);
        assertEquals(OFFLINE_CACHED, cached.state());
        assertEquals("shared", cached.rows().get(0).observation.commentId);
        assertEquals(CommentCheckpoint.State.MORE, cached.paging());
        assertNull(cached.lastCompleteSyncAt());
        assertEquals(SocialError.Code.NETWORK_ERROR, cached.error().code);
        assertEquals(Long.valueOf(clock.get() + CommentSyncCoordinator.BACKOFF_MS), cached.retryAt());

        repo = repository(3);
        online.set(true);
        assertEquals(WAIT, resume(job).state());
        assertEquals(2, server.getRequestCount());
        clock.set(cached.retryAt());
        respond(page(row("shared") + "," + row("new"), false));
        assertEquals(COMPLETE, resume(job).state());
        assertEquals("fixture-position", position());
        assertEquals(2, store.size());
        assertEquals(2, view(ig).rows().size());
        assertNull(view(ig).error());
    }

    @Test public void nextPage429PreservesCacheAndHonorsDeadlineAcrossRestart() throws Exception {
        respond(page(row("a"), false));
        assertEquals(COMPLETE, pull(q).state());
        clock.set(10_500);
        respond(page(row("b"), true));
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "120").setBody("{}"));
        Result limited = pull(q);
        assertEquals(WAIT, limited.state());
        assertEquals(Long.valueOf(130_500), limited.retryAt());
        CommentCheckpoint cp = writer.prepare(q).previous;
        assertEquals(CommentCheckpoint.State.MORE, cp.state);
        assertEquals(Long.valueOf(10_000), cp.lastCompleteSyncAt);
        assertEquals(Long.valueOf(10_500), view(q).lastAttemptAt());
        assertEquals(CommentRepository.Freshness.FRESH, view(q).freshness());
        assertEquals(2, view(q).rows().size());
        assertEquals(SocialError.Code.RATE_LIMITED, view(q).error().code);
        repo = repository(3);
        clock.set(130_499);
        assertEquals(WAIT, pull(q).state());
        assertEquals(WAIT, pull(query(Query.FB_TOPLEVEL, Target.POST, "post", 7)).state());
        assertSame(cp, writer.prepare(q).previous);
        assertEquals(3, server.getRequestCount());
        clock.incrementAndGet();
        respond(EMPTY_PAGE);
        assertEquals(COMPLETE, pull(q).state());
        assertNull(position());
        assertNull(position());
        assertEquals("fixture-position", position());
        assertEquals("fixture-position", position());
        assertEquals(4, server.getRequestCount());
        assertNull(view(q).error());
    }

    @Test public void timeoutUsesIncreasingBackoffAndNeverRestartsCursor() throws Exception {
        repo = repository(1);
        respond(page(row("a"), true));
        assertEquals(PAGE_LIMIT, pull(q).state());
        CommentCheckpoint cp = writer.prepare(q).previous;
        client = client.newBuilder().readTimeout(150, TimeUnit.MILLISECONDS).build();
        for (long delay : new long[]{30_000, 60_000, 120_000}) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            Result failure = pull(q);
            assertEquals(WAIT, failure.state());
            assertEquals(SocialError.Code.NETWORK_ERROR, failure.error().code);
            assertEquals(Long.valueOf(clock.get() + delay), failure.retryAt());
            assertEquals(WAIT, pull(q).state());
            assertSame(cp, writer.prepare(q).previous);
            clock.addAndGet(delay);
        }
        assertEquals(4, server.getRequestCount());
        assertEquals(CommentCheckpoint.State.MORE, cp.state);
        assertEquals(1, view(q).rows().size());
        assertNull(view(q).lastCompleteSyncAt());
    }

    @Test public void permissionReconnectAndGeneric403StopWithLastGoodData() throws Exception {
        respond(page(row("cached"), false));
        assertEquals(COMPLETE, pull(q).state());
        CommentCheckpoint cp = writer.prepare(q).previous;
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"error\":{\"code\":200}}"));
        assertEquals(SocialError.NextAction.RESOLVE_PERMISSION, pull(q).error().nextAction);
        assertEquals(ERROR, view(q).state());
        assertSame(cp, writer.prepare(q).previous);
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{}"));
        Result unknown = pull(q);
        assertEquals(FAILED, unknown.state());
        assertNull(unknown.error());
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{}"));
        assertEquals(SocialError.NextAction.RECONNECT, pull(q).error().nextAction);
        store.state(scope(q), ConnectionStatus.REAUTH_REQUIRED);
        assertEquals(FAILED, pull(q).state());
        assertEquals(ConnectionStatus.REAUTH_REQUIRED, view(q).connection());
        assertEquals(1, view(q).rows().size());
        assertEquals(4, server.getRequestCount());
    }

    @Test public void offlineWithAndWithoutCacheAndConfirmedEmpty() throws Exception {
        online.set(false);
        assertEquals(OFFLINE, pull(q).state());
        assertEquals(OFFLINE_NO_CACHE, view(q).state());
        assertNull(writer.prepare(q).previous);
        assertEquals(0, server.getRequestCount());
        online.set(true);
        respond(EMPTY_PAGE);
        assertEquals(COMPLETE, pull(q).state());
        assertEquals(EMPTY, view(q).state());
        online.set(false);
        assertEquals(OFFLINE_CACHED, view(q).state());
        assertEquals(OFFLINE, pull(q).state());
        clock.addAndGet(1_001);
        assertEquals(CommentRepository.Freshness.STALE, view(q).freshness());
        assertEquals(Long.valueOf(10_000), view(q).lastCompleteSyncAt());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void unknownHasMoreCannotComplete() throws Exception {
        String post = "urn:li:share:77";
        CommentPageRequest li = new CommentPageRequest(Account.LINKEDIN_MEMBER, "li", 7, "subject", post,
                Target.POST, post, "urn:li:activity:88", Query.LI_COMMENTS, 2, null, "202610", null);
        store.seed(scope(li), post);
        respond("{\"elements\":[]}");
        assertEquals(PAGING_UNKNOWN, pull(li).state());
        assertEquals(PARTIAL, view(li).state());
        assertEquals("NOT_ESTABLISHED", view(li).paginationReason());
        assertNull(view(li).lastCompleteSyncAt());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void rotationCanRebindToRetainedPullWithNoWorker() throws Exception {
        blockFirst(page(row("a"), false), EMPTY_PAGE);
        CompletableFuture<Result> old = new CompletableFuture<>(), rotated = new CompletableFuture<>();
        Cancelable original = repo.refresh(q, old::complete);
        assertNull(position());
        assertEquals(LOADING, view(q).state());
        repo.refresh(q, rotated::complete);
        original.cancel();
        release.countDown();
        assertEquals(COMPLETE, rotated.get(3, TimeUnit.SECONDS).state());
        assertFalse(old.isDone());
        online.set(false);
        assertEquals(OFFLINE_CACHED, view(q).state());
        assertEquals("a", view(q).rows().get(0).observation.commentId);
        assertEquals(1, server.getRequestCount());
    }

    @Test public void checkpointWriteFailureRollsBackMembershipAndPublishesError() throws Exception {
        respond(page(row("good"), false));
        assertEquals(COMPLETE, pull(q).state());
        CommentCheckpoint good = writer.prepare(q).previous;
        clock.addAndGet(123);
        store.failCheckpoint = true;
        respond(page(row("rolled-back"), true));
        assertEquals("STORAGE", pull(q).reason());
        assertSame(good, writer.prepare(q).previous);
        assertEquals(ERROR, view(q).state());
        assertEquals("STORAGE", view(q).failure());
        assertEquals(Long.valueOf(10_123), view(q).lastAttemptAt());
        assertEquals(1, view(q).rows().size());
        assertEquals("good", view(q).rows().get(0).observation.commentId);
        assertEquals(2, server.getRequestCount());
    }

    @Test public void supersededMoreResumesWinnersPositionInsideBudget() throws Exception {
        blockFirst(page(row("stale"), false), page(row("resumed"), false));
        CompletableFuture<Result> callback = new CompletableFuture<>();
        repo.refresh(q, callback::complete);
        assertNull(position());
        CommentPageWriter.Attempt rival = writer.prepare(q);
        CommentPage more = new CommentPage(rival.request, Collections.emptyList(), codec.encode(q, "winner"),
                true, null, clock.get(), CommentRecord.Source.MO_PHONG, "rival");
        writer.commit(rival, new CommentPageReceipt(rival.request, CommentPageResult.success(more)));
        release.countDown();
        assertEquals(COMPLETE, callback.get(3, TimeUnit.SECONDS).state());
        assertEquals("winner", position());
        assertEquals("resumed", view(q).rows().get(0).observation.commentId);
        assertNull(store.row(q.platform, q.channelId, "stale"));
        assertEquals(2, server.getRequestCount());
    }

    @Test public void invalidStoredPositionRestartsWithinBudgetWithoutCallingProviderWithIt() throws Exception {
        repo = repository(1);
        respond(page(row("a"), true));
        assertEquals(PAGE_LIMIT, pull(q).state());
        byte[] changed = new byte[16];
        changed[0] = 7;
        adapterKey = new CommentCursorCodec(new SecretKeySpec(changed, "AES"));
        assertEquals(PAGE_LIMIT, pull(q).state());
        assertEquals(CommentCheckpoint.State.RESTART_REQUIRED, writer.prepare(q).previous.state);
        assertNull(view(q).lastCompleteSyncAt());
        assertEquals(1, view(q).rows().size());
        respond(page(row("a"), false));
        assertEquals(COMPLETE, pull(q).state());
        assertNull(position());
        assertNull(position());
        assertEquals(2, server.getRequestCount());
        assertEquals(1, store.size());
    }

    @Test public void cancelledHttpAndLateResponseCannotCommitOrNotify() throws Exception {
        blockFirst(page(row("late"), false), page(row("fresh"), false));
        CompletableFuture<Result> late = new CompletableFuture<>();
        Cancelable sub = repo.refresh(q, late::complete);
        assertNull(position());
        sub.cancel();
        assertTrue(httpCancelled.await(2, TimeUnit.SECONDS));
        release.countDown();
        assertEquals(COMPLETE, pull(q).state());
        assertFalse(late.isDone());
        assertNull(store.row(q.platform, q.channelId, "late"));
        assertEquals(1, writer.prepare(q).previous.completedChains);
        assertEquals(1, store.size());
        assertEquals(2, server.getRequestCount());
    }

    @Test public void supersededRereadsWinningCheckpointAndDoesNotRefetchTerminalPage() throws Exception {
        blockFirst(page(row("late"), true), EMPTY_PAGE);
        CompletableFuture<Result> callback = new CompletableFuture<>();
        repo.refresh(q, callback::complete);
        assertNull(position());
        CommentPageWriter.Attempt rival = writer.prepare(q);
        CommentPage terminal = new CommentPage(rival.request, Collections.emptyList(), null, false, null,
                99_000, CommentRecord.Source.MO_PHONG, "rival");
        CommentCheckpoint won = writer.commit(rival,
                new CommentPageReceipt(rival.request, CommentPageResult.success(terminal))).checkpoint;
        release.countDown();
        assertEquals(COMPLETE, callback.get(3, TimeUnit.SECONDS).state());
        assertSame(won, writer.prepare(q).previous);
        assertTrue(view(q).rows().isEmpty());
        assertEquals(1, server.getRequestCount());
    }

    @Test public void instagramOfflineAndRetiredWorkPreserveFacebookCache() throws Exception {
        CommentPageRequest ig = new CommentPageRequest(Account.INSTAGRAM_PROFESSIONAL, "ig-channel", 3,
                "ig-user", "post", Target.POST, "post", null, Query.IG_COMMENTS, 2, null, "v27.0", null);
        store.seed(scope(ig), ig.postId);
        CommentCache.WorkKey key = repo.prepareWork(ig);
        respond(page(row("same"), false));
        assertEquals(COMPLETE, pull(q).state());
        respond("{'data':[{'id':'same','text':'IG'}]}");
        assertEquals(COMPLETE, pull(ig).state());
        online.set(false);
        assertEquals(OFFLINE_CACHED, view(ig).state());
        assertEquals("IG", view(ig).rows().get(0).message);
        online.set(true);
        var facebook = view(q);
        store.state(new PublishedPostScope(Platform.INSTAGRAM, AccountType.PROFESSIONAL,
                ig.channelId, 4, ig.externalAccountId), ConnectionStatus.DISCONNECTED);
        repo.afterInvalidation(ig.channelId, 3, fp -> assertEquals(ig.queryFingerprint, fp));
        assertEquals(OBSOLETE, resume(key).state());
        assertEquals(facebook, view(q));
        assertEquals(2, server.getRequestCount());
    }

    @Test public void lateInstagramPageCannotAdvanceCheckpointAndFacebookJobSurvivesCleanup() throws Exception {
        CommentPageRequest ig = instagram("ig-channel", q.postId, null);
        store.seed(scope(ig), ig.postId);
        repo = repository(1);
        respond(page(row("same"), false));
        assertEquals(COMPLETE, pull(q).state());
        var facebook = view(q);
        var facebookJob = repo.prepareWork(q);
        respond(page(row("same"), true));
        assertEquals(PAGE_LIMIT, pull(ig).state());
        assertNull(position());
        assertNull(position());
        var igJob = repo.prepareWork(ig);
        var checkpointKey = new CommentCheckpoint.Key(ig);
        var before = writer.prepare(ig).previous;
        blockFirst(page(row("late"), false), page(row("fb-new"), false));
        var worker = new CompletableFuture<Result>();
        repo.coordinator.resume(igJob, worker::complete);
        assertEquals("fixture-position", position());
        var attempt = store.transaction(tx -> tx.attempt(checkpointKey));
        store.state(new PublishedPostScope(ig.platform, ig.account.type, ig.channelId,
                ig.connectionRevision + 1, ig.externalAccountId), ConnectionStatus.DISCONNECTED);
        release.countDown(); // Deliver a successful page before the cleanup hook can cancel HTTP.
        assertEquals(OBSOLETE, worker.get(5, TimeUnit.SECONDS).state());
        assertNull(store.row(ig.platform, ig.channelId, "late"));
        assertSame(before, store.transaction(tx -> tx.checkpoint(checkpointKey)));
        assertSame(attempt, store.transaction(tx -> tx.attempt(checkpointKey)));

        java.util.List<String> cancelled = new java.util.ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            repo.afterInvalidation(ig.channelId, ig.connectionRevision, cancelled::add);
            assertEquals(Collections.singletonList(igJob.fingerprint()), cancelled);
            assertEquals(new SimulatedCommentStore.Counts(0, 0, 0, 0, 0, 0),
                    store.counts(ig.channelId, ig.connectionRevision));
            assertEquals(facebook, view(q));
        }
        assertEquals(OBSOLETE, resume(igJob).state());
        assertEquals(COMPLETE, resume(facebookJob).state());
        assertEquals(2, view(q).rows().size());
        assertNotNull(store.row(q.platform, q.channelId, "fb-new"));
        assertEquals(4, server.getRequestCount());
    }

    @Test public void cleanupInterruptedThenRetriedAfterReconnectPreservesNewAndOtherOwners() throws Exception {
        CommentPageRequest b = new CommentPageRequest(q.account, "B", 7, "account-B", q.postId,
                q.target, q.targetId, null, q.query, q.pageSize, q.historyFrom, q.apiVersion, null);
        CommentPageRequest a9 = query(q.query, q.target, q.targetId, 9);
        store.seed(scope(b), b.postId);
        CommentCache.WorkKey old = repo.prepareWork(q);
        for (CommentPageRequest r : new CommentPageRequest[]{q, b}) {
            repo.prepareWork(r);
            respond(page(row("same") + "," + row("old-only"), false));
            assertEquals(COMPLETE, pull(r).state());
            assertTrue(store.markHandled(r, "same"));
        }
        var full = new SimulatedCommentStore.Counts(2, 2, 1, 1, 1, 1);
        assertEquals(full, store.counts(q.channelId, 7));
        assertEquals(full, store.counts(b.channelId, 7));
        var beforeB = view(b);
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "120"));
        assertEquals(WAIT, pull(q).state());
        try {
            repo.afterInvalidation(q.channelId, 7, fp -> fail("active revision"));
            fail("owner must invalidate first");
        } catch (CommentPageWriter.Rejected expected) {
            assertEquals(CommentPageWriter.Failure.REVISION_NOT_RETIRED, expected.reason);
            assertEquals(full, store.counts(q.channelId, 7));
        }

        store.state(scope(query(q.query, q.target, q.targetId, 8)), ConnectionStatus.DISCONNECTED);
        assertDisconnected(pull(q));
        try {
            repo.prepareWork(query(Query.FB_TOPLEVEL, q.target, q.targetId, 7));
            fail("new Worker admission after invalidation");
        } catch (CommentPageWriter.Rejected denied) {
            assertEquals(CommentPageWriter.Failure.DISCONNECTED, denied.reason);
        }
        assertEquals(ERROR, view(q).state());
        assertEquals(SocialError.NextAction.RECONNECT, view(q).error().nextAction);
        assertEquals(OBSOLETE, resume(old).state());
        store.failCleanup = true;
        try {
            repo.afterInvalidation(q.channelId, 7, fingerprint -> assertEquals(q.queryFingerprint, fingerprint));
            fail("injected cleanup failure");
        } catch (CommentPageWriter.Rejected expected) {
            assertEquals(CommentPageWriter.Failure.STORAGE, expected.reason);
        }
        assertEquals(full, store.counts(q.channelId, 7));
        assertNotNull(store.transaction(tx -> tx.attempt(new CommentCheckpoint.Key(q))).retryAt());
        assertEquals(3, server.getRequestCount());

        store.seed(scope(a9), a9.postId);
        repo.prepareWork(a9);
        clock.incrementAndGet();
        respond(page(row("same"), false));
        assertEquals(COMPLETE, pull(a9).state());
        assertTrue(repo.loadCached(a9, CommentStore.Handled.HANDLED).rows().isEmpty());
        assertEquals(9, view(a9).rows().get(0).observation.scope.connectionRevision);
        assertFalse(store.markHandled(q, "same"));
        assertTrue(store.markHandled(a9, "same"));
        var beforeA9 = view(a9);
        store.failCleanup = false;
        java.util.List<String> cancelled = new java.util.ArrayList<>();
        for (int retry = 0; retry < 2; retry++) {
            repo.afterInvalidation(q.channelId, 7, cancelled::add);
            assertEquals(new SimulatedCommentStore.Counts(0, 0, 0, 0, 0, 0), store.counts(q.channelId, 7));
            assertEquals(new SimulatedCommentStore.Counts(1, 1, 1, 1, 1, 1), store.counts(a9.channelId, 9));
            assertEquals(full, store.counts(b.channelId, 7));
            assertEquals(beforeA9, view(a9));
            assertEquals(beforeB, view(b));
            assertEquals(1, repo.loadCached(b, CommentStore.Handled.HANDLED).rows().size());
            assertEquals(1, repo.loadCached(a9, CommentStore.Handled.HANDLED).rows().size());
            assertEquals(OBSOLETE, resume(old).state());
        }
        assertEquals(Collections.singletonList(q.queryFingerprint), cancelled);
        assertEquals(4, server.getRequestCount());
    }

    private static void assertDisconnected(Result result) {
        assertEquals(FAILED, result.state());
        assertEquals(SocialError.Code.CHANNEL_DISCONNECTED, result.error().code);
        assertEquals(SocialError.NextAction.RECONNECT, result.error().nextAction);
    }

    @Test public void invalidationWithRunningHttpAndQueuedPullSeparatesAllCallers() throws Exception {
        CommentCache.WorkKey key = repo.prepareWork(q);
        blockFirst(page(row("late"), false), EMPTY_PAGE);
        CompletableFuture<Result> worker = new CompletableFuture<>(), pull = new CompletableFuture<>(),
                queued = new CompletableFuture<>(), detached = new CompletableFuture<>();
        repo.coordinator.resume(key, worker::complete);
        assertNull(position());
        repo.refresh(q, pull::complete);
        repo.refresh(q, detached::complete).cancel();
        repo.refresh(query(Query.FB_TOPLEVEL, q.target, q.targetId, 7), queued::complete);
        store.state(scope(query(q.query, q.target, q.targetId, 8)), ConnectionStatus.DISCONNECTED);
        repo.afterInvalidation(q.channelId, 7, fp -> assertEquals(q.queryFingerprint, fp));
        assertTrue(httpCancelled.await(2, TimeUnit.SECONDS));
        release.countDown();
        assertDisconnected(pull.get(3, TimeUnit.SECONDS));
        assertDisconnected(queued.get(3, TimeUnit.SECONDS));
        assertEquals(OBSOLETE, worker.get(3, TimeUnit.SECONDS).state());
        assertFalse(detached.isDone());
        assertEquals(1, server.getRequestCount());
        assertEquals(new SimulatedCommentStore.Counts(0, 0, 0, 0, 0, 0), store.counts(q.channelId, 7));
    }

    @Test public void invalidationBeforeHttpAfterBindingRejectsAdmittedWorker() throws Exception {
        CountDownLatch binding = new CountDownLatch(1), proceed = new CountDownLatch(1);
        repo = new CommentRepository(new CommentSyncCoordinator(cache, r -> {
            binding.countDown();
            try { assertTrue(proceed.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
            return adapter(r);
        }, clock::get, online::get, io, 1), 1000);
        var result = new CompletableFuture<Result>();
        repo.coordinator.resume(repo.prepareWork(q), result::complete);
        assertTrue(binding.await(5, TimeUnit.SECONDS));
        store.state(scope(query(q.query, q.target, q.targetId, 8)), ConnectionStatus.DISCONNECTED);
        proceed.countDown();
        assertEquals(OBSOLETE, result.get(5, TimeUnit.SECONDS).state());
        assertEquals(0, server.getRequestCount());
        assertEquals(0, store.checkpoints());
    }

    @Test public void invalidationBeforeDeliveredHttpRejectsLatePageAndAttemptUpdate() throws Exception {
        blockFirst(page(row("late"), false), EMPTY_PAGE);
        CompletableFuture<Result> result = new CompletableFuture<>();
        repo.refresh(q, result::complete);
        assertNull(position());
        var key = new CommentCheckpoint.Key(q);
        var started = store.transaction(tx -> tx.attempt(key));
        store.state(scope(query(q.query, q.target, q.targetId, 8)), ConnectionStatus.DISCONNECTED);
        release.countDown(); // Simulate death of the invalidation owner before any cancellation.
        assertDisconnected(result.get(5, TimeUnit.SECONDS));
        assertSame(started, store.transaction(tx -> tx.attempt(key)));
        assertEquals(new SimulatedCommentStore.Counts(0, 0, 0, 1, 0, 0), store.counts(q.channelId, 7));
        assertEquals(1, server.getRequestCount());
    }

    @Test public void nextPage503WithRetryAfterPreservesCursorAndHonorsDeadlineAcrossRetry() throws Exception {
        // Page 1 succeeds with cursor "fixture-position"
        respond(page(row("c1"), true));
        // Page 2 hits HTTP 503 with Retry-After: 120
        server.enqueue(new MockResponse().setResponseCode(503).setHeader("Retry-After", "120").setBody("{}"));

        Result waitResult = pull(q);
        assertEquals(WAIT, waitResult.state());
        assertEquals(SocialError.Code.RATE_LIMITED, waitResult.error().code);
        assertEquals(Long.valueOf(10_000 + 120_000), waitResult.retryAt()); // clock=10_000, Retry-After=120s

        // Checkpoint preserved at MORE with row c1 cached, no lastCompleteSyncAt yet
        CommentCheckpoint cp = writer.prepare(q).previous;
        assertNotNull(cp);
        assertEquals(CommentCheckpoint.State.MORE, cp.state);
        assertNull(cp.lastCompleteSyncAt);
        assertEquals(1, view(q).rows().size());
        assertEquals("c1", view(q).rows().get(0).observation.commentId);

        // Before deadline: pulls return WAIT without issuing additional HTTP
        repo = repository(3);
        clock.set(129_999);
        assertEquals(WAIT, pull(q).state());
        assertEquals(2, server.getRequestCount());

        // At deadline: advance clock, enqueue page 2 success with dedup c1 + new c2
        clock.set(130_000);
        respond(page(row("c1") + "," + row("c2"), false));

        assertEquals(COMPLETE, pull(q).state());
        // Verify cursors across all three requests
        assertNull(position());                       // request 1: first page, no after param
        assertEquals("fixture-position", position()); // request 2: 503 page, used cursor from page 1
        assertEquals("fixture-position", position()); // request 3: retry reuses same cursor from checkpoint

        // Verify dedup and final data
        var cached = view(q);
        assertEquals(CommentCheckpoint.State.COMPLETE, cached.paging());
        assertEquals(2, cached.rows().size());
        assertEquals(Long.valueOf(130_000), cached.lastCompleteSyncAt());
        assertNull(cached.error());
        assertEquals(3, server.getRequestCount());
    }

    @Test public void invalidatedRevisionCannotReadOrJoinFreshRevision() throws Exception {
        CommentCache.WorkKey old = repo.prepareWork(q);
        blockFirst(page(row("old"), false), page(row("new"), false));
        CompletableFuture<Result> oldResult = new CompletableFuture<>(), freshResult = new CompletableFuture<>();
        repo.refresh(q, oldResult::complete);
        assertNull(position());
        CommentPageRequest fresh = query(Query.FB_STREAM, Target.POST, "post", 9);
        store.state(scope(query(q.query, q.target, q.targetId, 8)), ConnectionStatus.DISCONNECTED);
        store.seed(scope(fresh), fresh.postId);
        repo.afterInvalidation(q.channelId, 7, fp -> assertEquals(q.queryFingerprint, fp));
        repo.refresh(fresh, freshResult::complete);
        release.countDown();
        assertEquals(SocialError.Code.CHANNEL_DISCONNECTED, oldResult.get(3, TimeUnit.SECONDS).error().code);
        assertEquals(COMPLETE, freshResult.get(3, TimeUnit.SECONDS).state());
        assertEquals(OBSOLETE, resume(old).state());
        assertEquals(ERROR, view(q).state());
        assertEquals(SocialError.Code.CHANNEL_DISCONNECTED, view(q).error().code);
        assertDisconnected(pull(q));
        assertEquals("new", view(fresh).rows().get(0).observation.commentId);
        assertEquals(2, server.getRequestCount());
    }
}
