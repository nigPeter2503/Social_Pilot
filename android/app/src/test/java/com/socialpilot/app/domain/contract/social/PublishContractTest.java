package com.socialpilot.app.domain.contract.social;

import org.junit.Test;

import static org.junit.Assert.*;

public class PublishContractTest {
    private final PublishRequest request = new PublishRequest("r1", "c1", 1, "v1");

    @Test
    public void successRequiresPostEvidenceAndPreservesCorrelation() {
        PublishResult result = PublishResult.success(request, "p1", "https://example.com/posts/p1");
        assertEquals("r1", result.requestId);
        assertEquals("c1", result.channelId);
        assertEquals(1, result.connectionRevision);
        assertEquals(PublishResult.Outcome.SUCCESS, result.outcome);
        assertThrows(IllegalArgumentException.class, () -> PublishResult.success(request, "", result.postUrl));
        assertThrows(IllegalArgumentException.class, () -> PublishResult.success(request, "p1", null));
        assertThrows(IllegalArgumentException.class, () -> PublishResult.success(request, "p1", "http://example.com/p1"));
    }

    @Test
    public void uncertainDeliveryCannotBecomeRetryableRejection() {
        PublishResult result = PublishResult.unknown(request);
        assertEquals(PublishResult.Outcome.UNKNOWN, result.outcome);
        assertEquals(SocialError.NextAction.RECONCILE, result.error.nextAction);
        assertNull(result.postId);
        assertNull(result.postUrl);
        assertThrows(IllegalArgumentException.class, () -> PublishResult.rejected(request, result.error));
        assertThrows(IllegalArgumentException.class, () -> new SocialError(
                SocialError.Code.PUBLISH_RESULT_UNKNOWN, SocialError.NextAction.RETRY_WHEN_SAFE));
    }

    @Test
    public void permissionAndExpiredTokenHaveDifferentRemediation() {
        PublishResult permission = PublishResult.rejected(request, new SocialError(
                SocialError.Code.PERMISSION_REQUIRED, SocialError.NextAction.RESOLVE_PERMISSION));
        PublishResult auth = PublishResult.rejected(request, new SocialError(
                SocialError.Code.AUTH_REQUIRED, SocialError.NextAction.RECONNECT));
        assertEquals(PublishResult.Outcome.REJECTED, permission.outcome);
        assertNull(permission.postId);
        assertEquals(SocialError.NextAction.RECONNECT, auth.error.nextAction);
    }

    @Test
    public void knownPostWithoutUrlIsNotSuccessOrPermissionToRepublish() {
        PublishResult result = PublishResult.publishedUrlPending(request, "p1");
        assertEquals(PublishResult.Outcome.PUBLISHED_URL_PENDING, result.outcome);
        assertEquals("p1", result.postId);
        assertNull(result.postUrl);
        assertNull(result.error);
    }

    @Test
    public void missingApprovalReferenceOrInvalidRevisionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PublishRequest("r", "c", 1, " "));
        assertThrows(IllegalArgumentException.class, () -> new PublishRequest("r", "c", 0, "v"));
    }

    @Test
    public void disconnectedMetadataCannotRetainTokenReference() {
        assertThrows(IllegalArgumentException.class, () -> new ChannelInfo("c", Platform.FACEBOOK,
                "page", AccountType.PAGE, "Demo", ConnectionStatus.DISCONNECTED, 2, "secret-ref"));
        ChannelInfo disconnected = new ChannelInfo("c", Platform.FACEBOOK, "page", AccountType.PAGE,
                "Demo", ConnectionStatus.DISCONNECTED, 2, null);
        assertNull(disconnected.tokenReference);
    }

    @Test
    public void availableCapabilityRequiresVerificationEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new ChannelCapability(
                SocialOperation.PUBLISH_TEXT, CapabilityAvailability.AVAILABLE, null, null));
        ChannelCapability unknown = new ChannelCapability(SocialOperation.READ_POST_METRICS,
                CapabilityAvailability.UNVERIFIED, "NOT_TESTED", null);
        assertEquals(CapabilityAvailability.UNVERIFIED, unknown.availability);
    }
}
