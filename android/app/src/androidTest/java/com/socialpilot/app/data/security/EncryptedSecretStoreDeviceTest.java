package com.socialpilot.app.data.security;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real Android Keystore, isolated preference files and a test-only key alias. */
@RunWith(AndroidJUnit4.class)
public class EncryptedSecretStoreDeviceTest {
    private static final String ALIAS = "socialpilot.secret.sp027test.v1";
    private static final String FAKE_KEY = "fake-device-test-key-0000000001";
    private static final SecretRef OPENAI = new SecretRef(SecretNamespace.AI_PROVIDER, "llm.openai");
    private SharedPreferences aiFile;
    private SharedPreferences socialFile;

    @Before public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        aiFile = context.getSharedPreferences("sp027_test_secrets_ai", Context.MODE_PRIVATE);
        socialFile = context.getSharedPreferences("sp027_test_secrets_social", Context.MODE_PRIVATE);
        assertTrue(aiFile.edit().clear().commit());
        assertTrue(socialFile.edit().clear().commit());
        deleteAlias();
    }

    @After public void tearDown() throws Exception {
        aiFile.edit().clear().commit();
        socialFile.edit().clear().commit();
        deleteAlias();
    }

    @Test public void keystoreRoundTripSurvivesNewInstanceAndStoresNoPlaintext() throws Exception {
        EncryptedSecretStore store = store(SecretNamespace.AI_PROVIDER, aiFile);
        String revision = store.write(OPENAI, FAKE_KEY);
        SecretRead read = store(SecretNamespace.AI_PROVIDER, aiFile).read(OPENAI);
        assertEquals(SecretRead.Status.SUCCESS, read.status);
        assertEquals(FAKE_KEY, read.value);
        assertEquals(revision, read.revision);
        for (Object value : aiFile.getAll().values()) assertFalse(String.valueOf(value).contains(FAKE_KEY));
    }

    @Test public void concurrentFirstWritesShareOneKeystoreAlias() throws Exception {
        SecretRef gemini = new SecretRef(SecretNamespace.AI_PROVIDER, "llm.gemini");
        String geminiKey = "fake-device-test-key-0000000002";
        EncryptedSecretStore first = store(SecretNamespace.AI_PROVIDER, aiFile);
        EncryptedSecretStore second = store(SecretNamespace.AI_PROVIDER, aiFile);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> openAiWrite = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return first.write(OPENAI, FAKE_KEY);
            });
            Future<String> geminiWrite = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return second.write(gemini, geminiKey);
            });
            start.countDown();
            String firstRevision = openAiWrite.get(10, TimeUnit.SECONDS);
            String secondRevision = geminiWrite.get(10, TimeUnit.SECONDS);

            EncryptedSecretStore reopened = store(SecretNamespace.AI_PROVIDER, aiFile);
            SecretRead openAi = reopened.read(OPENAI);
            SecretRead geminiRead = reopened.read(gemini);
            assertEquals(FAKE_KEY, openAi.value);
            assertEquals(firstRevision, openAi.revision);
            assertEquals(geminiKey, geminiRead.value);
            assertEquals(secondRevision, geminiRead.revision);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test public void deletedKeystoreKeyGivesUnreadableThenRecoversOnWrite() throws Exception {
        EncryptedSecretStore store = store(SecretNamespace.AI_PROVIDER, aiFile);
        store.write(OPENAI, FAKE_KEY);
        deleteAlias();
        EncryptedSecretStore afterLoss = store(SecretNamespace.AI_PROVIDER, aiFile);
        assertEquals(SecretRead.Status.UNREADABLE, afterLoss.read(OPENAI).status);
        afterLoss.write(OPENAI, "fake-device-test-key-0000000002");
        assertEquals("fake-device-test-key-0000000002", afterLoss.read(OPENAI).value);
    }

    @Test public void socialNamespaceIsSeparate() throws Exception {
        EncryptedSecretStore ai = store(SecretNamespace.AI_PROVIDER, aiFile);
        EncryptedSecretStore social = store(SecretNamespace.SOCIAL_TOKEN, socialFile);
        SecretRef token = new SecretRef(SecretNamespace.SOCIAL_TOKEN, "channel-1");
        social.write(token, "fake-social-token");
        ai.write(OPENAI, FAKE_KEY);
        ai.delete(OPENAI);
        assertEquals("fake-social-token", social.read(token).value);
    }

    private static EncryptedSecretStore store(SecretNamespace namespace, SharedPreferences file) {
        return new EncryptedSecretStore(namespace, file, new KeystoreRecordCipher(ALIAS, true));
    }

    private static void deleteAlias() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS);
    }
}
