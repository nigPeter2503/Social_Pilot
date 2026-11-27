package com.socialpilot.app.domain.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class SharedUrlParserTest {
    @Test public void extractsLinkFromShareTextWithoutTrailingPunctuation() {
        assertEquals("https://example.com/article?q=1",
                SharedUrlParser.firstPublicWebUrl("Read this (https://example.com/article?q=1)."));
    }

    @Test public void rejectsLocalOrCredentialUrlsAndFindsNextPublicLink() {
        assertEquals("https://news.example.org/story",
                SharedUrlParser.firstPublicWebUrl("http://127.0.0.1/test "
                        + "https://user:pass@example.com/private "
                        + "https://news.example.org/story"));
        assertNull(SharedUrlParser.firstPublicWebUrl("http://localhost/"));
        assertNull(SharedUrlParser.firstPublicWebUrl("https://intranet.local/page"));
    }

    @Test public void rejectsUnsupportedOrOversizedShareText() {
        assertNull(SharedUrlParser.firstPublicWebUrl("ftp://example.com/file"));
        assertNull(SharedUrlParser.firstPublicWebUrl("https://example.com/" + "x".repeat(2100)));
        assertNull(SharedUrlParser.firstPublicWebUrl("x".repeat(16_385)));
    }
}
