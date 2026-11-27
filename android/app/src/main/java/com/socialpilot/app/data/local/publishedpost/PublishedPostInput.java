package com.socialpilot.app.data.local.publishedpost;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/** Normalized input only: no provider body, credential, secret reference or PublishJob. */
public final class PublishedPostInput {
    public enum Source { API_THAT, FIXTURE_TU_API_DA_CHE, MO_PHONG, PROTOTYPE }

    /** Trusted mapping/endpoint evidence from the caller, never inferred from a post ID. */
    public static final class Ownership {
        public final PublishedPostScope scope;
        public final String externalPostId;
        public final String evidenceRef;

        public Ownership(PublishedPostScope scope, String externalPostId, String evidenceRef) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.externalPostId = PublishedPostScope.opaque(externalPostId);
            this.evidenceRef = PublishedPostScope.reference(evidenceRef);
        }
    }

    public final PublishedPostScope scope;
    public final String externalPostId;
    public final Ownership ownership;
    /** Null means omitted/unknown, including explicit provider null; never a delete command. */
    public final String content;
    public final String permalink;
    public final Long publishedAt;
    public final Long providerUpdatedAt;
    public final long receivedAt;
    public final Source source;
    public final String sourceRef;

    public PublishedPostInput(PublishedPostScope scope, String externalPostId, Ownership ownership,
            String content, String permalink, Long publishedAt, Long providerUpdatedAt,
            long receivedAt, Source source, String sourceRef) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.externalPostId = PublishedPostScope.opaque(externalPostId);
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        if (receivedAt < 0 || publishedAt != null && publishedAt < 0
                || providerUpdatedAt != null && providerUpdatedAt < 0) throw PublishedPostScope.invalid();
        if (permalink != null) validatePermalink(permalink);
        this.content = content;
        this.permalink = permalink;
        this.publishedAt = publishedAt;
        this.providerUpdatedAt = providerUpdatedAt;
        this.receivedAt = receivedAt;
        this.source = Objects.requireNonNull(source, "source");
        this.sourceRef = PublishedPostScope.reference(sourceRef);
    }

    private static void validatePermalink(String value) {
        try {
            URI uri = new URI(value);
            // Deliberately accept only canonical HTTPS links without opaque credential surfaces.
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() != -1)
                throw PublishedPostScope.invalid();
        } catch (URISyntaxException ignored) { throw PublishedPostScope.invalid(); }
    }

    @Override public String toString() { return "PublishedPostInput[redacted]"; }
}
