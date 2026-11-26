package com.socialpilot.app.data.repository;

import com.socialpilot.app.domain.contract.social.CapabilityAvailability;
import com.socialpilot.app.domain.contract.social.ChannelCapability;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.SocialOperation;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Read-only metadata for the channel screen. No credentials cross this boundary. */
public final class ChannelDirectory {
    public static final class Identity {
        public final String channelId;
        public final String platform;
        public final String accountType;
        public final String externalAccountId;
        public final Long revision;

        public Identity(String channelId, String platform, String accountType,
                String externalAccountId, Long revision) {
            this.channelId = channelId;
            this.platform = platform;
            this.accountType = accountType;
            this.externalAccountId = externalAccountId;
            this.revision = revision;
        }

        private boolean matches(Identity other) {
            return channelId != null && revision != null && channelId.equals(other.channelId)
                    && platform.equals(other.platform) && accountType.equals(other.accountType)
                    && externalAccountId.equals(other.externalAccountId) && revision.equals(other.revision);
        }
    }

    public static final class Evidence {
        public final Identity identity;
        public final ChannelCapability capability;

        public Evidence(Identity identity, ChannelCapability capability) {
            this.identity = identity;
            this.capability = capability;
        }
    }

    public static final class Channel {
        public final Identity identity;
        public final String displayName;
        public final ConnectionStatus status;
        private final Map<SocialOperation, ChannelCapability> capabilities;

        public Channel(Identity identity, String displayName, ConnectionStatus status,
                List<Evidence> evidence) {
            this.identity = identity;
            this.displayName = displayName;
            this.status = status;
            Map<SocialOperation, ChannelCapability> scoped = new EnumMap<>(SocialOperation.class);
            for (Evidence entry : evidence) {
                if (!identity.matches(entry.identity)) continue;
                ChannelCapability capability = entry.capability;
                ChannelCapability previous = scoped.get(capability.operation);
                if (previous == null || previous.verifiedAt == null || (capability.verifiedAt != null
                        && capability.verifiedAt.compareTo(previous.verifiedAt) >= 0)) {
                    scoped.put(capability.operation, capability);
                }
            }
            capabilities = Collections.unmodifiableMap(scoped);
        }

        public ChannelCapability capability(SocialOperation operation) {
            ChannelCapability capability = capabilities.get(operation);
            return capability != null ? capability : new ChannelCapability(
                    operation, CapabilityAvailability.UNVERIFIED, "NOT_VERIFIED", null);
        }
    }

    private ChannelDirectory() {}
}
