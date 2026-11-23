package com.socialpilot.app.domain.agent;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
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
        return new PipelineSnapshot("s-1", "brief-1", 7, Arrays.asList(facebook, instagram));
    }

    @Test public void runningStageBecomesInterruptedAndKeepsItsRequestAndText() {
        PipelineSnapshot recovered = CheckpointPolicy.recover(midDraft(), 99);
        assertEquals(8, recovered.revision);
        assertEquals("s-1", recovered.sessionId);
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
        PipelineSnapshot stored = new PipelineSnapshot("s-2", "brief-1", 1,
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
        PipelineSnapshot older = new PipelineSnapshot("s-1", "brief-1", 6, stored.branches);
        assertEquals(CheckpointPolicy.Write.STALE, CheckpointPolicy.check(stored, older));
        PipelineSnapshot other = new PipelineSnapshot("s-9", "brief-1", 100, stored.branches);
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
    }

    @Test public void disconnectDropsOnlyThatChannelAndEmptiesTheSessionLast() {
        PipelineSnapshot stored = midDraft();
        PipelineSnapshot kept = CheckpointPolicy.withoutChannel(stored, "ig-1");
        assertEquals(1, kept.branches.size());
        assertNotNull(kept.branch("page-1"));
        assertEquals(8, kept.revision);
        assertSame(stored, CheckpointPolicy.withoutChannel(stored, "unknown"));
        assertNull(CheckpointPolicy.withoutChannel(kept, "page-1"));
    }

    private static PipelineInput input(String briefId, ChannelTarget... targets) {
        return new PipelineInput(briefId, "Topic", "Goal", "Audience", "Tone", "vi",
                Collections.emptyList(), Arrays.asList(targets));
    }
}
