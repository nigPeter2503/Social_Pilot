package com.socialpilot.app.domain.contract.social;

import java.util.Objects;

/** Channel metadata only. Never place an access token in tokenReference. */
public final class ChannelInfo {
    public final String channelId;
    public final Platform platform;
    public final String externalAccountId;
    public final AccountType accountType;
    public final String displayName;
    public final ConnectionStatus connectionStatus;
    public final long connectionRevision;
    public final String tokenReference;

    public ChannelInfo(String channelId, Platform platform, String externalAccountId,
            AccountType accountType, String displayName, ConnectionStatus connectionStatus,
            long connectionRevision, String tokenReference) {
        this.channelId = ContractChecks.text(channelId, "channelId");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.externalAccountId = ContractChecks.text(externalAccountId, "externalAccountId");
        this.accountType = Objects.requireNonNull(accountType, "accountType");
        this.displayName = ContractChecks.text(displayName, "displayName");
        this.connectionStatus = Objects.requireNonNull(connectionStatus, "connectionStatus");
        this.connectionRevision = ContractChecks.revision(connectionRevision);
        if (connectionStatus == ConnectionStatus.DISCONNECTED) {
            if (tokenReference != null) throw new IllegalArgumentException("Disconnected channel has no token reference");
        } else {
            ContractChecks.text(tokenReference, "tokenReference");
        }
        this.tokenReference = tokenReference;
    }
}
