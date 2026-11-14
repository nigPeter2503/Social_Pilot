package com.socialpilot.app.data.repository.social;
import com.socialpilot.app.domain.contract.*;
import com.socialpilot.app.domain.contract.social.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import static org.junit.Assert.*;
public class LinkedInSocialConnectorTest {
    MockWebServer server;ExecutorService io;ChannelInfo channel;
    AtomicReference<ChannelInfo> current;AtomicBoolean approved;AtomicBoolean reserved;AtomicInteger reauth;
    CountDownLatch recorded;AtomicReference<PublishResult> persisted;
    LinkedInSocialConnector connector;
    @Before public void setup() throws Exception {
        server=new MockWebServer();server.start();io=Executors.newSingleThreadExecutor();
        channel=new ChannelInfo("li-channel",Platform.LINKEDIN,"member",AccountType.PERSON,"Member",ConnectionStatus.CONNECTED,1,"li-reference");
        current=new AtomicReference<>(channel);approved=new AtomicBoolean(true);reserved=new AtomicBoolean();reauth=new AtomicInteger();recorded=new CountDownLatch(1);persisted=new AtomicReference<>();
        connector=new LinkedInSocialConnector(channel,new LinkedInSocialConnector.Credentials(){
            public ChannelInfo current(){return current.get();}public String accessToken(){return "fake-token";}public void requireReauth(long r){reauth.incrementAndGet();}
        },r->new LinkedInSocialConnector.Snapshot("version","Approved test text",approved.get()),new LinkedInSocialConnector.Attempts(){
            public boolean begin(PublishRequest r){return reserved.compareAndSet(false,true);}
            public void record(PublishResult r){persisted.set(r);recorded.countDown();}
        },new OkHttpClient.Builder().readTimeout(300,TimeUnit.MILLISECONDS).build(),server.url("/rest/posts"),"202609",io);
    }
    @After public void cleanup() throws Exception {io.shutdownNow();server.shutdown();}
    PublishRequest request(){return new PublishRequest("request","li-channel",1,"version");}
    PublishResult send(PublishRequest request) throws Exception {
        AtomicReference<PublishResult> result=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
        connector.publish(request,r->{result.set(r);done.countDown();});assertTrue(done.await(5,TimeUnit.SECONDS));return result.get();
    }
    @Test public void createdIdIsPendingUntilUrlIsVerified() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).addHeader("x-restli-id","urn:li:share:123"));
        var r=send(request());assertEquals(PublishResult.Outcome.PUBLISHED_URL_PENDING,r.outcome);assertNull(r.postUrl);
        var sent=server.takeRequest(2,TimeUnit.SECONDS);assertEquals("Bearer fake-token",sent.getHeader("Authorization"));
        assertEquals("202609",sent.getHeader("LinkedIn-Version"));assertEquals("2.0.0",sent.getHeader("X-Restli-Protocol-Version"));
        var json=com.google.gson.JsonParser.parseString(sent.getBody().readUtf8()).getAsJsonObject();
        assertEquals("urn:li:person:member",json.get("author").getAsString());assertEquals("Approved test text",json.get("commentary").getAsString());
        assertEquals(CapabilityAvailability.AVAILABLE,connector.capability(SocialOperation.PUBLISH_TEXT).availability);
        assertEquals(CapabilityAvailability.UNVERIFIED,connector.capability(SocialOperation.READ_COMMENTS).availability);
    }
    @Test public void approvalAndRevisionAreCheckedBeforeAnyHttp() throws Exception {
        approved.set(false);assertEquals(SocialError.Code.APPROVAL_REQUIRED,send(request()).error.code);assertEquals(0,server.getRequestCount());
        approved.set(true);assertEquals(SocialError.Code.CHANNEL_DISCONNECTED,send(new PublishRequest("r","li-channel",2,"version")).error.code);assertEquals(0,server.getRequestCount());
    }
    @Test public void authenticationFailureRequiresReconnect() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401));assertEquals(SocialError.Code.AUTH_REQUIRED,send(request()).error.code);assertEquals(1,reauth.get());
    }
    @Test public void forbiddenAndRateLimitHaveDistinctActions() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403));assertEquals(SocialError.Code.PERMISSION_REQUIRED,send(request()).error.code);
        reserved.set(false);server.enqueue(new MockResponse().setResponseCode(429));assertEquals(SocialError.NextAction.WAIT,send(request()).error.nextAction);
    }
    @Test public void timeoutIsUnknownAndDuplicateDoesNotPostAgain() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));assertEquals(PublishResult.Outcome.UNKNOWN,send(request()).outcome);
        assertEquals(PublishResult.Outcome.UNKNOWN,send(request()).outcome);assertEquals(1,server.getRequestCount());
    }
    @Test public void missingPostIdCannotBeSuccess() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201));assertEquals(PublishResult.Outcome.UNKNOWN,send(request()).outcome);
    }
    @Test public void cancellationPersistsUncertainOutcomeWithoutLateCallback() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));AtomicInteger callbacks=new AtomicInteger();
        Cancelable call=connector.publish(request(),r->callbacks.incrementAndGet());assertNotNull(server.takeRequest(3,TimeUnit.SECONDS));call.cancel();
        assertTrue(recorded.await(5,TimeUnit.SECONDS));assertEquals(PublishResult.Outcome.UNKNOWN,persisted.get().outcome);assertEquals(0,callbacks.get());
    }
    @Test public void serverFailureDoesNotEnableAutomaticRetry() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));assertEquals(PublishResult.Outcome.UNKNOWN,send(request()).outcome);
    }
}
