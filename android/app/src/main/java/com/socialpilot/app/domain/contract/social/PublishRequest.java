package com.socialpilot.app.domain.contract.social;

/** References an immutable approved version; carries no mutable content or credentials. */
public final class PublishRequest {
    public final String requestId;
    public final String channelId;
    public final long connectionRevision;
    public final String approvedVersionId;

    public PublishRequest(String requestId, String channelId, long connectionRevision,
            String approvedVersionId) {
        this.requestId = ContractChecks.text(requestId, "requestId");
        this.channelId = ContractChecks.text(channelId, "channelId");
        this.connectionRevision = ContractChecks.revision(connectionRevision);
        this.approvedVersionId = ContractChecks.text(approvedVersionId, "approvedVersionId");
    }
}
