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
        store.saveConnected(connection("new-page", "fake-new-token"));
        String verifier = new String(new char[43]).replace('\0', 'a');
        store.savePending("pending-session", verifier);
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(context);
        assertEquals("new-page (new-page)", reloaded.pageSummary());
        assertEquals("fake-new-token", reloaded.accessToken());
        assertEquals("pending-session", reloaded.getPending().sessionId);
        assertEquals(verifier, reloaded.getPending().verifier);
        assertEquals(2, preferences.getAll().size());
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

    private OAuthExchangeResponse connection(String page, String token) {
        return new OAuthExchangeResponse("fake-session", "FACEBOOK", page, "PAGE", page,
                token, null, "bearer", null);
    }
}
