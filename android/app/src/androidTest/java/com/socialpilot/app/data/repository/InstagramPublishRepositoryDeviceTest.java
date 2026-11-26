package com.socialpilot.app.data.repository;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.core.FacebookOAuthCoordinator;
import com.socialpilot.app.data.remote.instagram.ControlledInstagramGraph;
import com.socialpilot.app.data.remote.instagram.InstagramGraphClient;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.editor.ContentVersion;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.socialpilot.app.data.repository.InstagramPublishRepository.Status.CHECK_REQUIRED;
import static com.socialpilot.app.data.repository.InstagramPublishRepository.Status.CANCELLED;
import static com.socialpilot.app.data.repository.InstagramPublishRepository.Status.PUBLISHED;
import static com.socialpilot.app.data.repository.InstagramPublishRepository.Status.PUBLISHED_ID_UNKNOWN;
import static com.socialpilot.app.data.repository.InstagramPublishRepository.Status.READY;
import static com.socialpilot.app.data.repository.InstagramPublishRepository.Status.RECONNECT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class InstagramPublishRepositoryDeviceTest {
    private static final String FACEBOOK_ID = "1234567890";
    private static final String INSTAGRAM_ID = "9876543210";
    private static final String FACEBOOK_TOKEN = "test-facebook-token";
    private Context isolatedContext;
    private EncryptedOAuthStore connections;

    @Before public void isolateCredentialsAndAttempts() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "sp065_device_test_" + UUID.randomUUID() + "_";
        isolatedContext = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }

            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + name, mode);
            }
        };
        connections = new EncryptedOAuthStore(isolatedContext);
        connections.saveConnected(new OAuthExchangeResponse("facebook-test-session", "FACEBOOK",
                FACEBOOK_ID, "PAGE", "Demo Page", FACEBOOK_TOKEN, null, "bearer", null));
    }

    @After public void clearIsolatedPreferences() {
        isolatedContext.getSharedPreferences("oauth_secure_v1", Context.MODE_PRIVATE).edit().clear().commit();
        isolatedContext.getSharedPreferences("instagram_feed_spike", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void expiredInstagramRequiresReconnectWithoutCallingGraph() {
        saveInstagram("expired-test-session", "expired-ig-token", "2000-01-01T00:00:00Z");
        InstagramPublishRepository repository = repository(ControlledInstagramGraph.withResponse(chain -> {
            throw new AssertionError("An expired connection must not make a Graph request");
        }));

        InstagramPublishRepository.Result result = reconcile(repository);
        assertEquals(RECONNECT, result.status);
        assertTrue(result.isCurrent(connections));
        assertNull(connections.instagramConnection());
        assertEquals(FACEBOOK_TOKEN, connections.accessToken());
    }

    @Test public void revokedInstagramAndNewSessionLeaveFacebookCredentialsUntouched() {
        saveInstagram("revoked-test-session", "revoked-ig-token", null);
        InstagramPublishRepository repository = repository(ControlledInstagramGraph.withResponse(chain -> {
            Request request = chain.request();
            assertEquals("GET", request.method());
            assertEquals("/v27.0/" + INSTAGRAM_ID, request.url().encodedPath());
            assertEquals("id", request.url().queryParameter("fields"));
            String authorization = request.header("Authorization");
            if ("Bearer revoked-ig-token".equals(authorization)) {
                return json(request, 400, "{\"error\":{\"code\":190}}");
            }
            assertEquals("Bearer new-ig-token", authorization);
            return json(request, 200, "{\"id\":\"" + INSTAGRAM_ID + "\"}");
        }));

        assertEquals(RECONNECT, reconcile(repository).status);
        assertNull(connections.instagramConnection());
        assertEquals(FACEBOOK_TOKEN, connections.accessToken());
        saveInstagram("new-test-session", "new-ig-token", null);
        assertEquals(READY, reconcile(repository).status);
        assertEquals(FACEBOOK_TOKEN, connections.accessToken());
    }

    @Test public void deniedCallbackKeepsPriorInstagramAndFacebookConnections() throws Exception {
        saveInstagram("prior-instagram-session", "prior-ig-token", null);
        connections.savePending("denied-instagram-session",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "INSTAGRAM");
        ExecutorService io = Executors.newSingleThreadExecutor();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> event = new AtomicReference<>();
        try {
            FacebookOAuthCoordinator coordinator = new FacebookOAuthCoordinator(
                    connections, io, "https://127.0.0.1/");
            Uri callback = Uri.parse("socialpilot://oauth/callback?sessionId=denied-instagram-session&error=ACCESS_DENIED");
            coordinator.handleCallback(callback, new FacebookOAuthCoordinator.Listener() {
                @Override public void onAuthorizationUrl(String url) { finish("UNEXPECTED_AUTHORIZATION"); }
                @Override public void onConnected(String provider, String name, String id, String session) {
                    finish("UNEXPECTED_CONNECTION");
                }
                @Override public void onFailure(String code) { finish(code); }

                private void finish(String value) {
                    event.set(value);
                    completed.countDown();
                }
            });
            assertTrue("The app did not receive the denied callback", completed.await(5, TimeUnit.SECONDS));
            assertEquals("ACCESS_DENIED", event.get());
            assertNull(connections.getPending());
            assertEquals("prior-instagram-session", connections.instagramConnection().sessionId);
            assertEquals(FACEBOOK_TOKEN, connections.accessToken());
        } finally {
            io.shutdownNow();
        }
    }

    @Test public void losingPublishResponseKeepsContainerAndPreventsAnotherPost() {
        saveInstagram("publish-test-session", "test-ig-token", "2099-01-01T00:00:00Z");
        AtomicInteger requests = new AtomicInteger();
        InstagramGraphClient graph = ControlledInstagramGraph.withResponse(chain -> {
            Request request = chain.request();
            int number = requests.incrementAndGet();
            if (number == 1) {
                assertEquals("POST", request.method());
                assertEquals("/v27.0/" + INSTAGRAM_ID + "/media", request.url().encodedPath());
                return json(request, 200, "{\"id\":\"5678901234\"}");
            }
            if (number == 2) {
                assertEquals("POST", request.method());
                assertEquals("/v27.0/" + INSTAGRAM_ID + "/media_publish", request.url().encodedPath());
                throw new IOException("Controlled lost response");
            }
            assertEquals(3, number);
            assertEquals("GET", request.method());
            assertEquals("/v27.0/5678901234", request.url().encodedPath());
            assertEquals("status_code", request.url().queryParameter("fields"));
            return json(request, 200, "{\"status_code\":\"PUBLISHED\"}");
        });
        ContentVersion version = new ContentVersion("test-version", "test-brief", INSTAGRAM_ID,
                null, "Test caption", "https://example.test/image.jpg", 1L);

        assertEquals(CHECK_REQUIRED, repository(graph).publish(version, connections.instagramSession()).status);
        InstagramPublishRepository reloaded = repository(graph);
        assertEquals(PUBLISHED_ID_UNKNOWN, reconcile(reloaded).status);
        assertEquals(CHECK_REQUIRED, reloaded.publish(version, connections.instagramSession()).status);
        assertEquals(3, requests.get());
    }

    @Test public void permissionDeniedBeforePublishKeepsConnection() {
        saveInstagram("permission-test", "test-ig-token", "2099-01-01T00:00:00Z");
        AtomicInteger requests = new AtomicInteger();
        InstagramPublishRepository repository = repository(ControlledInstagramGraph.withResponse(chain -> {
            requests.incrementAndGet();
            return json(chain.request(), 403, "{\"error\":{\"code\":200}}");
        }));
        ContentVersion version = new ContentVersion("test-version", "test-brief", INSTAGRAM_ID,
                null, "Test caption", "https://example.test/image.jpg", 1L);
        assertEquals(InstagramPublishRepository.Status.PERMISSION_REQUIRED,
                repository.publish(version, connections.instagramSession()).status);
        assertEquals(InstagramPublishRepository.Status.PERMISSION_REQUIRED, reconcile(repository).status);
        assertEquals("test-ig-token", connections.instagramConnection().accessToken);
        assertEquals(2, requests.get());
    }

    @Test public void permissionDeniedReadingPermalinkKeepsMediaForReconciliation() {
        saveInstagram("permission-test", "test-ig-token", "2099-01-01T00:00:00Z");
        AtomicInteger requests = new AtomicInteger();
        InstagramPublishRepository repository = repository(ControlledInstagramGraph.withResponse(chain -> {
            Request request = chain.request();
            switch (requests.incrementAndGet()) {
                case 1:
                    assertEquals("POST", request.method());
                    return json(request, 200, "{\"id\":\"5678901234\"}");
                case 2:
                    assertEquals("POST", request.method());
                    return json(request, 200, "{\"id\":\"6789012345\"}");
                case 3:
                    assertEquals("GET", request.method());
                    return json(request, 403, "{\"error\":{\"code\":200}}");
                default:
                    assertEquals("GET", request.method());
                    assertEquals("/v27.0/6789012345", request.url().encodedPath());
                    return json(request, 200, "{\"id\":\"6789012345\",\"permalink\":\"https://www.instagram.com/p/test/\"}");
            }
        }));
        ContentVersion version = new ContentVersion("test-version", "test-brief", INSTAGRAM_ID,
                null, "Test caption", "https://example.test/image.jpg", 1L);
        assertEquals(InstagramPublishRepository.Status.PERMISSION_CHECK_REQUIRED,
                repository.publish(version, connections.instagramSession()).status);
        assertEquals(CHECK_REQUIRED, repository.publish(version, connections.instagramSession()).status);
        assertEquals("6789012345", reconcile(repository).mediaId);
        assertEquals(4, requests.get());
    }

    @Test public void unknownExpiryChecksIdentityBeforeAnyPublishWrite() {
        saveInstagram("unknown-expiry", "test-ig-token", null);
        AtomicInteger requests = new AtomicInteger();
        InstagramGraphClient graph = ControlledInstagramGraph.withResponse(chain -> {
            requests.incrementAndGet();
            assertEquals("GET", chain.request().method());
            assertEquals("id", chain.request().url().queryParameter("fields"));
            return json(chain.request(), 400, "{\"error\":{\"code\":190}}");
        });
        ContentVersion version = new ContentVersion("test-version", "test-brief", INSTAGRAM_ID,
                null, "Test caption", "https://example.test/image.jpg", 1L);
        assertEquals(RECONNECT, repository(graph).publish(version, connections.instagramSession()).status);
        assertEquals(1, requests.get());
    }

    @Test public void lateReadyIsDiscardedAfterSameAccountReconnect() {
        lateReadyIsDiscarded(INSTAGRAM_ID);
    }

    @Test public void lateReadyIsDiscardedAfterSwitchingAccounts() {
        lateReadyIsDiscarded("2222222222");
    }

    private void lateReadyIsDiscarded(String nextAccount) {
        saveInstagram("session-a", "token-a", null);
        InstagramPublishRepository repository = repository(ControlledInstagramGraph.withResponse(chain -> {
            Request request = chain.request();
            assertEquals("GET", request.method());
            if ("Bearer token-a".equals(request.header("Authorization"))) {
                saveInstagram(nextAccount, "session-b", "token-b", null);
                return json(request, 200, "{\"id\":\"" + INSTAGRAM_ID + "\"}");
            }
            assertEquals("/v27.0/" + nextAccount, request.url().encodedPath());
            return json(request, 200, "{\"id\":\"" + nextAccount + "\"}");
        }));

        InstagramPublishRepository.Result stale = reconcile(repository);
        assertEquals(CANCELLED, stale.status);
        assertEquals("session-a", stale.session.sessionId);
        assertFalse(stale.isCurrent(connections));
        assertEquals("session-b", connections.instagramConnection().sessionId);
        assertEquals(READY, reconcile(repository).status);
    }

    @Test public void staleContainerExpiryCannotRemoveAttemptForReconnectedAccount() {
        saveInstagram("session-a", "token-a", null);
        saveAttempt("{\"versionId\":\"v1\",\"containerId\":\"5678901234\"}");
        InstagramPublishRepository repository = repository(ControlledInstagramGraph.withResponse(chain -> {
            Request request = chain.request();
            assertEquals("/v27.0/5678901234", request.url().encodedPath());
            if ("Bearer token-a".equals(request.header("Authorization"))) {
                saveInstagram("session-b", "token-b", null);
                return json(request, 200, "{\"status_code\":\"EXPIRED\"}");
            }
            return json(request, 200, "{\"status_code\":\"PUBLISHED\"}");
        }));

        assertEquals(CANCELLED, reconcile(repository).status);
        assertEquals(PUBLISHED_ID_UNKNOWN, reconcile(repository).status);
    }

    @Test public void stalePermalinkIsDiscardedAndCanBeReadAgainWithNewSession() {
        saveInstagram("session-a", "token-a", null);
        saveAttempt("{\"versionId\":\"v1\",\"containerId\":\"5678901234\",\"mediaId\":\"6789012345\"}");
        InstagramPublishRepository repository = repository(ControlledInstagramGraph.withResponse(chain -> {
            Request request = chain.request();
            assertEquals("/v27.0/6789012345", request.url().encodedPath());
            String path = "new";
            if ("Bearer token-a".equals(request.header("Authorization"))) {
                saveInstagram("session-b", "token-b", null);
                path = "old";
            }
            return json(request, 200, "{\"id\":\"6789012345\",\"permalink\":\"https://www.instagram.com/p/" + path + "/\"}");
        }));

        assertNull(reconcile(repository).permalink);
        assertEquals("https://www.instagram.com/p/new/", reconcile(repository).permalink);
    }

    @Test public void staleAuthorizationFailureCannotInvalidateNewSession() {
        saveInstagram("session-a", "token-a", null);
        InstagramPublishRepository repository = repository(ControlledInstagramGraph.withResponse(chain -> {
            saveInstagram("session-b", "token-b", null);
            return json(chain.request(), 400, "{\"error\":{\"code\":190}}");
        }));

        assertEquals(CANCELLED, reconcile(repository).status);
        assertEquals("session-b", connections.instagramConnection().sessionId);
        assertEquals(FACEBOOK_TOKEN, connections.accessToken());
    }

    @Test public void staleIoFailureIsDiscardedAfterReconnect() {
        saveInstagram("session-a", "token-a", null);
        InstagramPublishRepository repository = repository(ControlledInstagramGraph.withResponse(chain -> {
            saveInstagram("session-b", "token-b", null);
            throw new IOException("Controlled connection failure");
        }));

        assertEquals(CANCELLED, reconcile(repository).status);
        assertEquals("session-b", connections.instagramConnection().sessionId);
    }

    @Test public void publishResponseAfterReconnectKeepsMediaForReadOnlyReconciliation() {
        saveInstagram("session-a", "token-a", "2099-01-01T00:00:00Z");
        AtomicInteger requests = new AtomicInteger();
        InstagramPublishRepository repository = repository(ControlledInstagramGraph.withResponse(chain -> {
            Request request = chain.request();
            int number = requests.incrementAndGet();
            if (number == 1) {
                assertEquals("POST", request.method());
                assertEquals("/v27.0/" + INSTAGRAM_ID + "/media", request.url().encodedPath());
                return json(request, 200, "{\"id\":\"5678901234\"}");
            }
            if (number == 2) {
                assertEquals("POST", request.method());
                assertEquals("/v27.0/" + INSTAGRAM_ID + "/media_publish", request.url().encodedPath());
                saveInstagram("session-b", "token-b", null);
                return json(request, 200, "{\"id\":\"6789012345\"}");
            }
            assertEquals(3, number);
            assertEquals("GET", request.method());
            assertEquals("Bearer token-b", request.header("Authorization"));
            assertEquals("/v27.0/6789012345", request.url().encodedPath());
            return json(request, 200, "{\"id\":\"6789012345\",\"permalink\":\"https://www.instagram.com/p/new/\"}");
        }));
        ContentVersion version = new ContentVersion("test-version", "test-brief", INSTAGRAM_ID,
                null, "Test caption", "https://example.test/image.jpg", 1L);

        assertEquals(CANCELLED, repository.publish(version, connections.instagramSession()).status);
        assertEquals(CHECK_REQUIRED, repository.publish(version, connections.instagramSession()).status);
        InstagramPublishRepository.Result reconciled = reconcile(repository);
        assertEquals(PUBLISHED, reconciled.status);
        assertEquals("6789012345", reconciled.mediaId);
        assertEquals(3, requests.get());
    }

    private InstagramPublishRepository.Result reconcile(InstagramPublishRepository repository) {
        return repository.reconcile(connections.instagramSession());
    }

    private void saveAttempt(String json) {
        assertTrue(isolatedContext.getSharedPreferences("instagram_feed_spike", Context.MODE_PRIVATE)
                .edit().putString(INSTAGRAM_ID, json).commit());
    }

    private void saveInstagram(String session, String token, String expiresAt) {
        saveInstagram(INSTAGRAM_ID, session, token, expiresAt);
    }

    private void saveInstagram(String accountId, String session, String token, String expiresAt) {
        connections.saveConnected(new OAuthExchangeResponse(session, "INSTAGRAM", accountId,
                "PROFESSIONAL", "test_ig", token, null, "bearer", expiresAt, FACEBOOK_ID,
                Collections.singletonList("instagram_basic"), "v27.0"));
    }

    private InstagramPublishRepository repository(InstagramGraphClient graph) {
        return new InstagramPublishRepository(isolatedContext, connections, graph);
    }

    private static Response json(Request request, int code, String body) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("Controlled response").body(ResponseBody.create(body, null)).build();
    }
}
