package com.socialpilot.app.domain.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class VersionApprovalStateTest {
    private static final String FACEBOOK = "facebook-channel";
    private static final String LINKEDIN = "linkedin-channel";

    @Test
    public void editingApprovedTextInvalidatesOnlyThatChannel() {
        VersionApprovalState state = new VersionApprovalState(brief());
        ContentVersion facebookV1 = version("fb-v1", FACEBOOK, null, "Facebook text", null);
        ContentVersion linkedinV1 = version("li-v1", LINKEDIN, null, "LinkedIn text", null);
        state.saveVersion(facebookV1);
        state.saveVersion(linkedinV1);
        state.approve(FACEBOOK, "fb-v1");
        state.approve(LINKEDIN, "li-v1");

        ContentVersion facebookV2 = version("fb-v2", FACEBOOK, "fb-v1", "Edited text", null);
        state.saveVersion(facebookV2);

        assertNull(state.approvedVersionId(FACEBOOK));
        assertSame(linkedinV1, state.requireEnqueueable(LINKEDIN, "li-v1"));
        assertThrows(IllegalStateException.class,
                () -> state.requireEnqueueable(FACEBOOK, "fb-v1"));
        assertThrows(IllegalStateException.class,
                () -> state.requireEnqueueable(FACEBOOK, "fb-v2"));
        state.approve(FACEBOOK, "fb-v2");
        assertSame(facebookV2, state.requireEnqueueable(FACEBOOK, "fb-v2"));
    }

    @Test
    public void changingImageAlsoInvalidatesApproval() {
        VersionApprovalState state = new VersionApprovalState(brief());
        state.saveVersion(version("fb-v1", FACEBOOK, null, "Same text", "image-a"));
        state.approve(FACEBOOK, "fb-v1");

        state.saveVersion(version("fb-v2", FACEBOOK, "fb-v1", "Same text", "image-b"));

        assertNull(state.approvedVersionId(FACEBOOK));
        assertThrows(IllegalStateException.class,
                () -> state.requireEnqueueable(FACEBOOK, "fb-v2"));
    }

    @Test
    public void restoringOldContentCreatesNewUnapprovedVersion() {
        VersionApprovalState state = new VersionApprovalState(brief());
        state.saveVersion(version("fb-v1", FACEBOOK, null, "Original", null));
        state.saveVersion(version("fb-v2", FACEBOOK, "fb-v1", "Edited", null));
        state.approve(FACEBOOK, "fb-v2");

        ContentVersion restored = version("fb-v3", FACEBOOK, "fb-v1", "Original", null);
        state.saveVersion(restored);

        assertEquals("fb-v3", state.currentVersionId(FACEBOOK));
        assertNull(state.approvedVersionId(FACEBOOK));
        assertThrows(IllegalStateException.class,
                () -> state.requireEnqueueable(FACEBOOK, "fb-v2"));
        assertThrows(IllegalStateException.class,
                () -> state.requireEnqueueable(FACEBOOK, "fb-v3"));
        state.approve(FACEBOOK, "fb-v3");
        assertSame(restored, state.requireEnqueueable(FACEBOOK, "fb-v3"));
    }

    @Test
    public void missingOrOtherChannelVersionCannotBeApprovedOrEnqueued() {
        VersionApprovalState state = new VersionApprovalState(brief());
        state.saveVersion(version("fb-v1", FACEBOOK, null, "Facebook text", null));
        state.approve(FACEBOOK, "fb-v1");

        assertThrows(IllegalArgumentException.class,
                () -> state.approve(FACEBOOK, "missing"));
        assertThrows(IllegalArgumentException.class,
                () -> state.requireEnqueueable(FACEBOOK, "missing"));
        assertThrows(IllegalArgumentException.class,
                () -> state.requireEnqueueable(LINKEDIN, "fb-v1"));
    }

    @Test
    public void rejectedParentDoesNotChangeExistingApproval() {
        VersionApprovalState state = new VersionApprovalState(brief());
        ContentVersion approved = version("fb-v1", FACEBOOK, null, "Facebook text", null);
        state.saveVersion(approved);
        state.approve(FACEBOOK, "fb-v1");

        assertThrows(IllegalArgumentException.class,
                () -> state.saveVersion(version("fb-v2", FACEBOOK, "missing", "Edited", null)));

        assertSame(approved, state.requireEnqueueable(FACEBOOK, "fb-v1"));
    }

    @Test
    public void historyKeepsPastApprovalWithoutAllowingOldVersionToEnqueue() {
        VersionApprovalState state = new VersionApprovalState(brief());
        ContentVersion first = version("fb-v1", FACEBOOK, null, "Original", null);
        ContentVersion second = version("fb-v2", FACEBOOK, "fb-v1", "Edited", null);
        state.saveVersion(first);
        state.approve(FACEBOOK, "fb-v1");
        state.saveVersion(second);

        List<ContentVersion> history = state.versionsForChannel(FACEBOOK);
        assertEquals(2, history.size());
        assertSame(first, history.get(0));
        assertSame(second, history.get(1));
        assertTrue(state.wasApproved(FACEBOOK, "fb-v1"));
        assertFalse(state.wasApproved(FACEBOOK, "fb-v2"));
        assertThrows(UnsupportedOperationException.class, history::clear);
        assertThrows(IllegalStateException.class,
                () -> state.requireEnqueueable(FACEBOOK, "fb-v1"));
    }

    private static Brief brief() {
        return new Brief("brief-1", "Topic", "Goal", "Audience", "Tone", "vi",
                Arrays.asList(new Brief.Target(FACEBOOK, "FACEBOOK"),
                        new Brief.Target(LINKEDIN, "LINKEDIN")),
                Collections.emptyList());
    }

    private static ContentVersion version(String id, String channelId, String parentId,
                                          String text, String imageReference) {
        return new ContentVersion(id, "brief-1", channelId, parentId,
                text, imageReference, 1_762_819_200_000L);
    }
}
