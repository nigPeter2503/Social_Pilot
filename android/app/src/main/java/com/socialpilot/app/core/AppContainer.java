package com.socialpilot.app.core;

import android.content.Context;
import androidx.room.Room;
import com.socialpilot.app.BuildConfig;
import com.socialpilot.app.data.ai.AiGateway;
import com.socialpilot.app.data.ai.AiKeyRepository;
import com.socialpilot.app.data.ai.ByokKeyVerifier;
import com.socialpilot.app.data.ai.ModelRegistryParser;
import com.socialpilot.app.data.ai.ModelSelectionStore;
import com.socialpilot.app.data.ai.ledger.BudgetLedger;
import com.socialpilot.app.data.ai.ledger.LedgerDatabase;
import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.data.repository.DraftRepository;
import com.socialpilot.app.data.security.EncryptedSecretStore;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.ModelRegistry;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    public final ModelRegistry modelRegistry;
    public final ModelSelectionStore modelSelections;
    /** One shared budget and usage ledger for every provider, task and channel ({@code ai_ledger.db}). */
    public final AttemptLedger ledger;
    public final AiGateway ai;

    public AppContainer(Context context) {
        Context app = context.getApplicationContext();
        database = Room.databaseBuilder(app, AppDatabase.class, "socialpilot.db").build();
        drafts = new DraftRepository(database.drafts(), io);
        facebookOAuth = new FacebookOAuthCoordinator(context, io, BuildConfig.OAUTH_API_BASE_URL);
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
