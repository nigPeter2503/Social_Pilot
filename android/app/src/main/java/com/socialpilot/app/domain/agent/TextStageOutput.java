package com.socialpilot.app.domain.agent;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Week-3 handoff to the editor (SP-036): the text of one channel with its correlation. The editor
 * applies it as a new unapproved version behind its expected-current guard. It is never a publish
 * request and never an approval. {@link #fullChainComplete} stays false until week-4 packaging.
 */
public final class TextStageOutput {
    public final String sessionId;
    public final String briefId;
    public final String channelId;
    public final long connectionRevision;
    public final ChannelPlatform platform;
    public final String language;
    public final String outline;
    public final String draft;
    public final RuleCheck ruleCheck;
    /** requestId of the attempt that produced each text stage. */
    public final Map<PipelineStage, String> requestIds;
    public final boolean fullChainComplete = false;

    private TextStageOutput(String sessionId, String briefId, ChannelBranch branch, String language) {
        this.sessionId = sessionId;
        this.briefId = briefId;
        this.channelId = branch.target.channelId;
        this.connectionRevision = branch.target.connectionRevision;
        this.platform = branch.target.platform;
        this.language = language;
        this.outline = branch.stage(PipelineStage.OUTLINE).text;
        this.draft = branch.stage(PipelineStage.DRAFT).text;
        this.ruleCheck = branch.ruleCheck;
        Map<PipelineStage, String> ids = new EnumMap<>(PipelineStage.class);
        for (StageResult result : branch.stages) if (result.stage.textStage) ids.put(result.stage, result.requestId);
        this.requestIds = Collections.unmodifiableMap(ids);
    }

    /** Null unless the channel's text stages all completed. */
    public static TextStageOutput of(PipelineSnapshot snapshot, String channelId, String language) {
        ChannelBranch branch = snapshot.branch(channelId);
        if (branch == null || branch.status != ChannelBranch.Status.TEXT_READY) return null;
        return new TextStageOutput(snapshot.sessionId, snapshot.briefId, branch, language);
    }
}
