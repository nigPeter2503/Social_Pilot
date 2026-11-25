package com.socialpilot.app.data.repository;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.local.ChannelSecretCleanup;
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
import java.util.Collections;
import java.util.UUID;
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
        store.restoreChannels();

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
        assertEquals("facebook-fake", store.accessToken());
        assertEquals("instagram-fake", store.instagramConnection().accessToken);
        assertNull(legacy.accessToken());
        assertNull(legacy.instagramConnection());
        String channelId = channels.selected(Platform.INSTAGRAM).info.channelId;
        assertEquals(2, channels.list().size());

        database.close();
        ChannelRepository reopened = open();
        new EncryptedOAuthStore(context, reopened).restoreChannels();
        assertEquals(channelId, reopened.selected(Platform.INSTAGRAM).info.channelId);
        assertEquals(1, reopened.find(channelId).scope.connectionRevision);
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

    @Test public void expiredPageCredentialRequiresReconnectAfterRestart() {
        ChannelRepository channels = open();
        ChannelRepository.Snapshot expired = channels.save(new OAuthExchangeResponse(
                "page-expired", "FACEBOOK", "1234567890", "PAGE", "Page",
                "expired-fake", null, "bearer", "2020-01-01T00:00:00Z"));
        assertEquals(ConnectionStatus.REAUTH_REQUIRED, expired.info.connectionStatus);
        assertNull(channels.credential(expired.scope));
        database.close();

        ChannelRepository reopened = open();
        reopened.restore();
        assertEquals(ConnectionStatus.REAUTH_REQUIRED,
                reopened.selected(Platform.FACEBOOK).info.connectionStatus);
        assertFalse(reopened.isCurrent(expired.scope));
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
