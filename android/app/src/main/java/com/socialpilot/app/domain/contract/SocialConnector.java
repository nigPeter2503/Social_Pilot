package com.socialpilot.app.domain.contract;
/** Extension contract; no provider implementation is registered yet. */
public interface SocialConnector {
    String platform();
    boolean canPublish();
    // Implement only after real OAuth and permission verification.
    Cancelable publish(String approvedVersionId, Result result);
    interface Result { void onSuccess(String postId, String url); void onError(String code); }
}
