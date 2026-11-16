package com.socialpilot.app.data.prototype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;
import androidx.work.Operation;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SampleRepositoryInstrumentedTest {
    @Test
    public void enqueueFailureKeepsPreviousCheckpointAndAllowsRetry() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PrototypeDatabase database = Room.inMemoryDatabaseBuilder(context, PrototypeDatabase.class).build();
        ExecutorService io = Executors.newSingleThreadExecutor();
        try {
            SampleResultDao dao = database.results();
            dao.save(new SampleResultEntity("A", "same text", "COMPLETE",
                    "SHA256:previous", "run-1", null, 100));
            SampleRepository repository = new SampleRepository(dao, io);
            WorkManager manager = WorkManager.getInstance(context);

            repository.enqueueCheckpoint("A", manager, (name, request) -> new ImmediateFuture(true));
            waitUntil(() -> dao.get("A").workEnqueueFailed);
            SampleResultEntity failed = dao.get("A");
            assertNull(failed.workRequestId);
            assertEquals("SHA256:previous", failed.checkpoint);

            repository.enqueueCheckpoint("A", manager, (name, request) -> new ImmediateFuture(false));
            waitUntil(() -> dao.get("A").workRequestId != null);
            assertFalse(dao.get("A").workEnqueueFailed);
            assertEquals("SHA256:previous", dao.get("A").checkpoint);

            // Equivalent to a process stop between Room assignment and WorkManager persistence.
            repository.reconcileWorkOnOpen(manager);
            waitUntil(() -> dao.get("A").workEnqueueFailed);
            assertNull(dao.get("A").workRequestId);
            assertEquals("SHA256:previous", dao.get("A").checkpoint);
        } finally {
            io.shutdownNow();
            database.close();
        }
    }

    private interface Condition { boolean ready(); }

    private static void waitUntil(Condition condition) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (condition.ready()) return;
            Thread.sleep(20);
        }
        assertTrue("Timed out waiting for repository", condition.ready());
    }

    private static final class ImmediateFuture implements ListenableFuture<Operation.State.SUCCESS> {
        private final boolean fail;

        ImmediateFuture(boolean fail) { this.fail = fail; }

        @Override public void addListener(Runnable listener, Executor executor) {
            executor.execute(listener);
        }

        @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }
        @Override public Operation.State.SUCCESS get() throws ExecutionException {
            if (fail) throw new ExecutionException(new IllegalStateException("enqueue rejected"));
            return Operation.SUCCESS;
        }
        @Override public Operation.State.SUCCESS get(long timeout, TimeUnit unit)
                throws ExecutionException {
            return get();
        }
    }
}
