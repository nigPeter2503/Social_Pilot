package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.data.local.snapshot.SimulatedSnapshotDao;
import com.socialpilot.app.data.local.snapshot.Snapshot;
import com.socialpilot.app.data.repository.metrics.*;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.*;

public final class DeviceMetricFixture implements MetricProvider {
    public static final Snapshot.Scope SCOPE = new Snapshot.Scope(Platform.FACEBOOK,
            FollowerRequest.Account.FACEBOOK_PAGE, "MO_PHONG-channel", "MO_PHONG-page", 7);
    public final SimulatedSnapshotDao dao = new SimulatedSnapshotDao();
    public final MetricCollectionCoordinator coordinator;
    public int calls;
    public boolean online = true;
    private final MetricCollectionCoordinator.Clock clock;
    public DeviceMetricFixture() { this(System::currentTimeMillis); }
    public DeviceMetricFixture(MetricCollectionCoordinator.Clock clock) {
        this.clock = clock;
        dao.bind(new ChannelInfo(SCOPE.channelId(), SCOPE.platform(), SCOPE.externalAccountId(),
                AccountType.PAGE, "MO_PHONG", ConnectionStatus.CONNECTED, 7, "mock-ref"));
        coordinator = new MetricCollectionCoordinator(dao, FollowerResult.Source.MO_PHONG, this,
                clock, () -> online);
    }
    @Override public Cancelable read(FollowerRequest r, FollowerMetricAdapter.ResultCallback callback) {
        calls++;
        FollowerResult.MetricDefinition d = new FollowerResult.MetricDefinition(r);
        FollowerResult.MetricSample s = new FollowerResult.MetricSample(r, d, 0L, null, clock.now(),
                FollowerResult.Source.MO_PHONG, "MO_PHONG-device");
        callback.complete(new FollowerResult(FollowerResult.Status.SUCCESS, d, s, null, null, null));
        return () -> {};
    }
    @Override public Cancelable read(PostRequest r, PostMetricAdapter.ResultCallback callback) {
        throw new AssertionError("unexpected post");
    }
}
