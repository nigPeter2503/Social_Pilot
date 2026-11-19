package com.socialpilot.app.domain.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An immutable UI snapshot. Targets may be empty while saved as an offline draft. */
public final class BriefForm {
    public final String topic;
    public final String goal;
    public final String audience;
    public final String tone;
    public final String contentLanguage;
    public final List<Brief.Target> targets;
    public final List<Brief.Source> sources;

    public BriefForm(String topic, String goal, String audience, String tone,
            String contentLanguage, List<Brief.Target> targets, List<Brief.Source> sources) {
        this.topic = trim(topic);
        this.goal = trim(goal);
        this.audience = trim(audience);
        this.tone = trim(tone);
        this.contentLanguage = contentLanguage;
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }

    public Brief validatedBrief(String briefId) {
        return new Brief(briefId, topic, goal, audience, tone,
                contentLanguage, targets, sources);
    }
}
