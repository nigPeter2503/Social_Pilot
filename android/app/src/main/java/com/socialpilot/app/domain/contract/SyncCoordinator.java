package com.socialpilot.app.domain.contract;
/** Extension contract; no provider implementation is registered yet. */
public interface SyncCoordinator {
    void requestSync(String channelId);
}
