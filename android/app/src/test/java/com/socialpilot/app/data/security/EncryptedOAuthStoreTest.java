package com.socialpilot.app.data.security;

import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.testing.MemoryPreferences;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncryptedOAuthStoreTest {
    private MemoryPreferences preferences;
    private TestRecordCipher cipher;
    private EncryptedOAuthStore store;

    @Before public void setUp() {
        preferences = new MemoryPreferences();
        cipher = new TestRecordCipher();
        store = new EncryptedOAuthStore(preferences, cipher);
    }

    private OAuthExchangeResponse connection(String page, String token) {
        return new OAuthExchangeResponse("fake-session", "FACEBOOK", page, "PAGE", page,
                token, null, "bearer", null);
    }

    @Test public void connectionIsOneEncryptedCommitAndSurvivesReload() {
        store.saveConnected(connection("old-page", "fake-old-token"));
        assertEquals(1, preferences.commits);
        assertEquals(1, preferences.memory.size());
        assertFalse(preferences.getString("connected_v2", "").contains("fake-old-token"));
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(preferences.reboot(), cipher);
        assertEquals("old-page (old-page)", reloaded.pageSummary());
        assertEquals("fake-old-token", reloaded.accessToken());
    }

    @Test public void invalidatingInstagramLeavesFacebookAndNewerInstagramSessionIntact() {
        store.saveConnected(connection("facebook-page", "test-facebook-token"));
        OAuthExchangeResponse instagram = new OAuthExchangeResponse("new-instagram-session", "INSTAGRAM",
                "17841459976957663", "PROFESSIONAL", "demo_user", "test-instagram-token", null,
                "bearer", null, "1234567890", java.util.Collections.singletonList("instagram_basic"), "v27.0");
        store.saveConnected(instagram);
        store.invalidateInstagram("stale-instagram-session");
        assertEquals("demo_user", store.instagramConnection().displayName);
        store.invalidateInstagram(instagram.sessionId);
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(preferences.reboot(), cipher);
        assertNull(reloaded.instagramConnection());
        assertEquals("test-facebook-token", reloaded.accessToken());
    }

    @Test public void disconnectedSnapshotDoesNotMatchAfterAnInterveningReconnect() {
        EncryptedOAuthStore.InstagramSession original = store.instagramSession();
        store.saveConnected(new OAuthExchangeResponse("instagram-session", "INSTAGRAM", "9876543210",
                "PROFESSIONAL", "demo_user", "test-instagram-token", null, "bearer", null, "1234567890",
                java.util.Collections.singletonList("instagram_basic"), "v27.0"));
        EncryptedOAuthStore.InstagramSession connected = store.instagramSession();
        assertTrue(store.isCurrentInstagramSession(connected));
        store.invalidateInstagram(connected.sessionId);

        assertNull(store.instagramConnection());
        assertFalse(store.isCurrentInstagramSession(original));
        assertFalse(store.isCurrentInstagramSession(connected));
        assertTrue(store.isCurrentInstagramSession(store.instagramSession()));
    }

    @Test public void reconnectEncryptionFailurePreservesWholeOldRecord() {
        store.saveConnected(connection("old-page", "fake-old-token"));
        cipher.failEncryption = true;
        assertThrows(IllegalStateException.class, () -> store.saveConnected(connection("new-page", "fake-new-token")));
        assertEquals(1, preferences.commits);
        assertEquals("old-page (old-page)", store.pageSummary());
        assertEquals("fake-old-token", store.accessToken());
    }

    @Test public void failedCommitCannotExposeNewPageWithOldTokenEvenIfMemoryChanged() {
        store.saveConnected(connection("old-page", "fake-old-token"));
        preferences.failCommits = 2; // Failed save, then failed cleanup; both still modify memory.
        assertThrows(IllegalStateException.class, () -> store.saveConnected(connection("new-page", "fake-new-token")));
        assertNull(store.pageSummary());
        assertNull(store.accessToken());
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(preferences.reboot(), cipher);
        assertEquals("old-page (old-page)", reloaded.pageSummary());
        assertEquals("fake-old-token", reloaded.accessToken());
        store.saveConnected(connection("new-page", "fake-new-token"));
        assertEquals("new-page (new-page)", store.pageSummary());
        assertEquals("fake-new-token", store.accessToken());
    }

    @Test public void pendingSessionAndVerifierStayTogetherOnEncryptionFailure() {
        store.savePending("old-session", "a".repeat(43));
        cipher.failEncryption = true;
        assertThrows(IllegalStateException.class, () -> store.savePending("new-session", "b".repeat(43)));
        assertEquals("old-session", store.getPending().sessionId);
        assertEquals("a".repeat(43), store.getPending().verifier);
        assertEquals(1, preferences.commits);
    }

    @Test public void pendingWriteFailureAndClearCannotLeaveHalfAPair() {
        store.savePending("old-session", "a".repeat(43));
        preferences.failCommits = 1;
        assertThrows(IllegalStateException.class, () -> store.savePending("new-session", "b".repeat(43)));
        assertNull(store.getPending());
        assertNull(new EncryptedOAuthStore(preferences.reboot(), cipher).getPending());
        store.savePending("valid-session", "c".repeat(43));
        store.clearPending();
        assertNull(store.getPending());
        assertNull(new EncryptedOAuthStore(preferences.reboot(), cipher).getPending());
    }

    @Test public void corruptedCiphertextInvalidatesWholeConnectionButKeepsPending() {
        store.saveConnected(connection("old-page", "fake-old-token"));
        store.savePending("pending-session", "a".repeat(43));
        preferences.edit().putString("connected_v2", "corrupted").commit();
        assertNull(store.pageSummary());
        assertNull(store.accessToken());
        assertFalse(preferences.contains("connected_v2"));
        assertEquals("pending-session", store.getPending().sessionId);
    }

    @Test public void rejectsUnknownRecordVersion() throws Exception {
        preferences.edit().putString("pending_v2", cipher.encrypt("pending_v2",
                "{\"version\":3,\"sessionId\":\"fake\",\"verifier\":\"" + "a".repeat(43) + "\"}")).commit();
        assertNull(store.getPending());
        assertFalse(preferences.contains("pending_v2"));
    }

    @Test public void rejectsIncompleteConnectionInsteadOfDisplayingMetadata() throws Exception {
        preferences.edit().putString("connected_v2", cipher.encrypt("connected_v2",
                "{\"version\":2,\"connection\":{\"externalAccountId\":\"page\",\"displayName\":\"Page\"}}")).commit();
        assertNull(store.pageSummary());
        assertNull(store.accessToken());
    }

    @Test public void legacyFieldsRequireReconnectAndDoNotDeleteUnrelatedData() {
        preferences.edit().putString("page_id", "new-page").putString("page_name", "new-page")
                .putString("access_token", "fake-old-token").putString("pending_session", "new-session")
                .putString("pending_verifier", "old-verifier").putString("unrelated", "keep").commit();
        assertNull(store.pageSummary());
        assertNull(store.accessToken());
        assertNull(store.getPending());
        assertEquals(Map.of("unrelated", "keep"), preferences.memory);
        store.saveConnected(connection("valid-page", "fake-valid-token"));
        assertEquals("valid-page (valid-page)", store.pageSummary());
    }

    @Test public void swappingRecordCiphertextsFailsAuthentication() {
        store.saveConnected(connection("old-page", "fake-old-token"));
        store.savePending("pending-session", "a".repeat(43));
        preferences.edit().putString("pending_v2", preferences.getString("connected_v2", null)).commit();
        assertNull(store.getPending());
        assertEquals("fake-old-token", store.accessToken());
    }
}
