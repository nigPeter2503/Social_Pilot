package com.socialpilot.app.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.data.remote.oauth.OAuthApi;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.*;
import com.socialpilot.app.ui.MainActivity;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Request;
import okio.Timeout;
import org.junit.*;
import org.junit.runner.RunWith;
import retrofit2.*;
import static org.junit.Assert.*;

/** Exercises the real Activity Intent callback using isolated Keystore data and an in-process OAuth API. */
@RunWith(AndroidJUnit4.class)
public class OAuthCallbackIntentDeviceTest {
    ExecutorService io;
    ActivityScenario<MainActivity> scenario;
    AppContainer container;
    OAuthCoordinator original;
    Intent launchIntent;
    Field coordinatorField;
    Context isolated;
    CountDownLatch release=new CountDownLatch(1),entered=new CountDownLatch(1);
    AtomicInteger exchanges=new AtomicInteger();
    @After public void cleanup() throws Exception {
        release.countDown();
        try {
            if(scenario!=null) {
                // ActivityScenario matches lifecycle events against the launch Intent.
                // MainActivity intentionally replaces that Intent when a deep link arrives.
                if(launchIntent!=null)scenario.onActivity(activity->activity.setIntent(launchIntent));
                scenario.close();
            }
        }
        finally {
            if(container!=null && original!=null)coordinatorField.set(container,original);
            if(io!=null)io.shutdownNow();
            if(isolated!=null)isolated.getSharedPreferences("oauth_secure_v1",0).edit().clear().commit();
        }
    }
    @Test public void twoProviderCallbacksThroughIntentsBothConnect() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        isolated=new android.content.ContextWrapper(context) {
            @Override public Context getApplicationContext(){return this;}
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){
                return super.getSharedPreferences("sp011_callback_test_"+name,mode);
            }
        };
        isolated.getSharedPreferences("oauth_secure_v1",0).edit().clear().commit();
        EncryptedOAuthStore store=new EncryptedOAuthStore(isolated);
        store.savePending("FACEBOOK","fb-session","f".repeat(43),"2099-01-01T00:00:00Z");
        store.savePending("LINKEDIN","li-session","l".repeat(43),"2099-01-01T00:00:00Z");
        OAuthApi api=new OAuthApi(){
            public Call<OAuthStartResponse> start(String provider,OAuthStartRequest request){throw new AssertionError("No login requested");}
            public Call<OAuthExchangeResponse> exchange(String provider,OAuthHandoffExchangeRequest request){
                return new FakeExchange(provider,request);
            }
        };
        io=Executors.newFixedThreadPool(4);
        var handler=new android.os.Handler(android.os.Looper.getMainLooper());
        OAuthCoordinator coordinator=new OAuthCoordinator(api,store,io,task->handler.post(task));
        container=((SocialPilotApplication)context.getApplicationContext()).container();
        coordinatorField=AppContainer.class.getDeclaredField("oauth");coordinatorField.setAccessible(true);
        original=(OAuthCoordinator)coordinatorField.get(container);coordinatorField.set(container,coordinator);
        scenario=ActivityScenario.launch(new Intent(context,MainActivity.class));
        scenario.onActivity(activity->launchIntent=new Intent(activity.getIntent()));
        scenario.onActivity(activity->activity.startActivity(callback(activity,"fb-session")));
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        scenario.onActivity(activity->activity.startActivity(callback(activity,"li-session")));
        instrumentation.waitForIdleSync();
        assertEquals(1,exchanges.get());
        release.countDown();
        long deadline=System.currentTimeMillis()+5000;
        while((store.channel("FACEBOOK")==null || store.channel("LINKEDIN")==null) && System.currentTimeMillis()<deadline)Thread.sleep(25);
        assertNotNull(store.channel("FACEBOOK"));assertNotNull(store.channel("LINKEDIN"));
        assertEquals(2,exchanges.get());
        instrumentation.waitForIdleSync();
        scenario.onActivity(activity->assertNull(activity.getIntent().getData()));
        assertNull(store.getPending("FACEBOOK"));assertNull(store.getPending("LINKEDIN"));
    }
    Intent callback(Context context,String session){
        return new Intent(Intent.ACTION_VIEW,Uri.parse("socialpilot://oauth/callback?sessionId="+session+"&handoffCode="+"h".repeat(43)),context,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
    final class FakeExchange implements Call<OAuthExchangeResponse> {
        final String provider;final OAuthHandoffExchangeRequest request;boolean executed,cancelled;
        FakeExchange(String provider,OAuthHandoffExchangeRequest request){this.provider=provider;this.request=request;}
        public Response<OAuthExchangeResponse> execute() throws IOException {
            executed=true;exchanges.incrementAndGet();
            if("facebook".equals(provider)){
                assertEquals("f".repeat(43),request.sessionVerifier);entered.countDown();
                try {if(!release.await(5,TimeUnit.SECONDS))throw new IOException("Timed out");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
            }else assertEquals("l".repeat(43),request.sessionVerifier);
            boolean facebook="facebook".equals(provider);
            return Response.success(new OAuthExchangeResponse(request.sessionId,facebook?"FACEBOOK":"LINKEDIN",
                    facebook?"fake-page":"fake-member",facebook?"PAGE":"PERSON","Fixture Member","fake-token",null,"Bearer","2099-01-01T00:00:00Z"));
        }
        public void enqueue(Callback<OAuthExchangeResponse> callback){throw new UnsupportedOperationException();}
        public boolean isExecuted(){return executed;}
        public void cancel(){cancelled=true;}
        public boolean isCanceled(){return cancelled;}
        public Call<OAuthExchangeResponse> clone(){return new FakeExchange(provider,request);}
        public Request request(){return new Request.Builder().url("https://example.invalid/").build();}
        public Timeout timeout(){return new Timeout();}
    }
}
