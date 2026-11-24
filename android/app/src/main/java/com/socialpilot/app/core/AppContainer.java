package com.socialpilot.app.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.room.Room;
import androidx.work.WorkManager;
import com.socialpilot.app.BuildConfig;
import com.socialpilot.app.data.ai.AiGateway;
import com.socialpilot.app.data.ai.AiKeyRepository;
import com.socialpilot.app.data.ai.ByokKeyVerifier;
import com.socialpilot.app.data.ai.ModelRegistryParser;
import com.socialpilot.app.data.ai.ModelSelectionStore;
import com.socialpilot.app.data.ai.image.BitmapTemplateRenderer;
import com.socialpilot.app.data.ai.image.ImageProvider;
import com.socialpilot.app.data.ai.image.ImageService;
import com.socialpilot.app.data.ai.image.ImageStore;
import com.socialpilot.app.data.ai.image.OpenAiImageProvider;
import com.socialpilot.app.data.ai.ledger.BudgetLedger;
import com.socialpilot.app.data.ai.ledger.LedgerDatabase;
import com.socialpilot.app.data.ai.pipeline.ChannelPipeline;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.prototype.PrototypeDatabase;
import com.socialpilot.app.data.prototype.SampleRepository;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.repository.InstagramPublishRepository;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.data.security.EncryptedSecretStore;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.ModelRegistry;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import com.socialpilot.app.prototype.DemoStreamCoordinator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
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
    public final ModelRegistry modelRegistry;
    public final ModelSelectionStore modelSelections;
    /** One shared budget and usage ledger for every provider, task and channel ({@code ai_ledger.db}). */
    public final AttemptLedger ledger;
    public final AiGateway ai;
    /** Week-3 text pipeline (SP-031); every stage goes through {@link #ai}. */
    public final ChannelPipeline pipeline;
    /** Private image files ({@code files/ai_images}, excluded from backup). */
    public final ImageStore imageStore;
    /** Channel images and template fallback (SP-032); same selection, BYOK and ledger as text. */
    public final ImageService images;

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
        facebookOAuth = new FacebookOAuthCoordinator(oauthStore, io, BuildConfig.OAUTH_API_BASE_URL);
        socialSecrets = new EncryptedSecretStore(app, SecretNamespace.SOCIAL_TOKEN);
        aiKeys = new AiKeyRepository(new EncryptedSecretStore(app, SecretNamespace.AI_PROVIDER),
                app.getSharedPreferences("ai_key_status_v1", Context.MODE_PRIVATE),
                new ByokKeyVerifier(), NetworkStatus.of(app), System::currentTimeMillis);
        modelRegistry = loadRegistry(app);
        modelSelections = new ModelSelectionStore(
                app.getSharedPreferences("ai_model_selection_v1", Context.MODE_PRIVATE), modelRegistry);
        BudgetLedger budgetLedger = new BudgetLedger(
                LedgerDatabase.open(app, LedgerDatabase.FILE_NAME).store(), System::currentTimeMillis);
        // Only rows of earlier processes are touched, so this may run while new calls reserve.
        io.execute(budgetLedger::recoverInterrupted);
        ledger = budgetLedger;
        ai = new AiGateway(modelSelections, AiGateway.adapters(aiKeys, modelRegistry),
                provider -> aiKeys.status(provider).usable(), ledger, io, System::currentTimeMillis);
        pipeline = new ChannelPipeline(ai, io, System::currentTimeMillis);
        imageStore = new ImageStore(new File(app.getFilesDir(), "ai_images"));
        Map<AiRequest.Provider, ImageProvider> imageProviders = new EnumMap<>(AiRequest.Provider.class);
        imageProviders.put(AiRequest.Provider.OPENAI,
                new OpenAiImageProvider(aiKeys.credentialSource(AiRequest.Provider.OPENAI)));
        // Channel connection revisions are wired with SP-022; until then the caller's revision is trusted.
        images = new ImageService(modelSelections, imageProviders, provider -> aiKeys.status(provider).usable(),
                ledger, imageStore, new BitmapTemplateRenderer(), ImageService.CALLER_REVISION, io,
                System::currentTimeMillis);
    }

    private static ModelRegistry loadRegistry(Context context) {
        try (InputStream input = context.getAssets().open("ai_model_registry.json")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int read; (read = input.read(buffer)) != -1; ) bytes.write(buffer, 0, read);
            return ModelRegistryParser.parseOrEmpty(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException missing) {
            return ModelRegistryParser.parseOrEmpty("");
        }
    }
}
