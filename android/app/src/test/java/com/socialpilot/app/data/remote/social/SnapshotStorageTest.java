package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.data.local.snapshot.*;
import com.socialpilot.app.data.remote.social.FollowerResult.Source;
import com.socialpilot.app.domain.contract.social.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** MO_PHONG normalized adapter callbacks. All identities, epochs, period evidence and roots are fixtures. */
public class SnapshotStorageTest {
    private static final String COLLECTION = "MO_PHONG-collection";
    private static final String POST = "urn:li:share:0007";
    private static final long START = 1795060800000L;
    private final SimulatedSnapshotDao dao = new SimulatedSnapshotDao();
    private final SnapshotWriter writer = SnapshotWriter.simulation(dao);
    private final Snapshot.Scope a = scope("c-A", 7);
    private final Snapshot.Scope b = scope("c-B", 1);

    private Snapshot.Scope scope(String channel, long revision) {
        return new Snapshot.Scope(Platform.LINKEDIN, FollowerRequest.Account.LINKEDIN_MEMBER,
                channel, "urn:li:person:" + channel, revision);
    }
    private void bind(Snapshot.Scope scope, ConnectionStatus status) {
        dao.bind(new ChannelInfo(scope.channelId(), scope.platform(), scope.externalAccountId(), AccountType.PERSON,
                "MO_PHONG", status, scope.connectionRevision(), status == ConnectionStatus.DISCONNECTED ? null : "mock-ref"));
    }
    @Before public void roots() {
        bind(a, ConnectionStatus.CONNECTED);
        bind(b, ConnectionStatus.CONNECTED);
        dao.own(a, POST, "local-001");
    }

    private FollowerRequest followerRequest(Snapshot.Scope scope, String collection) {
        return new FollowerRequest(scope.account(), scope.channelId(), scope.connectionRevision(),
                scope.externalAccountId(), "202610", collection);
    }
    private SnapshotReading follower(Snapshot.Scope scope, String collection, Long value, boolean failed, long at) {
        FollowerRequest request = followerRequest(scope, collection);
        FollowerResult.MetricDefinition d = new FollowerResult.MetricDefinition(request);
        FollowerResult.MissingReason reason = failed ? FollowerResult.MissingReason.RATE_LIMITED
                : value == null ? FollowerResult.MissingReason.PROVIDER_OMITTED : null;
        FollowerResult.MetricSample sample = new FollowerResult.MetricSample(request, d, value, reason,
                at, Source.MO_PHONG, "MO_PHONG-follower");
        return SnapshotReading.from(request, new FollowerResult(failed ? FollowerResult.Status.FAILURE : FollowerResult.Status.SUCCESS,
                d, sample, failed ? FollowerResult.Failure.PROVIDER_ERROR : null,
                failed ? new SocialError(SocialError.Code.RATE_LIMITED, SocialError.NextAction.WAIT) : null,
                failed ? at + 1000 : null));
    }

    private SnapshotReading post(Snapshot.Scope scope, PostRequest.Query query, PostRequest.Period period, Long value, long at) {
        PostRequest request = new PostRequest(scope.account(), scope.channelId(), scope.connectionRevision(),
                scope.externalAccountId(), POST, "202610", COLLECTION, query, period);
        List<PostResult.MetricDefinition> definitions = PostMetricMapping.definitions(request);
        PostResult.MetricSample sample = new PostResult.MetricSample(request, definitions.get(0), value,
                value == null ? PostResult.MissingReason.PROVIDER_OMITTED : null, at, Source.MO_PHONG, "MO_PHONG-post",
                period != null && value != null);
        return SnapshotReading.from(request, new PostResult(value == null ? PostResult.Status.PARTIAL : PostResult.Status.SUCCESS,
                definitions, Collections.singletonList(sample), null, null, null));
    }
    private Snapshot save(String attempt, long end, SnapshotReading... readings) {
        return writer.write(attempt, START, end, Arrays.asList(readings));
    }
    private SnapshotDao.History history(Snapshot.Scope scope) {
        return dao.read(scope, Source.MO_PHONG, START, Long.MAX_VALUE);
    }

    @Test public void replayAndRetryKeepSnapshotIdAndOriginalGoodSampleAfterWriterRestart() {
        SnapshotReading first = follower(a, COLLECTION, 0L, false, START + 1);
        Snapshot saved = save("run-1", START + 2, first);
        SnapshotWriter restarted = SnapshotWriter.simulation(dao);
        assertEquals(saved, restarted.write("run-1", START, START + 2, Collections.singletonList(first)));
        assertEquals(1, history(a).attempts().size());
        Snapshot retried = save("run-2", START + 22, follower(a, COLLECTION, 99L, false, START + 20));
        assertEquals(saved.snapshotId(), retried.snapshotId());
        assertEquals(1, history(a).snapshots().size());
        assertEquals(2, history(a).attempts().size());
        MetricSample s = retried.samples().get(0);
        assertEquals(Long.valueOf(0), s.value());
        assertNull(s.missingReason());
        assertEquals(START + 1, s.observedAt());
        assertEquals("COLLECTION", s.observedAtBasis());
        assertEquals(START + 1, s.collectedAt());
        assertEquals(START + 2, retried.collectedAt());
        assertEquals(Source.MO_PHONG, s.source());
        assertEquals("MO_PHONG-follower", s.sourceRef());
        assertEquals("202610", s.providerApiVersion());
        assertEquals("memberFollowersCount", s.definition().providerMetricName());
        assertEquals("follower-LINKEDIN_MEMBER-202610-v1", s.definition().definitionId());
        assertEquals("GAUGE", s.definition().measurementKind());
        assertEquals(a, retried.scope());
        assertEquals(a.channelId(), retried.key().subjectId());
    }

    @Test public void sameNativePostAndCollectionInTwoChannelsResolveIndependentLocalRoots() {
        SnapshotReading inA = post(a, PostRequest.Query.MEMBER_COMMENT, null, 2L, START + 1);
        SnapshotReading inB = post(b, PostRequest.Query.MEMBER_COMMENT, null, 8L, START + 1);
        Snapshot savedA = save("same-run", START + 2, inA);
        assertThrows(IllegalStateException.class, () -> save("same-run", START + 2, inB));
        assertTrue(history(b).attempts().isEmpty());
        dao.own(b, POST, "local-002");
        Snapshot savedB = save("same-run", START + 2, inB);
        assertEquals("local-001", savedA.key().subjectId());
        assertEquals("local-002", savedB.key().subjectId());
        assertNotEquals(savedA.snapshotId(), savedB.snapshotId());
        assertEquals(Collections.singletonList(savedA), history(a).snapshots());
        assertEquals(Collections.singletonList(savedB), history(b).snapshots());
    }

    @Test public void staleCallbackAndReplayAfterRevisionChangeAreRejectedInsideWrite() {
        SnapshotReading pending = follower(a, COLLECTION, 3L, false, START);
        save("first", START, pending);
        Snapshot.Scope next = scope(a.channelId(), 8);
        bind(next, ConnectionStatus.CONNECTED);
        assertThrows(IllegalStateException.class, () -> save("first", START, pending));
        assertThrows(IllegalStateException.class, () -> save("late", START, pending));
        assertThrows(IllegalStateException.class, () -> history(a));
        assertTrue(history(next).snapshots().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> save("collision", START, follower(next, COLLECTION, 4L, false, START)));
        assertThrows(IllegalStateException.class,
                () -> save("late-post", START, post(next, PostRequest.Query.MEMBER_COMMENT, null, 4L, START)));
    }

    @Test public void disconnectedChannelAndReplacedIdentityCannotCommit() {
        SnapshotReading pending = follower(a, COLLECTION, 3L, false, START);
        bind(a, ConnectionStatus.DISCONNECTED);
        assertThrows(IllegalStateException.class, () -> save("1", START, pending));
        Snapshot.Scope otherIdentity = new Snapshot.Scope(a.platform(), a.account(), a.channelId(),
                "urn:li:person:replacement", a.connectionRevision());
        bind(otherIdentity, ConnectionStatus.CONNECTED);
        assertThrows(IllegalStateException.class, () -> save("2", START, pending));
        assertTrue(history(otherIdentity).attempts().isEmpty());
    }

    @Test public void rollbackAfterWritingRegistryAndSnapshotThenRetry() {
        SnapshotReading one = post(a, PostRequest.Query.MEMBER_REACTION, null, 2L, START + 1);
        SnapshotReading two = post(a, PostRequest.Query.MEMBER_COMMENT, null, 3L, START + 2);
        dao.failAtAttempt = true;
        assertThrows(IllegalStateException.class, () -> save("1", START + 3, one, two));
        assertEquals(0, dao.definitionCount());
        assertTrue(history(a).snapshots().isEmpty());
        assertTrue(history(a).attempts().isEmpty());
        dao.failAtAttempt = false;
        Snapshot saved = save("1", START + 3, one, two);
        assertEquals(2, dao.definitionCount());
        assertEquals(2, saved.samples().size());
        assertEquals(START + 1, saved.samples().get(0).collectedAt());
        assertEquals(START + 2, saved.samples().get(1).collectedAt());
        assertEquals(1, history(a).attempts().size());
    }

    @Test public void failedRefreshStoresReasonAndActionWithoutAdvancingLastGood() {
        Snapshot good = save("1", START + 2, follower(a, COLLECTION, 15L, false, START + 1));
        assertNull(save("2", START + 4, follower(a, "manual", null, true, START + 3)));
        SnapshotDao.History h = history(a);
        assertEquals(Collections.singletonList(good), h.snapshots());
        CollectionAttempt failed = h.attempts().get(1);
        assertEquals(CollectionAttempt.Status.FAILURE, failed.status());
        assertEquals(START + 4, failed.completedAt());
        CollectionAttempt.Outcome o = failed.outcomes().get(0);
        assertEquals("RATE_LIMITED", o.samples().get(0).missingReason());
        assertNull(o.samples().get(0).value());
        assertEquals(SocialError.NextAction.WAIT, o.error().nextAction);
        assertEquals(Long.valueOf(START + 1003), o.retryAfterAt());
        SnapshotDao.History offline = dao.read(a, Source.MO_PHONG, START + 5, START + 6);
        assertTrue(offline.snapshots().isEmpty());
        assertTrue(offline.attempts().isEmpty());
        assertEquals(failed, offline.lastAttempt());
        assertEquals(good.snapshotId(), offline.lastGood().get(0).snapshotId());
        assertEquals(good.samples().get(0), offline.lastGood().get(0).sample());
        Snapshot recovered = save("3", START + 8, follower(a, "manual", 16L, false, START + 7));
        assertNotEquals(good.snapshotId(), recovered.snapshotId());
        assertEquals(2, history(a).snapshots().size());
        assertEquals(3, history(a).attempts().size());
    }

    @Test public void separatePeriodKeysAndMissingSampleCanBeFilledWithoutInventingBounds() {
        PostRequest.Period p1 = new PostRequest.Period("2026-11-01", "2026-11-08", "UTC", "MO_PHONG-p1");
        PostRequest.Period p2 = new PostRequest.Period("2026-11-08", "2026-11-15", "UTC", "MO_PHONG-p2");
        Snapshot partial = save("1", START + 2, post(a, PostRequest.Query.MEMBER_COMMENT, p1, null, START),
                post(a, PostRequest.Query.MEMBER_COMMENT, p2, 8L, START + 1));
        MetricSample missing = partial.samples().get(0);
        assertEquals("PROVIDER_OMITTED", missing.missingReason());
        assertNull(missing.providerPeriodStart());
        assertNull(missing.providerPeriodEnd());
        assertEquals(CollectionAttempt.Status.PARTIAL, history(a).attempts().get(0).status());
        Snapshot filled = save("2", START + 8, post(a, PostRequest.Query.MEMBER_COMMENT, p1, 5L, START + 7));
        assertEquals(partial.snapshotId(), filled.snapshotId());
        assertEquals(2, filled.samples().size());
        MetricSample measured = filled.samples().get(0);
        assertEquals(missing.definition(), measured.definition());
        assertEquals("PERIOD", measured.definition().measurementKind());
        assertEquals("VERIFIED_DATE_RANGE", measured.definition().measurementWindow());
        assertEquals(Long.valueOf(p1.start), measured.providerPeriodStart());
        assertEquals(Long.valueOf(p1.end), measured.providerPeriodEnd());
        assertEquals(p1.evidenceRef, measured.periodEvidenceRef());
        assertEquals(partial.samples().get(1), filled.samples().get(1));
        assertEquals(Collections.singletonList(filled), dao.read(a, Source.MO_PHONG, START + 1, START + 1).snapshots());
        assertTrue(dao.read(a, Source.MO_PHONG, START + 2, START + 6).snapshots().isEmpty());
    }

    @Test public void productionNamespaceRejectsFixtureAndInvalidBatchRollsBack() {
        SnapshotReading sample = follower(a, COLLECTION, 0L, false, START);
        assertThrows(IllegalArgumentException.class, () -> SnapshotWriter.production(dao).write("id", START, START,
                Collections.singletonList(sample)));
        assertThrows(IllegalArgumentException.class, () -> save("dup", START, sample, sample));
        assertThrows(IllegalArgumentException.class, () -> save("mixed", START, sample, follower(b, COLLECTION, 1L, false, START)));
        assertThrows(IllegalArgumentException.class, () -> save("time", START, follower(a, COLLECTION, 1L, false, START + 1)));
        assertTrue(history(a).attempts().isEmpty());
    }

    @Test public void rejectedAdapterResultCreatesNoHistory() {
        FollowerResult rejected = new FollowerResult(FollowerResult.Status.REJECTED, null, null,
                FollowerResult.Failure.SCOPE_MISMATCH, null, null);
        assertThrows(IllegalArgumentException.class, () -> SnapshotReading.from(followerRequest(a, COLLECTION), rejected));
        assertTrue(history(a).snapshots().isEmpty());
    }

    @Test public void existingOrganizationFixturePassesThroughActualPostParserAndStorage() throws Exception {
        String owner = "urn:li:organization:9000000000000000010";
        JsonObject body;
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/followers/linkedin-sp019.json"), StandardCharsets.UTF_8)) {
            body = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("organizationPostStatistics");
        }
        JsonObject wire = body.getAsJsonArray("elements").get(0).getAsJsonObject();
        String postId = wire.get("share").getAsString();
        PostRequest r = new PostRequest(FollowerRequest.Account.LINKEDIN_ORGANIZATION, "org-A", 4, owner,
                postId, "202610", COLLECTION, PostRequest.Query.ORGANIZATION_ORGANIC, null);
        List<PostResult.MetricDefinition> definitions = PostMetricMapping.definitions(r);
        PostMetricMapping.Parsed parsed = PostMetricMapping.parse(r, body, definitions, Collections.emptyMap(),
                START, Source.MO_PHONG, "MO_PHONG-sp019");
        assertFalse(parsed.invalid);
        assertFalse(parsed.rejected);
        SnapshotReading reading = SnapshotReading.from(r, new PostResult(PostResult.Status.PARTIAL, definitions,
                parsed.samples, null, null, null));
        dao.bind(new ChannelInfo(r.channelId, r.platform, owner, AccountType.ORGANIZATION, "MO_PHONG",
                ConnectionStatus.CONNECTED, r.connectionRevision, "mock-ref"));
        dao.own(reading.scope, postId, "local-org-post");
        Snapshot saved = save("1", START, reading);
        assertEquals(5, saved.samples().size());
        for (MetricSample s : saved.samples()) {
            assertEquals("PROVIDER_ROLLING_12_MONTHS_ORGANIC", s.definition().measurementKind());
            assertEquals("single-post;organic;noTimeIntervals", s.definition().providerFilter());
            assertEquals("MO_PHONG-sp019", s.sourceRef());
            if (s.definition().metricKey().equals("PROVIDER_LIKE_COUNT")) {
                assertTrue(s.definition().permitsNegative());
                assertEquals(Long.valueOf(wire.getAsJsonObject("totalShareStatistics").get("likeCount").getAsLong()), s.value());
            }
            if (s.definition().metricKey().equals("REACTION_COUNT")) {
                assertNull(s.value());
                assertEquals("UNVERIFIED", s.missingReason());
            }
        }
    }

    @Test public void concurrentReplayCommitsOneAttemptAndOneSnapshot() throws Exception {
        SnapshotReading reading = follower(a, COLLECTION, 2L, false, START);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Snapshot> write = () -> {
                start.await();
                return save("same", START, reading);
            };
            Future<Snapshot> left = pool.submit(write);
            Future<Snapshot> right = pool.submit(write);
            start.countDown();
            assertEquals(left.get(5, TimeUnit.SECONDS), right.get(5, TimeUnit.SECONDS));
            assertEquals(1, history(a).attempts().size());
            assertEquals(1, history(a).snapshots().size());
        } finally {
            pool.shutdownNow();
        }
    }
}
