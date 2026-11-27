package com.socialpilot.app.data.repository;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.local.ChannelSecretCleanup;
import com.socialpilot.app.data.local.DraftEntity;
import com.socialpilot.app.data.repository.ChannelRepository.ExpiredGrantException;
import com.socialpilot.app.data.repository.ChannelRepository.DisconnectResult;
import com.socialpilot.app.data.remote.instagram.ControlledInstagramGraph;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedSecretStore;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import com.socialpilot.app.domain.contract.secret.SecretStoreException;
import com.socialpilot.app.domain.contract.social.CapabilityAvailability;
import com.socialpilot.app.domain.contract.social.ChannelCapability;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.domain.contract.social.SocialOperation;
import com.socialpilot.app.domain.editor.ContentVersion;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ChannelRepositoryDeviceTest {
    private Context context;
    private String databaseName;
    private AppDatabase database;
    private SecretStore secrets;

    @Before public void setUp() {
        databaseName = "sp022-" + UUID.randomUUID() + ".db";
        context = new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(databaseName + name, mode);
            }
        };
        secrets = new EncryptedSecretStore(context, SecretNamespace.SOCIAL_TOKEN);
    }

    @After public void tearDown() {
        if (database != null) database.close();
        context.deleteDatabase(databaseName);
        context.getSharedPreferences("oauth_secure_v1", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("secrets_social_v1", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("instagram_feed_spike", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void migrationPreservesDraftsAndImportsBothLegacyConnectionsOnce() {
        try (SQLiteDatabase v1 = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)) {
            v1.execSQL("CREATE TABLE drafts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + " topic TEXT, createdAt INTEGER NOT NULL)");
            v1.execSQL("INSERT INTO drafts (id, topic, createdAt) VALUES (7, 'Keep', 100), (11, NULL, 200)");
            v1.setVersion(1);
        }
        EncryptedOAuthStore legacy = new EncryptedOAuthStore(context);
        legacy.saveConnected(page("fb-old", "facebook-fake"));
        legacy.saveConnected(instagram("ig-old", "instagram-fake"));
        legacy.savePending("pending-session", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "INSTAGRAM");
        ChannelRepository channels = open();
        EncryptedOAuthStore store = new EncryptedOAuthStore(context, channels);
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_facebook BEFORE INSERT ON channels"
                        + " WHEN NEW.platform = 'FACEBOOK' BEGIN SELECT RAISE(ABORT, 'fixture'); END");
        assertEquals(Collections.singleton(Platform.FACEBOOK), store.restoreChannels());
        assertEquals("facebook-fake", legacy.accessToken());
        assertNull(store.accessToken());
        assertEquals("instagram-fake", store.instagramConnection().accessToken);
        assertEquals(1, channels.selected(Platform.INSTAGRAM).scope.connectionRevision);

        try (android.database.Cursor rows = database.getOpenHelper().getReadableDatabase()
                .query("SELECT id, topic, createdAt FROM drafts ORDER BY id")) {
            assertTrue(rows.moveToFirst());
            assertEquals(7, rows.getLong(0));
            assertEquals("Keep", rows.getString(1));
            assertEquals(100, rows.getLong(2));
            assertTrue(rows.moveToNext());
            assertEquals(11, rows.getLong(0));
            assertTrue(rows.isNull(1));
            assertEquals(200, rows.getLong(2));
        }
        assertEquals("pending-session", store.getPending().sessionId);
        assertNull(legacy.instagramConnection());
        String channelId = channels.selected(Platform.INSTAGRAM).info.channelId;
        assertEquals(1, channels.list().size());
        assertTrue(database.channels().pendingCleanup().isEmpty());
        database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER reject_facebook");

        database.close();
        ChannelRepository reopened = open();
        EncryptedOAuthStore restored = new EncryptedOAuthStore(context, reopened);
        assertTrue(restored.restoreChannels().isEmpty());
        assertEquals("facebook-fake", restored.accessToken());
        assertEquals("instagram-fake", restored.instagramConnection().accessToken);
        assertNull(legacy.accessToken());
        assertEquals(1, reopened.selected(Platform.FACEBOOK).scope.connectionRevision);
        assertEquals(channelId, reopened.selected(Platform.INSTAGRAM).info.channelId);
        assertEquals(1, reopened.find(channelId).scope.connectionRevision);
        assertTrue(restored.restoreChannels().isEmpty());
        assertEquals(2, reopened.list().size());
        assertEquals(1, reopened.selected(Platform.FACEBOOK).scope.connectionRevision);
        assertEquals(1, reopened.selected(Platform.INSTAGRAM).scope.connectionRevision);
    }

    @Test public void expiredAndRevokedInstagramKeepDisplayIdentityAfterRestart() {
        ChannelRepository channels = open();
        EncryptedOAuthStore store = new EncryptedOAuthStore(context, channels);
        assertNull(store.selectedInstagramMetadata());
        ChannelRepository.Snapshot page = channels.save(page("fb-a", "facebook-fake"));
        OAuthExchangeResponse profile = instagram("ig-old", "expired-fake");
        ChannelRepository.Snapshot expired = channels.importLegacy(new OAuthExchangeResponse(profile.sessionId,
                profile.provider, profile.externalAccountId, profile.accountType, profile.displayName,
                profile.accessToken, null, "bearer", "2020-01-01T00:00:00Z",
                profile.linkedPageId, profile.grantedScopes, profile.graphApiVersion));
        database.close();

        ChannelRepository reopened = open();
        EncryptedOAuthStore restored = new EncryptedOAuthStore(context, reopened);
        assertTrue(restored.restoreChannels().isEmpty());
        assertEquals(profile.externalAccountId, restored.selectedInstagramMetadata().externalAccountId);
        assertTrue(expired.scope.matches(restored.selectedInstagramMetadata().scope));
        assertFalse(restored.selectedInstagramMetadata().connected);
        assertNull(restored.instagramSession().sessionId);
        assertNull(restored.instagramConnection());

        ChannelRepository.Snapshot next = reopened.save(instagram("ig-new", "new-fake"));
        assertEquals(expired.scope.channelId, next.scope.channelId);
        assertEquals(2, next.scope.connectionRevision);
        assertTrue(restored.selectedInstagramMetadata().connected);
        restored.invalidateInstagram(next.sessionId);
        database.close();

        ChannelRepository afterRevocation = open();
        EncryptedOAuthStore revoked = new EncryptedOAuthStore(context, afterRevocation);
        assertTrue(revoked.restoreChannels().isEmpty());
        assertTrue(next.scope.matches(revoked.selectedInstagramMetadata().scope));
        assertFalse(revoked.selectedInstagramMetadata().connected);
        assertNull(revoked.instagramConnection());
        assertEquals("facebook-fake", afterRevocation.credential(page.scope).accessToken);
    }

    @Test public void instagramImportFailureKeepsFacebookUsable() {
        EncryptedOAuthStore legacy = new EncryptedOAuthStore(context);
        legacy.saveConnected(page("fb-old", "facebook-fake"));
        legacy.saveConnected(instagram("ig-old", "instagram-fake"));
        ChannelRepository channels = open();
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_instagram BEFORE INSERT ON channels"
                        + " WHEN NEW.platform = 'INSTAGRAM' BEGIN SELECT RAISE(ABORT, 'fixture'); END");
        EncryptedOAuthStore store = new EncryptedOAuthStore(context, channels);
        assertEquals(Collections.singleton(Platform.INSTAGRAM), store.restoreChannels());
        assertEquals("facebook-fake", store.accessToken());
        assertEquals("instagram-fake", legacy.instagramConnection().accessToken);
        assertTrue(database.channels().pendingCleanup().isEmpty());
    }

    @Test public void deniedPageIdentityCheckDoesNotRevokeConnectionOrChangeCapabilities() {
        open();
        AtomicInteger checks = new AtomicInteger();
        ChannelRepository channels = new ChannelRepository(database, secrets,
                ControlledInstagramGraph.withResponse(chain -> {
                    boolean first = checks.getAndIncrement() == 0;
                    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                            .code(first ? 400 : 403).message("Fixture")
                            .body(ResponseBody.create(first ? "{\"error\":{\"code\":200}}"
                                    : "{\"error\":{\"code\":10}}", null)).build();
                }));
        ChannelRepository.Snapshot page = channels.save(page("fb-a", "facebook-fake"));
        ChannelCapability verified = new ChannelCapability(SocialOperation.READ_COMMENTS,
                CapabilityAvailability.AVAILABLE, "VERIFIED", "2026-11-25T08:00:00Z");
        assertTrue(channels.recordCapability(page.scope, verified));
        assertEquals(ChannelRepository.PageCheck.PERMISSION_REQUIRED, channels.checkFacebookPage(page.scope));
        assertEquals(ChannelRepository.PageCheck.PERMISSION_REQUIRED, channels.checkFacebookPage(page.scope));
        assertEquals("facebook-fake", channels.credential(page.scope).accessToken);
        assertEquals(CapabilityAvailability.AVAILABLE,
                channels.find(page.scope.channelId).capability(SocialOperation.READ_COMMENTS).availability);
    }

    @Test public void reconnectReusesIdentityAndRejectsOldScopeWhileCleaningSecrets() throws Exception {
        ChannelRepository channels = open();
        ChannelRepository.Snapshot page = channels.save(page("fb-a", "facebook-fake"));
        ChannelRepository.Snapshot prior = channels.save(instagram("ig-a", "instagram-old"));
        ChannelCapability permission = new ChannelCapability(SocialOperation.READ_COMMENTS,
                CapabilityAvailability.PERMISSION_REQUIRED, "MISSING_SCOPE", "2026-11-25T08:00:00Z");
        assertTrue(channels.recordCapability(prior.scope, permission));
        assertEquals(CapabilityAvailability.PERMISSION_REQUIRED,
                channels.find(prior.info.channelId).capability(SocialOperation.READ_COMMENTS).availability);
        ChannelRepository.Snapshot next = channels.save(instagram("ig-b", "instagram-new"));
        assertEquals(prior.info.channelId, next.info.channelId);
        assertEquals(2, next.scope.connectionRevision);
        assertNull(channels.credential(prior.scope));
        assertFalse(channels.recordCapability(prior.scope, permission));
        assertFalse(channels.requireReconnect(prior.scope));
        assertEquals(CapabilityAvailability.UNVERIFIED,
                channels.find(next.info.channelId).capability(SocialOperation.READ_COMMENTS).availability);
        assertEquals("facebook-fake", channels.credential(page.scope).accessToken);
        assertEquals(SecretRead.Status.MISSING, secrets.read(SecretRef.parse(prior.info.tokenReference)).status);
        assertEquals("instagram-new", channels.credential(next.scope).accessToken);

        SecretRef leftover = new SecretRef(SecretNamespace.SOCIAL_TOKEN, "interrupted-save");
        database.channels().queueCleanup(new ChannelSecretCleanup(leftover.opaque()));
        secrets.write(leftover, "fixture");
        channels.restore();
        assertEquals(SecretRead.Status.MISSING, secrets.read(leftover).status);
        assertTrue(database.channels().pendingCleanup().isEmpty());
        secrets.delete(SecretRef.parse(next.info.tokenReference));
        assertNull(channels.credential(next.scope));
        assertEquals(ConnectionStatus.REAUTH_REQUIRED, channels.find(next.info.channelId).info.connectionStatus);
        assertEquals(SecretRead.Status.MISSING, channels.find(next.info.channelId).credentialStatus);
    }

    @Test public void expiredPageReconnectPreservesTheWorkingCredentialAfterRestart() {
        ChannelRepository channels = open();
        ChannelRepository.Snapshot previous = channels.save(page("page-working", "old-fake"));
        OAuthExchangeResponse exchange = new Gson().fromJson("""
                {"sessionId":"page-expired","provider":"FACEBOOK","externalAccountId":"1234567890",
                "accountType":"PAGE","displayName":"Test Page","accessToken":"fake-page-token",
                "tokenType":"bearer","expiresAt":"2020-01-01T00:00:00Z",
                "tokenExpiresAt":"2030-01-01T00:00:00Z","dataAccessExpiresAt":"2020-01-01T00:00:00Z"}
                """, OAuthExchangeResponse.class);
        exchange.validate();
        assertThrows(ExpiredGrantException.class, () -> channels.save(exchange));
        assertSame(previous, channels.selected(Platform.FACEBOOK));
        assertEquals("old-fake", channels.credential(previous.scope).accessToken);
        assertTrue(database.channels().pendingCleanup().isEmpty());
        database.close();

        ChannelRepository reopened = open();
        reopened.restore();
        ChannelRepository.Snapshot restored = reopened.selected(Platform.FACEBOOK);
        assertTrue(previous.scope.matches(restored.scope));
        assertEquals("page-working", restored.sessionId);
        assertEquals(ConnectionStatus.CONNECTED, restored.info.connectionStatus);
        assertEquals("old-fake", reopened.credential(previous.scope).accessToken);
    }

    @Test public void unexpiredPageReconnectPromotesTheNewRevision() {
        ChannelRepository channels = open();
        ChannelRepository.Snapshot previous = channels.save(page("page-working", "old-fake"));
        OAuthExchangeResponse exchange = new OAuthExchangeResponse(
                "page-replacement", "FACEBOOK", "1234567890", "PAGE", "Test Page",
                "new-fake", null, "bearer", "2099-01-01T00:00:00Z", null, null, null,
                "2099-02-01T00:00:00Z", "2099-01-01T00:00:00Z");
        ChannelRepository.Snapshot next = channels.save(exchange);

        assertEquals(previous.scope.channelId, next.scope.channelId);
        assertEquals(2, next.scope.connectionRevision);
        assertTrue(next.connected());
        assertEquals("2099-01-01T00:00:00Z", next.expiresAt);
        assertNull(channels.credential(previous.scope));
        assertEquals(SecretRead.Status.MISSING, secrets.read(SecretRef.parse(previous.info.tokenReference)).status);
        assertEquals("new-fake", channels.credential(next.scope).accessToken);

        database.close();
        ChannelRepository reopened = open();
        reopened.restore();
        assertTrue(next.scope.matches(reopened.selected(Platform.FACEBOOK).scope));
        assertEquals("new-fake", reopened.credential(next.scope).accessToken);
    }

    @Test public void failedSecretWriteLeavesOldChannelAndCredentialUsable() {
        ChannelRepository channels = open();
        ChannelRepository.Snapshot previous = channels.save(page("fb-a", "old-fake"));
        SecretStore failing = new SecretStore() {
            @Override public SecretNamespace namespace() { return secrets.namespace(); }
            @Override public String write(SecretRef ref, String value) throws SecretStoreException {
                throw new SecretStoreException(SecretStoreException.Reason.WRITE_FAILED);
            }
            @Override public SecretRead read(SecretRef ref) { return secrets.read(ref); }
            @Override public void delete(SecretRef ref) throws SecretStoreException { secrets.delete(ref); }
        };
        ChannelRepository replacing = new ChannelRepository(database, failing);
        assertThrows(IllegalStateException.class, () -> replacing.save(page("fb-b", "new-fake")));
        assertEquals("old-fake", channels.credential(previous.scope).accessToken);
        assertEquals(1, channels.find(previous.info.channelId).scope.connectionRevision);
        assertTrue(database.channels().pendingCleanup().isEmpty());
    }

    @Test public void failedRoomPromotionRollsBackSelectionAndCleansNewReference() {
        ChannelRepository channels = open();
        ChannelRepository.Snapshot previous = channels.save(page("fb-a", "old-fake"));
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_channel_update BEFORE UPDATE ON channels"
                        + " WHEN NEW.connectionRevision > 1 BEGIN SELECT RAISE(ABORT, 'fixture'); END");
        assertThrows(RuntimeException.class, () -> channels.save(page("fb-b", "new-fake")));
        assertEquals(previous.scope.channelId, database.channels().selected("FACEBOOK").channelId);
        assertEquals("old-fake", channels.credential(previous.scope).accessToken);
        assertTrue(database.channels().pendingCleanup().isEmpty());
    }

    @Test public void cleanupJournalFailureDoesNotUndoCommittedSelection() {
        ChannelRepository channels = open();
        ChannelRepository.Snapshot old = channels.save(page("fb-a", "old-fake"));
        String ref = old.info.tokenReference;
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_cleanup BEFORE DELETE ON channel_secret_cleanup"
                        + " WHEN OLD.credentialRef = '" + ref + "' BEGIN SELECT RAISE(ABORT, 'fixture'); END");

        ChannelRepository.Snapshot next = channels.save(page("fb-b", "new-fake"));

        assertEquals(2, next.scope.connectionRevision);
        assertTrue(next.scope.matches(channels.selected(Platform.FACEBOOK).scope));
        assertEquals(2, database.channels().selected("FACEBOOK").connectionRevision);
        assertEquals("new-fake", channels.credential(next.scope).accessToken);
        assertEquals(SecretRead.Status.MISSING, secrets.read(SecretRef.parse(ref)).status);
        assertEquals(Collections.singletonList(ref), database.channels().pendingCleanup());
        database.close();

        ChannelRepository reopened = open();
        reopened.restore();
        assertTrue(next.scope.matches(reopened.selected(Platform.FACEBOOK).scope));
        database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER reject_cleanup");
        reopened.restore();
        reopened.restore();
        assertTrue(database.channels().pendingCleanup().isEmpty());
        assertEquals("new-fake", reopened.credential(next.scope).accessToken);
    }

    @Test public void mainReadsCommittedSnapshotWhileCredentialCleanupWaits() throws Exception {
        open();
        CountDownLatch deleting = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        SecretStore blockedCleanup = new SecretStore() {
            @Override public SecretNamespace namespace() { return secrets.namespace(); }
            @Override public String write(SecretRef ref, String value) throws SecretStoreException {
                return secrets.write(ref, value);
            }
            @Override public SecretRead read(SecretRef ref) { return secrets.read(ref); }
            @Override public void delete(SecretRef ref) throws SecretStoreException {
                deleting.countDown();
                try {
                    assertTrue("Test did not release cleanup", resume.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                secrets.delete(ref);
            }
        };
        ChannelRepository channels = new ChannelRepository(database, blockedCleanup);
        EncryptedOAuthStore oauth = new EncryptedOAuthStore(context, channels);
        oauth.saveConnected(instagram("ig-reused-session", "old-fake"));
        EncryptedOAuthStore.InstagramSession old = oauth.instagramSession();
        FutureTask<Void> reconnect = new FutureTask<>(() -> {
            oauth.saveConnected(instagram("ig-reused-session", "new-fake"));
            return null;
        });
        new Thread(reconnect, "fixture-reconnect").start();
        try {
            assertTrue(deleting.await(5, TimeUnit.SECONDS));
            FutureTask<Long> readOnMain = new FutureTask<>(() -> {
                assertFalse(oauth.isCurrentInstagramSession(old));
                return oauth.selectedInstagramMetadata().scope.connectionRevision;
            });
            new Handler(Looper.getMainLooper()).post(readOnMain);
            assertEquals(Long.valueOf(2), readOnMain.get(5, TimeUnit.SECONDS));
        } finally {
            resume.countDown();
            reconnect.get(10, TimeUnit.SECONDS);
        }
    }

    @Test public void disconnectFencesOldOAuthAndKeepsOtherChannelAndDraftAcrossReopen() {
        ChannelRepository channels = open();
        EncryptedOAuthStore store = new EncryptedOAuthStore(context, channels);
        ChannelRepository.Snapshot page = channels.save(page("fb-a", "facebook-fake"));
        ChannelRepository.Snapshot profile = channels.save(instagram("ig-a", "instagram-old"));
        long draftId = database.drafts().insert(new DraftEntity("Shared draft", 42));
        String verifier = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        store.savePending("ig-late", verifier, "INSTAGRAM");
        EncryptedOAuthStore.Pending inFlight = store.takePending("ig-late");
        store.savePending("fb-pending", verifier, "FACEBOOK");

        assertEquals(DisconnectResult.DISCONNECTED, store.disconnect(profile.scope));
        assertEquals(DisconnectResult.STALE, store.disconnect(profile.scope));
        assertThrows(IllegalStateException.class, () -> store.saveConnected(inFlight, instagram("ig-late", "old")));
        assertEquals("fb-pending", store.getPending().sessionId);
        assertNull(channels.credential(profile.scope));
        assertFalse(channels.requireReconnect(profile.scope));
        assertEquals(SecretRead.Status.MISSING, secrets.read(SecretRef.parse(profile.info.tokenReference)).status);
        database.close();

        ChannelRepository reopened = open();
        EncryptedOAuthStore restored = new EncryptedOAuthStore(context, reopened);
        assertTrue(restored.restoreChannels().isEmpty());
        assertNull(restored.selectedInstagramMetadata());
        assertEquals("facebook-fake", reopened.credential(page.scope).accessToken);
        try (android.database.Cursor drafts = database.getOpenHelper().getReadableDatabase()
                .query("SELECT id, topic FROM drafts")) {
            assertTrue(drafts.moveToFirst());
            assertEquals(draftId, drafts.getLong(0));
            assertEquals("Shared draft", drafts.getString(1));
            assertFalse(drafts.moveToNext());
        }
        ChannelRepository.Snapshot reconnected = reopened.save(instagram("ig-new", "instagram-new"));
        assertEquals(profile.scope.channelId, reconnected.scope.channelId);
        assertEquals(profile.scope.connectionRevision + 1, reconnected.scope.connectionRevision);
        assertEquals(DisconnectResult.STALE, reopened.disconnect(profile.scope));
        assertEquals("instagram-new", reopened.credential(reconnected.scope).accessToken);
    }

    @Test public void cleanupCanResumeAndKeepsOnlyMinimalInstagramEvidence() {
        open();
        AtomicInteger cleanups = new AtomicInteger();
        ChannelRepository channels = new ChannelRepository(database, secrets, (scope, account, retiredAt) -> {
            if (cleanups.getAndIncrement() < 2) throw new IllegalStateException("fixture");
            InstagramPublishRepository.retireAttempt(context, scope, account, retiredAt);
        });
        ChannelRepository.Snapshot profile = channels.save(instagram("ig-a", "instagram-old"));
        SharedPreferences attempts = context.getSharedPreferences("instagram_feed_spike", Context.MODE_PRIVATE);
        attempts.edit().putString(profile.info.externalAccountId,
                "{\"versionId\":\"v1\",\"containerId\":\"private-container\",\"mediaId\":\"post-1\","
                        + "\"permalink\":\"https://example.test/private-link\"}").commit();

        assertEquals(DisconnectResult.CLEANUP_PENDING, channels.disconnect(profile.scope));
        assertNull(channels.credential(profile.scope));
        assertThrows(IllegalStateException.class, () -> channels.save(instagram("ig-too-early", "ig-new")));
        assertNull(channels.selected(Platform.INSTAGRAM));
        channels.restore();
        assertEquals(3, cleanups.get());
        assertNull(attempts.getString(profile.info.externalAccountId, null));
        String evidence = attempts.getString("retained:" + profile.scope.channelId + ":v1", null);
        assertNotNull(evidence);
        assertTrue(evidence.contains("post-1"));
        assertFalse(evidence.contains("private-container"));
        assertFalse(evidence.contains("private-link"));
        assertTrue(attempts.getBoolean("needs-check:" + profile.scope.channelId, false));
        channels.restore();
        assertEquals(3, cleanups.get());
        ChannelRepository.Snapshot reconnected = channels.save(instagram("ig-new", "instagram-new"));
        assertEquals(profile.scope.connectionRevision + 1, reconnected.scope.connectionRevision);
        assertEquals("instagram-new", channels.credential(reconnected.scope).accessToken);
    }

    @Test public void choosingImmediateEraseKeepsOnlyTheManualCheckAfterReconnect() {
        open();
        ChannelRepository channels = new ChannelRepository(database, secrets, (scope, account, retiredAt) ->
                InstagramPublishRepository.retireAttempt(context, scope, account, retiredAt));
        EncryptedOAuthStore store = new EncryptedOAuthStore(context, channels);
        InstagramPublishRepository publisher = new InstagramPublishRepository(context, store,
                ControlledInstagramGraph.withResponse(chain -> {
                    throw new AssertionError("Checking a retired attempt must not send a request");
                }));
        ChannelRepository.Snapshot initial = channels.save(instagram("ig-original", "instagram-old"));
        SharedPreferences attempts = context.getSharedPreferences("instagram_feed_spike", Context.MODE_PRIVATE);
        attempts.edit().putString(initial.info.externalAccountId,
                "{\"versionId\":\"v1\",\"containerId\":\"private-container\"}").commit();
        publisher.setDisconnectChoice(initial.scope, true);
        assertEquals(DisconnectResult.DISCONNECTED, store.disconnect(initial.scope));
        assertFalse(attempts.contains(initial.info.externalAccountId));
        assertFalse(attempts.contains("retained:" + initial.scope.channelId + ":v1"));

        channels.save(instagram("ig-new", "instagram-new"));
        EncryptedOAuthStore.InstagramSession session = store.instagramSession();
        assertEquals(InstagramPublishRepository.Status.CONFIRM_PRIOR_POST, publisher.reconcile(session).status);
        assertTrue(publisher.confirmPriorPostChecked(session));
        assertFalse(attempts.contains("needs-check:" + initial.scope.channelId));
    }

    @Test public void publishAtThirtyDaysRequiresFreshConfirmation() {
        assertExpiryRequiresConfirmation(false);
    }

    @Test public void expirySweepOnStartAlsoRequiresFreshConfirmation() {
        assertExpiryRequiresConfirmation(true);
    }

    private void assertExpiryRequiresConfirmation(boolean startupSweep) {
        open();
        AtomicLong disconnectedAt = new AtomicLong();
        ChannelRepository channels = new ChannelRepository(database, secrets, (scope, account, retiredAt) -> {
            disconnectedAt.set(retiredAt);
            InstagramPublishRepository.retireAttempt(context, scope, account, retiredAt);
        });
        EncryptedOAuthStore store = new EncryptedOAuthStore(context, channels);
        AtomicLong clock = new AtomicLong(System.currentTimeMillis());
        InstagramPublishRepository publisher = new InstagramPublishRepository(context, store,
                ControlledInstagramGraph.withResponse(chain -> {
                    throw new AssertionError("Retained or expired evidence must block the Graph request");
                }), clock::get);
        ChannelRepository.Snapshot initial = channels.save(instagram("ig-original", "instagram-old"));
        SharedPreferences attempts = context.getSharedPreferences("instagram_feed_spike", Context.MODE_PRIVATE);
        String key = "retained:" + initial.scope.channelId + ":v1";
        attempts.edit().putString(initial.info.externalAccountId,
                "{\"versionId\":\"v1\",\"containerId\":\"private-container\"}").commit();
        assertEquals(DisconnectResult.DISCONNECTED, store.disconnect(initial.scope));
        assertTrue(attempts.contains(key));
        channels.save(instagram("ig-reconnect", "instagram-new"));
        EncryptedOAuthStore.InstagramSession session = store.instagramSession();
        ContentVersion version = new ContentVersion("v1", "b1", session.accountId, null,
                "Fixture post", "https://example.test/photo.jpg", clock.get());
        assertTrue(publisher.confirmPriorPostChecked(session));

        long boundary = disconnectedAt.get() + TimeUnit.DAYS.toMillis(30);
        clock.set(boundary - 1);
        assertEquals(InstagramPublishRepository.Status.PRIOR_VERSION_BLOCKED, publisher.publish(version, session).status);
        assertTrue(attempts.contains(key));
        clock.incrementAndGet();
        if (startupSweep) publisher.purgeExpiredEvidence();
        assertEquals(InstagramPublishRepository.Status.CONFIRM_PRIOR_POST, publisher.publish(version, session).status);
        assertFalse(attempts.contains(key));
        assertTrue(publisher.confirmPriorPostChecked(session));
        assertFalse(attempts.contains("needs-check:" + initial.scope.channelId));
    }

    private ChannelRepository open() {
        database = Room.databaseBuilder(context, AppDatabase.class, databaseName)
                .addMigrations(AppDatabase.MIGRATION_1_2).build();
        return new ChannelRepository(database, secrets);
    }

    private static OAuthExchangeResponse page(String session, String token) {
        return new OAuthExchangeResponse(session, "FACEBOOK", "1234567890", "PAGE", "Page",
                token, null, "bearer", null);
    }

    private static OAuthExchangeResponse instagram(String session, String token) {
        return new OAuthExchangeResponse(session, "INSTAGRAM", "17841459976957663", "PROFESSIONAL",
                "Instagram", token, null, "bearer", null, "1234567890",
                Collections.singletonList("instagram_basic"), "v27.0");
    }
}
