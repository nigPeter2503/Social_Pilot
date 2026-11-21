package com.socialpilot.app.data.local.snapshot;

import com.socialpilot.app.data.remote.social.FollowerRequest;
import com.socialpilot.app.data.remote.social.FollowerResult.Source;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.domain.contract.social.AccountType;
import com.socialpilot.app.domain.contract.social.ChannelInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A completed subject batch. POST subjectId is the local PublishedPost PK. */
public record Snapshot(String snapshotId, Key key, Scope scope, Source source, long collectionStartedAt,
        long collectedAt, List<MetricSample> samples) {
    public enum Subject { CHANNEL, POST }

    public record Scope(Platform platform, FollowerRequest.Account account, String channelId,
            String externalAccountId, long connectionRevision) {
        public boolean matches(ChannelInfo channel) {
            AccountType expected = account.type;
            return channel != null && account.platform == platform && platform == channel.platform && expected == channel.accountType
                    && channelId.equals(channel.channelId) && externalAccountId.equals(channel.externalAccountId)
                    && connectionRevision == channel.connectionRevision;
        }
    }

    public record Key(String channelId, Subject subjectType, String subjectId, String collectionId) {}

    public Snapshot {
        samples = Collections.unmodifiableList(new ArrayList<>(samples));
    }
}
