package com.socialpilot.app.data.local.publishedpost;

/** Normalized provider post with stable local identity and per-field provenance. */
public final class PublishedPost {
    public final String id;
    public final PublishedPostScope scope;
    public final String externalPostId;
    public final String content;
    public final String permalink;
    public final Long publishedAt;
    public final Long providerUpdatedAt;
    /** Receipt watermark of the accepted metadata, not the provider publication time. */
    public final long receivedAt;
    public final PublishedPostInput.Source source;
    public final String sourceRef;
    public final String ownershipEvidenceRef;
    /** Retained fields keep their original evidence instead of inheriting a later sparse response. */
    public final String contentSourceRef;
    public final String permalinkSourceRef;
    public final String publishedAtSourceRef;

    PublishedPost(String id, PublishedPostInput input, PublishedPost previous) {
        this.id = PublishedPostScope.opaque(id);
        scope = input.scope;
        externalPostId = input.externalPostId;
        content = choose(input.content, previous == null ? null : previous.content);
        permalink = choose(input.permalink, previous == null ? null : previous.permalink);
        publishedAt = choose(input.publishedAt, previous == null ? null : previous.publishedAt);
        providerUpdatedAt = choose(input.providerUpdatedAt,
                previous == null ? null : previous.providerUpdatedAt);
        receivedAt = previous == null ? input.receivedAt : Math.max(input.receivedAt, previous.receivedAt);
        source = input.source;
        sourceRef = input.sourceRef;
        ownershipEvidenceRef = input.ownership.evidenceRef;
        contentSourceRef = input.content != null ? input.sourceRef
                : previous == null ? null : previous.contentSourceRef;
        permalinkSourceRef = input.permalink != null ? input.sourceRef
                : previous == null ? null : previous.permalinkSourceRef;
        publishedAtSourceRef = input.publishedAt != null ? input.sourceRef
                : previous == null ? null : previous.publishedAtSourceRef;
    }

    private static <T> T choose(T incoming, T previous) { return incoming == null ? previous : incoming; }

    @Override public String toString() { return "PublishedPost[redacted]"; }
}
