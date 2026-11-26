package com.socialpilot.app.ui;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.core.FacebookOAuthCoordinator;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.repository.ChannelRepository;
import com.socialpilot.app.data.repository.ChannelRepository.PageCheck;
import com.socialpilot.app.data.repository.ControlledInstagramPublishRepository;
import com.socialpilot.app.data.repository.BriefRepository;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.repository.InstagramPublishRepository;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedSecretStore;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainViewModelInstagramDeviceTest {
    private static final String ACCOUNT_A = "9876543210";
    private static final String ACCOUNT_B = "2222222222";
    private static final String LINK_A = "https://www.instagram.com/p/post-a/";
    private static final String LINK_B = "https://www.instagram.com/p/post-b/";

    @Test public void pageVerificationCannotFollowACommittedReconnect() throws Exception {
        Instrumentation device = InstrumentationRegistry.getInstrumentation();
        Application app = (Application) device.getTargetContext().getApplicationContext();
        AppDatabase database = Room.inMemoryDatabaseBuilder(app, AppDatabase.class).build();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        // The test writes only generated secret references and removes them via the test namespace.
        String prefix = "sp022_page_test_" + UUID.randomUUID() + "_";
        Context isolated = new ContextWrapper(app) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + name, mode);
            }
        };
        AtomicInteger calls = new AtomicInteger();
        ChannelRepository channels = ControlledInstagramPublishRepository.channels(database,
                new EncryptedSecretStore(isolated, SecretNamespace.SOCIAL_TOKEN), chain -> {
                    calls.incrementAndGet();
                    return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                            .code(200).message("Fixture")
                            .body(ResponseBody.create("{\"id\":\"1234567890\"}", null)).build();
                });
        MainViewModel model = new MainViewModel(app, null, null, null, null, worker, channels);
        try {
            worker.submit(() -> channels.save(page("one"))).get(5, TimeUnit.SECONDS);
            device.runOnMainSync(() -> {
                model.checkFacebookPage();
                awaitWorker(worker);
            });
            device.runOnMainSync(() -> assertEquals(PageCheck.VERIFIED, model.currentFacebookPageCheck()));
            worker.submit(() -> channels.save(page("two"))).get(5, TimeUnit.SECONDS);
            device.runOnMainSync(() -> assertEquals(PageCheck.IDLE, model.currentFacebookPageCheck()));
            device.runOnMainSync(() -> {
                model.checkFacebookPage();
                awaitWorker(worker);
                worker.submit(() -> channels.save(page("three")));
                awaitWorker(worker);
                assertEquals(PageCheck.IDLE, model.currentFacebookPageCheck());
            });
            device.runOnMainSync(() -> {
                assertEquals(PageCheck.IDLE, model.currentFacebookPageCheck());
                model.checkFacebookPage();
                awaitWorker(worker);
            });
            device.runOnMainSync(() -> assertEquals(PageCheck.VERIFIED, model.currentFacebookPageCheck()));
            assertEquals(3, calls.get());
        } finally {
            worker.shutdownNow();
            database.close();
            isolated.getSharedPreferences("secrets_social_v1", Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    private static OAuthExchangeResponse page(String session) {
        return new OAuthExchangeResponse(session, "FACEBOOK", "1234567890", "PAGE", "Fixture Page",
                "test-token-" + session, null, "bearer", null);
    }

    @Test public void completionQueuedBeforeReconnectCannotExposeOldPost() {
        Instrumentation device = InstrumentationRegistry.getInstrumentation();
        Application application = (Application) device.getTargetContext().getApplicationContext();
        String prefix = "sp065_ui_test_" + UUID.randomUUID() + "_";
        Context isolated = new ContextWrapper(application) {
            @Override public Context getApplicationContext() { return this; }

            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + name, mode);
            }
        };
        AppDatabase database = Room.inMemoryDatabaseBuilder(application, AppDatabase.class).build();
        EncryptedOAuthStore store = new EncryptedOAuthStore(isolated);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        InstagramPublishRepository instagram = ControlledInstagramPublishRepository.create(isolated,
                store, chain -> {
                    Request request = chain.request();
                    assertEquals("GET", request.method());
                    String accountId = "Bearer token-a".equals(request.header("Authorization")) ? ACCOUNT_A : ACCOUNT_B;
                    assertEquals("/v27.0/" + accountId, request.url().encodedPath());
                    return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                            .message("Controlled response")
                            .body(ResponseBody.create("{\"id\":\"" + accountId + "\"}", null)).build();
                });
        MainViewModel model = new MainViewModel(application, new DraftRepository(database.drafts(), worker),
                new BriefRepository(database.briefs(), worker),
                new FacebookOAuthCoordinator(store, worker, "https://127.0.0.1/"), store, instagram, worker);
        List<InstagramPublishRepository.Result> emissions = new ArrayList<>();
        Observer<InstagramPublishRepository.Result> observer = emissions::add;
        SharedPreferences attempts = isolated.getSharedPreferences("instagram_feed_spike", Context.MODE_PRIVATE);
        assertTrue(attempts.edit().putString(ACCOUNT_A, attempt(LINK_A)).putString(ACCOUNT_B, attempt(LINK_B)).commit());
        store.saveConnected(connection("a", ACCOUNT_A));
        try {
            device.runOnMainSync(() -> {
                model.instagramResult().observeForever(observer);
                model.reconcileInstagram();
                awaitWorker(worker);
            });
            device.runOnMainSync(() -> {
                assertEquals(LINK_A, model.instagramPermalink());
                assertEquals(1, emissions.size());
                model.reconcileInstagram();
                // The worker posts A's result while this runnable still owns the main thread.
                awaitWorker(worker);
                store.saveConnected(connection("b", ACCOUNT_B));
                assertEquals(ACCOUNT_B, model.instagramConnection().externalAccountId);
                assertNull(model.instagramResultFor(model.instagramConnection()));
                assertNull(model.instagramPermalink());
            });
            device.runOnMainSync(() -> {
                assertEquals(1, emissions.size());
                assertFalse(Boolean.TRUE.equals(model.instagramBusy().getValue()));
                assertNull(model.instagramPermalink());
                model.reconcileInstagram();
                awaitWorker(worker);
            });
            device.runOnMainSync(() -> {
                assertEquals(2, emissions.size());
                assertEquals(ACCOUNT_B, emissions.get(1).session.accountId);
                assertEquals(InstagramPublishRepository.Status.PUBLISHED, emissions.get(1).status);
                assertEquals(LINK_B, model.instagramPermalink());
            });
        } finally {
            device.runOnMainSync(() -> model.instagramResult().removeObserver(observer));
            worker.shutdownNow();
            database.close();
            attempts.edit().clear().commit();
            isolated.getSharedPreferences("oauth_secure_v1", Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    private static void awaitWorker(ExecutorService worker) {
        try {
            worker.submit(() -> null).get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError("Instagram work did not finish", failure);
        }
    }

    private static OAuthExchangeResponse connection(String id, String accountId) {
        return new OAuthExchangeResponse("session-" + id, "INSTAGRAM", accountId, "PROFESSIONAL", "test_" + id,
                "token-" + id, null, "bearer", null, "1234567890", Collections.singletonList("instagram_basic"), "v27.0");
    }

    private static String attempt(String permalink) {
        return "{\"versionId\":\"v1\",\"containerId\":\"5678901234\",\"mediaId\":\"6789012345\",\"permalink\":\""
                + permalink + "\"}";
    }
}
