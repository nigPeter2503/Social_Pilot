package com.socialpilot.app.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.room.Room;
import androidx.work.WorkManager;
import com.socialpilot.app.BuildConfig;
import com.socialpilot.app.data.ai.AiKeyRepository;
import com.socialpilot.app.data.ai.ByokKeyVerifier;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.prototype.PrototypeDatabase;
import com.socialpilot.app.data.prototype.SampleRepository;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.repository.InstagramPublishRepository;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedSecretStore;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import com.socialpilot.app.prototype.DemoStreamCoordinator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppContainer {
    public final ExecutorService io = Executors.newFixedThreadPool(4);
    public final AppDatabase database;
    public final DraftRepository drafts;
    public final PrototypeDatabase prototypeDatabase;
    public final SampleRepository sampleResults;
    public final DemoStreamCoordinator sampleStreams;
    public final FacebookOAuthCoordinator facebookOAuth;
    public final EncryptedOAuthStore oauthStore;
    public final InstagramPublishRepository instagram;
    /** Social-token namespace for SP-022; separate file and Keystore key from AI keys. */
    public final SecretStore socialSecrets;
    public final AiKeyRepository aiKeys;

    public AppContainer(Context context) {
        Context app = context.getApplicationContext();
        database = Room.databaseBuilder(app, AppDatabase.class, "socialpilot.db").build();
        drafts = new DraftRepository(database.drafts(), io);
        prototypeDatabase = Room.databaseBuilder(app, PrototypeDatabase.class, "socialpilot-prototype.db")
                .addMigrations(PrototypeDatabase.MIGRATION_1_2,
                        PrototypeDatabase.MIGRATION_2_3,
                        PrototypeDatabase.MIGRATION_3_4).build();
        sampleResults = new SampleRepository(prototypeDatabase.results(), Executors.newSingleThreadExecutor());
        Handler main = new Handler(Looper.getMainLooper());
        sampleStreams = new DemoStreamCoordinator((action, delay) ->
                main.postDelayed(action, delay));
        sampleResults.markInterruptedOnOpen();
        sampleResults.reconcileWorkOnOpen(WorkManager.getInstance(app));
        oauthStore = new EncryptedOAuthStore(app);
        instagram = new InstagramPublishRepository(app, oauthStore);
        facebookOAuth = new FacebookOAuthCoordinator(oauthStore, Executors.newSingleThreadExecutor(),
                BuildConfig.OAUTH_API_BASE_URL);
        socialSecrets = new EncryptedSecretStore(app, SecretNamespace.SOCIAL_TOKEN);
        aiKeys = new AiKeyRepository(new EncryptedSecretStore(app, SecretNamespace.AI_PROVIDER),
                app.getSharedPreferences("ai_key_status_v1", Context.MODE_PRIVATE),
                new ByokKeyVerifier(), NetworkStatus.of(app), System::currentTimeMillis);
    }
}
