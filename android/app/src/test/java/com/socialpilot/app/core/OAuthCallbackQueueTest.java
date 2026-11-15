package com.socialpilot.app.core;

import com.google.gson.Gson;
import com.socialpilot.app.data.remote.oauth.OAuthApi;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedOAuthStoreTest;
import com.socialpilot.app.data.security.EncryptedOAuthStoreTest.MemoryPreferences;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

public class OAuthCallbackQueueTest {
    MockWebServer server;
    ExecutorService io;
    EncryptedOAuthStore store;
    OAuthCoordinator coordinator;
    CountDownLatch firstEntered, releaseFirst, completed;
    List<String> results;
    @Before public void setup() throws Exception {
        server=new MockWebServer();server.start();io=Executors.newFixedThreadPool(4);
        store=EncryptedOAuthStoreTest.testStore(new MemoryPreferences());
        OAuthApi api=new Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(OAuthApi.class);
        coordinator=new OAuthCoordinator(api,store,io,Runnable::run);
        firstEntered=new CountDownLatch(1);releaseFirst=new CountDownLatch(1);completed=new CountDownLatch(2);
        results=Collections.synchronizedList(new ArrayList<>());
        store.savePending("FACEBOOK","fb-session","f".repeat(43),"2099-01-01T00:00:00Z");
        store.savePending("LINKEDIN","li-session","l".repeat(43),"2099-01-01T00:00:00Z");
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                boolean facebook=request.getPath().contains("facebook");
                if(facebook) { firstEntered.countDown();if(!releaseFirst.await(5,TimeUnit.SECONDS))return new MockResponse().setResponseCode(500); }
                var identity=new OAuthExchangeResponse(facebook?"fb-session":"li-session",facebook?"FACEBOOK":"LINKEDIN",
                        facebook?"page":"member",facebook?"PAGE":"PERSON","Member","fake-token",null,"Bearer","2099-01-01T00:00:00Z");
                return new MockResponse().addHeader("Content-Type","application/json").setBody(new Gson().toJson(identity));
            }
        });
    }
    OAuthCoordinator.Listener listener() {
        return new OAuthCoordinator.Listener() {
            public void onAuthorizationUrl(String url) { fail("Unexpected authorization"); }
            public void onConnected(String name,String id) { results.add(id);completed.countDown(); }
            public void onFailure(String code) { results.add(code);completed.countDown(); }
        };
    }
    @After public void cleanup() throws Exception {releaseFirst.countDown();io.shutdownNow();server.shutdown();}
    void enqueueBoth() throws Exception {
        assertTrue(coordinator.acceptCallback("fb-session","a".repeat(43),null,listener()));
        assertTrue(firstEntered.await(3,TimeUnit.SECONDS));
        assertTrue(coordinator.acceptCallback("li-session","b".repeat(43),null,listener()));
    }
    @Test public void bothProvidersExchangeSeriallyAndDuplicateIsConsumedOnce() throws Exception {
        enqueueBoth();
        assertTrue(coordinator.acceptCallback("li-session","b".repeat(43),null,listener()));
        assertEquals(1,server.getRequestCount());
        releaseFirst.countDown();assertTrue(completed.await(5,TimeUnit.SECONDS));
        assertEquals(List.of("page","member"),results);
        assertEquals(2,server.getRequestCount());
        var first=server.takeRequest();var second=server.takeRequest();
        assertEquals("/api/oauth/facebook/exchange",first.getPath());
        assertEquals("/api/oauth/linkedin/exchange",second.getPath());
        assertTrue(first.getBody().readUtf8().contains("f".repeat(43)));
        assertTrue(second.getBody().readUtf8().contains("l".repeat(43)));
        assertNull(store.getPending("FACEBOOK"));assertNull(store.getPending("LINKEDIN"));
    }
    @Test public void expiryIsCheckedAgainWhenQueuedWorkStarts() throws Exception {
        enqueueBoth();
        store.savePending("LINKEDIN","li-session","l".repeat(43),"2000-01-01T00:00:00Z");
        releaseFirst.countDown();assertTrue(completed.await(5,TimeUnit.SECONDS));
        assertEquals(List.of("page","INVALID_SESSION"),results);
        assertEquals(1,server.getRequestCount());assertNull(store.getPending("LINKEDIN"));
    }
}
