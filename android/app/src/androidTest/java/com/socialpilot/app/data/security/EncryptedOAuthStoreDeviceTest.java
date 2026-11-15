package com.socialpilot.app.data.security;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses an isolated preferences file, never the target app's normal OAuth or Room data. */
@RunWith(AndroidJUnit4.class)
public class EncryptedOAuthStoreDeviceTest {
    private Context context;
    private SharedPreferences preferences;

    @Before public void setUp() {
        context = new android.content.ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("sp010_test_" + name, mode);
            }
        };
        preferences = context.getSharedPreferences("oauth_secure_v1", Context.MODE_PRIVATE);
        assertTrue(preferences.edit().clear().commit());
    }

    @After public void tearDown() { preferences.edit().clear().commit(); }

    @Test public void keystoreRoundTripKeepsReconnectAndPendingRecordsWhole() {
        EncryptedOAuthStore store = new EncryptedOAuthStore(context);
        store.saveConnected(connection("old-page", "fake-old-token"));
        store.saveConnected(connection("new-page", "fake-new-token"), true);
        String verifier = new String(new char[43]).replace('\0', 'a');
        store.savePending("pending-session", verifier);
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(context);
        assertEquals("new-page (new-page)", reloaded.pageSummary());
        assertEquals("fake-new-token", reloaded.accessToken());
        assertEquals("pending-session", reloaded.getPending().sessionId);
        assertEquals(verifier, reloaded.getPending().verifier);
        assertTrue(preferences.contains("pending_v3_FACEBOOK"));
        assertTrue(preferences.contains("revision_v1_FACEBOOK:old-page"));
        assertTrue(preferences.contains("revision_v1_FACEBOOK:new-page"));
        assertFalse(preferences.getString("connected_v3_FACEBOOK", "").contains("fake-new-token"));
    }

    @Test public void damagedRecordNeverLeavesConnectedPageMetadata() {
        EncryptedOAuthStore store = new EncryptedOAuthStore(context);
        store.saveConnected(connection("test-page", "fake-test-token"));
        preferences.edit().putString("connected_v3_FACEBOOK", "invalid-ciphertext").commit();
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(context);
        assertNull(reloaded.pageSummary());
        assertNull(reloaded.accessToken());
        assertFalse(preferences.contains("connected_v3_FACEBOOK"));
    }

    @Test public void bothProvidersAndPendingSessionsSurviveKeystoreReload() {
        EncryptedOAuthStore store = new EncryptedOAuthStore(context);
        store.saveConnected(connection("test-page", "fake-fb-token"));
        store.saveConnected(linkedin("2099-01-01T00:00:00Z"));
        String verifier = new String(new char[43]).replace('\0', 'a');
        store.savePending("FACEBOOK", "fb-session", verifier, null);
        store.savePending("LINKEDIN", "li-session", verifier, "2099-01-01T00:00:00Z");
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(context);
        assertEquals("fake-fb-token", reloaded.accessToken("FACEBOOK"));
        assertEquals("fake-li-token", reloaded.accessToken("LINKEDIN"));
        assertEquals("LINKEDIN", reloaded.findPending("li-session").provider);
        reloaded.clearPending("FACEBOOK", "wrong-session");
        assertNotNull(reloaded.getPending("FACEBOOK"));
        reloaded.clearPending("FACEBOOK", "fb-session");
        assertNull(reloaded.getPending("FACEBOOK"));
        assertNotNull(new EncryptedOAuthStore(context).getPending("LINKEDIN"));
    }

    @Test public void realKeystoreMigratesV2FacebookWithoutReplacingLinkedin() throws Exception {
        EncryptedOAuthStore store = new EncryptedOAuthStore(context);
        // Build the historical v2 envelope with the real cipher, using only fake credentials.
        java.lang.reflect.Field field = EncryptedOAuthStore.class.getDeclaredField("cipher");
        field.setAccessible(true);
        EncryptedOAuthStore.RecordCipher realCipher = (EncryptedOAuthStore.RecordCipher) field.get(store);
        String json = "{\"version\":2,\"connection\":"
                + new com.google.gson.Gson().toJson(connection("old-page", "fake-v2-token")) + "}";
        String encrypted = realCipher.encrypt("connected_v2", json);
        assertTrue(preferences.edit().putString("connected_v2", encrypted).commit());
        store.saveConnected(linkedin("2099-01-01T00:00:00Z"));
        assertEquals("fake-v2-token", store.accessToken("FACEBOOK"));
        assertFalse(preferences.contains("connected_v2"));
        assertTrue(preferences.contains("connected_v3_FACEBOOK"));
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(context);
        assertEquals("fake-v2-token", reloaded.accessToken("FACEBOOK"));
        assertEquals("fake-li-token", reloaded.accessToken("LINKEDIN"));
        assertEquals(1, reloaded.channel("FACEBOOK").connectionRevision);
        assertFalse(preferences.getString("connected_v3_FACEBOOK", "").contains("fake-v2-token"));
    }

    @Test public void reauthFlagSurvivesReloadWithoutAffectingFacebook() {
        EncryptedOAuthStore store = new EncryptedOAuthStore(context);
        store.saveConnected(connection("page", "fake-fb-token"));
        store.saveConnected(linkedin("2099-01-01T00:00:00Z"));
        long revision = store.channel("LINKEDIN").connectionRevision;
        store.requireReauth("LINKEDIN", revision);
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(context);
        assertEquals(com.socialpilot.app.domain.contract.social.ConnectionStatus.REAUTH_REQUIRED,
                reloaded.channel("LINKEDIN").connectionStatus);
        assertNull(reloaded.tokenFor("LINKEDIN", reloaded.channel("LINKEDIN").channelId, revision));
        assertEquals("fake-fb-token", reloaded.accessToken("FACEBOOK"));
    }

    @Test public void recoveryRetainsRevisionAndLostMarkerChangesChannel() {
        EncryptedOAuthStore store=new EncryptedOAuthStore(context);
        store.saveConnected(linkedin("2099-01-01T00:00:00Z"));
        var old=store.channel("LINKEDIN");
        preferences.edit().putString("connected_v3_LINKEDIN","broken").commit();
        store=new EncryptedOAuthStore(context);
        assertNull(store.channel("LINKEDIN"));
        store.saveConnected(linkedin("2099-01-01T00:00:00Z"));
        var restored=store.channel("LINKEDIN");
        assertEquals(old.channelId,restored.channelId);
        assertTrue(restored.connectionRevision>old.connectionRevision);
        assertNull(store.tokenFor("LINKEDIN",old.channelId,old.connectionRevision));
        preferences.edit().remove("connected_v3_LINKEDIN").remove("revision_v1_LINKEDIN:fake-member").commit();
        store=new EncryptedOAuthStore(context);
        store.saveConnected(linkedin("2099-01-01T00:00:00Z"));
        assertNotEquals(restored.channelId,store.channel("LINKEDIN").channelId);
        store.requireReauth("LINKEDIN",restored.channelId,store.channel("LINKEDIN").connectionRevision);
        assertEquals(com.socialpilot.app.domain.contract.social.ConnectionStatus.CONNECTED,store.channel("LINKEDIN").connectionStatus);
    }

    @Test public void staleV2RecoveryPreservesRealKeystoreRevisionMarker() throws Exception {
        EncryptedOAuthStore store=new EncryptedOAuthStore(context);
        store.saveConnected(connection("page","fake-current-token"));
        store.saveConnected(connection("page","fake-current-token"));
        var previous=store.channel("FACEBOOK");
        java.lang.reflect.Field field=EncryptedOAuthStore.class.getDeclaredField("cipher");
        field.setAccessible(true);
        EncryptedOAuthStore.RecordCipher cipher=(EncryptedOAuthStore.RecordCipher)field.get(store);
        String legacy="{\"version\":2,\"connection\":"
                +new com.google.gson.Gson().toJson(connection("page","fake-legacy-token"))+"}";
        assertTrue(preferences.edit().putString("connected_v2",cipher.encrypt("connected_v2",legacy))
                .remove("connected_v3_FACEBOOK").commit());
        EncryptedOAuthStore recovered=new EncryptedOAuthStore(context);
        var current=recovered.channel("FACEBOOK");
        assertEquals(previous.channelId,current.channelId);
        assertTrue(current.connectionRevision>previous.connectionRevision);
        assertNull(recovered.tokenFor("FACEBOOK",previous.channelId,previous.connectionRevision));
        assertFalse(preferences.contains("connected_v2"));
        assertEquals(current.connectionRevision,new EncryptedOAuthStore(context).channel("FACEBOOK").connectionRevision);
    }

    private OAuthExchangeResponse linkedin(String expiry) {
        return new OAuthExchangeResponse("fake-li-session", "LINKEDIN", "fake-member", "PERSON", "Test Member",
                "fake-li-token", null, "Bearer", expiry);
    }

    private OAuthExchangeResponse connection(String page, String token) {
        return new OAuthExchangeResponse("fake-session", "FACEBOOK", page, "PAGE", page,
                token, null, "bearer", null);
    }
}
