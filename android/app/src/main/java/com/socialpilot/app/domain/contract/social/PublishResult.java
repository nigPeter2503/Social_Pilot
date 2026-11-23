package com.socialpilot.app.domain.contract.social;

import java.util.Objects;

/** Factories prevent success without evidence and prevent an unknown outcome enabling retry. */
public final class PublishResult {
    public enum Outcome { SUCCESS, REJECTED, UNKNOWN, PUBLISHED_URL_PENDING }

    public final String requestId;
    public final String channelId;
    public final long connectionRevision;
    public final Outcome outcome;
    public final String postId;
    public final String postUrl;
    public final SocialError error;

    private PublishResult(PublishRequest request, Outcome outcome, String postId,
            String postUrl, SocialError error) {
        Objects.requireNonNull(request, "request");
        this.requestId = request.requestId;
        this.channelId = request.channelId;
        this.connectionRevision = request.connectionRevision;
        this.outcome = outcome;
        this.postId = postId;
        this.postUrl = postUrl;
        this.error = error;
    }

    public static PublishResult success(PublishRequest request, String postId, String postUrl) {
        return new PublishResult(request, Outcome.SUCCESS,
                ContractChecks.text(postId, "postId"), ContractChecks.httpsUrl(postUrl), null);
    }

    public static PublishResult rejected(PublishRequest request, SocialError error) {
        Objects.requireNonNull(error, "error");
        if (error.code == SocialError.Code.PUBLISH_RESULT_UNKNOWN
                || error.nextAction == SocialError.NextAction.RECONCILE) {
            throw new IllegalArgumentException("Uncertain result must use UNKNOWN");
        }
        return new PublishResult(request, Outcome.REJECTED, null, null, error);
    }

    public static PublishResult unknown(PublishRequest request) {
        return new PublishResult(request, Outcome.UNKNOWN, null, null,
                new SocialError(SocialError.Code.PUBLISH_RESULT_UNKNOWN,
                        SocialError.NextAction.RECONCILE));
    }

    public static PublishResult publishedUrlPending(PublishRequest request, String postId) {
        return new PublishResult(request, Outcome.PUBLISHED_URL_PENDING,
                ContractChecks.text(postId, "postId"), null, null);
    }
}
