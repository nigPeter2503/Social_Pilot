package com.socialpilot.app.domain.agent;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** SP-033 recovery mapping and write guard, without any runtime. */
public final class CheckpointPolicyTest {
    private static final ChannelTarget FACEBOOK = new ChannelTarget("page-1", ChannelPlatform.FACEBOOK, 3);
    private static final ChannelTarget INSTAGRAM = new ChannelTarget("ig-1", ChannelPlatform.INSTAGRAM, 5);

    private static StageResult completed(PipelineStage stage, String requestId, String text) {
        return StageResult.initial(stage).running(1, 10).accepted(requestId)
                .finished(StageResult.Status.COMPLETED, requestId, text, null, null, false, 20);
    }

    /** page-1: outline done, draft streaming when the process died. ig-1: already TEXT_READY. */
    private static PipelineSnapshot midDraft() {
        ChannelBranch facebook = ChannelBranch.initial(FACEBOOK)
                .withStage(completed(PipelineStage.OUTLINE, "r1", "outline"))
                .withStage(StageResult.initial(PipelineStage.DRAFT).running(1, 30).accepted("r2").streaming("half"));
        ChannelBranch instagram = ChannelBranch.initial(INSTAGRAM)
                .withStage(completed(PipelineStage.OUTLINE, "i1", "o"))
                .withStage(completed(PipelineStage.DRAFT, "i2", "d"))
                .withRuleCheck(completed(PipelineStage.RULE_CHECK, "i3", "VERDICT: PASS"),
                        RuleCheck.of("VERDICT: PASS", "d", Collections.emptyList()));
        return new PipelineSnapshot("s-1", "brief-1", input("brief-1", FACEBOOK, INSTAGRAM).briefFingerprint,
                7, Arrays.asList(facebook, instagram));
    }

    @Test public void runningStageBecomesInterruptedAndKeepsItsRequestAndText() {
        PipelineSnapshot recovered = CheckpointPolicy.recover(midDraft(), 99);
        assertEquals(8, recovered.revision);
        assertEquals("s-1", recovered.sessionId);
        assertEquals(midDraft().briefFingerprint, recovered.briefFingerprint);
        ChannelBranch branch = recovered.branch("page-1");
        assertEquals(ChannelBranch.Status.INTERRUPTED, branch.status);
        assertEquals(PipelineStage.DRAFT, branch.failedStage);
        StageResult draft = branch.stage(PipelineStage.DRAFT);
        assertEquals(StageResult.Status.INTERRUPTED, draft.status);
        assertEquals("r2", draft.requestId);
        assertEquals("half", draft.text);
        assertEquals(1, draft.attempt);
        assertEquals(99, draft.finishedAtMillis);
        assertNull(draft.settlement); // Cost is the ledger's to reconcile, never guessed here.
        assertEquals(StageResult.Status.COMPLETED, branch.stage(PipelineStage.OUTLINE).status);
        assertEquals(StageResult.Status.NOT_STARTED, branch.stage(PipelineStage.RULE_CHECK).status);
        assertEquals(StageResult.Status.NOT_IMPLEMENTED, branch.stage(PipelineStage.IMAGE).status);
        assertEquals(PipelineStage.DRAFT, branch.resumeStage());
        assertTrue(recovered.settled());
    }

    @Test public void finishedBranchesAreUntouched() {
        PipelineSnapshot stored = midDraft();
        assertSame(stored.branch("ig-1"), CheckpointPolicy.recover(stored, 99).branch("ig-1"));
    }

    @Test public void branchScheduledButNotStartedIsInterruptedAtItsFirstTextStage() {
        PipelineSnapshot stored = new PipelineSnapshot("s-2", "brief-1", input("brief-1", FACEBOOK).briefFingerprint, 1,
                Collections.singletonList(ChannelBranch.initial(FACEBOOK)));
        ChannelBranch branch = CheckpointPolicy.recover(stored, 5).branch("page-1");
        assertEquals(ChannelBranch.Status.INTERRUPTED, branch.status);
        assertEquals(PipelineStage.OUTLINE, branch.failedStage);
        assertEquals(StageResult.Status.NOT_STARTED, branch.stage(PipelineStage.OUTLINE).status);
        assertEquals(PipelineStage.OUTLINE, branch.resumeStage());
    }

    @Test public void writeGuardAcceptsOnlyNewerRevisionsOfTheSameSession() {
        PipelineSnapshot stored = midDraft();
        assertEquals(CheckpointPolicy.Write.ACCEPTED, CheckpointPolicy.check(null, stored));
        assertEquals(CheckpointPolicy.Write.ACCEPTED,
                CheckpointPolicy.check(stored, CheckpointPolicy.recover(stored, 1)));
        assertEquals(CheckpointPolicy.Write.STALE, CheckpointPolicy.check(stored, stored));
        PipelineSnapshot older = new PipelineSnapshot("s-1", "brief-1", stored.briefFingerprint, 6, stored.branches);
        assertEquals(CheckpointPolicy.Write.STALE, CheckpointPolicy.check(stored, older));
        PipelineSnapshot other = new PipelineSnapshot("s-9", "brief-1", stored.briefFingerprint, 100, stored.branches);
        assertEquals(CheckpointPolicy.Write.OTHER_SESSION, CheckpointPolicy.check(stored, other));
    }

    @Test public void mismatchDetectsBriefTargetAndConnectionChanges() {
        PipelineSnapshot stored = midDraft();
        assertNull(CheckpointPolicy.mismatch(input("brief-1", FACEBOOK, INSTAGRAM), stored));
        assertEquals(CheckpointPolicy.Mismatch.BRIEF_CHANGED,
                CheckpointPolicy.mismatch(input("brief-2", FACEBOOK, INSTAGRAM), stored));
        assertEquals(CheckpointPolicy.Mismatch.TARGETS_CHANGED,
                CheckpointPolicy.mismatch(input("brief-1", FACEBOOK), stored));
        ChannelTarget reconnected = new ChannelTarget("ig-1", ChannelPlatform.INSTAGRAM, 6);
        assertEquals(CheckpointPolicy.Mismatch.CONNECTION_CHANGED,
                CheckpointPolicy.mismatch(input("brief-1", FACEBOOK, reconnected), stored));
        ChannelTarget moved = new ChannelTarget("page-1", ChannelPlatform.INSTAGRAM, 3);
        assertEquals(CheckpointPolicy.Mismatch.TARGETS_CHANGED,
                CheckpointPolicy.mismatch(input("brief-1", moved), CheckpointPolicy.withoutChannel(stored, "ig-1")));
    }

    @Test public void briefFingerprintIncludesContentLanguageAndTheTypeAndOrderOfSources() {
        PipelineInput.Source link = new PipelineInput.Source(true, "https://example.com/menu");
        PipelineInput.Source note = new PipelineInput.Source(false, "Use the seasonal menu");
        PipelineInput original = inputWithSources("vi", link, note);
        PipelineSnapshot stored = new PipelineSnapshot("s-3", original.briefId, original.briefFingerprint, 1,
                Collections.singletonList(ChannelBranch.initial(FACEBOOK)));

        assertNull(CheckpointPolicy.mismatch(inputWithSources("vi",
                new PipelineInput.Source(true, " https://example.com/menu "), note), stored));
        assertEquals(CheckpointPolicy.Mismatch.BRIEF_CHANGED,
                CheckpointPolicy.mismatch(inputWithSources("en", link, note), stored));
        assertEquals(CheckpointPolicy.Mismatch.BRIEF_CHANGED,
                CheckpointPolicy.mismatch(inputWithSources("vi", note, link), stored));
        assertEquals(CheckpointPolicy.Mismatch.BRIEF_CHANGED, CheckpointPolicy.mismatch(inputWithSources("vi",
                new PipelineInput.Source(false, link.value), note), stored));
        assertEquals(CheckpointPolicy.Mismatch.BRIEF_CHANGED, CheckpointPolicy.mismatch(inputWithSources("vi",
                link, new PipelineInput.Source(false, "Use the regular menu")), stored));
        PipelineSnapshot missingFingerprint = new PipelineSnapshot("s-3", original.briefId, null, 1, stored.branches);
        assertEquals(CheckpointPolicy.Mismatch.BRIEF_CHANGED, CheckpointPolicy.mismatch(original, missingFingerprint));
    }

    @Test public void disconnectDropsOnlyThatChannelAndEmptiesTheSessionLast() {
        PipelineSnapshot stored = midDraft();
        PipelineSnapshot kept = CheckpointPolicy.withoutChannel(stored, "ig-1");
        assertEquals(1, kept.branches.size());
        assertNotNull(kept.branch("page-1"));
        assertEquals(stored.revision, kept.revision);
        assertEquals(stored.briefFingerprint, kept.briefFingerprint);
        assertSame(stored, CheckpointPolicy.withoutChannel(stored, "unknown"));
        assertNull(CheckpointPolicy.withoutChannel(kept, "page-1"));
    }

    private static PipelineInput inputWithSources(String language, PipelineInput.Source... sources) {
        List<PipelineInput.Source> ordered = Arrays.asList(sources);
        return new PipelineInput("brief-1", "Topic", "Goal", "Audience", "Tone", language,
                ordered, Collections.singletonList(FACEBOOK));
    }

    private static PipelineInput input(String briefId, ChannelTarget... targets) {
        return new PipelineInput(briefId, "Topic", "Goal", "Audience", "Tone", "vi",
                Collections.emptyList(), Arrays.asList(targets));
    }
}
