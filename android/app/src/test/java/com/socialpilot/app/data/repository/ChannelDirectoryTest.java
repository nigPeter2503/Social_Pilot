package com.socialpilot.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.socialpilot.app.data.repository.ChannelDirectory.Channel;
import com.socialpilot.app.data.repository.ChannelDirectory.Evidence;
import com.socialpilot.app.data.repository.ChannelDirectory.Identity;
import com.socialpilot.app.domain.contract.social.CapabilityAvailability;
import com.socialpilot.app.domain.contract.social.ChannelCapability;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.SocialOperation;
import java.util.List;
import org.junit.Test;

public class ChannelDirectoryTest {
    private static final String VERIFIED = "2026-11-21T02:00:00Z";
    private static final Identity FACEBOOK = new Identity("fb", "FACEBOOK", "PAGE", "page-demo", 1L);
    private static final Identity INSTAGRAM = instagram("ig-user-demo", 7L);

    @Test public void preservesFourStatesAndDoesNotInferOtherOperations() {
        Channel channel = channel(INSTAGRAM, List.of(
                evidence(INSTAGRAM, SocialOperation.PUBLISH_IMAGE, CapabilityAvailability.AVAILABLE, null, VERIFIED),
                evidence(INSTAGRAM, SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.PERMISSION_REQUIRED,
                        "MISSING_FOLLOWER_PERMISSION", VERIFIED),
                evidence(INSTAGRAM, SocialOperation.SET_ALT_TEXT, CapabilityAvailability.UNSUPPORTED,
                        "UNSUPPORTED", VERIFIED),
                evidence(INSTAGRAM, SocialOperation.READ_POST_METRICS, CapabilityAvailability.UNVERIFIED,
                        "PROVIDER_OMITTED", VERIFIED)));
        assertEquals(CapabilityAvailability.AVAILABLE, channel.capability(SocialOperation.PUBLISH_IMAGE).availability);
        assertEquals(VERIFIED, channel.capability(SocialOperation.PUBLISH_IMAGE).verifiedAt);
        assertEquals(CapabilityAvailability.PERMISSION_REQUIRED,
                channel.capability(SocialOperation.READ_FOLLOWER_COUNT).availability);
        assertEquals("MISSING_FOLLOWER_PERMISSION", channel.capability(SocialOperation.READ_FOLLOWER_COUNT).reasonCode);
        assertEquals(CapabilityAvailability.UNSUPPORTED, channel.capability(SocialOperation.SET_ALT_TEXT).availability);
        assertEquals(CapabilityAvailability.UNVERIFIED,
                channel.capability(SocialOperation.READ_POST_METRICS).availability);
        assertEquals(VERIFIED, channel.capability(SocialOperation.READ_POST_METRICS).verifiedAt);
        assertUnverified(channel.capability(SocialOperation.READ_COMMENTS));
        assertUnverified(channel.capability(SocialOperation.REPLY_COMMENT));
    }

    @Test public void keepsEvidenceWithinAccountAndRevisionWhenOldResponsesArrive() {
        Identity current = instagram("ig-user-demo", 8L);
        List<Evidence> evidence = List.of(
                evidence(current, SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.PERMISSION_REQUIRED,
                        "MISSING_FOLLOWER_PERMISSION", "2026-11-21T02:10:00Z"),
                evidence(INSTAGRAM, SocialOperation.READ_FOLLOWER_COUNT, CapabilityAvailability.AVAILABLE,
                        null, "2026-11-21T02:20:00Z"),
                evidence(INSTAGRAM, SocialOperation.READ_COMMENTS, CapabilityAvailability.AVAILABLE, null, VERIFIED),
                evidence(instagram("other-ig-account", 8L), SocialOperation.READ_COMMENTS,
                        CapabilityAvailability.AVAILABLE, null, VERIFIED),
                evidence(FACEBOOK, SocialOperation.READ_COMMENTS, CapabilityAvailability.AVAILABLE, null, VERIFIED));
        Channel updated = channel(current, evidence);
        assertEquals(CapabilityAvailability.PERMISSION_REQUIRED,
                updated.capability(SocialOperation.READ_FOLLOWER_COUNT).availability);
        assertUnverified(updated.capability(SocialOperation.READ_COMMENTS));
        assertEquals(CapabilityAvailability.AVAILABLE,
                channel(FACEBOOK, evidence).capability(SocialOperation.READ_COMMENTS).availability);
        assertUnverified(channel(FACEBOOK, evidence).capability(SocialOperation.READ_FOLLOWER_COUNT));
    }

    @Test public void retainsVerifiedSnapshotForReauthAndRecognizesStoredExpiry() throws Exception {
        Channel reauth = new Channel(INSTAGRAM, "Instagram fixture", ConnectionStatus.REAUTH_REQUIRED,
                List.of(evidence(INSTAGRAM, SocialOperation.READ_COMMENTS,
                        CapabilityAvailability.AVAILABLE, null, VERIFIED)));
        assertEquals(ConnectionStatus.REAUTH_REQUIRED, reauth.status);
        assertEquals(VERIFIED, reauth.capability(SocialOperation.READ_COMMENTS).verifiedAt);
        assertEquals(ConnectionStatus.REAUTH_REQUIRED,
                ChannelDirectoryRepository.connectionStatus("1970-01-01T00:00:01Z", 1000));
        assertEquals(ConnectionStatus.CONNECTED,
                ChannelDirectoryRepository.connectionStatus("1970-01-01T00:00:01Z", 999));
    }

    private static void assertUnverified(ChannelCapability capability) {
        assertEquals(CapabilityAvailability.UNVERIFIED, capability.availability);
        assertNull(capability.verifiedAt);
    }

    private static Identity instagram(String externalId, long revision) {
        return new Identity("ig-demo", "INSTAGRAM", "PROFESSIONAL", externalId, revision);
    }

    private static Channel channel(Identity identity, List<Evidence> evidence) {
        return new Channel(identity, "Fixture", ConnectionStatus.CONNECTED, evidence);
    }

    private static Evidence evidence(Identity identity, SocialOperation operation,
            CapabilityAvailability availability, String reason, String verifiedAt) {
        return new Evidence(identity, new ChannelCapability(operation, availability, reason, verifiedAt));
    }
}
