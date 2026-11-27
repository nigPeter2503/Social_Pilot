package com.socialpilot.app.domain.agent;

import java.util.Objects;

/**
 * What the editor and publisher receive for an image: a private reference and metadata only. No
 * absolute path, signed URL or credential is ever part of it. Attaching it to a version creates a
 * new unapproved version (SP-036 contract); deleting the file is decided by reference count.
 */
public final class ImageAsset {
    public enum Kind { GENERATED, TEMPLATE }

    public final String channelId;
    public final long connectionRevision;
    public final Kind kind;
    /** Opaque file name inside the app-private image area, for example {@code img_<uuid>.png}. */
    public final String ref;
    public final String mimeType;
    public final int widthPx;
    public final int heightPx;
    public final long byteSize;
    public final String sha256;
    /** False while the channel spec is provisional; publishing must treat it as unverified. */
    public final boolean specVerified;
    /** Ledger requestId of the generation; null for templates (no provider call). */
    public final String requestId;
    /** Template identifier for TEMPLATE assets. */
    public final String templateId;
    /** Why a template was used instead of generation, for example MODEL_BLOCKED; null otherwise. */
    public final String fallbackReason;

    public ImageAsset(String channelId, long connectionRevision, Kind kind, String ref, String mimeType, int widthPx,
                      int heightPx, long byteSize, String sha256, boolean specVerified, String requestId,
                      String templateId, String fallbackReason) {
        this.channelId = Objects.requireNonNull(channelId);
        this.connectionRevision = connectionRevision;
        this.kind = Objects.requireNonNull(kind);
        this.ref = Objects.requireNonNull(ref);
        this.mimeType = Objects.requireNonNull(mimeType);
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.byteSize = byteSize;
        this.sha256 = Objects.requireNonNull(sha256);
        this.specVerified = specVerified;
        this.requestId = requestId;
        this.templateId = templateId;
        this.fallbackReason = fallbackReason;
    }
}
