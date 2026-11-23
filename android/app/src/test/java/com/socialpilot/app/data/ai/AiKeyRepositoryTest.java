package com.socialpilot.app.data.ai;

import static com.socialpilot.app.data.ai.AiKeyRepository.KeyState.*;
import static com.socialpilot.app.domain.contract.ai.AiRequest.Provider.*;
import static org.junit.Assert.*;

import com.socialpilot.app.data.ai.AiKeyRepository.CheckResult;
import com.socialpilot.app.data.ai.AiKeyRepository.KeyStatus;
import com.socialpilot.app.data.ai.ByokKeyVerifier.Outcome;
import com.socialpilot.app.domain.contract.ai.AiRequest.Provider;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.secret.SecretStoreException;
import com.socialpilot.app.testing.MemoryPreferences;
import com.socialpilot.app.testing.MemorySecretStore;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class AiKeyRepositoryTest {
    private static final String KEY_A = "fake-openai-key-aaaaaaaa1234";
    private static final String KEY_B = "fake-openai-key-bbbbbbbb5678";
    private static final String GEMINI_KEY = "fake-gemini-key-cccccccc9999";
    private MockWebServer server;
    private MemorySecretStore secrets;
    private MemoryPreferences statusFile;
    private boolean online = true;
    private long now = 1_763_600_000_000L;
    private AiKeyRepository repository;

    @Before public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        secrets = new MemorySecretStore(SecretNamespace.AI_PROVIDER);
        statusFile = new MemoryPreferences();
        repository = repository(statusFile);
    }

    @After public void tearDown() throws Exception {
        server.shutdown();
    }

    private AiKeyRepository repository(MemoryPreferences file) {
        Map<Provider, HttpUrl> endpoints = new EnumMap<>(Provider.class);
        endpoints.put(OPENAI, server.url("/v1/models"));
        endpoints.put(GEMINI, server.url("/v1beta/models"));
        return new AiKeyRepository(secrets, file, new ByokKeyVerifier(endpoints, 5_000), () -> online, () -> now);
    }

    @Test public void saveStoresUnverifiedMaskedKeyWithoutNetwork() throws Exception {
        KeyStatus status = repository.save(OPENAI, "  " + KEY_A + "\n");
        assertEquals(UNVERIFIED, status.state);
        assertEquals("••••1234", status.maskedHint);
        assertFalse(status.usable());
        assertEquals(0, server.getRequestCount());
        assertEquals(KEY_A, repository.credentialSource(OPENAI).load());
        for (String value : statusFile.disk.values()) assertFalse(value.contains(KEY_A));
    }

    @Test public void rejectsMalformedKeysBeforeStoring() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(OPENAI, "short"));
        assertThrows(IllegalArgumentException.class, () -> repository.save(OPENAI, "fake key with spaces 000000"));
        assertThrows(IllegalArgumentException.class, () -> repository.save(OPENAI, "fake-key-ü-00000000000000"));
        assertEquals(NOT_SET, repository.status(OPENAI).state);
    }

    @Test public void validCheckIsPersistedAndSurvivesRestart() throws Exception {
        repository.save(OPENAI, KEY_A);
        server.enqueue(json(200, "{\"data\":[]}"));
        CheckResult result = repository.prepareVerification(OPENAI).run();
        assertEquals(Outcome.VALID, result.outcome);
        assertEquals(VALID, result.status.state);
        assertEquals(Long.valueOf(now), result.status.checkedAtMillis);
        KeyStatus afterRestart = repository(statusFile.reboot()).status(OPENAI);
        assertEquals(VALID, afterRestart.state);
        assertTrue(afterRestart.usable());
    }

    @Test public void wrongRevokedAndPermissionOutcomesAreDistinctStates() throws Exception {
        repository.save(OPENAI, KEY_A);
        server.enqueue(json(401, "{}"));
        assertEquals(INVALID, repository.prepareVerification(OPENAI).run().status.state);
        server.enqueue(json(403, "{}"));
        assertEquals(PERMISSION_DENIED, repository.prepareVerification(OPENAI).run().status.state);
    }

    @Test public void networkErrorsKeepThePreviousDefiniteStateAndAreNotWrongKey() throws Exception {
        repository.save(OPENAI, KEY_A);
        server.enqueue(json(200, "{\"data\":[]}"));
        repository.prepareVerification(OPENAI).run();
        server.enqueue(json(503, "{}"));
        CheckResult unavailable = repository.prepareVerification(OPENAI).run();
        assertEquals(Outcome.PROVIDER_UNAVAILABLE, unavailable.outcome);
        assertEquals(VALID, unavailable.status.state);
        server.enqueue(json(429, "{}"));
        assertEquals(VALID, repository.prepareVerification(OPENAI).run().status.state);
    }

    @Test public void offlineCheckMakesNoRequestAndKeepsKey() throws Exception {
        repository.save(GEMINI, GEMINI_KEY);
        online = false;
        CheckResult result = repository.prepareVerification(GEMINI).run();
        assertEquals(Outcome.OFFLINE, result.outcome);
        assertEquals(UNVERIFIED, result.status.state);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void checkWithoutKeyOrWithUnreadableKeyMakesNoRequest() {
        assertEquals(Outcome.NO_KEY, repository.prepareVerification(OPENAI).run().outcome);
        secrets.unreadable.add(AiKeyRepository.reference(OPENAI));
        CheckResult result = repository.prepareVerification(OPENAI).run();
        assertEquals(Outcome.KEY_UNREADABLE, result.outcome);
        assertEquals(UNREADABLE, result.status.state);
        assertThrows(IllegalStateException.class, () -> repository.credentialSource(OPENAI).load());
        assertEquals(0, server.getRequestCount());
    }

    @Test public void staleCheckCannotMarkAReplacementKeyValid() throws Exception {
        repository.save(OPENAI, KEY_A);
        server.enqueue(json(200, "{\"data\":[]}").setHeadersDelay(500, TimeUnit.MILLISECONDS));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<CheckResult> oldCheck = worker.submit(() -> repository.prepareVerification(OPENAI).run());
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
            repository.save(OPENAI, KEY_B); // User enters a new key while the old check runs.
            CheckResult result = oldCheck.get(3, TimeUnit.SECONDS);
            assertEquals(Outcome.SUPERSEDED, result.outcome);
            assertEquals(UNVERIFIED, repository.status(OPENAI).state);
            assertEquals("••••5678", repository.status(OPENAI).maskedHint);
        } finally {
            worker.shutdownNow();
        }
    }

    @Test public void resultOfAnOldKeyRevisionIsIgnoredAfterReplacement() throws Exception {
        repository.save(OPENAI, KEY_A);
        server.enqueue(json(200, "{\"data\":[]}"));
        repository.prepareVerification(OPENAI).run();
        assertEquals(VALID, repository.status(OPENAI).state);
        repository.save(OPENAI, KEY_B);
        assertEquals(UNVERIFIED, repository.status(OPENAI).state);
    }

    @Test public void cancelledCheckDoesNotChangeState() throws Exception {
        repository.save(OPENAI, KEY_A);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        AiKeyRepository.Verification verification = repository.prepareVerification(OPENAI);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<CheckResult> running = worker.submit(verification::run);
            server.takeRequest(2, TimeUnit.SECONDS);
            verification.cancel();
            CheckResult result = running.get(2, TimeUnit.SECONDS);
            assertEquals(Outcome.CANCELLED, result.outcome);
            assertEquals(UNVERIFIED, result.status.state);
        } finally {
            worker.shutdownNow();
        }
    }

    @Test public void deleteRemovesOnlyThisProviderAndNoSocialToken() throws Exception {
        MemorySecretStore social = new MemorySecretStore(SecretNamespace.SOCIAL_TOKEN);
        SecretRef token = new SecretRef(SecretNamespace.SOCIAL_TOKEN, "channel-1");
        social.write(token, "fake-social-token");
        repository.save(OPENAI, KEY_A);
        repository.save(GEMINI, GEMINI_KEY);
        server.enqueue(json(200, "{\"models\":[]}"));
        repository.prepareVerification(GEMINI).run();
        assertEquals(NOT_SET, repository.delete(OPENAI).state);
        assertNull(repository.credentialSource(OPENAI).load());
        assertEquals(VALID, repository.status(GEMINI).state);
        assertEquals(GEMINI_KEY, repository.credentialSource(GEMINI).load());
        assertEquals("fake-social-token", social.read(token).value);
        assertFalse(statusFile.memory.containsKey("status.OPENAI"));
    }

    @Test public void storageFailureKeepsPreviousKey() throws Exception {
        repository.save(OPENAI, KEY_A);
        secrets.failWrites = true;
        assertThrows(SecretStoreException.class, () -> repository.save(OPENAI, KEY_B));
        assertEquals(KEY_A, repository.credentialSource(OPENAI).load());
        secrets.failDeletes = true;
        assertThrows(SecretStoreException.class, () -> repository.delete(OPENAI));
        assertEquals(KEY_A, repository.credentialSource(OPENAI).load());
    }

    @Test public void damagedStatusRecordFallsBackToUnverified() throws Exception {
        repository.save(OPENAI, KEY_A);
        statusFile.edit().putString("status.OPENAI", "{not json").commit();
        assertEquals(UNVERIFIED, repository.status(OPENAI).state);
    }

    @Test public void repositoryRefusesSocialNamespace() {
        assertThrows(IllegalArgumentException.class, () -> new AiKeyRepository(
                new MemorySecretStore(SecretNamespace.SOCIAL_TOKEN), statusFile,
                new ByokKeyVerifier(), () -> true, () -> 0L));
    }

    @Test public void maskNeverRevealsShortKeys() {
        assertEquals("••••", AiKeyRepository.mask("abc"));
        assertEquals("••••wxyz", AiKeyRepository.mask("abcdefghijklmnopqrstuvwxyz"));
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body);
    }
}
