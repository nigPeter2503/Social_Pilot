package com.socialpilot.app.domain.contract.analytics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.domain.contract.social.SocialError;
import com.socialpilot.app.domain.contract.social.SocialOperation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.socialpilot.app.domain.contract.analytics.AnalyticsTypes.*;
import static com.socialpilot.app.domain.contract.analytics.DashboardData.*;
import static com.socialpilot.app.domain.contract.analytics.DashboardQuery.*;
import static org.junit.Assert.*;

public final class AnalyticsConsumerContractTest {
    private static JsonObject examples;
    private static JsonObject views;
    private static JsonObject snapshot;
    private static JsonObject sample;
    private static DashboardQuery query;
    private static ChannelScope scope;

    @BeforeClass public static void loadPinnedFixtures() throws IOException {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("docs/contracts/analytics-examples.json"))) {
            root = root.getParent();
        }
        assertNotNull("Run inside the Social_Pilot checkout", root);
        examples = JsonParser.parseString(readFixture(root.resolve("docs/contracts/analytics-examples.json")))
                .getAsJsonObject();
        String handoff = readFixture(root.resolve("docs/handoffs/sp-045-analytics-input.md"));
        String marker = "~~~json";
        int start = handoff.indexOf(marker);
        assertTrue("Missing SP-045.05 dashboard payload", start >= 0);
        int end = handoff.indexOf("~~~", start + marker.length());
        JsonObject payload = JsonParser.parseString(handoff.substring(start + marker.length(), end))
                .getAsJsonObject();
        assertEquals("0.1.0", text(payload, "handoffVersion"));
        assertEquals("MO_PHONG", text(payload, "source"));
        assertEquals("draft-4", text(examples, "contractRevision"));
        assertEquals("MO_PHONG", text(examples, "source"));
        views = payload.getAsJsonObject("views");
        snapshot = examples.getAsJsonObject("dtoExamples").getAsJsonObject("snapshot");
        sample = snapshot.getAsJsonArray("samples").get(0).getAsJsonObject();
        scope = scope(payload.getAsJsonObject("resolvedScope"));
        JsonObject input = payload.getAsJsonObject("query");
        assertEquals(List.of(scope.channelId()), strings(input.getAsJsonArray("channelIds")));
        query = new DashboardQuery(List.of(scope), text(input, "metricDefinitionId"),
                time(input, "requestedStart"), time(input, "requestedEnd"), text(input, "zoneId"),
                new Cohort(strings(input.getAsJsonObject("cohort").getAsJsonArray("cohortPostIds"))),
                Grouping.valueOf(text(input, "grouping")), input.get("topLimit").getAsInt());
    }

    @Test public void successfulEmptyIsDistinctFromZero() {
        DashboardData data = empty(query);
        assertEquals(EvidenceSource.MO_PHONG, data.source());
        assertEquals(ContentState.EMPTY, data.contentState());
        assertEquals(SyncState.IDLE, data.syncState());
        assertTrue(data.channelCards().isEmpty() && data.postMetrics().isEmpty() && data.growthPoints().isEmpty()
                && data.weeklyEngagement().isEmpty() && data.topPosts().isEmpty()
                && data.groupedMetrics().isEmpty() && data.goals().isEmpty() && data.errors().isEmpty());
        assertEquals(CoverageLevel.NONE, data.coverage().temporal());
        assertEquals(CoverageLevel.NONE, data.coverage().cohort());
        assertEquals(0, data.coverage().totalCount());
        assertNull(data.lastSuccessAt());
        JsonObject noHistory = example("coverageCases", "no-history").getAsJsonObject("expected");
        assertNull(time(noHistory, "actualStart"));
        assertNull(time(noHistory, "actualEnd"));
        assertEquals("MISSING_BASELINE", text(noHistory, "reason"));

        MetricReading zero = reading(example("metricValues", "available-zero"), new Freshness(false, query.requestedEnd()));
        assertEquals(Long.valueOf(0), zero.value());
        assertNull(zero.missingReason());
    }

    @Test public void permissionIsExposedOnTheMetricAndRefreshError() {
        JsonObject expected = example("missingReasonCases", "permission-revoked-after-verification");
        JsonObject error = example("pageStates", "permission-error").getAsJsonObject("error");
        DashboardError permission = socialError(error, null);
        MetricReading missing = reading(example("metricValues", "permission"), new Freshness(null, null));
        ChannelCard card = new ChannelCard(scope, scope.channelId(), missing, SyncState.ERROR, List.of(permission));
        assertNull(card.follower().value());
        assertEquals(MissingReason.valueOf(text(expected, "expected")), card.follower().missingReason());
        assertEquals(SocialError.Code.valueOf(text(expected, "socialErrorCode")), card.errors().get(0).error().code);
        assertEquals(SocialError.NextAction.RESOLVE_PERMISSION, card.errors().get(0).error().nextAction);
    }

    @Test public void chartGapHasNoSnapshotAndMissingBaselineDoesNotBecomeZero() {
        JsonObject gap = view("growth-gap");
        GrowthPoint point = new GrowthPoint(scope(gap.getAsJsonObject("scope")), time(gap, "observedAt"),
                gap.get("isGap").getAsBoolean(), reading(gap, new Freshness(null, null)));
        assertTrue(point.isGap());
        assertEquals(query.requestedStart(), point.observedAt());
        assertEquals(scope, point.scope());
        assertNull(point.reading().value());
        assertTrue(point.reading().sourceSnapshotIds().isEmpty());
        assertEquals(MissingReason.NOT_SYNCED, point.reading().missingReason());
        JsonObject dto = view("follower-delta-missing-baseline");
        CalculatedValue delta = new CalculatedValue(text(dto, "definitionId"), null,
                CalculationReason.valueOf(text(dto, "reason")), List.of(),
                new Bounds(time(dto, "requestedStart"), time(dto, "requestedEnd"),
                        time(dto, "actualStart"), time(dto, "actualEnd")),
                coverage(dto.getAsJsonObject("coverage")), freshness(dto), List.of(sampleSource()));
        JsonObject expected = example("coverageCases", "one-snapshot").getAsJsonObject("expected");
        assertNull(delta.value());
        assertEquals(CalculationReason.valueOf(text(expected, "reason")), delta.reason());
        assertEquals(CoverageLevel.NONE, delta.coverage().temporal());
        assertNull(delta.bounds().actualStart());
        assertNull(delta.bounds().actualEnd());
        assertEquals(strings(dto.getAsJsonArray("sourceSnapshotIds")), delta.sourceSnapshotIds());
    }

    @Test public void partialWeekAndFullSignedDeltaUseTheirPinnedProjections() {
        JsonObject partial = example("coverageCases", "partial-week").getAsJsonObject("expected");
        Coverage coverage = new Coverage(CoverageLevel.valueOf(text(partial, "coverage")),
                CoverageLevel.NONE, 0, 0, List.of());
        Bounds projection = bounds(partial);
        assertEquals(Instant.parse("2026-11-02T17:15:00Z").toEpochMilli(), projection.actualStart().longValue());
        assertEquals(Instant.parse("2026-11-07T17:20:00Z").toEpochMilli(), projection.actualEnd().longValue());
        assertTrue(projection.actualStart() > projection.requestedStart());
        assertTrue(projection.actualEnd() < projection.requestedEnd());
        assertEquals(CoverageLevel.PARTIAL, coverage.temporal());
        assertEquals(CoverageLevel.NONE, coverage.cohort());
        assertNull(text(partial, "reason"));

        JsonObject decrease = example("snapshotCases", "decrease");
        JsonObject expected = decrease.getAsJsonObject("expected");
        JsonObject full = example("coverageCases", "full-boundaries").getAsJsonObject("expected");
        Bounds fullBounds = bounds(full);
        Coverage fullCoverage = new Coverage(CoverageLevel.valueOf(text(full, "coverage")),
                CoverageLevel.NONE, 0, 0, List.of());
        CalculatedValue delta = new CalculatedValue(text(decrease, "beforeDefinition"),
                expected.get("delta").getAsLong(), null, List.of(), fullBounds, fullCoverage,
                new Freshness(null, null), List.of());
        CalculatedValue growth = new CalculatedValue(delta.definitionId(), expected.get("growth").getAsDouble(),
                null, List.of(), fullBounds, fullCoverage, delta.freshness(), List.of());
        assertEquals("followers-v1", delta.definitionId());
        assertEquals(Long.valueOf(-5), delta.value());
        assertEquals(Double.valueOf(-5), growth.value());
        assertEquals(CoverageLevel.FULL, delta.coverage().temporal());
    }

    @Test public void staleCacheAndEvidenceSurviveAnOfflineRefreshError() {
        JsonObject stale = view("dashboard-stale-refresh");
        JsonObject entry = stale.getAsJsonObject("followerEntry");
        JsonObject errors = stale.getAsJsonArray("errors").get(0).getAsJsonObject();
        DashboardError network = socialError(errors.getAsJsonObject("error"), time(errors, "retryAfterAt"));
        MetricSource source = sampleSource();
        MetricReading reading = new MetricReading(text(entry, "definitionId"),
                entry.getAsJsonObject("follower").get("value").getAsLong(), null,
                time(entry, "followerCollectedAt"), freshness(entry), List.of(source));
        ChannelCard card = new ChannelCard(scope, scope.channelId(), reading, SyncState.ERROR, List.of(network));
        DashboardData projection = data(query, ContentState.CONTENT, List.of(card),
                coverage(view("dashboard-empty").getAsJsonObject("coverage")), stale, List.of(network));
        FixtureRepository repo = new FixtureRepository();
        FixtureConsumer consumer = new FixtureConsumer(repo);
        consumer.attach(query);
        consumer.refresh();
        repo.observe.emit(projection);
        repo.refresh.emit(new AnalyticsRepository.RefreshResult(query,
                AnalyticsRepository.RefreshOutcome.FAILED, List.of(network)));
        repo.drain();
        consumer.drainUi();
        DashboardData data = consumer.rendered;
        assertEquals(AnalyticsRepository.RefreshOutcome.FAILED, consumer.completed.outcome());
        assertEquals(List.of(network), consumer.completed.errors());
        MetricReading cached = data.channelCards().get(0).follower();
        assertEquals(Long.valueOf(100), cached.value());
        assertEquals(source.collectedAt(), cached.collectedAt().longValue());
        assertNotEquals(time(snapshot, "collectedAt"), cached.collectedAt());
        assertEquals(List.of("mock-snapshot-1"), cached.sourceSnapshotIds());
        assertEquals("mock-collection-1", source.collectionId());
        assertEquals("mock-followers-v1", source.definitionId());
        assertEquals("analytics-draft-4-snapshot", source.sourceRef());
        assertEquals(EvidenceSource.MO_PHONG, source.source());
        assertEquals(Boolean.TRUE, cached.freshness().isStale());
        assertEquals(cached.collectedAt(), data.lastSuccessAt());
        assertEquals(cached.freshness().lastSuccessAt(), data.lastSuccessAt());
        assertEquals(73, Duration.between(Instant.ofEpochMilli(data.lastSuccessAt()),
                Instant.ofEpochMilli(data.lastAttemptAt())).toHours());
        assertEquals(time(stale, "now"), data.lastAttemptAt());
        assertEquals(ContentState.CONTENT, data.contentState());
        assertEquals(SyncState.ERROR, data.syncState());
        assertTrue(data.offline());
        assertEquals(SocialError.Code.valueOf(text(example("missingReasonCases", "network-error"), "socialErrorCode")),
                data.errors().get(0).error().code);
        assertEquals(SocialError.NextAction.RETRY_WHEN_SAFE, data.errors().get(0).error().nextAction);
    }

    @Test public void reattachDoesNotRefreshAndCancelDropsCallbacks() {
        FixtureRepository repo = new FixtureRepository();
        FixtureConsumer consumer = new FixtureConsumer(repo);
        consumer.attach(query);
        repo.observe.emit(empty(query));
        repo.drain();
        consumer.drainUi();
        assertEquals(query, consumer.rendered.query());
        assertEquals(0, repo.refreshCount);

        consumer.refresh();
        consumer.detach();
        consumer.attach(query);
        repo.observe.emit(empty(query));
        repo.refresh.emit(result(query));
        repo.drain();
        consumer.drainUi();
        assertEquals(1, repo.refreshCount);
        assertEquals(query, consumer.completed.query());

        repo.observe.emit(empty(query));
        repo.drain(); // The observer callback posts UI work before detach.
        consumer.detach();
        consumer.drainUi();
        assertNull(consumer.rendered);

        repo.observe.emit(empty(query));
        consumer.refresh();
        repo.refresh.emit(result(query));
        consumer.cancelRefresh(); // The refresh callback is still waiting in the repository queue.
        repo.drain();
        consumer.drainUi();
        assertNull(consumer.completed);
        assertEquals(4, repo.deliveries);
    }

    @Test public void changingRevisionRejectsBothOldCallbacksAlreadyPostedToUi() {
        FixtureRepository repo = new FixtureRepository();
        FixtureConsumer consumer = new FixtureConsumer(repo);
        consumer.attach(query);
        consumer.refresh();
        repo.observe.emit(empty(query));
        repo.refresh.emit(result(query));
        repo.drain();
        DashboardQuery next = new DashboardQuery(
                List.of(new ChannelScope(scope.platform(), scope.channelId(), scope.connectionRevision() + 1)),
                query.metricDefinitionId(), query.requestedStart(), query.requestedEnd(), query.zoneId(),
                query.cohort(), query.grouping(), query.topLimit());
        assertNotEquals(query, next);
        consumer.attach(next);
        consumer.drainUi();
        assertNull(consumer.rendered);
        assertNull(consumer.completed);
        repo.observe.emit(empty(next));
        repo.drain();
        consumer.drainUi();
        assertEquals(next, consumer.rendered.query());
        assertEquals(1, repo.refreshCount);
    }

    private static final class FixtureConsumer {
        private final AnalyticsRepository repository;
        private final Queue<Runnable> ui = new ArrayDeque<>();
        private DashboardQuery selected;
        private Cancelable observation;
        private Cancelable request;
        private long observationGeneration;
        private long refreshGeneration;
        private DashboardData rendered;
        private AnalyticsRepository.RefreshResult completed;

        FixtureConsumer(AnalyticsRepository repository) {
            this.repository = repository;
        }

        void attach(DashboardQuery query) {
            detach();
            if (!query.equals(selected)) {
                cancelRefresh();
                selected = query;
            }
            long token = observationGeneration;
            observation = repository.observeCachedDashboard(query, data -> ui.add(() -> {
                if (token == observationGeneration && query.equals(data.query())) {
                    rendered = data;
                }
            }));
        }

        void detach() {
            observationGeneration++;
            if (observation != null) {
                observation.cancel();
                observation = null;
            }
            rendered = null;
        }

        void refresh() {
            cancelRefresh();
            DashboardQuery query = selected;
            long token = refreshGeneration;
            request = repository.refresh(query, result -> ui.add(() -> {
                if (token == refreshGeneration && query.equals(result.query())) {
                    completed = result;
                }
            }));
        }

        void cancelRefresh() {
            refreshGeneration++;
            if (request != null) {
                request.cancel();
                request = null;
            }
            completed = null;
        }

        void drainUi() {
            while (!ui.isEmpty()) {
                ui.remove().run();
            }
        }
    }

    /** Two manually drained queues model the delivery order without sleeps or a provider. */
    private static final class FixtureRepository implements AnalyticsRepository {
        private final Queue<Runnable> callbacks = new ArrayDeque<>();
        private Delivery<DashboardData> observe;
        private Delivery<RefreshResult> refresh;
        private int refreshCount;
        private int deliveries;

        @Override public Cancelable observeCachedDashboard(DashboardQuery query, DashboardObserver observer) {
            observe = new Delivery<>(observer::onChanged);
            return observe;
        }

        @Override public Cancelable refresh(DashboardQuery query, RefreshCallback callback) {
            refreshCount++;
            refresh = new Delivery<>(callback::onComplete);
            return refresh;
        }

        void drain() {
            while (!callbacks.isEmpty()) {
                callbacks.remove().run();
            }
        }

        private final class Delivery<T> implements Cancelable {
            private final Consumer<T> callback;
            private boolean cancelled;

            Delivery(Consumer<T> callback) {
                this.callback = callback;
            }

            void emit(T value) {
                callbacks.add(() -> {
                    if (!cancelled) {
                        deliveries++;
                        callback.accept(value);
                    }
                });
            }

            @Override public void cancel() {
                cancelled = true;
            }
        }
    }

    private static DashboardData empty(DashboardQuery selected) {
        JsonObject state = view("dashboard-empty");
        return data(selected, ContentState.EMPTY, List.of(), coverage(state.getAsJsonObject("coverage")), state, List.of());
    }

    private static AnalyticsRepository.RefreshResult result(DashboardQuery selected) {
        return new AnalyticsRepository.RefreshResult(selected, AnalyticsRepository.RefreshOutcome.SUCCESS, List.of());
    }

    private static DashboardData data(DashboardQuery selected, ContentState content, List<ChannelCard> cards,
            Coverage coverage, JsonObject state, List<DashboardError> errors) {
        return new DashboardData(selected, EvidenceSource.MO_PHONG, content, cards, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), coverage, time(state, "lastSuccessAt"),
                time(state, "lastAttemptAt"), SyncState.valueOf(text(state, "syncState")),
                state.get("offline").getAsBoolean(), errors);
    }

    private static MetricReading reading(JsonObject view, Freshness freshness) {
        String reason = text(view, "missingReason");
        JsonElement value = view.get("value");
        return new MetricReading(query.metricDefinitionId(), value.isJsonNull() ? null : value.getAsLong(),
                reason == null ? null : MissingReason.valueOf(reason), time(view, "collectedAt"), freshness, List.of());
    }

    private static MetricSource sampleSource() {
        return new MetricSource(text(snapshot, "snapshotId"), text(sample, "collectionId"),
                scope(sample.getAsJsonObject("scope")), text(sample, "entityId"), text(sample, "definitionId"),
                EvidenceSource.valueOf(text(sample, "source")), text(sample, "sourceRef"), time(sample, "observedAt"),
                ObservedAtBasis.valueOf(text(sample, "observedAtBasis")), time(sample, "collectedAt"),
                time(sample, "providerPeriodStart"), time(sample, "providerPeriodEnd"), text(sample, "providerApiVersion"));
    }

    private static DashboardError socialError(JsonObject source, Long retryAfterAt) {
        return new DashboardError(scope, SocialOperation.READ_FOLLOWER_COUNT,
                new SocialError(SocialError.Code.valueOf(text(source, "code")),
                        SocialError.NextAction.valueOf(text(source, "nextAction"))), null, retryAfterAt);
    }

    private static Freshness freshness(JsonObject view) {
        JsonObject value = view.getAsJsonObject("freshness");
        return new Freshness(value.get("isStale").getAsBoolean(), time(value, "lastSuccessAt"));
    }

    private static Coverage coverage(JsonObject value) {
        assertTrue(value.getAsJsonArray("excludedReasons").isEmpty());
        return new Coverage(CoverageLevel.valueOf(text(value, "temporal")),
                CoverageLevel.valueOf(text(value, "cohort")), value.get("includedCount").getAsInt(),
                value.get("totalCount").getAsInt(), List.of());
    }

    private static Bounds bounds(JsonObject value) {
        return new Bounds(query.requestedStart(), query.requestedEnd(),
                time(value, "actualStart"), time(value, "actualEnd"));
    }

    private static ChannelScope scope(JsonObject value) {
        return new ChannelScope(Platform.valueOf(text(value, "platform")), text(value, "channelId"),
                value.get("connectionRevision").getAsLong());
    }

    private static JsonObject example(String group, String id) {
        for (JsonElement item : examples.getAsJsonArray(group)) {
            if (id.equals(text(item.getAsJsonObject(), "id"))) {
                return item.getAsJsonObject();
            }
        }
        throw new AssertionError("Missing SP-045.05 fixture " + group + "/" + id);
    }

    private static JsonObject view(String id) {
        return views.getAsJsonObject(id);
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement item : array) {
            values.add(item.getAsString());
        }
        return values;
    }

    private static Long time(JsonObject source, String key) {
        String value = text(source, key);
        return value == null ? null : Instant.parse(value).toEpochMilli();
    }

    private static String text(JsonObject source, String key) {
        JsonElement value = source.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static String readFixture(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
