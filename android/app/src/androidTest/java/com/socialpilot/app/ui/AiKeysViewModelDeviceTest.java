package com.socialpilot.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class AiKeysViewModelDeviceTest {
    @Test public void queuedSaveCannotRestoreKeyAfterDeleteAndReopen() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Application app = (Application) instrumentation.getTargetContext().getApplicationContext();
        SharedPreferences status = app.getSharedPreferences("byok_order_test_" + UUID.randomUUID(), Context.MODE_PRIVATE);
        Map<SecretRef, SecretRead> records = new HashMap<>();
        SecretStore secrets = new SecretStore() {
            @Override public SecretNamespace namespace() { return SecretNamespace.AI_PROVIDER; }
            @Override public SecretRead read(SecretRef ref) {
                return records.getOrDefault(ref, SecretRead.missing());
            }
            @Override public String write(SecretRef ref, String key) {
                String revision = UUID.randomUUID().toString();
                records.put(ref, SecretRead.success(key, revision));
                return revision;
            }
            @Override public void delete(SecretRef ref) { records.remove(ref); }
        };
        AtomicInteger networkChecks = new AtomicInteger();
        AiKeyRepository keys = new AiKeyRepository(secrets, status, new ByokKeyVerifier(),
                () -> { networkChecks.incrementAndGet(); return false; }, () -> 1_000L);
        String geminiKey = "fixture-gemini-old-000000000";
        keys.save(Provider.OPENAI, "fixture-openai-old-000000000");
        keys.save(Provider.GEMINI, geminiKey);
        Deque<Runnable> queued = new ArrayDeque<>();
        AtomicReference<AiKeysViewModel> original = new AtomicReference<>();
        AtomicReference<AiKeysViewModel> reopened = new AtomicReference<>();
        try {
            instrumentation.runOnMainSync(() -> original.set(new AiKeysViewModel(app, keys, queued::add)));
            while (!queued.isEmpty()) queued.removeLast().run();
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertTrue(original.get().save(Provider.OPENAI, "fixture-openai-new-000000000"));
                original.get().delete(Provider.OPENAI);
                reopened.set(new AiKeysViewModel(app, keys, queued::add));
            });

            // Prefer newly submitted workers, which used to run delete before the older save.
            while (!queued.isEmpty()) queued.removeLast().run();
            instrumentation.waitForIdleSync();

            assertEquals(AiKeyRepository.KeyState.NOT_SET, keys.status(Provider.OPENAI).state);
            assertNull(keys.credentialSource(Provider.OPENAI).load());
            assertEquals(geminiKey, keys.credentialSource(Provider.GEMINI).load());
            assertEquals(0, networkChecks.get());
            instrumentation.runOnMainSync(() -> {
                var state = original.get().state(Provider.OPENAI).getValue();
                assertEquals(AiKeyRepository.KeyState.NOT_SET, state.status.state);
                assertEquals(AiKeysViewModel.Notice.DELETED, state.notice);
                assertEquals(AiKeyRepository.KeyState.NOT_SET,
                        reopened.get().state(Provider.OPENAI).getValue().status.state);
            });
        } finally {
            status.edit().clear().commit();
        }
    }

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
