package com.socialpilot.app.domain.contract;
/** Extension contract; no provider implementation is registered yet. */
public interface PublishQueue {
    void enqueueApprovedVersion(String channelId, String versionId);
}
