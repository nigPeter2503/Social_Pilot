package com.socialpilot.app.core;

import android.content.Context;
import androidx.room.Room;
import com.socialpilot.app.BuildConfig;
import com.socialpilot.app.data.ai.AiKeyRepository;
import com.socialpilot.app.data.ai.ByokKeyVerifier;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.security.EncryptedSecretStore;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppContainer {
    public final ExecutorService io = Executors.newFixedThreadPool(4);
    public final AppDatabase database;
    public final DraftRepository drafts;
    public final FacebookOAuthCoordinator facebookOAuth;
    /** Social-token namespace for SP-022; separate file and Keystore key from AI keys. */
    public final SecretStore socialSecrets;
    public final AiKeyRepository aiKeys;

    public AppContainer(Context context) {
        Context app = context.getApplicationContext();
        database = Room.databaseBuilder(app, AppDatabase.class, "socialpilot.db").build();
        drafts = new DraftRepository(database.drafts(), io);
        facebookOAuth = new FacebookOAuthCoordinator(context, io, BuildConfig.OAUTH_API_BASE_URL);
        socialSecrets = new EncryptedSecretStore(app, SecretNamespace.SOCIAL_TOKEN);
        aiKeys = new AiKeyRepository(new EncryptedSecretStore(app, SecretNamespace.AI_PROVIDER),
                app.getSharedPreferences("ai_key_status_v1", Context.MODE_PRIVATE),
                new ByokKeyVerifier(), NetworkStatus.of(app), System::currentTimeMillis);
    }
}
