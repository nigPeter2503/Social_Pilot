package com.socialpilot.app.domain.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class BriefTest {
    @Test
    public void immutableSourceListsMayBeEmptyOrPopulated() {
        Brief empty = brief(List.of(new Brief.Target("facebook-1", "FACEBOOK")), List.of());
        Brief.Source source = new Brief.Source("TEXT", "sample");
        Brief populated = brief(List.of(new Brief.Target("facebook-1", "FACEBOOK")), List.of(source));

        assertEquals(0, empty.sources().size());
        assertEquals(source, populated.sources().get(0));
        assertThrows(IllegalArgumentException.class,
                () -> brief(List.of(new Brief.Target("facebook-1", "FACEBOOK")),
                        Arrays.asList(source, null)));
    }

    @Test
    public void briefAcceptsOneTargetPerPlatformButRejectsTwoFacebookChannels() {
        Brief both = brief(List.of(new Brief.Target("facebook-1", "FACEBOOK"),
                new Brief.Target("linkedin-1", "LINKEDIN")), List.of());
        assertEquals(2, both.targets().size());

        assertThrows(IllegalArgumentException.class,
                () -> brief(List.of(new Brief.Target("facebook-1", "FACEBOOK"),
                        new Brief.Target("facebook-2", "FACEBOOK")), List.of()));
    }

    private static Brief brief(List<Brief.Target> targets, List<Brief.Source> sources) {
        return new Brief("brief-1", "Topic", "Goal", "Audience", "Tone", "vi", targets, sources);
    }
}
