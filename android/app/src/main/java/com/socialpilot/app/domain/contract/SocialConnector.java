package com.socialpilot.app.domain.contract;

import com.socialpilot.app.domain.contract.social.ChannelCapability;
import com.socialpilot.app.domain.contract.social.ChannelInfo;
import com.socialpilot.app.domain.contract.social.PublishRequest;
import com.socialpilot.app.domain.contract.social.PublishResult;
import com.socialpilot.app.domain.contract.social.SocialOperation;

/**
 * One connector per channel connection revision. No production adapter is registered yet.
 * See docs/social-contract.md for approval, reconciliation and disconnect obligations.
 */
public interface SocialConnector {
    ChannelInfo channel();

    /** Missing evidence must return UNVERIFIED, never infer permission from OAuth success. */
    ChannelCapability capability(SocialOperation operation);

    /**
     * Non-blocking. Validate approval and connection revision immediately before sending.
     * Deliver at most one terminal result, on a background thread; the consumer dispatches UI work.
     * Cancel must cancel the HTTP call and suppress subsequent callbacks, not imply remote rollback.
     * Persist an uncertain in-flight outcome for reconciliation even if its UI callback is suppressed.
     */
    Cancelable publish(PublishRequest request, Result callback);

    interface Result {
        void onResult(PublishResult result);
    }
}
