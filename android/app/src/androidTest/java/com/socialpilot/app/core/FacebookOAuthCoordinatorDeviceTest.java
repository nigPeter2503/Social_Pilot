package com.socialpilot.app.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.remote.oauth.OAuthApi;
import com.socialpilot.app.data.repository.ChannelRepository;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedSecretStore;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@RunWith(AndroidJUnit4.class)
public class FacebookOAuthCoordinatorDeviceTest {
    @Test public void expiredExchangeReportsFailureAndPreservesTheWorkingPage() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "sp022_callback_" + UUID.randomUUID() + "_";
        Context context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + name, mode);
            }
        };
        AppDatabase database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        var io = Executors.newSingleThreadExecutor();
        try {
            var secrets = new EncryptedSecretStore(context, SecretNamespace.SOCIAL_TOKEN);
            var channels = new ChannelRepository(database, secrets);
            var store = new EncryptedOAuthStore(context, channels);
            store.saveConnected(new OAuthExchangeResponse("working", "FACEBOOK", "1234567890", "PAGE",
                    "Test Page", "old-fake", null, "bearer", null));
            var previous = channels.selected(Platform.FACEBOOK);
            String code = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
            store.savePending("expired", code, "FACEBOOK");

            var client = new OkHttpClient.Builder().addInterceptor(chain ->
                    new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                            .code(200).message("Fixture").body(ResponseBody.create("""
                                    {"sessionId":"expired","provider":"FACEBOOK","externalAccountId":"1234567890",
                                    "accountType":"PAGE","displayName":"Test Page","accessToken":"new-fake",
                                    "tokenType":"bearer","expiresAt":"2020-01-01T00:00:00Z",
                                    "tokenExpiresAt":"2099-01-01T00:00:00Z",
                                    "dataAccessExpiresAt":"2020-01-01T00:00:00Z"}
                                    """, MediaType.get("application/json"))).build()).build();
            OAuthApi api = new Retrofit.Builder().baseUrl("https://example.test/").client(client)
                    .addConverterFactory(GsonConverterFactory.create()).build().create(OAuthApi.class);
            var coordinator = new FacebookOAuthCoordinator(store, io, api);
            var finished = new CountDownLatch(1);
            var event = new AtomicReference<String>();
            coordinator.handleCallback(Uri.parse("socialpilot://oauth/callback?sessionId=expired&handoffCode=" + code),
                    new FacebookOAuthCoordinator.Listener() {
                        @Override public void onAuthorizationUrl(String url) { finish("AUTHORIZATION"); }
                        @Override public void onConnected(String provider, String name, String id, String session) {
                            finish("CONNECTED");
                        }
                        @Override public void onFailure(String error) { finish(error); }

                        private void finish(String result) {
                            event.set(result);
                            finished.countDown();
                        }
                    });

            assertTrue("No callback event", finished.await(5, TimeUnit.SECONDS));
            assertEquals(FacebookOAuthCoordinator.GRANT_EXPIRED, event.get());
            assertNull(store.getPending());
            assertTrue(previous.scope.matches(channels.selected(Platform.FACEBOOK).scope));
            assertEquals("old-fake", store.accessToken());
        } finally {
            io.shutdownNow();
            database.close();
            target.deleteSharedPreferences(prefix + "oauth_secure_v1");
            target.deleteSharedPreferences(prefix + "secrets_social_v1");
        }
    }
}
