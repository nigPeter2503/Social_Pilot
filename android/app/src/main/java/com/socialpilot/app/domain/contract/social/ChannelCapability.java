package com.socialpilot.app.domain.contract.social;

import java.util.Objects;

/** verifiedAt is an ISO 8601 UTC timestamp, or null when not yet verified. */
public final class ChannelCapability {
    public final SocialOperation operation;
    public final CapabilityAvailability availability;
    public final String reasonCode;
    public final String verifiedAt;

    public ChannelCapability(SocialOperation operation, CapabilityAvailability availability,
            String reasonCode, String verifiedAt) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.availability = Objects.requireNonNull(availability, "availability");
        if (availability != CapabilityAvailability.UNVERIFIED) {
            ContractChecks.text(verifiedAt, "verifiedAt");
        }
        if (verifiedAt != null) {
            ContractChecks.timestamp(verifiedAt, "verifiedAt");
        }
        if (availability != CapabilityAvailability.AVAILABLE) {
            ContractChecks.text(reasonCode, "reasonCode");
        }
        this.reasonCode = reasonCode;
        this.verifiedAt = verifiedAt;
    }
}
