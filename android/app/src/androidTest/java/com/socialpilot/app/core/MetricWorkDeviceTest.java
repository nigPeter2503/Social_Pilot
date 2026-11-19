package com.socialpilot.app.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.*;
import androidx.work.testing.*;
import com.socialpilot.app.data.local.snapshot.Snapshot;
import com.socialpilot.app.data.remote.social.*;
import com.socialpilot.app.data.repository.metrics.MetricJob;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import static org.junit.Assert.*;
import static com.socialpilot.app.data.remote.social.DeviceMetricFixture.SCOPE;

public class MetricWorkDeviceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();

    private static WorkerFactory factory(DeviceMetricFixture fixture) {
        return new WorkerFactory() {
            @Override public ListenableWorker createWorker(Context app, String name, WorkerParameters params) {
                return name.equals(MetricCollectionWorker.class.getName()) ? new MetricCollectionWorker(app, params, fixture.coordinator) : null;
            }
        };
    }

    @Test public void recreatedWorkersReuseInputAndOnlyRetryWhenDue() {
        java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong(1_795_060_800_000L);
        DeviceMetricFixture fixture = new DeviceMetricFixture(time::get);
        MetricJob job = MetricJob.fresh(time.get(), SCOPE, "v27.0", true, Collections.emptyList());
        Data input = MetricCollectionWork.encode(job);
        UUID id = UUID.randomUUID();
        fixture.online = false;
        for (int start = 0; start <= 2; start++) {
            MetricCollectionWorker worker = TestListenableWorkerBuilder.from(context, MetricCollectionWorker.class)
                    .setInputData(input).setId(id).setWorkerFactory(factory(fixture)).setRunAttemptCount(start).build();
            ListenableWorker.Result result = worker.doWork();
            if (start < 2) assertEquals(ListenableWorker.Result.retry(), result);
            else assertTrue(result instanceof ListenableWorker.Result.Success);
            assertEquals(start == 2 ? 1 : 0, fixture.calls);
            fixture.online = true;
            time.addAndGet(15_000);
        }
        com.socialpilot.app.data.local.snapshot.SnapshotDao.History history =
                fixture.dao.read(SCOPE, FollowerResult.Source.MO_PHONG, job.collectionStartedAt(), time.get());
        assertEquals(2, history.attempts().size());
        assertNotEquals(history.attempts().get(0).attemptId(), history.lastAttempt().attemptId());
        assertEquals(1, history.snapshots().size());
        assertEquals(job.collectionId(), history.lastAttempt().key().collectionId());
        assertEquals(job.collectionStartedAt(), history.lastAttempt().collectionStartedAt());
        assertEquals(job.collectionStartedAt() + 30_000, history.snapshots().get(0).collectedAt());
    }

    @Test public void driverQueuesTwoManualCollectionsAndRunsWorkerOnlyWhenConnected() throws Exception {
        DeviceMetricFixture fixture = new DeviceMetricFixture();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, new Configuration.Builder()
                .setWorkerFactory(factory(fixture)).setExecutor(new SynchronousExecutor()).build());
        try {
            WorkManager wm = WorkManager.getInstance(context);
            TestDriver driver = WorkManagerTestInitHelper.getTestDriver(context);
            MetricCollectionWork work = new MetricCollectionWork(context, fixture.coordinator, System::currentTimeMillis);
            MetricCollectionWork.Ticket a = work.manualRefresh(SCOPE, "v27.0", true, Collections.emptyList());
            a.enqueue().getResult().get(10, TimeUnit.SECONDS);
            MetricCollectionWork.Ticket b = work.manualRefresh(SCOPE, "v27.0", true, Collections.emptyList());
            b.enqueue().getResult().get(10, TimeUnit.SECONDS);
            assertEquals(WorkInfo.State.ENQUEUED, wm.getWorkInfoById(a.workId()).get().getState());
            assertEquals(WorkInfo.State.BLOCKED, wm.getWorkInfoById(b.workId()).get().getState());
            assertEquals(0, fixture.calls);
            driver.setAllConstraintsMet(a.workId());
            assertEquals(a.job().collectionId(), finished(wm, a.workId()).getString("resultCollection"));
            driver.setAllConstraintsMet(b.workId());
            assertEquals(b.job().collectionId(), finished(wm, b.workId()).getString("resultCollection"));
            assertNotEquals(a.job().collectionId(), b.job().collectionId());
            assertEquals(2, fixture.calls);
            assertEquals(2, fixture.dao.read(SCOPE, FollowerResult.Source.MO_PHONG, 0, Long.MAX_VALUE).snapshots().size());
        } finally { WorkManagerTestInitHelper.closeWorkDatabase(); }
    }

    @Test public void restoresPostTargetsAndVerifiedPeriodFromWorkData() {
        Snapshot.Scope scope = new Snapshot.Scope(com.socialpilot.app.domain.contract.social.Platform.LINKEDIN,
                FollowerRequest.Account.LINKEDIN_MEMBER, "fixture", "urn:li:person:fixture", 4);
        PostRequest.Period period = new PostRequest.Period("2026-11-01", "2026-11-08", "UTC", "MO_PHONG");
        MetricJob job = MetricJob.fresh(17, scope, "202610", false, Collections.singletonList(
                new MetricJob.PostTarget("urn:li:share:0007", PostRequest.Query.MEMBER_COMMENT, period)));
        MetricJob copy = MetricCollectionWork.decode(MetricCollectionWork.encode(job));
        assertEquals(job.collectionId(), copy.collectionId());
        assertEquals(17, copy.collectionStartedAt());
        assertEquals(scope, copy.scope());
        assertFalse(copy.follower());
        assertEquals("urn:li:share:0007", copy.posts().get(0).postId());
        assertEquals(PostRequest.Query.MEMBER_COMMENT, copy.posts().get(0).query());
        assertEquals(period.end, copy.posts().get(0).period().end);
    }

    /** Invoke separately in two processes with -e sp045Phase queue/resume on an offline disposable AVD. */
    @Test public void offlineProcessRestart() throws Exception {
        String phase = InstrumentationRegistry.getArguments().getString("sp045Phase");
        Assume.assumeTrue("requires two externally controlled processes", phase != null);
        MetricDeviceRunner.FixtureApplication app = (MetricDeviceRunner.FixtureApplication) context;
        WorkManager wm = WorkManager.getInstance(context);
        SharedPreferences marker = context.getSharedPreferences("MO_PHONG-device-restart", Context.MODE_PRIVATE);
        ConnectivityManager network = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assertNull("device must start offline", network.getActiveNetwork());
        if ("queue".equals(phase)) {
            MetricCollectionWork.Ticket t = app.container().metrics.manualRefresh(SCOPE, "v27.0", true, Collections.emptyList());
            t.enqueue().getResult().get(10, TimeUnit.SECONDS);
            assertEquals(WorkInfo.State.ENQUEUED, wm.getWorkInfoById(t.workId()).get().getState());
            assertEquals(0, app.fixture.calls);
            assertTrue(marker.edit().putString("work", t.workId().toString()).putString("id", t.job().collectionId())
                    .putLong("started", t.job().collectionStartedAt()).putInt("pid", Process.myPid()).commit());
            report("queued pid=" + Process.myPid() + " work=" + t.workId() + " collection=" + t.job().collectionId()
                    + " started=" + t.job().collectionStartedAt() + " calls=0");
        } else {
            assertEquals("resume", phase);
            assertNotEquals(marker.getInt("pid", -1), Process.myPid());
            UUID id = UUID.fromString(marker.getString("work", null));
            assertEquals(WorkInfo.State.ENQUEUED, wm.getWorkInfoById(id).get().getState());
            assertEquals(0, app.fixture.calls);
            shell("svc wifi enable");
            shell("svc data enable");
            Data data = finished(wm, id);
            assertEquals("COMPLETE", data.getString("state"));
            assertEquals(marker.getString("id", null), data.getString("resultCollection"));
            assertEquals(marker.getLong("started", -1), data.getLong("resultStarted", -2));
            Snapshot snap = app.fixture.dao.read(SCOPE, FollowerResult.Source.MO_PHONG, 0, Long.MAX_VALUE).snapshots().get(0);
            assertEquals(data.getString("resultCollection"), snap.key().collectionId());
            assertEquals(data.getLong("resultStarted", -1), snap.collectionStartedAt());
            assertEquals(1, app.fixture.calls);
            report("resumed pid=" + Process.myPid() + " work=" + id + " collection=" + snap.key().collectionId()
                    + " started=" + snap.collectionStartedAt() + " calls=1 snapshot=" + snap.snapshotId());
        }
    }
    private static Data finished(WorkManager wm, UUID id) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 90_000;
        WorkInfo info;
        do {
            info = wm.getWorkInfoById(id).get(5, TimeUnit.SECONDS);
            if (info.getState().isFinished()) break;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertEquals(WorkInfo.State.SUCCEEDED, info.getState());
        return info.getOutputData();
    }
    private static void shell(String command) throws Exception {
        ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd)) { while (in.read() != -1) { } }
    }
    private static void report(String line) {
        Bundle b = new Bundle();
        b.putString("stream", line + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, b);
    }
}
