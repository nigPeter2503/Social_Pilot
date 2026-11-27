package com.socialpilot.app.data.ai.image;

import static org.junit.Assert.*;

import com.socialpilot.app.domain.agent.ChannelPlatform;
import com.socialpilot.app.domain.agent.ImageSpec;
import com.socialpilot.app.domain.agent.TemplateLayout;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.ByteString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class ImageStoreAndProviderTest {
    private File dir;
    private MockWebServer server;

    @Before public void setUp() throws Exception {
        dir = Files.createTempDirectory("store").toFile();
        server = new MockWebServer();
        server.start();
    }

    @After public void tearDown() throws Exception {
        server.shutdown();
        File[] files = dir.listFiles();
        if (files != null) for (File file : files) file.delete();
        dir.delete();
    }

    private static AiRequest imageRequest() {
        return new AiRequest("s", "page-1", 3L, "image", "req-1", AiRequest.Provider.OPENAI, "fixture-image",
                AiRequest.Task.IMAGE, "A cup of coffee", 1);
    }

    private OpenAiImageProvider provider(String key) {
        return new OpenAiImageProvider(server.url("/v1/images/generations"), () -> key, 5_000);
    }

    @Test public void storeCommitsAtomicallyAndCleanupKeepsReferencedImages() throws Exception {
        ImageStore previous = new ImageStore(dir);
        ImageStore.Stored kept = previous.save(ImageServiceTest.png(10, 10));
        ImageStore.Stored orphan = previous.save(ImageServiceTest.png(20, 20));
        assertTrue(new File(dir, "img_00000000-0000-0000-0000-000000000000.png.tmp").createNewFile());
        ImageStore store = new ImageStore(dir);
        assertEquals(64, kept.sha256.length());
        assertNotNull(store.file(kept.ref));
        assertEquals(2, store.cleanup(new HashSet<>(Collections.singletonList(kept.ref)))); // orphan + temp
        assertNotNull(store.file(kept.ref));
        assertNull(store.file(orphan.ref));
        // References are opaque names; anything else is refused.
        assertNull(store.file("../secrets.xml"));
        assertNull(store.file("/data/data/x.png"));
        assertNull(store.file(null));
        assertTrue(store.delete(kept.ref));
        assertFalse(store.delete(kept.ref));
    }

    @Test public void cleanupWithStaleReferencesKeepsAConcurrentSaveInHandoff() throws Exception {
        ImageStore previous = new ImageStore(dir);
        ImageStore.Stored orphan = previous.save(ImageServiceTest.png(10, 10));
        ImageStore store = new ImageStore(dir);
        var snapshotBeforeSave = Collections.<String>emptySet();
        ExecutorService writer = Executors.newSingleThreadExecutor();
        java.util.concurrent.CountDownLatch saved = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch handoff = new java.util.concurrent.CountDownLatch(1);
        try {
            Future<ImageStore.Stored> pending = writer.submit(() -> {
                ImageStore.Stored image = store.save(ImageServiceTest.png(20, 20));
                saved.countDown();
                if (!handoff.await(5, TimeUnit.SECONDS)) throw new AssertionError("handoff was not released");
                return image;
            });
            assertTrue(saved.await(5, TimeUnit.SECONDS));
            assertEquals(1, store.cleanup(snapshotBeforeSave));
            assertNull(store.file(orphan.ref));
            handoff.countDown();
            assertNotNull(store.file(pending.get(5, TimeUnit.SECONDS).ref));
        } finally {
            handoff.countDown();
            writer.shutdownNow();
        }
    }

    @Test public void pngHeaderIsRequired() {
        assertNotNull(PngInfo.read(ImageServiceTest.png(1080, 1350)));
        assertEquals(1350, PngInfo.read(ImageServiceTest.png(1080, 1350)).height);
        assertNull(PngInfo.read("GIF89a........................".getBytes()));
        assertNull(PngInfo.read(new byte[8]));
        assertNull(PngInfo.read(ImageServiceTest.png(0, 10)));
    }

    @Test public void templateLayoutShrinksWrapsAndTruncatesWithoutLosingWords() {
        ImageSpec spec = ImageSpec.defaults(ChannelPlatform.FACEBOOK);
        TemplateLayout.Measurer monospace = (text, size) -> text.length() * size * 0.6f;
        TemplateLayout small = TemplateLayout.of(spec, "Quán cà phê mới", "SocialPilot", monospace);
        assertEquals(Collections.singletonList("Quán cà phê mới"), small.lines);
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 80; i++) longText.append("word").append(i).append(' ');
        TemplateLayout big = TemplateLayout.of(spec, longText.toString(), "", monospace);
        assertTrue(big.lines.size() <= 5);
        assertTrue(big.lines.get(big.lines.size() - 1).endsWith("…"));
        assertTrue(big.headlineSizePx < small.headlineSizePx);
        float maxWidth = spec.widthPx - 2f * big.marginPx;
        for (String line : big.lines) assertTrue(line, monospace.width(line, big.headlineSizePx) <= maxWidth);
        List<String> broken = TemplateLayout.of(spec, "x".repeat(200), "", monospace).lines;
        for (String line : broken) assertFalse(line.isEmpty());
        assertTrue(TemplateLayout.of(spec, "   ", "", monospace).lines.isEmpty());
    }

    @Test public void templateWrapAndEllipsisKeepWholeSurrogatePairs() {
        ImageSpec spec = ImageSpec.defaults(ChannelPlatform.FACEBOOK);
        TemplateLayout split = TemplateLayout.of(spec, "a🚀b", "", (text, size) -> text.length() * 450);
        assertEquals(Arrays.asList("a", "🚀", "b"), split.lines);
        TemplateLayout truncated = TemplateLayout.of(spec, "🚀".repeat(8), "",
                (text, size) -> text.length() * 400);
        assertEquals(Arrays.asList("🚀", "🚀", "🚀", "🚀", "…"), truncated.lines);
    }

    @Test public void malformedOptionalUsageDoesNotDiscardACompletedImage() {
        byte[] png = ImageServiceTest.png(1024, 1024);
        String payload = "{\"data\":[{\"b64_json\":\"" + ByteString.of(png).base64() + "\"}],"
                + "\"usage\":{\"input_tokens\":-1,\"output_tokens\":1.5,\"total_tokens\":\"bad\"}}";
        ImageProvider.Response response = OpenAiImageProvider.parse(payload);
        assertEquals(ImageProvider.Response.Outcome.SUCCEEDED, response.outcome);
        assertArrayEquals(png, response.image);
        assertEquals(com.socialpilot.app.domain.contract.ai.AiUsage.State.MISSING, response.usage.state);
    }

    @Test public void openAiImageSendsOneSizedRequestAndDecodesBase64() throws Exception {
        byte[] png = ImageServiceTest.png(1024, 1024);
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(
                "{\"created\":1,\"data\":[{\"b64_json\":\"" + ByteString.of(png).base64() + "\"}],"
                        + "\"usage\":{\"input_tokens\":12,\"output_tokens\":100,\"total_tokens\":112}}"));
        ImageProvider.Response response = provider("fake-openai-key-for-fixture").prepare(imageRequest(), "1024x1024").run();
        assertEquals(ImageProvider.Response.Outcome.SUCCEEDED, response.outcome);
        assertArrayEquals(png, response.image);
        assertEquals(Long.valueOf(112), response.usage.totalTokens);
        RecordedRequest sent = server.takeRequest();
        assertEquals("Bearer fake-openai-key-for-fixture", sent.getHeader("Authorization"));
        String body = sent.getBody().readUtf8();
        assertTrue(body.contains("\"size\":\"1024x1024\""));
        assertTrue(body.contains("\"n\":1"));
        assertTrue(body.contains("\"model\":\"fixture-image\""));
    }

    @Test public void urlOnlyOrMalformedResponsesAreProtocolErrors() {
        for (String body : Arrays.asList("{\"data\":[{\"url\":\"https://signed.example/img?sig=abc\"}]}",
                "{\"data\":[]}", "not json", "{\"data\":[{\"b64_json\":\"!!!\"}]}")) {
            server.enqueue(new MockResponse().setBody(body));
            ImageProvider.Response response = provider("fake-key-1234567890").prepare(imageRequest(), "1024x1024").run();
            assertEquals(body, ImageProvider.Response.Outcome.FAILED, response.outcome);
            assertEquals(AiError.Code.PROTOCOL_ERROR, response.error.code);
            assertTrue(response.mayHaveDispatched);
        }
    }

    @Test public void httpErrorsMapToContractCodes() {
        int[] statuses = {401, 403, 429, 500, 400};
        AiError.Code[] codes = {AiError.Code.INVALID_CREDENTIAL, AiError.Code.PERMISSION_DENIED,
                AiError.Code.RATE_LIMITED, AiError.Code.PROVIDER_UNAVAILABLE, AiError.Code.INVALID_REQUEST};
        for (int i = 0; i < statuses.length; i++) {
            server.enqueue(new MockResponse().setResponseCode(statuses[i]).setBody("{\"error\":{}}"));
            assertEquals(codes[i], provider("fake-key-1234567890").prepare(imageRequest(), "1024x1024").run().error.code);
        }
    }

    @Test public void missingOrInvalidKeyNeverSendsAndCancelStopsTheCall() throws Exception {
        ImageProvider.Response noKey = provider("bad key").prepare(imageRequest(), "1024x1024").run();
        assertEquals(AiError.Code.INVALID_CREDENTIAL, noKey.error.code);
        assertFalse(noKey.mayHaveDispatched);
        assertEquals(0, server.getRequestCount());
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        ImageProvider.Attempt attempt = provider("fake-key-1234567890").prepare(imageRequest(), "1024x1024");
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            Future<ImageProvider.Response> running = thread.submit(attempt::run);
            server.takeRequest(5, TimeUnit.SECONDS);
            attempt.cancel();
            ImageProvider.Response cancelled = running.get(5, TimeUnit.SECONDS);
            assertEquals(ImageProvider.Response.Outcome.CANCELLED, cancelled.outcome);
            assertTrue(cancelled.mayHaveDispatched);
        } finally {
            thread.shutdownNow();
        }
        ImageProvider.Attempt early = provider("fake-key-1234567890").prepare(imageRequest(), "1024x1024");
        early.cancel();
        assertFalse(early.run().mayHaveDispatched);
        assertThrows(IllegalArgumentException.class, () -> provider("k").prepare(
                new AiRequest("s", null, null, "x", "r", AiRequest.Provider.OPENAI, "m", AiRequest.Task.TEXT, "p", 1),
                "1024x1024"));
    }
}
