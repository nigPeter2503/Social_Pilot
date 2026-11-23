package com.socialpilot.app.domain.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Result of the rule-check stage: the model's self-review plus deterministic local checks. */
public final class RuleCheck {
    public enum Verdict { PASS, REVISE, UNPARSED }

    /** Local findings that do not depend on the model. */
    public enum LocalIssue {
        EMPTY_DRAFT,
        /** The draft contains a link that is not one of the brief's sources. */
        LINK_NOT_IN_SOURCES,
        /** Platform length limits are not verified yet, so length is not checked (no invented numbers). */
        LENGTH_LIMIT_UNVERIFIED
    }

    private static final Pattern LINK = Pattern.compile("https?://[^\\s)\\]>\"']+", Pattern.CASE_INSENSITIVE);

    public final Verdict verdict;
    public final List<String> modelIssues;
    public final List<LocalIssue> localIssues;
    public final List<String> unknownLinks;

    private RuleCheck(Verdict verdict, List<String> modelIssues, List<LocalIssue> localIssues, List<String> unknownLinks) {
        this.verdict = verdict;
        this.modelIssues = Collections.unmodifiableList(modelIssues);
        this.localIssues = Collections.unmodifiableList(localIssues);
        this.unknownLinks = Collections.unmodifiableList(unknownLinks);
    }

    /** True when nothing needs attention beyond the always-present unverified-limit note. */
    public boolean clean() {
        return verdict == Verdict.PASS && localIssues.size() == 1
                && localIssues.get(0) == LocalIssue.LENGTH_LIMIT_UNVERIFIED;
    }

    public static RuleCheck of(String modelOutput, String draft, List<PipelineInput.Source> sources) {
        Verdict verdict = Verdict.UNPARSED;
        List<String> issues = new ArrayList<>();
        if (modelOutput != null) {
            for (String raw : modelOutput.split("\\r?\\n")) {
                String line = raw.trim();
                String upper = line.toUpperCase(Locale.ROOT);
                if (verdict == Verdict.UNPARSED && upper.startsWith("VERDICT:")) {
                    String value = upper.substring("VERDICT:".length()).trim();
                    if (value.startsWith("PASS")) verdict = Verdict.PASS;
                    else if (value.startsWith("REVISE")) verdict = Verdict.REVISE;
                } else if (line.startsWith("- ") && line.length() > 2) {
                    issues.add(line.substring(2).trim());
                }
            }
        }
        List<LocalIssue> local = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        if (draft == null || draft.trim().isEmpty()) {
            local.add(LocalIssue.EMPTY_DRAFT);
        } else {
            Set<String> known = new LinkedHashSet<>();
            for (PipelineInput.Source source : sources) {
                Matcher inSource = LINK.matcher(source.value);
                while (inSource.find()) known.add(normalize(inSource.group()));
            }
            Matcher inDraft = LINK.matcher(draft);
            while (inDraft.find()) {
                String link = inDraft.group();
                if (!known.contains(normalize(link)) && !unknown.contains(link)) unknown.add(link);
            }
            if (!unknown.isEmpty()) local.add(LocalIssue.LINK_NOT_IN_SOURCES);
        }
        local.add(LocalIssue.LENGTH_LIMIT_UNVERIFIED);
        return new RuleCheck(verdict, issues, local, unknown);
    }

    private static String normalize(String link) {
        String value = link.toLowerCase(Locale.ROOT);
        while (value.endsWith("/") || value.endsWith(".") || value.endsWith(",")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
