package com.socialpilot.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.util.Collections;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

public final class PublicArticleRepositoryTest {
    @Test public void importsOnlyPublicHtmlMetadataAndKeepsFinalCitationUrl() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(302)
                    .setHeader("Location", "/article"));
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<html><head><title>A public article</title>"
                            + "<meta name='description' content='Summary'></head>"
                            + "<body><p>Article body is not copied into the brief.</p></body></html>"));
            server.start();
            String input = server.url("/start").newBuilder().host("example.com").build().toString();
            PublicArticleRepository.Result result = repository().inspect(input);
            assertTrue(result.isSuccess());
            assertEquals(input, result.requestedUrl);
            assertEquals(server.url("/article").newBuilder().host("example.com").build()
                    .toString(), result.url);
            assertEquals("A public article", result.title);
            assertEquals("Summary", result.description);
        }
    }

    @Test public void reportsLoginAndMissingMetadataWithoutInventingContent() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(401));
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/html")
                    .setBody("<html><form><input type='password'></form></html>"));
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/html")
                    .setBody("<html><body>Unlabelled content</body></html>"));
            server.start();
            String input = server.url("/story").newBuilder().host("example.com").build().toString();
            PublicArticleRepository importer = repository();
            assertEquals(PublicArticleRepository.Failure.LOGIN_REQUIRED,
                    importer.inspect(input).failure);
            assertEquals(PublicArticleRepository.Failure.LOGIN_REQUIRED,
                    importer.inspect(input).failure);
            PublicArticleRepository.Result missing = importer.inspect(input);
            assertFalse(missing.isSuccess());
            assertEquals(PublicArticleRepository.Failure.EMPTY_METADATA, missing.failure);
            assertEquals("", missing.title);
        }
    }

    @Test public void rejectsLocalInputBeforeNetworkRequest() {
        assertEquals(PublicArticleRepository.Failure.INVALID_URL,
                repository().inspect("http://127.0.0.1/private").failure);
    }

    @Test public void refusesRedirectToLocalAddressAndOversizedResponse() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(302)
                    .setHeader("Location", "http://127.0.0.1/private"));
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/html")
                    .setBody("x".repeat(131_073)));
            server.start();
            String input = server.url("/story").newBuilder().host("example.com").build().toString();
            PublicArticleRepository importer = repository();
            assertEquals(PublicArticleRepository.Failure.INVALID_URL,
                    importer.inspect(input).failure);
            assertEquals(PublicArticleRepository.Failure.TOO_LARGE,
                    importer.inspect(input).failure);
        }
    }

    private static PublicArticleRepository repository() {
        OkHttpClient client = new OkHttpClient.Builder().followRedirects(false)
                .dns(host -> Collections.singletonList(InetAddress.getByName("127.0.0.1")))
                .build();
        return new PublicArticleRepository(client);
    }
}
