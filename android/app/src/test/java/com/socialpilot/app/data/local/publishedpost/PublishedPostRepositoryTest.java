package com.socialpilot.app.data.local.publishedpost;

import static com.socialpilot.app.data.local.publishedpost.PublishedPostRepository.Action.*;
import static com.socialpilot.app.data.local.publishedpost.PublishedPostRepository.Status.*;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.socialpilot.app.domain.contract.social.AccountType;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.Platform;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

/** All data/races are MO_PHONG. No device, provider, SQLite or Room execution. */
public class PublishedPostRepositoryTest {
    private SimulatedPublishedPostStore store;
    private PublishedPostRepository repo;
    private PublishedPostScope a;
    private PublishedPostScope b;

    @Before public void setUp() {
        store = new SimulatedPublishedPostStore();
        repo = PublishedPostRepository.forSimulation(store);
        a = scope("a", 1, Platform.FACEBOOK, AccountType.PAGE, "000PageA");
        b = scope("b", 1, Platform.FACEBOOK, AccountType.PAGE, "000PageB");
        store.connect(a); store.connect(b);
    }

    private static PublishedPostScope scope(String channel, long revision, Platform platform,
            AccountType account, String external) {
        return new PublishedPostScope(platform, account, channel, revision, external);
    }

    private PublishedPostScope revised(PublishedPostScope s, long revision) {
        return scope(s.channelId, revision, s.platform, s.accountType, s.externalAccountId);
    }

    private PublishedPostInput input(PublishedPostScope scope, String post, String content,
            String url, Long published, Long updated, long received) {
        return new PublishedPostInput(scope, post,
                new PublishedPostInput.Ownership(scope, post, "mock-ownership"), content, url,
                published, updated, received, PublishedPostInput.Source.MO_PHONG, "mock-source");
    }

    private PublishedPostInput input(PublishedPostScope scope, String post) {
        return input(scope, post, null, null, null, null, 100);
    }

    private PublishedPost saved(PublishedPostInput input) {
        PublishedPostRepository.WriteResult result = repo.upsert(input);
        assertEquals(COMMITTED, result.status);
        return result.entries.get(0).post;
    }

    private void rejected(PublishedPostRepository.Status status, PublishedPostInput input) {
        PublishedPostRepository.WriteResult result = repo.upsert(input);
        assertEquals(status, result.status); assertTrue(result.entries.isEmpty());
    }

    @Test public void newPostDoesNotRequirePublishJob() {
        PublishedPostRepository.WriteResult result = repo.upsert(input(a, "p"));
        assertEquals(COMMITTED, result.status); assertEquals(INSERTED, result.entries.get(0).action);
        assertNotNull(result.entries.get(0).post.id); assertEquals(1, store.size());
    }

    @Test public void repeatedUpsertKeepsLocalId() {
        PublishedPost first = saved(input(a, "p"));
        PublishedPostRepository.WriteResult again = repo.upsert(input(a, "p"));
        assertEquals(UNCHANGED, again.entries.get(0).action);
        assertEquals(first.id, again.entries.get(0).post.id); assertEquals(1, store.size());
    }

    @Test public void concurrentSameKeyWritersKeepOneStableId() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<PublishedPostRepository.WriteResult> first = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS)); return repo.upsert(input(a, "p"));
            });
            Future<PublishedPostRepository.WriteResult> second = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS)); return repo.upsert(input(a, "p"));
            });
            start.countDown();
            PublishedPostRepository.WriteResult one = first.get(5, TimeUnit.SECONDS);
            PublishedPostRepository.WriteResult two = second.get(5, TimeUnit.SECONDS);
            assertEquals(COMMITTED, one.status); assertEquals(COMMITTED, two.status);
            assertEquals(one.entries.get(0).post.id, two.entries.get(0).post.id);
            assertEquals(1, store.size());
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test public void duplicateKeyWithinOneBatchUsesSameId() {
        PublishedPostRepository.WriteResult result = repo.upsertAll(Arrays.asList(input(a, "p"), input(a, "p")));
        assertEquals(COMMITTED, result.status); assertEquals(2, result.entries.size());
        assertEquals(result.entries.get(0).post.id, result.entries.get(1).post.id);
        assertEquals(UNCHANGED, result.entries.get(1).action); assertEquals(1, store.size());
    }

    @Test public void sameExternalIdInTwoChannelsIsIndependent() {
        PublishedPost first = saved(input(a, "p")); PublishedPost second = saved(input(b, "p"));
        assertNotEquals(first.id, second.id); assertEquals(2, store.size());
        assertEquals(first.id, repo.lookupByExternalId(a, "p").post.id);
        assertEquals(second.id, repo.lookupByExternalId(b, "p").post.id);
    }

    @Test public void caseAndLeadingZerosAreDistinctAndPreserved() {
        for (String key : Arrays.asList("AbC", "abc", "00001", "1", " 00001 "))
            assertEquals(key, saved(input(a, key)).externalPostId);
        assertEquals(5, store.size());
    }

    @Test public void longUrnIsNotTruncatedOrConvertedToNumber() {
        String urn = "urn:li:ugcPost:00090071992547409931234567890123456789012345678901234567890";
        assertEquals(urn, saved(input(a, urn)).externalPostId); // No account inference from URN.
    }

    @Test public void tupleKeyDoesNotUseAmbiguousDelimiters() {
        PublishedPostScope c = scope("a:b", 1, a.platform, a.accountType, "page"); store.connect(c);
        assertNotEquals(saved(input(a, "b:c")).id, saved(input(c, "c")).id);
    }

    @Test public void syntheticFixtureCoversAllThreeAccountTypes() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/published-post/mo-phong.json"), StandardCharsets.UTF_8)) {
            JsonObject fixture = new Gson().fromJson(reader, JsonObject.class);
            assertEquals("MO_PHONG", fixture.get("source").getAsString());
            for (JsonElement element : fixture.getAsJsonArray("cases")) {
                JsonObject row = element.getAsJsonObject();
                PublishedPostScope s = scope(row.get("channelId").getAsString(),
                        row.get("connectionRevision").getAsLong(),
                        Platform.valueOf(row.get("platform").getAsString()),
                        AccountType.valueOf(row.get("accountType").getAsString()),
                        row.get("externalAccountId").getAsString());
                store.connect(s);
                String external = row.get("externalPostId").getAsString();
                assertEquals(external, saved(input(s, external)).externalPostId);
                assertEquals(FOUND, repo.lookupByExternalId(s, external).status);
            }
        }
    }

    @Test public void wrongPlatformRejected() {
        rejected(SCOPE_MISMATCH, input(scope("a", 1, Platform.LINKEDIN, AccountType.PERSON, "000PageA"), "p"));
    }
    @Test public void wrongAccountTypeRejected() {
        PublishedPostScope member = scope("li", 1, Platform.LINKEDIN, AccountType.PERSON, "subject");
        store.connect(member);
        rejected(SCOPE_MISMATCH, input(scope("li", 1, Platform.LINKEDIN, AccountType.ORGANIZATION, "subject"), "p"));
    }
    @Test public void wrongExternalIdentityRejected() {
        rejected(SCOPE_MISMATCH, input(scope("a", 1, a.platform, a.accountType, "000pagea"), "p"));
    }
    @Test public void missingChannelIsExplicit() {
        rejected(CHANNEL_MISSING, input(scope("missing", 1, a.platform, a.accountType, "page"), "p"));
    }
    @Test public void wrongRevisionRejected() { rejected(SCOPE_MISMATCH, input(revised(a, 2), "p")); }
    @Test public void disconnectedRejected() {
        store.state(revised(a, 2), ConnectionStatus.DISCONNECTED); rejected(DISCONNECTED, input(a, "p"));
    }
    @Test public void reauthRequiredRejected() {
        store.state(a, ConnectionStatus.REAUTH_REQUIRED); rejected(AUTH_REQUIRED, input(a, "p"));
    }
    @Test public void disconnectBetweenRequestAndTransactionRejectsLateWrite() {
        store.beforeWork = () -> store.disconnectAndClean(revised(a, 2));
        rejected(DISCONNECTED, input(a, "p")); assertEquals(0, store.size());
    }
    @Test public void reconnectBetweenRequestAndTransactionRejectsOldRevision() {
        store.beforeWork = () -> store.connect(revised(a, 3));
        rejected(SCOPE_MISMATCH, input(a, "p")); assertEquals(0, store.size());
    }
    @Test public void accountSwapBetweenRequestAndTransactionRejected() {
        store.beforeWork = () -> store.connect(scope("a", 2, a.platform, a.accountType, "different"));
        rejected(SCOPE_MISMATCH, input(a, "p"));
    }
    @Test public void scopeRecheckedAfterWriteRollsBackBatch() {
        store.afterWrite = () -> store.state(a, ConnectionStatus.DISCONNECTED);
        rejected(DISCONNECTED, input(a, "p")); assertEquals(0, store.size());
    }
    @Test public void reconnectAfterWriteRollsBackBatch() {
        store.afterWrite = () -> store.connect(revised(a, 2));
        rejected(SCOPE_MISMATCH, input(a, "p")); assertEquals(0, store.size());
    }
    @Test public void disconnectSerializesWithCommitAndCleansOnlyItsChannel() throws Exception {
        saved(input(b, "other"));
        store.writeEntered = new CountDownLatch(1); store.allowWrite = new CountDownLatch(1);
        CountDownLatch disconnectStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PublishedPostRepository.WriteResult> write = executor.submit(() -> repo.upsert(input(a, "p")));
            assertTrue(store.writeEntered.await(5, TimeUnit.SECONDS));
            Future<?> disconnect = executor.submit(() -> {
                disconnectStarted.countDown(); store.disconnectAndClean(revised(a, 2));
            });
            assertTrue(disconnectStarted.await(5, TimeUnit.SECONDS));
            assertFalse(disconnect.isDone()); store.allowWrite.countDown();
            assertEquals(COMMITTED, write.get(5, TimeUnit.SECONDS).status);
            disconnect.get(5, TimeUnit.SECONDS);
            assertEquals(DISCONNECTED, repo.lookupByExternalId(a, "p").status);
            assertEquals(FOUND, repo.lookupByExternalId(b, "other").status);
            assertEquals(1, store.size());
            store.connect(revised(a, 3)); rejected(SCOPE_MISMATCH, input(a, "p"));
            assertEquals(MISSING, repo.lookupByExternalId(revised(a, 3), "p").status);
        } finally { store.allowWrite.countDown(); executor.shutdownNow(); }
    }

    @Test public void requestOwnershipCannotBeMovedToAnotherChannel() {
        PublishedPostInput bad = new PublishedPostInput(a, "p", new PublishedPostInput.Ownership(b, "p", "mock"),
                null, null, null, null, 100, PublishedPostInput.Source.MO_PHONG, "mock");
        rejected(OWNERSHIP_CONFLICT, bad);
    }
    @Test public void ownershipPostMustMatchExactly() {
        PublishedPostInput bad = new PublishedPostInput(a, "AbC", new PublishedPostInput.Ownership(a, "abc", "mock"),
                null, null, null, null, 100, PublishedPostInput.Source.MO_PHONG, "mock");
        rejected(OWNERSHIP_CONFLICT, bad);
    }
    @Test public void existingRowCannotSilentlyChangeSubject() {
        PublishedPost old = saved(input(a, "p"));
        PublishedPostScope other = scope("a", 1, a.platform, a.accountType, "different"); store.connect(other);
        rejected(OWNERSHIP_CONFLICT, input(other, "p"));
        assertEquals(SCOPE_MISMATCH, repo.lookupByLocalId(other, old.id).status);
    }
    @Test public void retainedOldRevisionCannotSilentlyBeReused() {
        saved(input(a, "p")); store.connect(revised(a, 2));
        rejected(OWNERSHIP_CONFLICT, input(revised(a, 2), "p"));
    }
    @Test public void ownershipConflictRollsBackEarlierInsert() {
        PublishedPostInput bad = new PublishedPostInput(a, "bad", new PublishedPostInput.Ownership(b, "bad", "mock"),
                null, null, null, null, 100, PublishedPostInput.Source.MO_PHONG, "mock");
        assertEquals(OWNERSHIP_CONFLICT, repo.upsertAll(Arrays.asList(input(a, "first"), bad)).status);
        assertEquals(0, store.size());
    }
    @Test public void secondWriteFailureRollsBackBothRows() {
        store.failWriteAt = 2;
        PublishedPostRepository.WriteResult result = repo.upsertAll(Arrays.asList(input(a, "1"), input(a, "2")));
        assertEquals(STORAGE_ERROR, result.status); assertTrue(result.entries.isEmpty()); assertEquals(0, store.size());
    }
    @Test public void failedCommitRollsBackUpdateAndKeepsGoodMetadata() {
        PublishedPost old = saved(input(a, "p", "good", null, null, null, 100)); store.failCommit = true;
        rejected(STORAGE_ERROR, input(a, "p", "new", null, null, null, 200)); store.failCommit = false;
        assertEquals("good", repo.lookupByLocalId(a, old.id).post.content);
    }
    @Test public void failureAfterEarlierUpdateRestoresPreviousRow() {
        PublishedPost old = saved(input(a, "p", "good", null, null, null, 100));
        store.failWriteAt = 2;
        PublishedPostRepository.WriteResult result = repo.upsertAll(Arrays.asList(
                input(a, "p", "new", null, null, null, 200), input(b, "new")));
        assertEquals(STORAGE_ERROR, result.status); assertEquals(1, store.size());
        assertEquals("good", repo.lookupByLocalId(a, old.id).post.content);
        assertEquals(MISSING, repo.lookupByExternalId(b, "new").status);
    }
    @Test public void metadataStartsNullableWithoutInventedValues() {
        PublishedPost post = saved(input(a, "p"));
        assertNull(post.content); assertNull(post.permalink); assertNull(post.publishedAt);
        assertNull(post.providerUpdatedAt); assertNull(post.contentSourceRef);
    }
    @Test public void omittedAndNullNeverDeleteKnownMetadata() {
        PublishedPost old = saved(input(a, "p", "good", "https://www.facebook.com/posts/mock", 50L, 80L, 100));
        PublishedPost latest = saved(input(a, "p", null, null, null, 90L, 200));
        assertEquals(old.id, latest.id); assertEquals(old.content, latest.content);
        assertEquals(old.permalink, latest.permalink); assertEquals(old.publishedAt, latest.publishedAt);
        assertEquals(old.contentSourceRef, latest.contentSourceRef);
    }
    @Test public void retainedFieldsKeepTheirOriginalEvidenceAfterSparseUpdate() {
        saved(input(a, "p", "good", null, null, 100L, 200));
        PublishedPostInput sparse = new PublishedPostInput(a, "p",
                new PublishedPostInput.Ownership(a, "p", "new-ownership"), null,
                "https://www.facebook.com/posts/mock", null, 200L, 300,
                PublishedPostInput.Source.MO_PHONG, "new-source");
        PublishedPost post = saved(sparse);
        assertEquals("new-source", post.sourceRef); assertEquals("mock-source", post.contentSourceRef);
        assertEquals("new-source", post.permalinkSourceRef); assertNull(post.publishedAtSourceRef);
        assertEquals("new-ownership", post.ownershipEvidenceRef);
    }
    @Test public void emptyContentIsARealValue() {
        saved(input(a, "p", "old", null, null, null, 100));
        assertEquals("", saved(input(a, "p", "", null, null, null, 200)).content);
    }
    @Test public void newerProviderVersionUpdatesSameLocalRow() {
        PublishedPost old = saved(input(a, "p", "old", null, null, 100L, 200));
        PublishedPost latest = saved(input(a, "p", "new", null, null, 200L, 300));
        assertEquals(old.id, latest.id); assertEquals("new", latest.content);
    }
    @Test public void olderProviderVersionArrivingLaterIsIgnored() {
        PublishedPost old = saved(input(a, "p", "new", null, null, 200L, 300));
        PublishedPostRepository.WriteResult result = repo.upsert(input(a, "p", "old", null, null, 100L, 400));
        assertEquals(STALE, result.entries.get(0).action); assertSame(old, result.entries.get(0).post);
    }
    @Test public void providerVersionWinsOverReceiptButReceiptWatermarkDoesNotRegress() {
        saved(input(a, "p", "old", null, null, 100L, 300));
        PublishedPost post = saved(input(a, "p", "new", null, null, 200L, 200));
        assertEquals("new", post.content); assertEquals(300, post.receivedAt);
        assertEquals(Long.valueOf(200), post.providerUpdatedAt);
    }
    @Test public void olderReceiptWithoutProviderTimeIsIgnored() {
        saved(input(a, "p", "new", null, null, null, 300));
        assertEquals(STALE, repo.upsert(input(a, "p", "old", null, null, null, 200)).entries.get(0).action);
        assertEquals("new", repo.lookupByExternalId(a, "p").post.content);
    }
    @Test public void equalProviderVersionWithConflictingMetadataIsNotSuccess() {
        saved(input(a, "p", "good", null, null, 100L, 200));
        rejected(METADATA_CONFLICT, input(a, "p", "bad", null, null, 100L, 300));
        assertEquals("good", repo.lookupByExternalId(a, "p").post.content);
    }
    @Test public void equalReceiptConflictRollsBackEarlierBatchUpdate() {
        saved(input(a, "p", "good", null, null, null, 100));
        PublishedPostRepository.WriteResult result = repo.upsertAll(Arrays.asList(
                input(a, "new"), input(a, "p", "bad", null, null, null, 100)));
        assertEquals(METADATA_CONFLICT, result.status); assertEquals(1, store.size());
    }
    @Test public void equalVersionCanFillUnknownFields() {
        saved(input(a, "p", null, null, null, 100L, 200));
        assertEquals("known", saved(input(a, "p", "known", null, null, 100L, 200)).content);
    }
    @Test public void undatedResponseCannotReplaceProviderVersionedMetadata() {
        saved(input(a, "p", "good", null, null, 100L, 200));
        rejected(METADATA_CONFLICT, input(a, "p", "unorderable", null, null, null, 300));
    }
    @Test public void undatedResponseCanFillGapsAndKeepsProviderTime() {
        saved(input(a, "p", "good", null, null, 100L, 200));
        PublishedPost post = saved(input(a, "p", null, null, 50L, null, 300));
        assertEquals(Long.valueOf(100), post.providerUpdatedAt); assertEquals(Long.valueOf(50), post.publishedAt);
    }
    @Test public void lookupMissingIsDifferentFromScopeFailure() {
        assertEquals(MISSING, repo.lookupByExternalId(a, "missing").status);
        assertEquals(SCOPE_MISMATCH, repo.lookupByExternalId(revised(a, 2), "missing").status);
        assertNull(repo.lookupByExternalId(revised(a, 2), "missing").post);
    }
    @Test public void localLookupCannotLeakAnotherChannel() {
        PublishedPost post = saved(input(a, "p"));
        assertEquals(SCOPE_MISMATCH, repo.lookupByLocalId(b, post.id).status);
        assertNull(repo.lookupByLocalId(b, post.id).post);
    }
    @Test public void localLookupRequiresCurrentRevision() {
        PublishedPost post = saved(input(a, "p"));
        assertEquals(SCOPE_MISMATCH, repo.lookupByLocalId(revised(a, 2), post.id).status);
    }
    @Test public void lookupDistinguishesDisconnectedAndAuthRequired() {
        store.state(a, ConnectionStatus.DISCONNECTED);
        assertEquals(DISCONNECTED, repo.lookupByExternalId(a, "p").status);
        store.state(a, ConnectionStatus.REAUTH_REQUIRED);
        assertEquals(AUTH_REQUIRED, repo.lookupByLocalId(a, "missing").status);
    }
    @Test public void databaseReadFailureNeverBecomesMissing() {
        store.failRead = true;
        assertEquals(STORAGE_ERROR, repo.lookupByExternalId(a, "p").status);
        assertEquals(STORAGE_ERROR, repo.lookupByLocalId(a, "id").status);
        rejected(STORAGE_ERROR, input(a, "p"));
    }
    @Test public void productionRejectsAllFixtureProvenances() {
        PublishedPostRepository production = PublishedPostRepository.forProduction(store);
        for (PublishedPostInput.Source source : PublishedPostInput.Source.values()) {
            if (source == PublishedPostInput.Source.API_THAT) continue;
            PublishedPostInput in = new PublishedPostInput(a, "p", new PublishedPostInput.Ownership(a, "p", "mock"),
                    null, null, null, null, 100, source, "mock");
            assertEquals(PROVENANCE_REJECTED, production.upsert(in).status);
        }
        assertEquals(0, store.size());
    }
    @Test public void productionLookupRejectsSimulatedHistory() {
        PublishedPost post = saved(input(a, "p"));
        PublishedPostRepository production = PublishedPostRepository.forProduction(store);
        assertEquals(PROVENANCE_REJECTED, production.lookupByExternalId(a, "p").status);
        assertEquals(PROVENANCE_REJECTED, production.lookupByLocalId(a, post.id).status);
    }
    @Test public void explicitlyVerifiedApiInputPassesProductionPolicyInHarnessOnly() {
        PublishedPostInput api = new PublishedPostInput(a, "p", new PublishedPostInput.Ownership(a, "p", "test-only"),
                null, null, null, null, 100, PublishedPostInput.Source.API_THAT, "test-only");
        assertEquals(COMMITTED, PublishedPostRepository.forProduction(store).upsert(api).status);
        // This synthetic test of policy is NOT evidence of a real API run or production persistence.
    }
    @Test public void credentialBearingUrlsRejectedWithSanitizedError() {
        for (String url : Arrays.asList("https://x.test/p?access_token=MOCK_SECRET",
                "https://user:MOCK_SECRET@x.test/p", "https://x.test/p#MOCK_SECRET",
                "http://x.test/p", "https://x.test/p?%61ccess_token=MOCK_SECRET")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> input(a, "p", null, url, null, null, 100));
            assertFalse(error.toString().contains("MOCK_SECRET"));
        }
    }
    @Test public void sourceReferenceCannotContainRawUrlOrBearer() {
        assertThrows(IllegalArgumentException.class, () -> new PublishedPostInput(a, "p",
                new PublishedPostInput.Ownership(a, "p", "mock"), null, null, null, null,
                100, PublishedPostInput.Source.MO_PHONG, "https://x.test/?token=MOCK_SECRET"));
        assertThrows(IllegalArgumentException.class, () -> new PublishedPostInput.Ownership(a, "p", "Bearer MOCK_SECRET"));
    }
    @Test public void outwardResultsNeverIncludeStorageExceptionOrSecretFields() {
        store.failCommit = true;
        String json = new Gson().toJson(repo.upsert(input(a, "p")));
        assertFalse(json.contains("MOCK_SECRET")); assertFalse(json.contains("detail"));
        assertFalse(json.contains("tokenReference")); assertFalse(json.contains("access_token"));
        assertEquals("PublishedPostInput[redacted]", input(a, "private-post").toString());
        assertEquals("PublishedPostScope[redacted]", a.toString());
    }
    @Test public void blankIdsInvalidRevisionsAndTimestampsRejected() {
        assertThrows(IllegalArgumentException.class, () -> input(a, " "));
        assertThrows(IllegalArgumentException.class, () -> revised(a, 0));
        assertThrows(IllegalArgumentException.class, () -> input(a, "p", null, null, -1L, null, 100));
        assertThrows(IllegalArgumentException.class, () -> input(a, "p", null, null, null, -1L, 100));
        assertThrows(IllegalArgumentException.class, () -> input(a, "p", null, null, null, null, -1));
    }
    @Test public void invalidPlatformAccountCombinationsRejected() {
        assertThrows(IllegalArgumentException.class, () -> scope("x", 1, Platform.FACEBOOK, AccountType.PERSON, "x"));
        assertThrows(IllegalArgumentException.class, () -> scope("x", 1, Platform.LINKEDIN, AccountType.PAGE, "x"));
    }
    @Test public void emptyBatchRejectedBeforeTransaction() {
        assertThrows(IllegalArgumentException.class, () -> repo.upsertAll(Collections.emptyList()));
    }
}
