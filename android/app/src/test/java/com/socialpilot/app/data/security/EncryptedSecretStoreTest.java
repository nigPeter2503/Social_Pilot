package com.socialpilot.app.data.security;

import static org.junit.Assert.*;

import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.secret.SecretStoreException;
import com.socialpilot.app.testing.MemoryPreferences;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class EncryptedSecretStoreTest {
    private static final String FAKE_KEY = "fake-key-openai-000000000001";
    private static final SecretRef OPENAI = new SecretRef(SecretNamespace.AI_PROVIDER, "llm.openai");
    private static final SecretRef GEMINI = new SecretRef(SecretNamespace.AI_PROVIDER, "llm.gemini");
    private MemoryPreferences preferences;
    private TestRecordCipher cipher;
    private EncryptedSecretStore store;

    @Before public void setUp() {
        preferences = new MemoryPreferences();
        cipher = new TestRecordCipher();
        store = new EncryptedSecretStore(SecretNamespace.AI_PROVIDER, preferences, cipher);
    }

    @Test public void writeIsOneEncryptedCommitAndSurvivesRestart() throws Exception {
        String revision = store.write(OPENAI, FAKE_KEY);
        assertEquals(1, preferences.commits);
        assertEquals(1, preferences.disk.size());
        for (String stored : preferences.disk.values()) assertFalse(stored.contains(FAKE_KEY));
        for (String name : preferences.disk.keySet()) assertFalse(name.contains(FAKE_KEY));
        SecretRead read = new EncryptedSecretStore(SecretNamespace.AI_PROVIDER, preferences.reboot(), cipher).read(OPENAI);
        assertEquals(SecretRead.Status.SUCCESS, read.status);
        assertEquals(FAKE_KEY, read.value);
        assertEquals(revision, read.revision);
        assertFalse(read.toString().contains(FAKE_KEY));
    }

    @Test public void concurrentFirstWritesAcrossInstancesKeepBothRecordsReadable() throws Exception {
        FirstWriteCipher firstWriteCipher = new FirstWriteCipher();
        EncryptedSecretStore openAiStore = new EncryptedSecretStore(SecretNamespace.AI_PROVIDER, preferences, firstWriteCipher);
        EncryptedSecretStore geminiStore = new EncryptedSecretStore(SecretNamespace.AI_PROVIDER, preferences, firstWriteCipher);
        String geminiKey = "fake-key-gemini-0000000000";
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> openAi = workers.submit(() -> openAiStore.write(OPENAI, FAKE_KEY));
            assertTrue(firstWriteCipher.firstLookup.await(5, TimeUnit.SECONDS));
            Future<String> gemini = workers.submit(() -> geminiStore.write(GEMINI, geminiKey));
            String openAiRevision = openAi.get(5, TimeUnit.SECONDS);
            String geminiRevision = gemini.get(5, TimeUnit.SECONDS);

            EncryptedSecretStore reopened = new EncryptedSecretStore(
                    SecretNamespace.AI_PROVIDER, preferences.reboot(), firstWriteCipher);
            assertEquals(FAKE_KEY, reopened.read(OPENAI).value);
            assertEquals(openAiRevision, reopened.read(OPENAI).revision);
            assertEquals(geminiKey, reopened.read(GEMINI).value);
            assertEquals(geminiRevision, reopened.read(GEMINI).revision);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test public void everyWriteGetsANewRevision() throws Exception {
        String first = store.write(OPENAI, FAKE_KEY);
        String second = store.write(OPENAI, FAKE_KEY);
        assertNotEquals(first, second);
        assertEquals(second, store.read(OPENAI).revision);
    }

    @Test public void missingReferenceIsMissingNotUnreadable() {
        assertEquals(SecretRead.Status.MISSING, store.read(OPENAI).status);
    }

    @Test public void encryptionFailureKeepsPreviousKeyUntouched() throws Exception {
        String revision = store.write(OPENAI, FAKE_KEY);
        cipher.failEncryption = true;
        SecretStoreException failure = assertThrows(SecretStoreException.class, () -> store.write(OPENAI, "fake-key-new-0000000000000"));
        assertEquals(SecretStoreException.Reason.ENCRYPTION_FAILED, failure.reason);
        assertEquals(1, preferences.commits);
        assertEquals(FAKE_KEY, store.read(OPENAI).value);
        assertEquals(revision, store.read(OPENAI).revision);
    }

    @Test public void failedCommitNeverServesUnconfirmedValueAndRestartKeepsDurableOne() throws Exception {
        store.write(OPENAI, FAKE_KEY);
        preferences.failCommits = 1; // Memory changes, disk does not.
        SecretStoreException failure = assertThrows(SecretStoreException.class, () -> store.write(OPENAI, "fake-key-new-0000000000000"));
        assertEquals(SecretStoreException.Reason.WRITE_FAILED, failure.reason);
        assertEquals(SecretRead.Status.UNREADABLE, store.read(OPENAI).status);
        assertEquals(FAKE_KEY, new EncryptedSecretStore(SecretNamespace.AI_PROVIDER, preferences.reboot(), cipher).read(OPENAI).value);
        store.write(OPENAI, "fake-key-new-0000000000000");
        assertEquals("fake-key-new-0000000000000", store.read(OPENAI).value);
    }

    @Test public void failedOpenAiReplacementCannotBeFlushedByAGeminiWrite() throws Exception {
        String original = store.write(OPENAI, FAKE_KEY);
        preferences.failCommits = 1;
        assertThrows(SecretStoreException.class, () -> store.write(OPENAI, "fake-key-new-0000000000000"));
        store.write(GEMINI, "fake-key-gemini-0000000000");

        EncryptedSecretStore restarted = new EncryptedSecretStore(SecretNamespace.AI_PROVIDER, preferences.reboot(), cipher);
        assertEquals(FAKE_KEY, restarted.read(OPENAI).value);
        assertEquals(original, restarted.read(OPENAI).revision);
        assertEquals(FAKE_KEY, store.read(OPENAI).value);
    }

    @Test public void failedWriteAndDeleteCannotLeakIntoAnotherRecordsCommit() throws Exception {
        String revision = store.write(OPENAI, FAKE_KEY);
        preferences.failCommits = 2;
        assertThrows(SecretStoreException.class, () -> store.write(OPENAI, "fake-key-new-0000000000000"));
        assertThrows(SecretStoreException.class, () -> store.delete(OPENAI));

        EncryptedSecretStore second = new EncryptedSecretStore(SecretNamespace.AI_PROVIDER, preferences, cipher);
        second.write(GEMINI, "fake-key-gemini-0000000000");
        EncryptedSecretStore restarted = new EncryptedSecretStore(SecretNamespace.AI_PROVIDER, preferences.reboot(), cipher);
        assertEquals(FAKE_KEY, restarted.read(OPENAI).value);
        assertEquals(revision, restarted.read(OPENAI).revision);
        assertEquals("fake-key-gemini-0000000000", restarted.read(GEMINI).value);
    }

    @Test public void lostKeystoreKeyIsUnreadableWithoutCrashAndCanBeReplaced() throws Exception {
        store.write(OPENAI, FAKE_KEY);
        EncryptedSecretStore afterKeyLoss = new EncryptedSecretStore(SecretNamespace.AI_PROVIDER,
                preferences.reboot(), new TestRecordCipher((byte) 7));
        assertEquals(SecretRead.Status.UNREADABLE, afterKeyLoss.read(OPENAI).status);
        afterKeyLoss.write(OPENAI, "fake-key-again-00000000000");
        assertEquals("fake-key-again-00000000000", afterKeyLoss.read(OPENAI).value);
    }

    @Test public void keystoreFailureOnReadIsUnreadable() throws Exception {
        store.write(OPENAI, FAKE_KEY);
        cipher.failDecryption = true;
        assertEquals(SecretRead.Status.UNREADABLE, store.read(OPENAI).status);
    }

    @Test public void corruptedOrUnknownVersionRecordIsUnreadable() throws Exception {
        preferences.edit().putString("secret.llm.openai", "not-a-ciphertext").commit();
        assertEquals(SecretRead.Status.UNREADABLE, store.read(OPENAI).status);
        preferences.edit().putString("secret.llm.openai", cipher.encrypt("ai/secret.llm.openai",
                "{\"version\":2,\"ref\":\"secret://ai/llm.openai\",\"secret\":\"x\",\"revision\":\"r\"}")).commit();
        assertEquals(SecretRead.Status.UNREADABLE, store.read(OPENAI).status);
    }

    @Test public void swappedCiphertextFailsAuthentication() throws Exception {
        store.write(OPENAI, FAKE_KEY);
        store.write(GEMINI, "fake-key-gemini-0000000000");
        preferences.edit().putString("secret.llm.gemini", preferences.getString("secret.llm.openai", null)).commit();
        assertEquals(SecretRead.Status.UNREADABLE, store.read(GEMINI).status);
        assertEquals(FAKE_KEY, store.read(OPENAI).value);
    }

    @Test public void deleteRemovesOnlyThatReference() throws Exception {
        store.write(OPENAI, FAKE_KEY);
        store.write(GEMINI, "fake-key-gemini-0000000000");
        store.delete(OPENAI);
        assertEquals(SecretRead.Status.MISSING, store.read(OPENAI).status);
        assertEquals("fake-key-gemini-0000000000", store.read(GEMINI).value);
        store.delete(OPENAI); // Idempotent.
    }

    @Test public void namespacesAreIsolatedFilesAndRejectForeignReferences() throws Exception {
        MemoryPreferences socialFile = new MemoryPreferences();
        EncryptedSecretStore social = new EncryptedSecretStore(SecretNamespace.SOCIAL_TOKEN, socialFile, cipher);
        SecretRef token = new SecretRef(SecretNamespace.SOCIAL_TOKEN, "channel-1");
        social.write(token, "fake-social-token");
        store.write(OPENAI, FAKE_KEY);
        assertThrows(IllegalArgumentException.class, () -> store.read(token));
        assertThrows(IllegalArgumentException.class, () -> store.delete(token));
        assertThrows(IllegalArgumentException.class, () -> social.write(OPENAI, FAKE_KEY));
        store.delete(OPENAI);
        assertEquals("fake-social-token", social.read(token).value);
        // Same record name in another namespace still fails: the namespace is associated data.
        preferences.edit().putString("secret.channel-1", socialFile.getString("secret.channel-1", null)).commit();
        assertEquals(SecretRead.Status.UNREADABLE,
                store.read(new SecretRef(SecretNamespace.AI_PROVIDER, "channel-1")).status);
    }

    /** Models alias replacement if two callers observe the same missing Keystore alias. */
    private static final class FirstWriteCipher implements RecordCipher {
        final CountDownLatch firstLookup = new CountDownLatch(1);
        private final CountDownLatch secondLookup = new CountDownLatch(1);
        private final CountDownLatch firstGenerated = new CountDownLatch(1);
        private volatile byte alias;

        @Override public String encrypt(String name, String plaintext) throws Exception {
            byte key = alias;
            if (key == 0) {
                if (name.endsWith(OPENAI.id)) {
                    firstLookup.countDown();
                    // This wait expires when the second writer is correctly blocked outside the cipher.
                    secondLookup.await(1, TimeUnit.SECONDS);
                    key = 1;
                    alias = key;
                    firstGenerated.countDown();
                } else {
                    secondLookup.countDown();
                    assertTrue(firstGenerated.await(5, TimeUnit.SECONDS));
                    key = 2;
                    alias = key;
                }
            }
            return new TestRecordCipher(key).encrypt(name, plaintext);
        }

        @Override public String decrypt(String name, String ciphertext) throws Exception {
            return new TestRecordCipher(alias).decrypt(name, ciphertext);
        }
    }

    @Test public void referenceRoundTripsAsOpaqueText() {
        SecretRef ref = SecretRef.parse("secret://social/3f2c-ab_1.x");
        assertEquals(SecretNamespace.SOCIAL_TOKEN, ref.namespace);
        assertEquals("secret://social/3f2c-ab_1.x", ref.opaque());
        assertThrows(IllegalArgumentException.class, () -> SecretRef.parse("secret://other/x"));
        assertThrows(IllegalArgumentException.class, () -> SecretRef.parse("secret://ai/has space"));
        assertThrows(IllegalArgumentException.class, () -> SecretRef.parse("https://ai/x"));
    }
}
