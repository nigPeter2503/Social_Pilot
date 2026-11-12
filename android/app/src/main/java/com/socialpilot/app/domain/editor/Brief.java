package com.socialpilot.app.domain.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Input shared by the two channel previews. It does not represent a publish request. */
public final class Brief {
    private final String briefId;
    private final String topic;
    private final String goal;
    private final String audience;
    private final String tone;
    private final String language;
    private final List<Target> targets;
    private final List<Source> sources;

    public Brief(String briefId, String topic, String goal, String audience, String tone,
                 String language, List<Target> targets, List<Source> sources) {
        this.briefId = required("briefId", briefId);
        this.topic = required("topic", topic);
        this.goal = required("goal", goal);
        this.audience = required("audience", audience);
        this.tone = required("tone", tone);
        if (!"vi".equals(language) && !"en".equals(language)) {
            throw new IllegalArgumentException("language must be vi or en");
        }
        this.language = language;
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("at least one target is required");
        }
        Set<String> channelIds = new HashSet<>();
        Set<String> platforms = new HashSet<>();
        for (Target target : targets) {
            if (target == null || !channelIds.add(target.channelId)) {
                throw new IllegalArgumentException("targets must be non-null and distinct");
            }
            if (!platforms.add(target.platform)) {
                throw new IllegalArgumentException("only one target per platform is supported");
            }
        }
        this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
        if (sources == null) {
            throw new IllegalArgumentException("sources must be non-null");
        }
        List<Source> sourceCopy = new ArrayList<>();
        for (Source source : sources) {
            if (source == null) {
                throw new IllegalArgumentException("sources must not contain null");
            }
            sourceCopy.add(source);
        }
        this.sources = Collections.unmodifiableList(sourceCopy);
    }

    public String briefId() { return briefId; }
    public String topic() { return topic; }
    public String goal() { return goal; }
    public String audience() { return audience; }
    public String tone() { return tone; }
    public String language() { return language; }
    public List<Target> targets() { return targets; }
    public List<Source> sources() { return sources; }

    private static String required(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    public static final class Target {
        private final String channelId;
        private final String platform;

        public Target(String channelId, String platform) {
            this.channelId = required("channelId", channelId);
            if (!"FACEBOOK".equals(platform) && !"LINKEDIN".equals(platform)) {
                throw new IllegalArgumentException("unsupported platform");
            }
            this.platform = platform;
        }

        public String channelId() { return channelId; }
        public String platform() { return platform; }
    }

    public static final class Source {
        private final String kind;
        private final String value;

        public Source(String kind, String value) {
            if (!"URL".equals(kind) && !"TEXT".equals(kind)) {
                throw new IllegalArgumentException("unsupported source kind");
            }
            this.kind = kind;
            this.value = required("source value", value);
        }

        public String kind() { return kind; }
        public String value() { return value; }
    }
}
