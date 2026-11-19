package com.socialpilot.app.domain.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class BriefFormValidatorTest {
    @Test public void missingFieldsAreReportedIndividually() {
        BriefForm form = new BriefForm(" ", "Goal", "", "Tone", "vi",
                Collections.emptyList(), Collections.emptyList());
        BriefFormValidator.Errors draft = BriefFormValidator.validate(form, false);
        assertTrue(draft.topic);
        assertTrue(draft.audience);
        assertFalse(draft.goal);
        assertFalse(draft.tone);
        assertFalse(draft.targets);
        assertTrue(BriefFormValidator.validate(form, true).targets);
    }

    @Test public void duplicatePlatformCannotBecomeDispatchPayload() {
        BriefForm form = new BriefForm("Topic", "Goal", "Audience", "Tone", "en",
                Arrays.asList(new Brief.Target("channel-a", "FACEBOOK"),
                        new Brief.Target("channel-b", "FACEBOOK")), Collections.emptyList());
        assertTrue(BriefFormValidator.validate(form, true).targets);
    }

    @Test public void oneTargetAndSourcesProduceStableBriefSnapshot() {
        BriefForm form = new BriefForm("  Topic  ", " Goal ", " Audience ", " Tone ", "vi",
                Collections.singletonList(new Brief.Target("internal-channel", "FACEBOOK")),
                Collections.singletonList(new Brief.Source("URL", "https://example.org/article")));
        assertFalse(BriefFormValidator.validate(form, true).hasErrors());
        Brief payload = form.validatedBrief("brief-1");
        assertEquals("Topic", payload.topic());
        assertEquals("Goal", payload.goal());
        assertEquals("internal-channel", payload.targets().get(0).channelId());
        assertEquals("https://example.org/article", payload.sources().get(0).value());
    }
}
