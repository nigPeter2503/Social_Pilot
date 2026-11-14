package com.socialpilot.app.data.security;

import android.content.SharedPreferences;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncryptedOAuthStoreTest {
    private MemoryPreferences preferences;
    private TestCipher cipher;
    private EncryptedOAuthStore store;

    @Before public void setUp() {
        preferences = new MemoryPreferences();
        cipher = new TestCipher();
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
        assertFalse(preferences.getString("connected_v3_FACEBOOK", "").contains("fake-old-token"));
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(preferences.reboot(), cipher);
        assertEquals("old-page (old-page)", reloaded.pageSummary());
        assertEquals("fake-old-token", reloaded.accessToken());
    }

    @Test public void reconnectEncryptionFailurePreservesWholeOldRecord() {
        store.saveConnected(connection("old-page", "fake-old-token"));
        cipher.failEncryption = true;
        assertThrows(IllegalStateException.class, () -> store.saveConnected(connection("new-page", "fake-new-token"),true));
        assertEquals(1, preferences.commits);
        assertEquals("old-page (old-page)", store.pageSummary());
        assertEquals("fake-old-token", store.accessToken());
    }

    @Test public void failedCommitCannotExposeNewPageWithOldTokenEvenIfMemoryChanged() {
        store.saveConnected(connection("old-page", "fake-old-token"));
        preferences.failCommits = 2; // Failed save, then failed cleanup; both still modify memory.
        assertThrows(IllegalStateException.class, () -> store.saveConnected(connection("new-page", "fake-new-token"),true));
        assertNull(store.pageSummary());
        assertNull(store.accessToken());
        EncryptedOAuthStore reloaded = new EncryptedOAuthStore(preferences.reboot(), cipher);
        assertEquals("old-page (old-page)", reloaded.pageSummary());
        assertEquals("fake-old-token", reloaded.accessToken());
        store.saveConnected(connection("new-page", "fake-new-token"),true);
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
        preferences.edit().putString("connected_v3_FACEBOOK", "corrupted").commit();
        assertNull(store.pageSummary());
        assertNull(store.accessToken());
        assertFalse(preferences.contains("connected_v3_FACEBOOK"));
        assertEquals("pending-session", store.getPending().sessionId);
    }

    @Test public void rejectsUnknownRecordVersion() throws Exception {
        preferences.edit().putString("pending_v3_FACEBOOK", cipher.encrypt("pending_v3_FACEBOOK",
                "{\"version\":99,\"sessionId\":\"fake\",\"verifier\":\"" + "a".repeat(43) + "\"}")).commit();
        assertNull(store.getPending());
        assertFalse(preferences.contains("pending_v3_FACEBOOK"));
    }

    @Test public void rejectsIncompleteConnectionInsteadOfDisplayingMetadata() throws Exception {
        preferences.edit().putString("connected_v3_FACEBOOK", cipher.encrypt("connected_v3_FACEBOOK",
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
        preferences.edit().putString("pending_v3_FACEBOOK", preferences.getString("connected_v3_FACEBOOK", null)).commit();
        assertNull(store.getPending());
        assertEquals("fake-old-token", store.accessToken());
    }

    private OAuthExchangeResponse linkedin(String id) {
        return new OAuthExchangeResponse("li-session","LINKEDIN",id,"PERSON","Member","fake-li-token",null,"Bearer","2099-01-01T00:00:00Z");
    }
    @Test public void twoProvidersSurviveReloadAndPendingClearIsIsolated() {
        store.saveConnected(connection("page","fake-fb-token"));store.saveConnected(linkedin("member"));
        store.savePending("FACEBOOK","fb-session","a".repeat(43),null);
        store.savePending("LINKEDIN","li-session","b".repeat(43),"2099-01-01T00:00:00Z");
        store.clearPending("LINKEDIN");
        var reloaded=new EncryptedOAuthStore(preferences.reboot(),cipher);
        assertEquals("fake-fb-token",reloaded.accessToken("FACEBOOK"));assertEquals("fake-li-token",reloaded.accessToken("LINKEDIN"));
        assertNotNull(reloaded.getPending("FACEBOOK"));assertNull(reloaded.getPending("LINKEDIN"));
    }
    @Test public void migratesV2FacebookWithoutTouchingLinkedin() throws Exception {
        store.saveConnected(linkedin("member"));
        String old="{\"version\":2,\"connection\":"+new com.google.gson.Gson().toJson(connection("page","fake-fb-token"))+"}";
        preferences.edit().putString("connected_v2",cipher.encrypt("connected_v2",old)).commit();
        assertEquals("fake-fb-token",store.accessToken("FACEBOOK"));assertFalse(preferences.contains("connected_v2"));
        var reload=new EncryptedOAuthStore(preferences.reboot(),cipher);
        assertEquals("fake-fb-token",reload.accessToken("FACEBOOK"));assertEquals("fake-li-token",reload.accessToken("LINKEDIN"));
        assertEquals(1,reload.channel("FACEBOOK").connectionRevision);
    }
    @Test public void failedMigrationDoesNotReportConnectedAndCanRetryAfterRestart() throws Exception {
        String old="{\"version\":2,\"connection\":"+new com.google.gson.Gson().toJson(connection("page","fake-fb-token"))+"}";
        preferences.edit().putString("connected_v2",cipher.encrypt("connected_v2",old)).commit();
        cipher.failEncryption=true;assertNull(store.accessToken("FACEBOOK"));assertTrue(preferences.contains("connected_v2"));
        cipher.failEncryption=false;assertEquals("fake-fb-token",new EncryptedOAuthStore(preferences.reboot(),cipher).accessToken());
    }
    @Test public void failedMigrationCommitDoesNotDeleteDurableV2Source() throws Exception {
        String old="{\"version\":2,\"connection\":"+new com.google.gson.Gson().toJson(connection("page","fake-fb-token"))+"}";
        preferences.edit().putString("connected_v2",cipher.encrypt("connected_v2",old)).commit();
        preferences.failCommits=1;
        assertNull(store.accessToken("FACEBOOK"));
        assertTrue(preferences.reboot().contains("connected_v2"));
        assertEquals("fake-fb-token",new EncryptedOAuthStore(preferences.reboot(),cipher).accessToken());
    }
    @Test public void cannotSwapProviderCiphertexts() {
        store.saveConnected(connection("page","fake-fb-token"));store.saveConnected(linkedin("member"));
        preferences.edit().putString("connected_v3_LINKEDIN",preferences.getString("connected_v3_FACEBOOK",null)).commit();
        assertNull(store.accessToken("LINKEDIN"));assertEquals("fake-fb-token",store.accessToken());
    }
    @Test public void reconnectRevisionPersistsAndAccountReplacementIsRejected() {
        store.saveConnected(linkedin("member"));store.saveConnected(linkedin("member"));
        var reload=new EncryptedOAuthStore(preferences.reboot(),cipher);assertEquals(2,reload.channel("LINKEDIN").connectionRevision);
        assertNull(reload.tokenFor("LINKEDIN",reload.channel("LINKEDIN").channelId,1));
        assertEquals("fake-li-token",reload.tokenFor("LINKEDIN",reload.channel("LINKEDIN").channelId,2));
        assertThrows(IllegalStateException.class,()->reload.saveConnected(linkedin("other")));
        assertEquals("member",reload.channel("LINKEDIN").externalAccountId);
        reload.requireReauth("LINKEDIN",1);assertEquals(com.socialpilot.app.domain.contract.social.ConnectionStatus.CONNECTED,reload.channel("LINKEDIN").connectionStatus);
        reload.requireReauth("LINKEDIN",2);assertNull(reload.tokenFor("LINKEDIN",reload.channel("LINKEDIN").channelId,2));assertEquals(com.socialpilot.app.domain.contract.social.ConnectionStatus.REAUTH_REQUIRED,reload.channel("LINKEDIN").connectionStatus);
    }

    private static final class TestCipher implements EncryptedOAuthStore.RecordCipher {
        boolean failEncryption;
        private final SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");
        @Override public String encrypt(String name, String plaintext) throws Exception {
            if (failEncryption) throw new IllegalStateException("Injected encryption failure");
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(iv).put(encrypted).array());
        }
        @Override public String decrypt(String name, String ciphertext) throws Exception {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(ciphertext));
            byte[] iv = new byte[12]; buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        }
    }

    /** Models Android's memory-first commit, including failure without rollback. */
    private static final class MemoryPreferences implements SharedPreferences {
        final Map<String, String> memory = new HashMap<>();
        final Map<String, String> disk = new HashMap<>();
        int commits, failCommits;
        MemoryPreferences reboot() {
            MemoryPreferences result = new MemoryPreferences();
            result.memory.putAll(disk); result.disk.putAll(disk); return result;
        }
        @Override public Map<String, ?> getAll() { return new HashMap<>(memory); }
        @Override public String getString(String key, String fallback) { return memory.getOrDefault(key, fallback); }
        @Override public boolean contains(String key) { return memory.containsKey(key); }
        @Override public Editor edit() { return new MemoryEditor(); }
        @Override public Set<String> getStringSet(String key, Set<String> fallback) { throw new UnsupportedOperationException(); }
        @Override public int getInt(String key, int fallback) { throw new UnsupportedOperationException(); }
        @Override public long getLong(String key, long fallback) { throw new UnsupportedOperationException(); }
        @Override public float getFloat(String key, float fallback) { throw new UnsupportedOperationException(); }
        @Override public boolean getBoolean(String key, boolean fallback) { throw new UnsupportedOperationException(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        private final class MemoryEditor implements Editor {
            final Map<String, String> changes = new HashMap<>();
            boolean clear;
            @Override public Editor putString(String key, String value) { changes.put(key, value); return this; }
            @Override public Editor remove(String key) { changes.put(key, null); return this; }
            @Override public Editor clear() { clear = true; return this; }
            @Override public boolean commit() {
                commits++;
                if (clear) memory.clear();
                changes.forEach((key, value) -> { if (value == null) memory.remove(key); else memory.put(key, value); });
                if (failCommits > 0) { failCommits--; return false; }
                disk.clear(); disk.putAll(memory); return true;
            }
            @Override public void apply() { commit(); }
            @Override public Editor putStringSet(String key, Set<String> values) { throw new UnsupportedOperationException(); }
            @Override public Editor putInt(String key, int value) { throw new UnsupportedOperationException(); }
            @Override public Editor putLong(String key, long value) { throw new UnsupportedOperationException(); }
            @Override public Editor putFloat(String key, float value) { throw new UnsupportedOperationException(); }
            @Override public Editor putBoolean(String key, boolean value) { throw new UnsupportedOperationException(); }
        }
    }
}
