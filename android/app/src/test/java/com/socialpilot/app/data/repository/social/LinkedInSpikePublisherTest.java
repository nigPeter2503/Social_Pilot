package com.socialpilot.app.data.repository.social;

import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedOAuthStoreTest;
import com.socialpilot.app.data.security.EncryptedOAuthStoreTest.MemoryPreferences;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.social.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.*;
import org.junit.*;
import static org.junit.Assert.*;

public class LinkedInSpikePublisherTest {
    MemoryPreferences credentials,attempts;
    EncryptedOAuthStore store;
    MockWebServer server;
    ExecutorService io;
    LinkedInSpikePublisher publisher;
    @Before public void setup() throws Exception {
        credentials=new MemoryPreferences();attempts=new MemoryPreferences();
        store=EncryptedOAuthStoreTest.testStore(credentials);connect();
        server=new MockWebServer();server.start();io=Executors.newFixedThreadPool(4);reloadPublisher();
    }
    void connect() { store.saveConnected(new OAuthExchangeResponse("session","LINKEDIN","member","PERSON","Member","fake-token",null,"Bearer","2099-01-01T00:00:00Z")); }
    void reloadPublisher() { publisher=new LinkedInSpikePublisher(store,attempts,io,"202609",new OkHttpClient(),server.url("/rest/posts")); }
    PublishResult send(LinkedInSpikePublisher.Review review) throws Exception {
        var result=new AtomicReference<PublishResult>();var done=new CountDownLatch(1);
        publisher.publishApprovedText(review,r->{result.set(r);done.countDown();});
        assertTrue(done.await(5,TimeUnit.SECONDS));return result.get();
    }
    @After public void cleanup() throws Exception {io.shutdownNow();server.shutdown();}
    @Test public void authenticationFailureReloadReconnectAndReviewSendsNewAttempt() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));
        assertEquals(SocialError.Code.AUTH_REQUIRED,send(publisher.review("text")).error.code);
        credentials=credentials.reboot();attempts=attempts.reboot();store=EncryptedOAuthStoreTest.testStore(credentials);reloadPublisher();
        assertTrue(publisher.state().summary.contains("RECONNECT"));
        assertEquals(SocialError.Code.AUTH_REQUIRED,send(publisher.review("text")).error.code);
        assertEquals(1,server.getRequestCount());connect();
        server.enqueue(new MockResponse().setResponseCode(201).addHeader("x-restli-id","urn:li:share:123"));
        assertEquals(PublishResult.Outcome.PUBLISHED_URL_PENDING,send(publisher.review("text")).outcome);
        assertEquals(2,server.getRequestCount());
    }
    @Test public void approvedPermissionRetryCanBeUsedOnlyOnce() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403));
        send(publisher.review("text"));
        var approved=publisher.review("text");assertEquals("PERMISSION_REQUIRED",approved.errorCode);
        server.enqueue(new MockResponse().setResponseCode(201).addHeader("x-restli-id","urn:li:share:123"));
        assertEquals(PublishResult.Outcome.PUBLISHED_URL_PENDING,send(approved).outcome);
        assertEquals(PublishResult.Outcome.PUBLISHED_URL_PENDING,send(approved).outcome);
        assertEquals(2,server.getRequestCount());
    }
    @Test public void rateDeadlineSurvivesReloadAndPreventsHttp() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429).addHeader("Retry-After","3600"));
        send(publisher.review("text"));attempts=attempts.reboot();reloadPublisher();
        var review=publisher.review("text");assertTrue(review.retryAt>System.currentTimeMillis());
        assertEquals(SocialError.Code.RATE_LIMITED,send(review).error.code);
        assertEquals(1,server.getRequestCount());
    }
    @Test public void recoveredChannelCannotBypassPendingPost() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).addHeader("x-restli-id","urn:li:share:123"));
        send(publisher.review("text"));
        credentials.edit().remove("connected_v3_LINKEDIN").remove("revision_v1_LINKEDIN:member").commit();
        store=EncryptedOAuthStoreTest.testStore(credentials);connect();reloadPublisher();
        assertEquals(PublishResult.Outcome.PUBLISHED_URL_PENDING,send(publisher.review("text")).outcome);
        assertEquals(1,server.getRequestCount());
    }
    @Test public void confirmingReopenedAReturnsAStateAndKeepsBPointer() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).addHeader("x-restli-id","urn:li:share:123"));
        send(publisher.review("A"));
        server.enqueue(new MockResponse().setResponseCode(201).addHeader("x-restli-id","urn:li:share:456"));
        send(publisher.review("B"));
        String lastKey=attempts.getString("last_key",null);
        String lastPayload=attempts.getString("last_payload",null);
        var reviewA=publisher.review("A");
        var resultA=send(reviewA);
        var displayedA=publisher.stateForResult(reviewA,resultA);
        var confirmedA=publisher.confirmOpenedPostUrl(displayedA.confirmation,
                "https://www.linkedin.com/feed/update/urn:li:share:123/");
        assertTrue(confirmedA.summary.startsWith("SUCCESS"));
        assertTrue(confirmedA.summary.contains("urn:li:share:123"));
        assertNotNull(confirmedA.confirmation);
        assertEquals(lastKey,attempts.getString("last_key",null));
        assertEquals(lastPayload,attempts.getString("last_payload",null));
        assertTrue(publisher.state().summary.contains("urn:li:share:456"));
        assertTrue(publisher.confirmOpenedPostUrl(confirmedA.confirmation,
                "https://www.linkedin.com/feed/update/urn:li:share:123/").summary.startsWith("SUCCESS"));
        assertEquals(2,server.getRequestCount());
    }
}
