package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.domain.contract.social.AccountType;
import com.socialpilot.app.domain.contract.social.ChannelInfo;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.Objects;

/** Adapter-local request; does not extend the pending shared analytics contract. */
public final class FollowerRequest {
    public enum Account { FACEBOOK_PAGE(Platform.FACEBOOK, AccountType.PAGE),
        LINKEDIN_MEMBER(Platform.LINKEDIN, AccountType.PERSON),
        LINKEDIN_ORGANIZATION(Platform.LINKEDIN, AccountType.ORGANIZATION),
        INSTAGRAM_PROFESSIONAL(Platform.INSTAGRAM, AccountType.PROFESSIONAL);

        public final Platform platform;
        public final AccountType type;
        Account(Platform platform, AccountType type) {
            this.platform = platform;
            this.type = type;
        }
    }

    public final Account account;
    public final Platform platform;
    public final String channelId;
    public final long connectionRevision;
    public final String externalAccountId;
    public final String apiVersion;
    public final String collectionId;

    public FollowerRequest(Account account, String channelId, long connectionRevision,
            String externalAccountId, String apiVersion, String collectionId) {
        this.account = Objects.requireNonNull(account, "account");
        this.platform = account.platform;
        this.channelId = text(channelId);
        if (connectionRevision <= 0) throw new IllegalArgumentException("connectionRevision");
        this.connectionRevision = connectionRevision;
        this.externalAccountId = text(externalAccountId);
        this.apiVersion = text(apiVersion);
        this.collectionId = text(collectionId);
        if (platform != Platform.LINKEDIN && !apiVersion.matches("v[0-9]+\\.[0-9]+")
                || platform == Platform.LINKEDIN && !apiVersion.matches("[0-9]{4}(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("apiVersion");
        }
        if (account == Account.LINKEDIN_MEMBER && !externalAccountId.matches("urn:li:person:[A-Za-z0-9_-]+")
                || account == Account.LINKEDIN_ORGANIZATION && !externalAccountId.matches("urn:li:organization:[0-9]+")) {
            throw new IllegalArgumentException("externalAccountId/account");
        }
    }

    public boolean matches(ChannelInfo channel) {
        AccountType expected = account.type;
        return channel != null && channelId.equals(channel.channelId) && platform == channel.platform
                && connectionRevision == channel.connectionRevision && expected == channel.accountType
                && externalAccountId.equals(channel.externalAccountId);
    }

    boolean sameTarget(FollowerRequest other) {
        return other != null && account == other.account && platform == other.platform
                && channelId.equals(other.channelId) && connectionRevision == other.connectionRevision
                && externalAccountId.equals(other.externalAccountId) && apiVersion.equals(other.apiVersion);
    }

    static String text(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("text required");
        return value;
    }
}
