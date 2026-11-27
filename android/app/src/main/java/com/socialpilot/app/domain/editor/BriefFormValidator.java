package com.socialpilot.app.domain.editor;

import java.util.HashSet;
import java.util.Set;

public final class BriefFormValidator {
    private BriefFormValidator() { }

    public static final class Errors {
        public final boolean topic;
        public final boolean goal;
        public final boolean audience;
        public final boolean tone;
        public final boolean language;
        public final boolean targets;
        public final boolean sources;

        private Errors(boolean topic, boolean goal, boolean audience, boolean tone,
                boolean language, boolean targets, boolean sources) {
            this.topic = topic;
            this.goal = goal;
            this.audience = audience;
            this.tone = tone;
            this.language = language;
            this.targets = targets;
            this.sources = sources;
        }

        public boolean hasErrors() {
            return topic || goal || audience || tone || language || targets || sources;
        }
    }

    public static Errors validate(BriefForm form, boolean requireTargets) {
        boolean invalidTargets = requireTargets && form.targets.isEmpty();
        Set<String> channels = new HashSet<>();
        Set<String> platforms = new HashSet<>();
        for (Brief.Target target : form.targets) {
            if (target == null || !channels.add(target.channelId())
                    || !platforms.add(target.platform())) invalidTargets = true;
        }
        boolean invalidSources = false;
        for (Brief.Source source : form.sources) {
            if (source == null) invalidSources = true;
        }
        return new Errors(form.topic.isEmpty(), form.goal.isEmpty(), form.audience.isEmpty(),
                form.tone.isEmpty(), !"vi".equals(form.contentLanguage)
                        && !"en".equals(form.contentLanguage),
                invalidTargets, invalidSources);
    }
}
