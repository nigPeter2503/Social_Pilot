package com.socialpilot.app.data.ai.image;

import com.socialpilot.app.data.ai.AiGateway;
import com.socialpilot.app.domain.agent.ChannelTarget;
import com.socialpilot.app.domain.agent.ImageAsset;
import com.socialpilot.app.domain.agent.ImageSpec;
import com.socialpilot.app.domain.agent.TemplateLayout;
import com.socialpilot.app.domain.contract.BudgetManager;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.ai.AiCostEstimate;
import com.socialpilot.app.domain.contract.ai.AiError;
import com.socialpilot.app.domain.contract.ai.AiRequest;
import com.socialpilot.app.domain.contract.ai.AiTaskType;
import com.socialpilot.app.domain.contract.ai.AiUsage;
import com.socialpilot.app.domain.contract.ai.AttemptLedger;
import com.socialpilot.app.domain.contract.ai.ModelEntry;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Channel images (SP-032). A generation goes through the same model selection, BYOK gate and
 * shared ledger as text: reserve the per-image price before dispatch, settle or hold after. When
 * generation cannot start (no usable model, size or budget), the local template is used instead;
 * it makes no provider call and costs nothing. A provider failure after dispatch is reported, not
 * silently replaced: the caller may ask for {@link #template} explicitly. Text is never touched.
 */
public final class ImageService {
    /** Whether a channel connection is still at the revision the image was made for. */
    public interface RevisionCheck {
        boolean current(String channelId, long connectionRevision);
    }

    /** Until channel connections are wired (SP-022), trust the revision the caller passed. */
    public static final RevisionCheck CALLER_REVISION = (channelId, revision) -> true;

    public interface Callback {
        /** Called once, on a worker thread. */
        void onFinished(Result result);
    }

    public static final class Request {
        public final String sessionId;
        public final ChannelTarget target;
        public final String stepId;
        public final ImageSpec spec;
        /** Image description (week-4 IMAGE_PROMPT output); the spec style is appended. */
        public final String prompt;
        /** Template headline, usually the topic or the first line of the post. */
        public final String headline;
        public final String footer;

        public Request(String sessionId, ChannelTarget target, String stepId, ImageSpec spec, String prompt,
                       String headline, String footer) {
            this.sessionId = Objects.requireNonNull(sessionId);
            this.target = Objects.requireNonNull(target);
            this.stepId = Objects.requireNonNull(stepId);
            this.spec = Objects.requireNonNull(spec);
            if (spec.platform != target.platform) throw new IllegalArgumentException("Spec of another channel");
            this.prompt = prompt == null ? "" : prompt;
            this.headline = headline == null ? "" : headline;
            this.footer = footer == null ? "" : footer;
        }
    }

    public static final class Result {
        public enum Status { READY, FAILED, CANCELLED, STALE }

        public final Status status;
        public final ImageAsset asset;
        public final String requestId;
        public final AiError error;
        /** Pre-dispatch reason (template fallback) or STORAGE_FAILED / INVALID_IMAGE. */
        public final String reason;
        /** From the ledger's stored row; null when nothing was reserved or {@link #ledgerWriteFailed}. */
        public final UsageRecord.Settlement settlement;
        public final Long estimateMicros;
        public final boolean budgetStopped;
        /** The terminal could not be written: cost unknown, the reservation stays held (UNCERTAIN at next start). */
        public final boolean ledgerWriteFailed;

        Result(Status status, ImageAsset asset, String requestId, AiError error, String reason,
               UsageRecord.Settlement settlement, Long estimateMicros, boolean budgetStopped) {
            this(status, asset, requestId, error, reason, settlement, estimateMicros, budgetStopped, false);
        }

        private Result(Status status, ImageAsset asset, String requestId, AiError error, String reason,
                       UsageRecord.Settlement settlement, Long estimateMicros, boolean budgetStopped,
                       boolean ledgerWriteFailed) {
            this.status = status;
            this.asset = asset;
            this.requestId = requestId;
            this.error = error;
            this.reason = reason;
            this.settlement = settlement;
            this.estimateMicros = estimateMicros;
            this.budgetStopped = budgetStopped;
            this.ledgerWriteFailed = ledgerWriteFailed;
        }

        Result withLedgerWriteFailed() {
            return new Result(status, asset, requestId, error, reason, null, null, budgetStopped, true);
        }
    }

    private final AiGateway.Selections selections;
    private final Map<AiRequest.Provider, ImageProvider> providers;
    private final AiGateway.KeyGate keys;
    private final AttemptLedger ledger;
    private final ImageStore store;
    private final TemplateRenderer renderer;
    private final RevisionCheck revisions;
    private final Executor workers;
    private final AiGateway.Clock clock;
    private final Set<Job> active = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ImageService(AiGateway.Selections selections, Map<AiRequest.Provider, ImageProvider> providers,
                        AiGateway.KeyGate keys, AttemptLedger ledger, ImageStore store, TemplateRenderer renderer,
                        RevisionCheck revisions, Executor workers, AiGateway.Clock clock) {
        this.selections = Objects.requireNonNull(selections);
        this.providers = providers.isEmpty() ? new EnumMap<>(AiRequest.Provider.class) : new EnumMap<>(providers);
        this.keys = Objects.requireNonNull(keys);
        this.ledger = ledger;
        this.store = Objects.requireNonNull(store);
        this.renderer = Objects.requireNonNull(renderer);
        this.revisions = Objects.requireNonNull(revisions);
        this.workers = Objects.requireNonNull(workers);
        this.clock = Objects.requireNonNull(clock);
        if (ledger != null) {
            ledger.addExhaustionListener(() -> {
                for (Job job : active) job.stopForBudget();
            });
        }
    }

    /** Generated image, or the template when generation cannot start. */
    public Job generate(Request request, Callback callback) {
        return start(new Job(request, callback, true));
    }

    /** Local template only; no provider call and no budget use. */
    public Job template(Request request, Callback callback) {
        return start(new Job(request, callback, false));
    }

    private Job start(Job job) {
        try {
            workers.execute(job::run);
        } catch (RejectedExecutionException full) {
            job.finish(new Result(Result.Status.FAILED, null, null, null, "WORKER_UNAVAILABLE", null, null, false));
        }
        return job;
    }

    public final class Job implements Cancelable {
        private final Request request;
        private final Callback callback;
        private final boolean generate;
        private volatile boolean cancelled;
        private volatile boolean budgetStopped;
        private volatile ImageProvider.Attempt attempt;
        private volatile boolean ledgerWriteFailed;
        private boolean finished;

        Job(Request request, Callback callback, boolean generate) {
            this.request = request;
            this.callback = Objects.requireNonNull(callback);
            this.generate = generate;
        }

        @Override
        public void cancel() {
            cancelled = true;
            ImageProvider.Attempt current = attempt;
            if (current != null) current.cancel();
        }

        void stopForBudget() {
            budgetStopped = true;
            cancel();
        }

        void run() {
            if (cancelled) {
                finish(new Result(Result.Status.CANCELLED, null, null, null, null, null, null, false));
                return;
            }
            if (!generate) {
                renderTemplate(null);
                return;
            }
            ModelEntry entry = selections.selected(AiTaskType.IMAGE);
            String reason = unavailable(entry);
            if (reason != null) {
                renderTemplate(reason);
                return;
            }
            AiRequest ai = new AiRequest(request.sessionId, request.target.channelId, request.target.connectionRevision,
                    request.stepId, UUID.randomUUID().toString(), entry.provider(), entry.model(),
                    AiRequest.Task.IMAGE, prompt(), 1);
            AiCostEstimate bound = entry.price.imageCost(1);
            // Registered before reserve: a cap lowered right after the reservation commits must stop this job.
            active.add(this);
            BudgetManager.Admission admission;
            try {
                admission = ledger.reserve(ai, AiTaskType.IMAGE, bound);
            } catch (RuntimeException failure) {
                active.remove(this);
                finish(new Result(Result.Status.FAILED, null, null, null, "LEDGER_WRITE_FAILED", null, null, false));
                return;
            }
            if (admission != BudgetManager.Admission.RESERVED) {
                active.remove(this);
                renderTemplate(admission == BudgetManager.Admission.LIMIT_REACHED
                        ? AiGateway.Rejection.BUDGET_LIMIT_REACHED.name() : AiGateway.Rejection.DUPLICATE_REQUEST.name());
                return;
            }
            if (cancelled) {
                // Stopped between reserve and dispatch: never sent; release the hold once.
                active.remove(this);
                UsageRecord released = complete(ai, false, null, AiUsage.MISSING);
                UsageRecord.Settlement settlement = released == null ? null : released.settlement;
                finish(budgetStopped
                        ? new Result(Result.Status.FAILED, null, ai.requestId, null,
                                AiGateway.Rejection.BUDGET_LIMIT_REACHED.name(), settlement, null, true)
                        : new Result(Result.Status.CANCELLED, null, ai.requestId, null, null, settlement, null, false));
                return;
            }
            ImageProvider.Response response;
            ImageProvider.Attempt prepared = providers.get(entry.provider()).prepare(ai, request.spec.providerSize);
            attempt = prepared;
            try {
                if (cancelled) prepared.cancel();
                response = prepared.run();
            } finally {
                active.remove(this);
            }
            boolean succeeded = response.outcome == ImageProvider.Response.Outcome.SUCCEEDED;
            // Billed per image: a produced image is charged even if the user cancelled meanwhile.
            UsageRecord record = complete(ai, succeeded || response.mayHaveDispatched, succeeded ? bound : null,
                    response.usage == null ? AiUsage.MISSING : response.usage);
            UsageRecord.Settlement settlement = record == null ? null : record.settlement;
            Long micros = record == null ? null : record.estimateMicros;
            if (response.outcome != ImageProvider.Response.Outcome.SUCCEEDED || cancelled) {
                Result.Status status = budgetStopped ? Result.Status.FAILED
                        : cancelled || response.outcome == ImageProvider.Response.Outcome.CANCELLED
                        ? Result.Status.CANCELLED : Result.Status.FAILED;
                finish(new Result(status, null, ai.requestId, response.error, null, settlement, micros, budgetStopped));
                return;
            }
            PngInfo info = PngInfo.read(response.image);
            if (info == null) {
                finish(new Result(Result.Status.FAILED, null, ai.requestId, new AiError(AiError.Code.PROTOCOL_ERROR, null),
                        "INVALID_IMAGE", settlement, micros, false));
                return;
            }
            commit(response.image, info, ImageAsset.Kind.GENERATED, ai.requestId, null, null, settlement, micros);
        }

        private String unavailable(ModelEntry entry) {
            if (entry == null) return AiGateway.Rejection.NO_MODEL_SELECTED.name();
            ModelEntry.Block block = entry.blockFor(AiTaskType.IMAGE, 1);
            if (block != ModelEntry.Block.NONE) return AiGateway.Rejection.MODEL_BLOCKED.name() + ":" + block.name();
            if (!providers.containsKey(entry.provider())) return AiGateway.Rejection.PROVIDER_NOT_REGISTERED.name();
            if (!keys.usable(entry.provider())) return AiGateway.Rejection.KEY_NOT_USABLE.name();
            if (request.spec.providerSize == null) return "IMAGE_SIZE_UNVERIFIED";
            if (ledger == null) return AiGateway.Rejection.BUDGET_NOT_CONFIGURED.name();
            if (ledger.limits() == null) return AiGateway.Rejection.BUDGET_NOT_SET.name();
            return null;
        }

        private String prompt() {
            String description = request.prompt.trim().isEmpty() ? request.headline : request.prompt;
            return description.trim() + "\nStyle: " + request.spec.style;
        }

        private void renderTemplate(String fallbackReason) {
            byte[] png;
            try {
                png = renderer.render(request.spec, request.headline, request.footer);
            } catch (RuntimeException failed) {
                finish(new Result(Result.Status.FAILED, null, null, null, "TEMPLATE_FAILED", null, null, false));
                return;
            }
            PngInfo info = PngInfo.read(png);
            if (info == null) {
                finish(new Result(Result.Status.FAILED, null, null, null, "INVALID_IMAGE", null, null, false));
                return;
            }
            if (cancelled) {
                finish(new Result(Result.Status.CANCELLED, null, null, null, fallbackReason, null, null, false));
                return;
            }
            commit(png, info, ImageAsset.Kind.TEMPLATE, null, TemplateLayout.TEMPLATE_ID, fallbackReason, null, null);
        }

        /** Saves only while the channel is still at the request's revision; re-checks after saving. */
        private void commit(byte[] png, PngInfo info, ImageAsset.Kind kind, String requestId, String templateId,
                            String fallbackReason, UsageRecord.Settlement settlement, Long micros) {
            ChannelTarget target = request.target;
            if (!revisions.current(target.channelId, target.connectionRevision)) {
                finish(new Result(Result.Status.STALE, null, requestId, null, fallbackReason, settlement, micros, false));
                return;
            }
            ImageStore.Stored stored;
            try {
                stored = store.save(png);
            } catch (IOException failed) {
                finish(new Result(Result.Status.FAILED, null, requestId, null, "STORAGE_FAILED", settlement, micros, false));
                return;
            }
            if (!revisions.current(target.channelId, target.connectionRevision)) {
                store.delete(stored.ref); // Disconnected while saving: never attach to the old channel.
                finish(new Result(Result.Status.STALE, null, requestId, null, fallbackReason, settlement, micros, false));
                return;
            }
            ImageAsset asset = new ImageAsset(target.channelId, target.connectionRevision, kind, stored.ref,
                    "image/png", info.width, info.height, stored.byteSize, stored.sha256,
                    request.spec.verification == ImageSpec.Verification.VERIFIED, requestId, templateId, fallbackReason);
            finish(new Result(Result.Status.READY, asset, requestId, null, fallbackReason, settlement, micros, false));
        }

        /**
         * Settlement and usage in one ledger transaction; the record carries the period pinned at
         * reserve. Null when the write failed: the hold stays and the result says so.
         */
        private UsageRecord complete(AiRequest ai, boolean mayHaveDispatched, AiCostEstimate estimate, AiUsage usage) {
            try {
                UsageRecord record = ledger.complete(ai.requestId, mayHaveDispatched, estimate, usage);
                if (record == null) ledgerWriteFailed = true;
                return record;
            } catch (RuntimeException failure) {
                ledgerWriteFailed = true;
                return null;
            }
        }

        synchronized void finish(Result result) {
            if (finished) return;
            finished = true;
            callback.onFinished(ledgerWriteFailed ? result.withLedgerWriteFailed() : result);
        }
    }
}
