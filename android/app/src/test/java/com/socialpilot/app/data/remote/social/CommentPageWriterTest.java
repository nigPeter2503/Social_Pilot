package com.socialpilot.app.data.remote.social;

import com.google.gson.*;
import com.socialpilot.app.data.local.comment.*;
import com.socialpilot.app.data.local.comment.CommentPageWriter.*;
import com.socialpilot.app.data.local.publishedpost.*;
import com.socialpilot.app.domain.contract.social.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.*;
import org.junit.Test;
import static com.socialpilot.app.data.remote.social.CommentPageRequest.*;
import static com.socialpilot.app.data.remote.social.CommentRecord.*;
import static org.junit.Assert.*;

/** MO_PHONG. Tests use the actual SP-041.01 rows and SP-041.02 records/codec. */
public class CommentPageWriterTest {
    private final SimulatedCommentStore store = new SimulatedCommentStore();
    private final CommentCursorCodec codec = new CommentCursorCodec(new SecretKeySpec(new byte[32], "AES"));
    private final CommentPageWriter writer = CommentPageWriter.forSimulation(store, codec);
    private final CommentPageRequest a = fb("a", "000Post", Query.FB_STREAM);
    private final ChannelCapability reply = new ChannelCapability(SocialOperation.REPLY_COMMENT,
            CapabilityAvailability.UNVERIFIED, "fixture", null);

    private CommentPageRequest fb(String channel, String post, Query query) {
        return new CommentPageRequest(Account.FACEBOOK_PAGE, channel, 7, "subject-" + channel, post,
                Target.POST, post, null, query, 2, 1L, "v27.0", null);
    }

    private PublishedPostScope scope(CommentPageRequest q, long revision) {
        AccountType type = q.account == Account.FACEBOOK_PAGE ? AccountType.PAGE
                : q.account == Account.LINKEDIN_MEMBER ? AccountType.PERSON : AccountType.ORGANIZATION;
        return new PublishedPostScope(q.platform, type, q.channelId, revision, q.externalAccountId);
    }

    private void seed(CommentPageRequest q) { store.seed(scope(q, q.connectionRevision), q.postId); }

    private CommentRecord item(CommentPageRequest q, String id, String message, Long updated, long at) {
        return new CommentRecord(q, id, ParentState.UNKNOWN, null,
                message == null ? ContentState.OMITTED : ContentState.AVAILABLE,
                message, null, null, updated, at, Source.MO_PHONG, "fixture", reply);
    }

    private CommentPage page(CommentPageRequest q, long at, Boolean more, CommentRecord... items) {
        String cursor = Boolean.TRUE.equals(more) ? codec.encode(q, "2") : null;
        return new CommentPage(q, Arrays.asList(items), cursor, more,
                more == null ? "NOT_ESTABLISHED" : null, at, Source.MO_PHONG, "fixture");
    }

    private Commit commitFixture(Attempt attempt, CommentPage page) {
        return writer.commit(attempt, new CommentPageReceipt(attempt.request, CommentPageResult.success(page)));
    }

    private Commit write(CommentPageRequest q, long at, CommentRecord... items) {
        return commitFixture(writer.prepare(q), page(q, at, false, items));
    }

    private StoredComment row(CommentPageRequest q, String id) { return store.row(q.platform, q.channelId, id); }

    private void reject(Failure reason, Runnable work) {
        assertEquals(reason, assertThrows(Rejected.class, work::run).reason);
    }

    @Test public void replayAndConcurrentSiblingCannotRewindCheckpoint() {
        seed(a);
        Attempt first = writer.prepare(a), sibling = writer.prepare(a);
        CommentPage input = page(a, 10, true, item(a, "001", "a", null, 10));
        CommentCheckpoint cp = commitFixture(first, input).checkpoint;
        reject(Failure.SUPERSEDED, () -> commitFixture(sibling, input));
        reject(Failure.SUPERSEDED, () -> commitFixture(first, input));
        assertSame(cp, writer.prepare(a).previous);
        assertEquals(1, store.size());
        write(a, 20, item(a, "001", "a", null, 20));
        commitFixture(writer.prepare(a), input);
        assertEquals(1, store.size());
        assertEquals(20, row(a, "001").observation.syncedAt);
    }

    @Test public void providerOrderThenReceiptOrderKeepsSelectedTimestamp() {
        seed(a);
        write(a, 10, item(a, "id", "A", 10L, 10));
        write(a, 14, item(a, "id", "stale", 8L, 14));
        assertEquals("A", row(a, "id").message);
        write(a, 15, item(a, "id", "B", null, 15));
        write(a, 14, item(a, "id", "C", 13L, 14));
        assertEquals("B", row(a, "id").message);
        assertNull(row(a, "id").observation.providerUpdatedAt);
        assertEquals(15, row(a, "id").observation.syncedAt);
    }

    @Test public void olderResponseCanFillMissingMetadataWithoutRetimestampingContent() {
        seed(a);
        write(a, 20, item(a, "id", "new", 14L, 20));
        CommentRecord old = new CommentRecord(a, "id", ParentState.UNKNOWN, null, ContentState.AVAILABLE,
                "old", "author", 2L, 5L, 12, Source.MO_PHONG, "older", reply);
        write(a, 12, old);
        StoredComment merged = row(a, "id");
        assertEquals("new", merged.message);
        assertEquals("author", merged.authorDisplayName);
        assertEquals(Long.valueOf(2), merged.createdAt);
        assertNull(merged.timeMissingReason);
        assertEquals(Long.valueOf(14), merged.observation.providerUpdatedAt);
        assertEquals(20, merged.observation.syncedAt);
    }

    @Test public void observedHistoryBoundsSurviveUnknownPageAndAreScopedByHistoryPolicy() {
        seed(a);
        CommentRecord early = new CommentRecord(a, "early", ParentState.ROOT, null, ContentState.AVAILABLE,
                "", null, 2L, null, 10, Source.MO_PHONG, "fixture", reply);
        CommentRecord late = new CommentRecord(a, "late", ParentState.ROOT, null, ContentState.AVAILABLE,
                "", null, 9L, null, 12, Source.MO_PHONG, "fixture", reply);
        commitFixture(writer.prepare(a), page(a, 10, true, early));
        CommentCheckpoint unknown = commitFixture(writer.prepare(a), page(a, 12, null, late)).checkpoint;
        assertEquals(Long.valueOf(1), unknown.historyFrom);
        assertEquals(Long.valueOf(2), unknown.oldestObservedAt);
        assertEquals(Long.valueOf(9), unknown.newestObservedAt);
        assertNull(unknown.lastCompleteSyncAt);
        CommentPageRequest changed = new CommentPageRequest(a.account, a.channelId, a.connectionRevision,
                a.externalAccountId, a.postId, a.target, a.targetId, null, a.query, a.pageSize, 2L, a.apiVersion, null);
        assertNull(writer.prepare(changed).previous);
    }

    @Test public void equalTimestampConflictSurvivesReplayAndSignalsRefresh() {
        seed(a);
        write(a, 10, item(a, "id", "known", 5L, 10));
        assertTrue(write(a, 12, item(a, "id", "different", 5L, 12)).needsRefresh);
        assertTrue(row(a, "id").needsRefresh);
        assertEquals("known", row(a, "id").message);
        assertEquals(10, row(a, "id").observation.syncedAt);
        assertTrue(write(a, 10, item(a, "id", "known", 5L, 10)).needsRefresh);
        assertFalse(write(a, 16, item(a, "id", "corrected", 6L, 16)).needsRefresh);
    }

    @Test public void missingTimeEmptyOmissionDeletionAndUnresolvedParent() {
        seed(a);
        CommentRecord known = new CommentRecord(a, "001A", ParentState.KNOWN, "not-loaded", ContentState.AVAILABLE,
                "", "name", 5L, 7L, 10, Source.MO_PHONG, "fixture", reply);
        write(a, 10, known, item(a, "001a", null, null, 10));
        assertNull(row(a, "not-loaded"));
        assertEquals(2, store.size());
        assertEquals(TimeMissingReason.PROVIDER_OMITTED, row(a, "001a").timeMissingReason);
        assertEquals(ParentState.UNKNOWN, row(a, "001a").parentState);
        write(a, 20, item(a, "001A", null, null, 20));
        StoredComment sparse = row(a, "001A");
        assertEquals(ContentState.OMITTED, sparse.observation.contentState);
        assertEquals("", sparse.message);
        assertEquals("not-loaded", sparse.parentCommentId);
        assertEquals(Long.valueOf(5), sparse.createdAt);
        assertNull(sparse.timeMissingReason);
        assertEquals("name", sparse.authorDisplayName);
        CommentRecord deleted = new CommentRecord(a, "001A", ParentState.ROOT, null, ContentState.DELETED,
                null, null, null, null, 30, Source.MO_PHONG, "confirmed-delete", reply);
        write(a, 30, deleted);
        assertEquals(ContentState.DELETED, row(a, "001A").contentState);
        assertNull(row(a, "001A").message);
        assertEquals(ParentState.ROOT, row(a, "001A").parentState);
        write(a, 40, item(a, "001A", null, null, 40));
        assertEquals(ContentState.DELETED, row(a, "001A").contentState);
    }

    @Test public void unknownPageDoesNotCompleteOrErasePreviousCompletion() {
        seed(a);
        Commit initial = commitFixture(writer.prepare(a), page(a, 10, null));
        assertEquals(CommentCheckpoint.State.UNKNOWN, initial.checkpoint.state);
        assertNull(initial.checkpoint.lastCompleteSyncAt);
        assertEquals("NOT_ESTABLISHED", initial.checkpoint.paginationReason);
        write(a, 20);
        Commit pending = commitFixture(writer.prepare(a), page(a, 30, true));
        assertEquals(Long.valueOf(20), pending.checkpoint.lastCompleteSyncAt);
        Commit unknown = commitFixture(writer.prepare(a), page(a, 40, null));
        assertEquals(Long.valueOf(20), unknown.checkpoint.lastCompleteSyncAt);
        assertEquals(40, unknown.checkpoint.lastPageAt);
    }

    @Test public void sameKeyDifferentPostRollsBackWholePage() {
        CommentPageRequest other = fb("a", "other", Query.FB_STREAM);
        seed(a); seed(other);
        write(a, 10, item(a, "shared", "A", null, 10));
        reject(Failure.POST_CONFLICT, () -> write(other, 11,
                item(other, "new", "B", null, 11), item(other, "shared", "B", null, 11)));
        assertNull(writer.prepare(other).previous);
        assertNull(row(other, "new"));
        assertEquals("A", row(a, "shared").message);
    }

    @Test public void crossChannelKeysAndQueriesAreIndependent() {
        CommentPageRequest b = fb("b", a.postId, Query.FB_STREAM);
        CommentPageRequest top = fb("a", a.postId, Query.FB_TOPLEVEL);
        seed(a); seed(b);
        write(a, 10, item(a, "same", "A", null, 10));
        write(b, 10, item(b, "same", "B", null, 10));
        assertEquals("A", row(a, "same").message);
        assertEquals("B", row(b, "same").message);
        assertNull(writer.prepare(top).previous);
        reject(Failure.INVALID_PAGE, () -> commitFixture(writer.prepare(top), page(a, 20, true)));
        reject(Failure.INVALID_PAGE, () -> write(a, 20, item(b, "other", "B", null, 20)));
    }

    @Test public void conflictingUndatedViewsCannotUseReceiptOrderAcrossQueries() {
        CommentPageRequest top = fb("a", a.postId, Query.FB_TOPLEVEL);
        seed(a);
        write(a, 10, item(a, "id", "initial", null, 10));
        assertTrue(write(top, 20, item(top, "id", "different", null, 20)).needsRefresh);
        assertEquals("initial", row(a, "id").message);
        assertEquals(a.queryFingerprint, row(a, "id").observationQuery);
        assertEquals(2, store.checkpoints());
    }

    @Test public void receiptMustBelongToThisExactAttemptEvenForSameQuery() {
        seed(a);
        Attempt first = writer.prepare(a), other = writer.prepare(a);
        CommentPageReceipt receipt = new CommentPageReceipt(first.request, CommentPageResult.success(page(a, 10, false)));
        reject(Failure.INVALID_PAGE, () -> writer.commit(other, receipt));
        assertNull(writer.prepare(a).previous);
        writer.commit(first, receipt);
    }

    @Test public void guardedLookupChecksIdentityConnectionRevisionPostAndSource() {
        store.state(scope(a, 7), ConnectionStatus.CONNECTED);
        reject(Failure.MISSING_POST, () -> writer.prepare(a));
        seed(a);
        Attempt pending = writer.prepare(a);
        store.state(new PublishedPostScope(a.platform, AccountType.PAGE, "a", 7, "replaced"), ConnectionStatus.CONNECTED);
        reject(Failure.SCOPE, () -> commitFixture(pending, page(a, 10, false)));
        store.state(scope(a, 7), ConnectionStatus.REAUTH_REQUIRED);
        reject(Failure.AUTH_REQUIRED, () -> writer.prepare(a));
        store.state(scope(a, 8), ConnectionStatus.CONNECTED);
        reject(Failure.SCOPE, () -> commitFixture(pending, page(a, 10, false)));
        store.state(scope(a, 7), ConnectionStatus.CONNECTED);
        reject(Failure.SOURCE, () -> CommentPageWriter.forProduction(store, codec).prepare(a));
        CommentPage wrong = new CommentPage(a, Collections.emptyList(), null, false, null, 10, Source.API_THAT, "fixture");
        reject(Failure.SOURCE, () -> commitFixture(pending, wrong));
        assertEquals(0, store.checkpoints());
    }

    @Test public void invalidationBeforeCommitAndReconnectRejectOldCallback() {
        seed(a);
        Attempt old = writer.prepare(a);
        store.invalidate(scope(a, 8));
        reject(Failure.DISCONNECTED, () -> commitFixture(old, page(a, 20, false)));
        store.state(scope(a, 9), ConnectionStatus.CONNECTED);
        reject(Failure.SCOPE, () -> commitFixture(old, page(a, 20, false)));
        assertEquals(0, store.size());
        assertEquals(0, store.checkpoints());
    }

    @Test public void commitBeforeInvalidationRemovesOnlyAffectedScope() {
        CommentPageRequest b = fb("b", a.postId, Query.FB_STREAM);
        seed(a); seed(b);
        write(a, 10, item(a, "id", "A", null, 10));
        write(b, 10, item(b, "id", "B", null, 10));
        Attempt old = writer.prepare(a);
        store.invalidate(scope(a, 8));
        assertNull(row(a, "id"));
        assertEquals("B", row(b, "id").message);
        assertEquals(1, store.checkpoints());
        reject(Failure.DISCONNECTED, () -> commitFixture(old, page(a, 30, false)));
    }

    @Test public void finalGuardRollsBackEvenAfterCheckpointWrite() {
        seed(a);
        store.afterCheckpoint = () -> store.state(scope(a, 8), ConnectionStatus.CONNECTED);
        reject(Failure.SCOPE, () -> write(a, 20, item(a, "late", "", null, 20)));
        assertEquals(0, store.size());
        assertEquals(0, store.checkpoints());
    }

    @Test public void checkpointAndCommitFaultRestoreRowsCursorRangeAndCompletion() {
        seed(a);
        write(a, 9);
        CommentCheckpoint cp = commitFixture(writer.prepare(a), page(a, 10, true,
                item(a, "id", "old", null, 10))).checkpoint;
        Attempt next = writer.prepare(a);
        CommentRecord fresh = new CommentRecord(a, "new", ParentState.ROOT, null, ContentState.AVAILABLE,
                "", null, 12L, null, 20, Source.MO_PHONG, "fixture", reply);
        CommentPage update = page(a, 20, false, item(a, "id", "new", null, 20), fresh);
        for (int fault = 0; fault < 2; fault++) {
            store.failCheckpoint = fault == 0;
            store.failCommit = fault == 1;
            reject(Failure.STORAGE, () -> commitFixture(next, update));
            store.failCheckpoint = false;
            store.failCommit = false;
            assertSame(cp, writer.prepare(a).previous);
            assertNull(row(a, "new"));
            assertEquals("old", row(a, "id").message);
            assertNull(cp.oldestObservedAt);
            assertEquals(Long.valueOf(9), cp.lastCompleteSyncAt);
            assertEquals("2", codec.decode(writer.prepare(a).request));
        }
        CommentCheckpoint done = commitFixture(next, update).checkpoint;
        assertEquals(Long.valueOf(12), done.oldestObservedAt);
        assertEquals(Long.valueOf(12), done.newestObservedAt);
        assertEquals(Long.valueOf(20), done.lastCompleteSyncAt);
    }

    @Test public void cursorBoundaryRejectsRawForeignAndTamperedEnvelopes() {
        seed(a);
        Attempt attempt = writer.prepare(a);
        String badKey = codec.encode(fb("a", a.postId, Query.FB_TOPLEVEL), "2");
        for (String bad : Arrays.asList("https://fixture.invalid/?access_token=synthetic",
                badKey, "sp041v1.invalid", codec.encode(a, "Bearer synthetic"))) {
            CommentPage page = new CommentPage(a, Collections.emptyList(), bad, true, null, 20, Source.MO_PHONG, "fixture");
            reject(Failure.INVALID_CURSOR, () -> commitFixture(attempt, page));
        }
        reject(Failure.INVALID_CURSOR, () -> writer.prepare(a.resume("raw")));
        assertEquals(0, store.checkpoints());
    }

    private CommentPageReceipt fetch(CommentAdapter adapter, Attempt attempt) throws Exception {
        CompletableFuture<CommentPageReceipt> future = new CompletableFuture<>();
        CommentPageReceipt.read(adapter, attempt.request, future::complete);
        return future.get(5, TimeUnit.SECONDS);
    }

    private void networkChain(Account account) throws Exception {
        boolean facebook = account == Account.FACEBOOK_PAGE;
        String post = "urn:li:share:9000000000000000001";
        CommentPageRequest q = facebook ? a : new CommentPageRequest(account, "li", 7, "fixture-subject",
                post, Target.POST, post, "urn:li:activity:9000000000000000002", Query.LI_COMMENTS, 2, 1L, "202610", null);
        seed(q);
        AtomicLong clock = new AtomicLong(100);
        ChannelInfo channel = new ChannelInfo(q.channelId, q.platform, q.externalAccountId, scope(q, 7).accountType,
                "fixture", ConnectionStatus.CONNECTED, 7, "fixture-ref");
        ChannelCapability read = new ChannelCapability(SocialOperation.READ_COMMENTS,
                CapabilityAvailability.AVAILABLE, null, "2026-11-19T00:00:00Z");
        CommentAdapter.Binding binding = new CommentAdapter.Binding(channel, q, read, reply, "fixture",
                "fixture-ownership", 2, null);
        OkHttpClient client = new OkHttpClient();
        String resource = facebook ? "facebook-sp018.json" : "linkedin-sp019.json";
        try (MockWebServer server = new MockWebServer(); InputStreamReader input = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/comments/" + resource)), StandardCharsets.UTF_8)) {
            server.start();
            JsonArray pages = JsonParser.parseReader(input).getAsJsonObject().getAsJsonArray("pages");
            java.util.function.Function<CommentCursorCodec, CommentAdapter> adapterWithKey = key -> facebook
                    ? new FacebookCommentAdapter(client, ignored -> binding, ignored -> "fixture-token",
                            clock::getAndIncrement, null, key, server.url("/"), Source.MO_PHONG, "fixture")
                    : new LinkedInCommentAdapter(client, ignored -> binding, ignored -> "fixture-token",
                            clock::getAndIncrement, null, key, server.url("/"), Source.MO_PHONG, "fixture");
            CommentAdapter adapter = adapterWithKey.apply(codec);
            server.enqueue(new MockResponse().setBody(pages.get(0).toString()));
            Attempt first = writer.prepare(q);
            CommentPageReceipt firstReceipt = fetch(adapter, first);
            CommentPage p1 = firstReceipt.result.page;
            CommentCheckpoint cp = writer.commit(first, firstReceipt).checkpoint;
            assertEquals(2, store.size());
            assertEquals(CommentCheckpoint.State.MORE, cp.state);
            assertNull(cp.lastCompleteSyncAt);
            assertNotNull(cp.oldestObservedAt);
            assertEquals(cp.oldestObservedAt, cp.newestObservedAt);
            Attempt second = writer.prepare(q);
            server.enqueue(new MockResponse().setResponseCode(429).setBody("{}"));
            CommentPageReceipt errorReceipt = fetch(adapter, second);
            CommentPageResult throttled = errorReceipt.result;
            reject(Failure.INVALID_PAGE, () -> writer.commit(second, errorReceipt));
            assertNull(throttled.page);
            assertEquals(SocialError.Code.RATE_LIMITED, throttled.error.code);
            assertSame(cp, writer.prepare(q).previous);
            assertEquals(2, store.size());

            // Inject a confirmed unusable continuation (lost test key); no live provider expiry heuristic.
            CommentCursorCodec rotated = new CommentCursorCodec(new SecretKeySpec(new byte[16], "AES"));
            CommentPageReceipt unusable = fetch(adapterWithKey.apply(rotated), second);
            assertEquals(CommentPageResult.Failure.INVALID_CURSOR, unusable.result.failure);
            assertEquals(2, server.getRequestCount());
            Attempt restart = writer.restartExpired(second);
            assertEquals(q.queryFingerprint, restart.request.queryFingerprint);
            assertNull(restart.request.cursor);
            assertEquals(CommentCheckpoint.State.RESTART_REQUIRED, restart.previous.state);
            assertEquals(cp.oldestObservedAt, restart.previous.oldestObservedAt);
            assertEquals(cp.lastPageAt, restart.previous.lastPageAt);
            reject(Failure.SUPERSEDED, () -> commitFixture(second, page(q, 150, false)));
            server.enqueue(new MockResponse().setBody(pages.get(0).toString()));
            reject(Failure.INVALID_PAGE, () -> writer.commit(restart, firstReceipt));
            writer.commit(restart, fetch(adapter, restart));
            assertEquals(2, store.size());
            Attempt resumed = writer.prepare(q);
            server.enqueue(new MockResponse().setBody(pages.get(1).toString()));
            CommentPageReceipt lastReceipt = fetch(adapter, resumed);
            CommentPage last = lastReceipt.result.page;
            CommentCheckpoint done = writer.commit(resumed, lastReceipt).checkpoint;
            assertEquals(3, store.size());
            assertEquals(CommentCheckpoint.State.COMPLETE, done.state);
            assertEquals(Long.valueOf(last.fetchedAt), done.lastCompleteSyncAt);
            assertNull(writer.prepare(q).request.cursor);
            String shared = p1.items.get(0).commentId;
            assertEquals(last.items.get(0).commentId, shared);
            assertEquals(last.items.get(0).message, row(q, shared).message);
            assertEquals(p1.items.get(0).createdAt, row(q, shared).createdAt);
            if (!facebook) assertEquals("urn:li:comment:(urn:li:activity:9000000000000000002,9000000000000000101)", shared);
            String param = facebook ? "after" : "start";
            String start = facebook ? null : "0";
            String position = facebook ? "synthetic-cursor-1" : "2";
            for (String expected : Arrays.asList(start, position, start, position)) {
                assertEquals(expected, server.takeRequest(1, TimeUnit.SECONDS).getRequestUrl().queryParameter(param));
            }
        } finally {
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
        }
    }

    @Test public void facebookFixtureRestartsDedupsAndCompletes() throws Exception { networkChain(Account.FACEBOOK_PAGE); }
    @Test public void linkedInMemberFixtureRestartsDedupsAndCompletes() throws Exception { networkChain(Account.LINKEDIN_MEMBER); }
    @Test public void linkedInOrgFixtureRestartsDedupsAndCompletes() throws Exception { networkChain(Account.LINKEDIN_ORGANIZATION); }
}
