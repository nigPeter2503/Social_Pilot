package com.socialpilot.app.core;

import android.content.*;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.*;
import com.socialpilot.app.data.local.comment.CommentStore;
import com.socialpilot.app.data.remote.social.DeviceCommentFixture;
import com.socialpilot.app.data.remote.social.CommentPageRequest;
import androidx.work.testing.WorkManagerTestInitHelper;
import static com.socialpilot.app.data.remote.social.CommentPageRequest.Query.*;
import com.socialpilot.app.data.repository.comment.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import static org.junit.Assert.*;

public final class CommentWorkDeviceTest {
    private Context app;
    private WorkManager wm;
    private DeviceCommentFixture fixture;

    @Before public void setup() {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        Assume.assumeTrue(app instanceof CommentDeviceRunner.FixtureApp);
        CommentDeviceRunner.FixtureApp fixtureApp = (CommentDeviceRunner.FixtureApp) app;
        fixtureApp.resetFixture();
        fixture = fixtureApp.fixture();
        wm = WorkManager.getInstance(app);
    }

    private UUID enqueue() throws Exception {
        return enqueue(fixture.query);
    }

    private UUID enqueue(CommentPageRequest q) throws Exception {
        CommentSyncWorker.enqueue(app, fixture.repo, q).getResult().get(10, TimeUnit.SECONDS);
        for (WorkInfo info : wm.getWorkInfosForUniqueWork(CommentSyncWorker.uniqueName(q.queryFingerprint))
                .get(5, TimeUnit.SECONDS)) if (!info.getState().isFinished()) return info.getId();
        throw new AssertionError("no pending work");
    }

    private WorkInfo finish(UUID id) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 90_000;
        do {
            WorkInfo info = wm.getWorkInfoById(id).get(5, TimeUnit.SECONDS);
            if (info.getState().isFinished()) return info;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("work timeout");
    }

    @Test public void invalidatedWorkCancelsRunningAndNetworkConstrainedQueries() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(3);
        WorkManagerTestInitHelper.initializeTestWorkManager(app, new Configuration.Builder().setExecutor(workers).build());
        wm = WorkManager.getInstance(app);
        var driver = WorkManagerTestInitHelper.getTestDriver(app);
        var a = DeviceCommentFixture.instagram(7, CommentPageRequest.Target.POST);
        var b = fixture.query;
        var fresh = DeviceCommentFixture.instagram(9, CommentPageRequest.Target.POST);
        fixture.seed(a);
        try {
            fixture.holdResponse = false;
            for (var q : Arrays.asList(a, b)) {
                UUID seed = enqueue(q);
                driver.setAllConstraintsMet(seed);
                assertEquals("COMPLETE", finish(seed).getOutputData().getString("state"));
                assertTrue(fixture.store.markHandled(q, "device-id"));
            }
            var cachedB = fixture.repo.loadCached(b, CommentStore.Handled.HANDLED);
            var full = new com.socialpilot.app.data.local.publishedpost.SimulatedCommentStore.Counts(1, 1, 1, 1, 1, 1);
            assertEquals(full, fixture.store.counts(a.channelId, 7));
            assertEquals(full, fixture.store.counts(b.channelId, 7));
            fixture.holdResponse = true;
            UUID active = enqueue(a);
            UUID waiting = enqueue(DeviceCommentFixture.instagram(7, CommentPageRequest.Target.COMMENT));
            UUID other = enqueue(b);
            driver.setAllConstraintsMet(active);
            assertTrue(fixture.entered.await(10, TimeUnit.SECONDS));
            assertEquals(WorkInfo.State.RUNNING, wm.getWorkInfoById(active).get().getState());
            assertEquals(WorkInfo.State.ENQUEUED, wm.getWorkInfoById(waiting).get().getState());
            ArrayBlockingQueue<CommentSyncCoordinator.Result> pull = new ArrayBlockingQueue<>(1);
            fixture.repo.refresh(a, pull::offer);
            fixture.store.state(fixture.scope(DeviceCommentFixture.instagram(8, CommentPageRequest.Target.POST)),
                    com.socialpilot.app.domain.contract.social.ConnectionStatus.DISCONNECTED);
            fixture.store.failCleanup = true;
            try {
                CommentSyncWorker.afterInvalidation(app, fixture.repo, a.channelId, 7);
                fail("expected interrupted cleanup");
            } catch (com.socialpilot.app.data.local.comment.CommentPageWriter.Rejected rejected) {
                assertEquals("STORAGE", rejected.reason.name());
            }
            assertEquals(cachedB, fixture.repo.loadCached(b, CommentStore.Handled.HANDLED));
            assertEquals(1, fixture.store.counts(a.channelId, 7).rows());
            fixture.seed(fresh);
            UUID newer = enqueue(fresh);
            assertTrue(fixture.cancelled.await(5, TimeUnit.SECONDS));
            assertEquals(WorkInfo.State.CANCELLED, finish(active).getState());
            assertEquals(WorkInfo.State.CANCELLED, finish(waiting).getState());
            assertEquals("CHANNEL_DISCONNECTED", pull.poll(5, TimeUnit.SECONDS).error().code.name());
            assertEquals(3, fixture.calls.get());
            assertEquals(WorkInfo.State.ENQUEUED, wm.getWorkInfoById(other).get().getState());
            assertEquals(WorkInfo.State.ENQUEUED, wm.getWorkInfoById(newer).get().getState());
            fixture.release.countDown();
            driver.setAllConstraintsMet(newer);
            assertEquals("COMPLETE", finish(newer).getOutputData().getString("state"));
            assertTrue(fixture.repo.loadCached(fresh, CommentStore.Handled.HANDLED).rows().isEmpty());
            assertTrue(fixture.store.markHandled(fresh, "device-id"));
            var cachedA9 = fixture.repo.loadCached(fresh, CommentStore.Handled.HANDLED);
            fixture.store.failCleanup = false;
            for (int retry = 0; retry < 2; retry++) {
                CommentSyncWorker.afterInvalidation(app, fixture.repo, a.channelId, 7);
                assertEquals(new com.socialpilot.app.data.local.publishedpost.SimulatedCommentStore.Counts(0, 0, 0, 0, 0, 0),
                        fixture.store.counts(a.channelId, 7));
                assertEquals(full, fixture.store.counts(a.channelId, 9));
                assertEquals(full, fixture.store.counts(b.channelId, 7));
                assertEquals(cachedA9, fixture.repo.loadCached(fresh, CommentStore.Handled.HANDLED));
                assertEquals(cachedB, fixture.repo.loadCached(b, CommentStore.Handled.HANDLED));
            }
            driver.setAllConstraintsMet(other);
            assertEquals("COMPLETE", finish(other).getOutputData().getString("state"));
            assertEquals(5, fixture.calls.get());
            assertEquals(1, fixture.repo.loadCached(b, CommentStore.Handled.ALL).rows().size());
            assertEquals(1, fixture.repo.loadCached(fresh, CommentStore.Handled.ALL).rows().size());
        } finally {
            fixture.release.countDown();
            workers.shutdownNow();
        }
    }

    @Test public void stopCancelsInFlightHttp() throws Exception {
        UUID id = enqueue();
        assertTrue(fixture.entered.await(15, TimeUnit.SECONDS));
        try {
            wm.cancelWorkById(id).getResult().get(10, TimeUnit.SECONDS);
            assertTrue(fixture.cancelled.await(5, TimeUnit.SECONDS));
        } finally { fixture.release.countDown(); }
        assertEquals(WorkInfo.State.CANCELLED, finish(id).getState());
        assertEquals(1, fixture.calls.get());
        assertNull(fixture.repo.loadCached(fixture.query, CommentStore.Handled.ALL).paging());
    }

    @Test public void retryAfterCannotExceedStartBudgetOrSendEarlyHttp() {
        fixture.status = 429;
        fixture.release.countDown();
        Data input = CommentSyncWorker.encode(fixture.repo.prepareWork(fixture.query));
        assertEquals(new HashSet<>(Arrays.asList("query", "revision", "completedBefore")), input.getKeyValueMap().keySet());
        UUID work = UUID.randomUUID();
        for (int start = 0; start <= CommentSyncCoordinator.MAX_WORK_STARTS; start++) {
            CommentSyncWorker worker = androidx.work.testing.TestListenableWorkerBuilder.from(app, CommentSyncWorker.class)
                    .setInputData(input).setId(work).setRunAttemptCount(start).build();
            ListenableWorker.Result outcome = worker.doWork();
            if (start + 1 < CommentSyncCoordinator.MAX_WORK_STARTS) assertEquals(ListenableWorker.Result.retry(), outcome);
            else {
                assertTrue(outcome instanceof ListenableWorker.Result.Failure);
                assertEquals("EXHAUSTED", ((ListenableWorker.Result.Failure) outcome).getOutputData().getString("state"));
            }
            assertEquals(1, fixture.calls.get());
        }
        assertNull(fixture.repo.loadCached(fixture.query, CommentStore.Handled.ALL).lastCompleteSyncAt());
    }

    @Test public void networkLossDuringSecondPageKeepsCheckpointAndResumes() throws Exception {
        fixture.twoPages = true;
        UUID id = enqueue();
        assertTrue(fixture.entered.await(15, TimeUnit.SECONDS));
        try {
            shell("svc wifi disable");
            shell("svc data disable");
            assertTrue(fixture.cancelled.await(30, TimeUnit.SECONDS));
            // WorkManager's stop signal can arrive before ConnectivityManager publishes
            // the final loss of the active default network.
            ConnectivityManager net = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            long until = SystemClock.elapsedRealtime() + 10_000;
            while (net.getActiveNetwork() != null && SystemClock.elapsedRealtime() < until) SystemClock.sleep(50);
            CommentRepository.View offline = fixture.repo.loadCached(fixture.query, CommentStore.Handled.ALL);
            assertFalse(offline.online());
            assertEquals(1, offline.rows().size());
            assertEquals(com.socialpilot.app.data.local.comment.CommentCheckpoint.State.MORE, offline.paging());
            assertNull(offline.lastCompleteSyncAt());
            assertEquals(2, fixture.calls.get());
        } finally {
            fixture.release.countDown();
            shell("svc wifi enable");
            shell("svc data enable");
        }
        assertEquals("COMPLETE", finish(id).getOutputData().getString("state"));
        assertEquals(3, fixture.calls.get());
        assertTrue(fixture.resumedPosition);
        assertEquals(2, fixture.repo.loadCached(fixture.query, CommentStore.Handled.ALL).rows().size());
    }

    /** Run queue/resume as separate instrumentation invocations, starting with both radios off. */
    @Test public void offlineRestartAndPull() throws Exception {
        String phase = InstrumentationRegistry.getArguments().getString("phase");
        Assume.assumeTrue(phase != null);
        ConnectivityManager net = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        assertNull(net.getActiveNetwork());
        SharedPreferences mark = app.getSharedPreferences("sp041-device-test", Context.MODE_PRIVATE);
        boolean disconnect = "true".equals(InstrumentationRegistry.getArguments().getString("disconnect"));
        if ("queue".equals(phase)) {
            UUID id = enqueue();
            assertEquals(id, enqueue());
            assertEquals(WorkInfo.State.ENQUEUED, wm.getWorkInfoById(id).get().getState());
            assertEquals(0, fixture.calls.get());
            if (disconnect) {
                fixture.invalidate(8);
                assertTrue(mark.edit().putLong("retiredAtRevision", 8L).commit());
            }
            assertTrue(mark.edit().putString("work", id.toString()).putInt("pid", android.os.Process.myPid()).commit());
            report("offline pid=" + android.os.Process.myPid() + " work=" + id + " requests=0");
            return;
        }
        assertEquals("resume", phase);
        UUID id = UUID.fromString(mark.getString("work", null));
        assertNotEquals(mark.getInt("pid", -1), android.os.Process.myPid());
        assertEquals(WorkInfo.State.ENQUEUED, wm.getWorkInfoById(id).get().getState());
        assertEquals(0, fixture.calls.get());
        if (disconnect) {
            long revision = mark.getLong("retiredAtRevision", -1);
            assertEquals(8, revision);
            fixture.invalidate(revision);
            var fresh = DeviceCommentFixture.query(fixture.query.channelId, revision + 1, FB_STREAM);
            fixture.seed(fresh);
            shell("svc wifi enable");
            shell("svc data enable");
            WorkInfo stale = finish(id);
            assertEquals(WorkInfo.State.FAILED, stale.getState());
            assertEquals("OBSOLETE", stale.getOutputData().getString("state"));
            assertEquals(0, fixture.calls.get());
            assertNull(fixture.repo.loadCached(fresh, CommentStore.Handled.ALL).paging());
            UUID newId = enqueue(fresh);
            fixture.release.countDown();
            assertEquals("COMPLETE", finish(newId).getOutputData().getString("state"));
            assertEquals(1, fixture.calls.get());
            assertEquals(1, fixture.repo.loadCached(fresh, CommentStore.Handled.ALL).rows().size());
            report("resume pid=" + android.os.Process.myPid() + " oldWork=" + id + " OBSOLETE calls=0; A9 COMPLETE calls=1");
            return;
        }
        shell("svc wifi enable");
        shell("svc data enable");
        assertTrue(fixture.entered.await(60, TimeUnit.SECONDS));
        ArrayBlockingQueue<CommentSyncCoordinator.Result> pull = new ArrayBlockingQueue<>(1);
        fixture.repo.refresh(fixture.query, pull::offer);
        fixture.release.countDown();
        assertEquals(CommentSyncCoordinator.State.COMPLETE, pull.poll(10, TimeUnit.SECONDS).state());
        WorkInfo info = finish(id);
        assertEquals(WorkInfo.State.SUCCEEDED, info.getState());
        assertEquals("COMPLETE", info.getOutputData().getString("state"));
        assertEquals(1, fixture.calls.get());
        assertEquals(1, fixture.repo.loadCached(fixture.query, CommentStore.Handled.ALL).rows().size());
        report("online pid=" + android.os.Process.myPid() + " work=" + id + " requests=1 rows=1 state=COMPLETE");
    }

    private static void shell(String command) throws Exception {
        ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd)) { while (in.read() != -1) {} }
    }
    private static void report(String text) {
        Bundle b = new Bundle();
        b.putString("stream", text + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, b);
    }
}
