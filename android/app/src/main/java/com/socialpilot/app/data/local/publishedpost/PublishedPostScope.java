package com.socialpilot.app.data.local.publishedpost;

import com.socialpilot.app.domain.contract.social.AccountType;
import com.socialpilot.app.domain.contract.social.Platform;
import java.util.Objects;

/** Credential-free projection of the subject/revision supplied by HO-CHANNEL. */
public final class PublishedPostScope {
    public final Platform platform;
    public final AccountType accountType;
    public final String channelId;
    public final long connectionRevision;
    public final String externalAccountId;

    public PublishedPostScope(Platform platform, AccountType accountType, String channelId,
            long connectionRevision, String externalAccountId) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.accountType = Objects.requireNonNull(accountType, "accountType");
        if (platform == Platform.FACEBOOK && accountType != AccountType.PAGE
                || platform == Platform.LINKEDIN && accountType == AccountType.PAGE
                || connectionRevision < 1) throw invalid();
        this.channelId = opaque(channelId);
        this.externalAccountId = opaque(externalAccountId);
        this.connectionRevision = connectionRevision;
    }

    public boolean same(PublishedPostScope other) {
        return other != null && platform == other.platform && accountType == other.accountType
                && channelId.equals(other.channelId) && connectionRevision == other.connectionRevision
                && externalAccountId.equals(other.externalAccountId);
    }

    static String opaque(String value) {
        if (value == null || value.trim().isEmpty()) throw invalid();
        return value; // No trimming, case folding, numeric conversion or splitting URNs.
    }

    static String reference(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}")) throw invalid();
        return value;
    }

    static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid published post input");
    }

    @Override public String toString() { return "PublishedPostScope[redacted]"; }
}
