package com.socialpilot.app.domain.agent;

/** Platforms the pipeline can write for (SP-016 contract: one Facebook Page, one Instagram account). */
public enum ChannelPlatform {
    FACEBOOK, INSTAGRAM;

    /** Maps a brief target platform; null when the pipeline does not support it (for example LINKEDIN). */
    public static ChannelPlatform fromBrief(String platform) {
        if ("FACEBOOK".equals(platform)) return FACEBOOK;
        if ("INSTAGRAM".equals(platform)) return INSTAGRAM;
        return null;
    }
}
