package com.socialpilot.app.ui;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import com.socialpilot.app.data.ai.AiKeyRepository;
import com.socialpilot.app.data.ai.ByokKeyVerifier;
import com.socialpilot.app.domain.contract.ai.AiRequest.Provider;
import com.socialpilot.app.domain.contract.secret.SecretNamespace;
import com.socialpilot.app.domain.contract.secret.SecretRead;
import com.socialpilot.app.domain.contract.secret.SecretRef;
import com.socialpilot.app.domain.contract.secret.SecretStore;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class AiKeysViewModelDeviceTest {
    @Test public void clearingScreenBeforeWorkerStartsPreventsQueuedChecksFromReachingNetworkAdmission() {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Application app = (Application) instrumentation.getTargetContext().getApplicationContext();
        String prefsName = "byok_view_model_test_" + UUID.randomUUID();
        SharedPreferences status = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        SecretStore secrets = new SecretStore() {
            @Override public SecretNamespace namespace() { return SecretNamespace.AI_PROVIDER; }
            @Override public SecretRead read(SecretRef ref) {
                return SecretRead.success("fixture-key-000000000000", "fixture-revision");
            }
            @Override public String write(SecretRef ref, String key) { throw new AssertionError("unexpected write"); }
            @Override public void delete(SecretRef ref) { throw new AssertionError("unexpected delete"); }
        };
        AtomicInteger networkChecks = new AtomicInteger();
        AiKeyRepository keys = new AiKeyRepository(secrets, status, new ByokKeyVerifier(),
                () -> { networkChecks.incrementAndGet(); return false; }, () -> 1_000L);
        Queue<Runnable> queued = new ArrayDeque<>();
        try {
            instrumentation.runOnMainSync(() -> {
                AiKeysViewModel model = new AiKeysViewModel(app, keys, queued::add);
                model.check(Provider.OPENAI);
                model.check(Provider.GEMINI);
                model.onCleared();
            });
            assertEquals(4, queued.size()); // Two initial loads and two checks are still queued.
            while (!queued.isEmpty()) queued.remove().run();
            assertEquals(0, networkChecks.get());
        } finally {
            status.edit().clear().commit();
        }
    }
}
