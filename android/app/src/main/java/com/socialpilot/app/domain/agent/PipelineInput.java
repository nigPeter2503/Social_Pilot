package com.socialpilot.app.domain.agent;

import com.socialpilot.app.domain.editor.Brief;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A validated brief plus the selected targets. The pipeline writes only for these targets and
 * never adds a channel that was not selected.
 */
public final class PipelineInput {
    public enum Problem { NO_TARGETS, TOO_MANY_TARGETS, DUPLICATE_PLATFORM, DUPLICATE_CHANNEL, UNSUPPORTED_PLATFORM, MISSING_REVISION }

    public static final class Source {
        public final boolean url;
        public final String value;

        public Source(boolean url, String value) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("source value is required");
            this.url = url;
            this.value = value.trim();
        }
    }

    public final String briefId;
    public final String topic;
    public final String goal;
    public final String audience;
    public final String tone;
    /** Content language ("vi" or "en"), not the UI language. */
    public final String language;
    public final List<Source> sources;
    public final List<ChannelTarget> targets;

    public PipelineInput(String briefId, String topic, String goal, String audience, String tone, String language,
                         List<Source> sources, List<ChannelTarget> targets) {
        this.briefId = required(briefId, "briefId");
        this.topic = required(topic, "topic");
        this.goal = required(goal, "goal");
        this.audience = required(audience, "audience");
        this.tone = required(tone, "tone");
        if (!"vi".equals(language) && !"en".equals(language)) throw new IllegalArgumentException("language must be vi or en");
        this.language = language;
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        Problem problem = check(targets);
        if (problem != null) throw new IllegalArgumentException(problem.name());
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
    }

    /** 1–2 targets, at most one per platform, distinct channels. Null when valid. */
    public static Problem check(List<ChannelTarget> targets) {
        if (targets == null || targets.isEmpty()) return Problem.NO_TARGETS;
        if (targets.size() > 2) return Problem.TOO_MANY_TARGETS;
        Set<String> channels = new HashSet<>();
        Set<ChannelPlatform> platforms = EnumSet.noneOf(ChannelPlatform.class);
        for (ChannelTarget target : targets) {
            if (!channels.add(target.channelId)) return Problem.DUPLICATE_CHANNEL;
            if (!platforms.add(target.platform)) return Problem.DUPLICATE_PLATFORM;
        }
        return null;
    }

    /** Result of mapping a saved brief; exactly one of {@code input} or {@code problem} is set. */
    public static final class Mapping {
        public final PipelineInput input;
        public final Problem problem;

        private Mapping(PipelineInput input, Problem problem) {
            this.input = input;
            this.problem = problem;
        }
    }

    /**
     * Maps an editor brief. Each target needs the current connection revision of its channel
     * (from the social connection owner); a target without one is not run.
     */
    public static Mapping from(Brief brief, Map<String, Long> connectionRevisions) {
        List<ChannelTarget> targets = new ArrayList<>();
        for (Brief.Target target : brief.targets()) {
            ChannelPlatform platform = ChannelPlatform.fromBrief(target.platform());
            if (platform == null) return new Mapping(null, Problem.UNSUPPORTED_PLATFORM);
            Long revision = connectionRevisions.get(target.channelId());
            if (revision == null || revision <= 0) return new Mapping(null, Problem.MISSING_REVISION);
            targets.add(new ChannelTarget(target.channelId(), platform, revision));
        }
        Problem problem = check(targets);
        if (problem != null) return new Mapping(null, problem);
        List<Source> sources = new ArrayList<>();
        for (Brief.Source source : brief.sources()) sources.add(new Source("URL".equals(source.kind()), source.value()));
        return new Mapping(new PipelineInput(brief.briefId(), brief.topic(), brief.goal(), brief.audience(), brief.tone(),
                brief.language(), sources, targets), null);
    }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
