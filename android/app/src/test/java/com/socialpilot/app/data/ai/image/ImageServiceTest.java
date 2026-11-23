package com.socialpilot.app.data.ai.image;

import static com.socialpilot.app.domain.contract.ai.ModelCapabilities.Support.*;
import static org.junit.Assert.*;

import com.socialpilot.app.data.ai.ModelSelectionStore;
import com.socialpilot.app.data.ai.ledger.BudgetLedger;
import com.socialpilot.app.data.ai.ledger.MemoryLedgerStore;
import com.socialpilot.app.domain.agent.ChannelPlatform;
import com.socialpilot.app.domain.agent.ChannelTarget;
import com.socialpilot.app.domain.agent.ImageAsset;
import com.socialpilot.app.domain.agent.ImageSpec;
import com.socialpilot.app.domain.agent.TemplateLayout;
import com.socialpilot.app.domain.contract.BudgetManager;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.ModelCapabilities;
import com.socialpilot.app.domain.contract.ai.ModelEntry;
import com.socialpilot.app.domain.contract.ai.ModelRegistry;
import com.socialpilot.app.domain.contract.ai.PriceSheet;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import com.socialpilot.app.testing.MemoryPreferences;
import com.socialpilot.app.testing.RecordingBudget;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockWebServer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Labelled fake image provider and renderer; real store on a temp directory; recording budget. */
public final class ImageServiceTest {
    /** Labelled fixture price: 0.04 USD per image. */
    private static final PriceSheet PRICE = new PriceSheet(Currency.getInstance("USD"), BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.04"), "fixture-2026-11-23", "fixture",
            "2026-11-23T00:00:00Z");
    private static final ChannelTarget FACEBOOK = new ChannelTarget("page-1", ChannelPlatform.FACEBOOK, 3);
    private static final ChannelTarget INSTAGRAM = new ChannelTarget("ig-1", ChannelPlatform.INSTAGRAM, 5);
    private File dir;
    private ImageStore store;
    private RecordingBudget budget;
    private FakeProvider provider;
    private ModelSelectionStore selections;
    private ModelEntry verified;
    private ModelEntry unverified;
    private boolean revisionCurrent = true;
    private final List<ExecutorService> pools = new ArrayList<>();

    @Before public void setUp() throws Exception {
        dir = Files.createTempDirectory("images").toFile();
        store = new ImageStore(dir);
        budget = new RecordingBudget();
        provider = new FakeProvider();
        verified = entry("fixture-image", ModelEntry.Verification.VERIFIED, PRICE);
        unverified = entry("fixture-image-unverified", ModelEntry.Verification.UNVERIFIED, PRICE);
        selections = new ModelSelectionStore(new MemoryPreferences(),
                new ModelRegistry("fixture", Arrays.asList(verified, unverified)));
    }

    @After public void tearDown() {
        for (ExecutorService pool : pools) pool.shutdownNow();
        File[] files = dir.listFiles();
        if (files != null) for (File file : files) file.delete();
        dir.delete();
    }

    private static ModelEntry entry(String model, ModelEntry.Verification verification, PriceSheet price) {
        return new ModelEntry(new ModelCapabilities(AiRequest.Provider.OPENAI, model, UNSUPPORTED, UNSUPPORTED,
                SUPPORTED, UNKNOWN, 1), EnumSet.of(AiTaskType.IMAGE), verification, price, "fixture");
    }

    /** Minimal PNG header of the given size: enough for the IHDR check. */
    static byte[] png(int width, int height) {
        byte[] bytes = new byte[33];
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        System.arraycopy(head, 0, bytes, 0, head.length);
        for (int i = 0; i < 4; i++) {
            bytes[16 + i] = (byte) (width >>> (24 - 8 * i));
            bytes[20 + i] = (byte) (height >>> (24 - 8 * i));
        }
        return bytes;
    }

    private final TemplateRenderer renderer = (spec, headline, footer) -> png(spec.widthPx, spec.heightPx);

    private ImageService service(java.util.concurrent.Executor workers) {
        Map<AiRequest.Provider, ImageProvider> providers = new EnumMap<>(AiRequest.Provider.class);
        providers.put(AiRequest.Provider.OPENAI, provider);
        return new ImageService(selections, providers, p -> true, budget, store, renderer,
                (channel, revision) -> revisionCurrent, workers, () -> 1795451400000L);
    }

    private static ImageService.Request request(ChannelTarget target, ImageSpec spec) {
        return new ImageService.Request("session-1", target, "image", spec, "A cup of coffee on a table",
                "Quán cà phê mới khai trương", "SocialPilot");
    }

    private static ImageSpec sized(ChannelPlatform platform) {
        return ImageSpec.defaults(platform).withProviderSize("1024x1024");
    }

    private ImageService.Result run(ImageService service, boolean generate, ImageService.Request request) {
        AtomicReference<ImageService.Result> result = new AtomicReference<>();
        if (generate) service.generate(request, result::set);
        else service.template(request, result::set);
        return result.get();
    }

    @Test public void withoutAUsableModelTheTemplateIsUsedWithoutAnyReservation() {
        ImageService.Result none = run(service(Runnable::run), true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertEquals(ImageService.Result.Status.READY, none.status);
        assertEquals(ImageAsset.Kind.TEMPLATE, none.asset.kind);
        assertEquals("NO_MODEL_SELECTED", none.asset.fallbackReason);
        assertEquals(TemplateLayout.TEMPLATE_ID, none.asset.templateId);
        assertNull(none.asset.requestId);
        selections.select(AiTaskType.IMAGE, unverified);
        ImageService.Result blocked = run(service(Runnable::run), true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertEquals("MODEL_BLOCKED:UNVERIFIED", blocked.asset.fallbackReason);
        assertEquals(0, budget.count("reserve:"));
        assertTrue(provider.requests.isEmpty());
    }

    @Test public void unknownImageCapabilityOrMissingImagePriceIsNeverTreatedAsSupported() {
        ModelEntry unknown = new ModelEntry(new ModelCapabilities(AiRequest.Provider.OPENAI, "m", UNSUPPORTED,
                UNSUPPORTED, UNKNOWN, UNKNOWN, 1), EnumSet.of(AiTaskType.IMAGE), ModelEntry.Verification.VERIFIED,
                PRICE, "fixture");
        assertEquals(ModelEntry.Block.CAPABILITY_UNKNOWN, unknown.blockFor(AiTaskType.IMAGE, 1));
        PriceSheet tokensOnly = new PriceSheet(Currency.getInstance("USD"), BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, "fixture", "fixture", "2026-11-23T00:00:00Z");
        assertEquals(ModelEntry.Block.PRICE_MISSING,
                entry("m2", ModelEntry.Verification.VERIFIED, tokensOnly).blockFor(AiTaskType.IMAGE, 1));
        assertNull(tokensOnly.imageCost(1));
        assertEquals(40_000, PRICE.imageCost(1).micros);
    }

    @Test public void generationReservesThePerImagePriceSettlesOnceAndStoresOnlyAReference() {
        selections.select(AiTaskType.IMAGE, verified);
        provider.image = png(1024, 1024);
        provider.usage = new AiUsage(20L, 100L, 120L, null, null);
        ImageService.Result result = run(service(Runnable::run), true, request(INSTAGRAM, sized(ChannelPlatform.INSTAGRAM)));
        assertEquals(ImageService.Result.Status.READY, result.status);
        ImageAsset asset = result.asset;
        assertEquals(ImageAsset.Kind.GENERATED, asset.kind);
        assertEquals("ig-1", asset.channelId);
        assertEquals(5, asset.connectionRevision);
        assertEquals(1024, asset.widthPx);
        assertFalse(asset.specVerified); // Provisional spec travels with the asset.
        assertTrue(asset.ref.matches("img_[0-9a-f-]{36}\\.png"));
        assertNotNull(store.file(asset.ref));
        assertEquals(40_000, budget.lastReservedMicros);
        assertEquals(1, budget.count("settle:"));
        assertEquals(UsageRecord.Settlement.SETTLED, result.settlement);
        UsageRecord record = budget.usage.records.get(result.requestId);
        assertEquals(AiTaskType.IMAGE, record.taskType);
        assertEquals(Long.valueOf(120), record.usage.totalTokens);
        AiRequest sent = provider.requests.get(0);
        assertEquals(AiRequest.Task.IMAGE, sent.task);
        assertTrue(sent.prompt.contains("vertical feed photo")); // Instagram style, not Facebook's.
        assertEquals("1024x1024", provider.sizes.get(0));
    }

    @Test public void providerFailureAfterDispatchIsHeldUncertainAndDoesNotFallBackOnItsOwn() {
        selections.select(AiTaskType.IMAGE, verified);
        provider.failure = new AiError(AiError.Code.CONTENT_BLOCKED, null);
        ImageService service = service(Runnable::run);
        ImageService.Result failed = run(service, true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertEquals(ImageService.Result.Status.FAILED, failed.status);
        assertEquals(AiError.Code.CONTENT_BLOCKED, failed.error.code);
        assertEquals(UsageRecord.Settlement.UNCERTAIN, failed.settlement);
        assertNull(failed.asset);
        assertEquals(0, dir.listFiles().length);
        // The caller may choose the template explicitly; it costs nothing.
        ImageService.Result template = run(service, false, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertEquals(ImageAsset.Kind.TEMPLATE, template.asset.kind);
        assertNull(template.asset.fallbackReason);
        assertEquals(1, budget.count("reserve:"));
    }

    @Test public void failureBeforeDispatchReleasesTheReservationAndStoresTheTemplate() {
        selections.select(AiTaskType.IMAGE, verified);
        provider.failure = new AiError(AiError.Code.INVALID_CREDENTIAL, null);
        provider.dispatched = false;
        ImageService.Result result = run(service(Runnable::run), true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertEquals(ImageService.Result.Status.READY, result.status);
        assertEquals(ImageAsset.Kind.TEMPLATE, result.asset.kind);
        assertNotNull(store.file(result.asset.ref));
        assertEquals(UsageRecord.Settlement.RELEASED, result.settlement);
        assertEquals(1, budget.count("release:"));
    }

    @Test public void credentialLostAfterPreflightFallsBackWithoutSendingHttp() throws Exception {
        selections.select(AiTaskType.IMAGE, verified);
        try (MockWebServer server = new MockWebServer()) {
            Map<AiRequest.Provider, ImageProvider> adapters = new EnumMap<>(AiRequest.Provider.class);
            adapters.put(AiRequest.Provider.OPENAI, new OpenAiImageProvider(server.url("/v1/images/generations"),
                    () -> { throw new IllegalStateException("credential no longer readable"); }, 1_000));
            ImageService service = new ImageService(selections, adapters, p -> true, budget, store,
                    (spec, headline, footer) -> png(spec.widthPx, spec.heightPx),
                    (channel, revision) -> true, Runnable::run, () -> 1_000L);
            ImageService.Result result = run(service, true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
            assertEquals(ImageService.Result.Status.READY, result.status);
            assertEquals(ImageAsset.Kind.TEMPLATE, result.asset.kind);
            assertEquals("KEY_NOT_USABLE", result.asset.fallbackReason);
            assertEquals(UsageRecord.Settlement.RELEASED, result.settlement);
            assertNotNull(result.requestId);
            assertNull(result.asset.requestId);
            assertEquals(0, server.getRequestCount());
            assertEquals(1, budget.count("release:"));
            assertEquals(0, budget.count("settle:"));
        }
    }

    @Test public void providerExceptionsKeepAccountingConservative() {
        selections.select(AiTaskType.IMAGE, verified);
        Map<AiRequest.Provider, ImageProvider> adapters = new EnumMap<>(AiRequest.Provider.class);
        adapters.put(AiRequest.Provider.OPENAI, (request, size) -> {
            throw new IllegalStateException("prepare failed");
        });
        ImageService before = new ImageService(selections, adapters, p -> true, budget, store, renderer,
                (channel, revision) -> true, Runnable::run, () -> 1_000L);
        ImageService.Request request = request(FACEBOOK, sized(ChannelPlatform.FACEBOOK));
        ImageService.Result fallback = run(before, true, request);
        assertEquals(ImageAsset.Kind.TEMPLATE, fallback.asset.kind);
        assertEquals("PROVIDER_PRE_DISPATCH_FAILED", fallback.reason);
        assertEquals(UsageRecord.Settlement.RELEASED, fallback.settlement);

        adapters.put(AiRequest.Provider.OPENAI, (ai, size) -> new ImageProvider.Attempt() {
            @Override public void cancel() {}
            @Override public ImageProvider.Response run() { throw new IllegalStateException("run failed"); }
        });
        ImageService during = new ImageService(selections, adapters, p -> true, budget, store, renderer,
                (channel, revision) -> true, Runnable::run, () -> 1_000L);
        ImageService.Result failed = run(during, true, request);
        assertEquals(ImageService.Result.Status.FAILED, failed.status);
        assertEquals(UsageRecord.Settlement.UNCERTAIN, failed.settlement);
        assertNull(failed.asset);
        assertEquals(1, budget.count("release:"));
    }

    @Test public void callbackRunsOutsideTheJobMonitor() {
        Queue<Runnable> queued = new ArrayDeque<>();
        AtomicReference<ImageService.Job> job = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();
        ImageService service = service(queued::add);
        job.set(service.template(request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)), result -> {
            assertFalse(Thread.holdsLock(job.get()));
            callbacks.incrementAndGet();
        }));
        queued.remove().run();
        assertEquals(1, callbacks.get());
    }

    @Test public void budgetLimitOrMissingSizeFallsBackToTheTemplate() {
        selections.select(AiTaskType.IMAGE, verified);
        budget.next = BudgetManager.Admission.LIMIT_REACHED;
        ImageService.Result limited = run(service(Runnable::run), true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertEquals("BUDGET_LIMIT_REACHED", limited.asset.fallbackReason);
        ImageService.Result noSize = run(service(Runnable::run), true,
                request(FACEBOOK, ImageSpec.defaults(ChannelPlatform.FACEBOOK)));
        assertEquals("IMAGE_SIZE_UNVERIFIED", noSize.asset.fallbackReason);
        budget.limits = null;
        budget.next = BudgetManager.Admission.RESERVED;
        ImageService.Result noCaps = run(service(Runnable::run), true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertEquals("BUDGET_NOT_SET", noCaps.asset.fallbackReason);
        assertTrue(provider.requests.isEmpty());
    }

    @Test public void cancelMidGenerationKeepsTheChargeButNoFile() throws Exception {
        selections.select(AiTaskType.IMAGE, verified);
        provider.image = png(1024, 1024);
        provider.hang = true;
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pools.add(pool);
        AtomicReference<ImageService.Result> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        ImageService.Job job = service(pool).generate(request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)), r -> {
            result.set(r);
            done.countDown();
        });
        assertTrue(provider.started.await(5, TimeUnit.SECONDS));
        provider.succeedOnCancel = true; // The image arrived anyway: it is billed.
        job.cancel();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(ImageService.Result.Status.CANCELLED, result.get().status);
        assertEquals(UsageRecord.Settlement.SETTLED, result.get().settlement);
        assertEquals(0, dir.listFiles().length);
    }

    @Test public void cancellationDuringCommitDiscardsTheFileButKeepsSettlement() throws Exception {
        selections.select(AiTaskType.IMAGE, verified);
        Map<AiRequest.Provider, ImageProvider> adapters = new EnumMap<>(AiRequest.Provider.class);
        adapters.put(AiRequest.Provider.OPENAI, provider);
        CountDownLatch fileSaved = new CountDownLatch(1);
        CountDownLatch resumeCommit = new CountDownLatch(1);
        AtomicInteger revisionChecks = new AtomicInteger();
        ImageService.RevisionCheck revisions = (channel, revision) -> {
            if (revisionChecks.incrementAndGet() == 2) {
                fileSaved.countDown();
                try {
                    if (!resumeCommit.await(5, TimeUnit.SECONDS)) throw new AssertionError("commit was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            return true;
        };
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pools.add(pool);
        ImageService service = new ImageService(selections, adapters, p -> true, budget, store, renderer,
                revisions, pool, () -> 1_000L);
        var result = new java.util.concurrent.CompletableFuture<ImageService.Result>();
        ImageService.Job job = service.generate(request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)), result::complete);
        try {
            assertTrue(fileSaved.await(5, TimeUnit.SECONDS));
            assertEquals(1, dir.listFiles().length);
            job.cancel();
            resumeCommit.countDown();
            ImageService.Result terminated = result.get(5, TimeUnit.SECONDS);
            assertEquals(ImageService.Result.Status.CANCELLED, terminated.status);
            assertEquals(UsageRecord.Settlement.SETTLED, terminated.settlement);
            assertNull(terminated.asset);
            assertEquals(0, dir.listFiles().length);
        } finally {
            resumeCommit.countDown();
        }
    }

    @Test public void budgetExhaustionStopsARunningGeneration() throws Exception {
        selections.select(AiTaskType.IMAGE, verified);
        provider.hang = true;
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pools.add(pool);
        AtomicReference<ImageService.Result> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        service(pool).generate(request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)), r -> {
            result.set(r);
            done.countDown();
        });
        assertTrue(provider.started.await(5, TimeUnit.SECONDS));
        budget.exhaust();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(ImageService.Result.Status.FAILED, result.get().status);
        assertTrue(result.get().budgetStopped);
        assertEquals(UsageRecord.Settlement.UNCERTAIN, result.get().settlement);
    }

    @Test public void budgetStopBetweenReserveAndDispatchSendsNothingAndReleasesOnce() {
        selections.select(AiTaskType.IMAGE, verified);
        provider.image = png(1024, 1024);
        budget.afterReserve = budget::exhaust; // Cap lowered right after the reservation committed.
        ImageService.Result result = run(service(Runnable::run), true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertTrue(provider.requests.isEmpty());
        assertEquals(1, budget.count("release:"));
        assertEquals(0, budget.count("settle:"));
        assertEquals(ImageService.Result.Status.FAILED, result.status);
        assertTrue(result.budgetStopped);
        assertEquals("BUDGET_LIMIT_REACHED", result.reason);
        assertEquals(UsageRecord.Settlement.RELEASED, result.settlement);
        assertNull(result.asset);
    }

    @Test public void revokedReservationNeverReachesProviderWithoutLocalListener() {
        selections.select(AiTaskType.IMAGE, verified);
        MemoryLedgerStore ledgerStore = new MemoryLedgerStore();
        BudgetLedger workerLedger = new BudgetLedger(ledgerStore, () -> 1795451400000L);
        BudgetLedger settingsLedger = new BudgetLedger(ledgerStore, () -> 1795451400000L);
        settingsLedger.setLimits(new AttemptLedger.Limits("USD", 100_000, 200_000, "Asia/Bangkok"));
        AtomicInteger runs = new AtomicInteger();
        Map<AiRequest.Provider, ImageProvider> adapters = new EnumMap<>(AiRequest.Provider.class);
        adapters.put(AiRequest.Provider.OPENAI, (ai, size) -> {
            settingsLedger.setLimits(new AttemptLedger.Limits("USD", 40_000, 200_000, "Asia/Bangkok"));
            return new ImageProvider.Attempt() {
                @Override public ImageProvider.Response run() {
                    runs.incrementAndGet();
                    return ImageProvider.Response.cancelled(true);
                }

                @Override public void cancel() {}
            };
        });
        ImageService service = new ImageService(selections, adapters, p -> true, workerLedger, store, renderer,
                (channel, revision) -> true, Runnable::run, () -> 1795451400000L);

        ImageService.Result result = run(service, true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));

        assertEquals(0, runs.get());
        assertEquals(ImageService.Result.Status.FAILED, result.status);
        assertTrue(result.budgetStopped);
        assertEquals(UsageRecord.Settlement.RELEASED, result.settlement);
        assertNull(result.asset);
    }

    @Test public void ledgerWriteFailureIsReportedAndNeverShownAsSettled() {
        selections.select(AiTaskType.IMAGE, verified);
        provider.image = png(1024, 1024);
        budget.failNextComplete = true;
        ImageService.Result result = run(service(Runnable::run), true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertNotNull(result);
        assertTrue(result.ledgerWriteFailed);
        assertNull(result.settlement);
        assertNull(result.estimateMicros);
        assertNull(budget.usage.records.get(result.requestId)); // Nothing half-written.
    }

    @Test public void usageUsesThePeriodTheLedgerPinnedAcrossMidnight() {
        selections.select(AiTaskType.IMAGE, verified);
        provider.image = png(1024, 1024);
        // Service clock is 23:30 on 23/11 in Bangkok; the ledger reserves after midnight.
        budget.clock = () -> 1795451400000L + TimeUnit.HOURS.toMillis(1);
        ImageService.Result result = run(service(Runnable::run), true, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertEquals("2026-11-24", budget.usage.records.get(result.requestId).period.dayKey);
        assertEquals(UsageRecord.Settlement.SETTLED, result.settlement);
    }

    @Test public void staleRevisionNeverAddsAnImage() {
        revisionCurrent = false;
        ImageService.Result result = run(service(Runnable::run), false, request(FACEBOOK, sized(ChannelPlatform.FACEBOOK)));
        assertEquals(ImageService.Result.Status.STALE, result.status);
        assertNull(result.asset);
        assertEquals(0, dir.listFiles().length);
    }

    @Test public void eachChannelGetsItsOwnTemplateSizeAndSpec() {
        ImageService service = service(Runnable::run);
        ImageAsset facebook = run(service, false, request(FACEBOOK, ImageSpec.defaults(ChannelPlatform.FACEBOOK))).asset;
        ImageAsset instagram = run(service, false, request(INSTAGRAM, ImageSpec.defaults(ChannelPlatform.INSTAGRAM))).asset;
        assertNotEquals(facebook.heightPx, instagram.heightPx);
        assertNotEquals(facebook.ref, instagram.ref);
        assertEquals("page-1", facebook.channelId);
        assertEquals("ig-1", instagram.channelId);
        assertThrows(IllegalArgumentException.class,
                () -> request(FACEBOOK, ImageSpec.defaults(ChannelPlatform.INSTAGRAM)));
    }

    @Test public void invalidTemplateOutputIsReportedNotStored() {
        ImageService service = new ImageService(selections, new EnumMap<>(AiRequest.Provider.class), p -> true,
                budget, store, (spec, headline, footer) -> new byte[] {1, 2, 3},
                (channel, revision) -> true, Runnable::run, () -> 0L);
        ImageService.Result result = run(service, false, request(FACEBOOK, ImageSpec.defaults(ChannelPlatform.FACEBOOK)));
        assertEquals(ImageService.Result.Status.FAILED, result.status);
        assertEquals("INVALID_IMAGE", result.reason);
        assertEquals(0, dir.listFiles().length);
    }

    /** Labelled fake: succeeds, fails, or blocks until cancelled. */
    private static final class FakeProvider implements ImageProvider {
        final List<AiRequest> requests = Collections.synchronizedList(new ArrayList<>());
        final List<String> sizes = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch started = new CountDownLatch(1);
        volatile byte[] image = png(1024, 1024);
        volatile AiUsage usage = AiUsage.MISSING;
        volatile AiError failure;
        volatile boolean dispatched = true;
        volatile boolean hang;
        volatile boolean succeedOnCancel;

        @Override public Attempt prepare(AiRequest request, String size) {
            requests.add(request);
            sizes.add(size);
            CountDownLatch cancelled = new CountDownLatch(1);
            AtomicBoolean wasCancelled = new AtomicBoolean();
            return new Attempt() {
                @Override public Response run() {
                    if (hang) {
                        started.countDown();
                        try {
                            cancelled.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        if (succeedOnCancel) return Response.succeeded(image, usage);
                        return Response.cancelled(true);
                    }
                    if (wasCancelled.get()) return Response.cancelled(false);
                    if (failure != null) return Response.failed(failure, dispatched);
                    return Response.succeeded(image, usage);
                }

                @Override public void cancel() {
                    wasCancelled.set(true);
                    cancelled.countDown();
                }
            };
        }
    }
}
