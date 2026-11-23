package com.socialpilot.app.domain.contract.social;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChannelCapabilityTest {
    @Test
    public void rejectsMalformedTimestampsForEveryAvailability() {
        for (CapabilityAvailability availability : CapabilityAvailability.values()) {
            for (String timestamp : new String[] {"", " ", "not-a-date",
                    "2026-02-30T12:00:00Z", "2026-11-09T12:00:00+07:00",
                    "2026-11-09T12:00:00.000Z", "2026-11-09T24:00:00Z"}) {
                assertThrows(availability + ": " + timestamp, IllegalArgumentException.class,
                        () -> capability(availability, timestamp));
            }
        }
    }

    @Test
    public void acceptsValidUtcIncludingLeapDayForEveryAvailability() {
        for (CapabilityAvailability availability : CapabilityAvailability.values()) {
            for (String timestamp : new String[] {"2026-11-09T12:00:00Z", "2024-02-29T00:00:00Z"}) {
                assertEquals(timestamp, capability(availability, timestamp).verifiedAt);
            }
        }
    }

    @Test
    public void onlyUnverifiedAllowsMissingTimestamp() {
        for (CapabilityAvailability availability : CapabilityAvailability.values()) {
            if (availability == CapabilityAvailability.UNVERIFIED) {
                assertNull(capability(availability, null).verifiedAt);
            } else {
                assertThrows(IllegalArgumentException.class, () -> capability(availability, null));
            }
        }
    }

    private ChannelCapability capability(CapabilityAvailability availability, String timestamp) {
        return new ChannelCapability(SocialOperation.PUBLISH_TEXT, availability, "TEST", timestamp);
    }
}
