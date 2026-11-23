package com.socialpilot.app.domain.agent;

import java.util.Objects;

/** One selected channel with the connection revision its output is bound to. */
public final class ChannelTarget {
    public final String channelId;
    public final ChannelPlatform platform;
    public final long connectionRevision;

    public ChannelTarget(String channelId, ChannelPlatform platform, long connectionRevision) {
        if (channelId == null || channelId.trim().isEmpty()) throw new IllegalArgumentException("channelId is required");
        if (connectionRevision <= 0) throw new IllegalArgumentException("connectionRevision must be positive");
        this.channelId = channelId;
        this.platform = Objects.requireNonNull(platform);
        this.connectionRevision = connectionRevision;
    }
}
