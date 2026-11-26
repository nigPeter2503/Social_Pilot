package com.socialpilot.app.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Application;
import android.app.LocaleManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.R;
import com.socialpilot.app.SocialPilotApplication;
import com.socialpilot.app.core.AppContainer;
import com.socialpilot.app.core.FacebookOAuthCoordinator;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.repository.ChannelRepository;
import com.socialpilot.app.data.repository.ChannelRepository.Snapshot;
import com.socialpilot.app.data.repository.ControlledInstagramPublishRepository;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.repository.InstagramPublishRepository;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedSecretStore;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class MainActivityConnectionDeviceTest {
    private static final String ACCOUNT = "9876543210";

    @Test public void reconcilePermissionIsShownInVietnameseAndEnglishWithoutClearingApproval() throws Exception {
        SocialPilotApplication app = (SocialPilotApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assumeTrue(app.getPackageName().endsWith(".sp022review") && Build.VERSION.SDK_INT >= 33);
        LocaleManager locales = app.getSystemService(LocaleManager.class);
        LocaleList original = locales.getApplicationLocales();
        String prefix = "sp022_review_" + UUID.randomUUID() + "_";
        Context isolated = new ContextWrapper(app) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + name, mode);
            }
        };
        AppDatabase database = Room.inMemoryDatabaseBuilder(app, AppDatabase.class).build();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        EncryptedSecretStore secrets = new EncryptedSecretStore(isolated, SecretNamespace.SOCIAL_TOKEN);
        ChannelRepository channels = new ChannelRepository(database, secrets);
        EncryptedOAuthStore store = new EncryptedOAuthStore(isolated, channels);
        AtomicInteger checks = new AtomicInteger();
        InstagramPublishRepository repository = ControlledInstagramPublishRepository.create(isolated, store, chain -> {
            assertEquals("GET", chain.request().method());
            assertEquals("/v27.0/" + ACCOUNT, chain.request().url().encodedPath());
            checks.incrementAndGet();
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(403).message("Fixture")
                    .body(ResponseBody.create("{\"error\":{\"code\":200}}", null)).build();
        });
        Supplier<MainViewModel> createModel = () -> new MainViewModel(app, new DraftRepository(database.drafts(), worker),
                new FacebookOAuthCoordinator(store, worker, "https://127.0.0.1/"), store, repository, worker, channels);
        Application.ActivityLifecycleCallbacks installModel = installMainViewModel(createModel);
        app.registerActivityLifecycleCallbacks(installModel);
        try {
            channels.save(connection("permission", null));
            channels.save(new OAuthExchangeResponse("expired-page", "FACEBOOK", "1234567890", "PAGE",
                    "Test Page", "expired-fake", null, "bearer", "2020-01-01T00:00:00Z"));
            setTestLocale(app, locales, "vi");
            assertPermissionAfterApproval(worker, "Đã duyệt nội dung hiện tại. Chỉnh sửa sẽ hủy duyệt.",
                    "Thiếu quyền cho thao tác Instagram này. Kiểm tra quyền đã cấp cho ứng dụng.",
                    "Cần kết nối lại Facebook Page Test Page (1234567890).");
            setTestLocale(app, locales, "en");
            assertPermissionAfterApproval(worker, "Current content approved. Editing it clears the approval.",
                    "Permission is required for this Instagram action. Review the access granted to the app.",
                    "Reconnect Facebook Page Test Page (1234567890).");
            assertEquals(2, checks.get());
        } finally {
            app.unregisterActivityLifecycleCallbacks(installModel);
            locales.setApplicationLocales(original);
            worker.shutdownNow();
            database.close();
            app.deleteSharedPreferences(prefix + "secrets_social_v1");
        }
    }

    private static void assertPermissionAfterApproval(ExecutorService worker, String approved, String denied,
            String reconnectPage) throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ((TextView) activity.findViewById(R.id.instagramCaption)).setText("Fixture caption");
                ((TextView) activity.findViewById(R.id.instagramImageUrl)).setText("https://example.test/photo.jpg");
                assertTrue(activity.findViewById(R.id.instagramApprove).performClick());
            });
            onView(withId(R.id.facebookPageStatus)).check(matches(withText(reconnectPage)));
            onView(withId(R.id.instagramPublishStatus)).check(matches(withText(approved)));
            scenario.onActivity(activity -> {
                View reconcile = activity.findViewById(R.id.instagramReconcile);
                assertTrue(reconcile.isEnabled());
                assertTrue(reconcile.performClick());
            });
            worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
            onView(withId(R.id.instagramPublishStatus)).check(matches(withText(denied)));
            onView(withId(R.id.instagramPublish)).check(matches(isEnabled()));
        }
    }

    private static void setTestLocale(Application app, LocaleManager locales, String language)
            throws InterruptedException {
        locales.setApplicationLocales(LocaleList.forLanguageTags(language));
        for (int i = 0; i < 60; i++) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if (language.equals(app.getResources().getConfiguration().getLocales().get(0).getLanguage())) return;
            Thread.sleep(50);
        }
        assertEquals(language, app.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    private static Application.ActivityLifecycleCallbacks installMainViewModel(Supplier<MainViewModel> createModel) {
        return new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityPreCreated(@NonNull Activity activity, Bundle state) {
                if (!(activity instanceof MainActivity)) return;
                MainActivity mainActivity = (MainActivity) activity;
                new ViewModelProvider(mainActivity.getViewModelStore(), new ViewModelProvider.Factory() {
                    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                        return modelClass.cast(createModel.get());
                    }
                }).get(MainViewModel.class);
            }
            @Override public void onActivityCreated(@NonNull Activity activity, Bundle state) {}
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        };
    }

    @Test public void coldActivityShowsInstagramReconnectInVietnameseAndEnglish() throws Exception {
        SocialPilotApplication app = (SocialPilotApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        // This test exercises the real AppContainer on the isolated SP-022 QA package only.
        assumeTrue(app.getPackageName().endsWith(".sp022review") && Build.VERSION.SDK_INT >= 33);
        AppContainer container = app.container();
        assertTrue(container.channels.list().isEmpty());
        LocaleManager locales = app.getSystemService(LocaleManager.class);
        LocaleList original = locales.getApplicationLocales();
        try {
            setTestLocale(app, locales, "vi");
            assertInstagramState("Chưa kết nối Instagram.");
            Snapshot expired = container.channels.save(connection("expired", "2020-01-01T00:00:00Z"));
            assertInstagramState("Cần kết nối lại Instagram @fixture (" + ACCOUNT + ").");

            Snapshot revoked = container.channels.save(connection("revoked", null));
            assertTrue(container.channels.requireReconnect(revoked.scope));
            setTestLocale(app, locales, "en");
            assertInstagramState("Reconnect Instagram account @fixture (" + ACCOUNT + ").");
        } finally {
            Snapshot selected = container.channels.selected(Platform.INSTAGRAM);
            if (selected != null) {
                container.database.getOpenHelper().getWritableDatabase().execSQL(
                        "DELETE FROM channels WHERE channelId = ?", new Object[] {selected.scope.channelId});
                container.socialSecrets.delete(SecretRef.parse(selected.info.tokenReference));
                container.channels.restore();
            }
            locales.setApplicationLocales(original);
        }
    }

    private static void assertInstagramState(String expected) throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<String> text = new AtomicReference<>();
            for (int i = 0; i < 30; i++) {
                scenario.onActivity(activity -> text.set(
                        ((TextView) activity.findViewById(R.id.instagramAccountStatus)).getText().toString()));
                if (expected.equals(text.get())) break;
                Thread.sleep(50);
            }
            onView(withId(R.id.instagramAccountStatus)).check(matches(withText(expected)));
            onView(withId(R.id.instagramPublish)).check(matches(not(isEnabled())));
            onView(withId(R.id.connectInstagramButton)).check(matches(isEnabled()));
        }
    }

    private static OAuthExchangeResponse connection(String session, String expiresAt) {
        return new OAuthExchangeResponse(session, "INSTAGRAM", ACCOUNT, "PROFESSIONAL", "fixture",
                "test-token", null, "bearer", expiresAt, "1234567890",
                Collections.singletonList("instagram_basic"), "v27.0");
    }
}
