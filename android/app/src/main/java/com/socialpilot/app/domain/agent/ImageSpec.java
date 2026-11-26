package com.socialpilot.app.domain.agent;

import java.util.Objects;

/**
 * Per-channel image configuration. Sizes are not settled by the team yet (no verified platform
 * numbers in the repo), so the defaults are {@link Verification#PROVISIONAL}: they let the template
 * render and are carried into every asset so publishing (SP-025) can refuse unverified specs.
 */
public final class ImageSpec {
    public enum Verification { PROVISIONAL, VERIFIED }

    public final ChannelPlatform platform;
    public final int widthPx;
    public final int heightPx;
    /** Size string sent to the image API; null when no supported size is confirmed for the model. */
    public final String providerSize;
    /** Style direction appended to generation prompts. */
    public final String style;
    /** Template colours, ARGB. */
    public final int backgroundTop;
    public final int backgroundBottom;
    public final int accent;
    public final int textColor;
    public final Verification verification;
    public final String source;

    public ImageSpec(ChannelPlatform platform, int widthPx, int heightPx, String providerSize, String style,
                     int backgroundTop, int backgroundBottom, int accent, int textColor,
                     Verification verification, String source) {
        if (widthPx < 64 || heightPx < 64 || widthPx > 4096 || heightPx > 4096) {
            throw new IllegalArgumentException("Invalid image size");
        }
        this.platform = Objects.requireNonNull(platform);
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.providerSize = providerSize;
        this.style = Objects.requireNonNull(style);
        this.backgroundTop = backgroundTop;
        this.backgroundBottom = backgroundBottom;
        this.accent = accent;
        this.textColor = textColor;
        this.verification = Objects.requireNonNull(verification);
        this.source = Objects.requireNonNull(source);
    }

    /** Provisional defaults: different size, style and colours per channel; none verified. */
    public static ImageSpec defaults(ChannelPlatform platform) {
        String source = "Provisional SP-032 default, not verified against Meta docs; confirm with SP-025 before publish";
        switch (platform) {
            case INSTAGRAM:
                return new ImageSpec(platform, 1080, 1350, null,
                        "bright, vertical feed photo, bold subject, no text in the image",
                        0xFF833AB4, 0xFFF77737, 0xFFFFFFFF, 0xFFFFFFFF, Verification.PROVISIONAL, source);
            default:
                return new ImageSpec(platform, 1080, 1080, null,
                        "clean square photo for a Facebook Page post, no text in the image",
                        0xFF1E3A8A, 0xFF2563EB, 0xFFFACC15, 0xFFFFFFFF, Verification.PROVISIONAL, source);
        }
    }

    public ImageSpec withProviderSize(String size) {
        return new ImageSpec(platform, widthPx, heightPx, size, style, backgroundTop, backgroundBottom, accent,
                textColor, verification, source);
    }
}
