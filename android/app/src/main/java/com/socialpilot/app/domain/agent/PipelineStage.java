package com.socialpilot.app.domain.agent;

/** The six steps of each channel branch (Proposal F2.B2), in order. */
public enum PipelineStage {
    OUTLINE("outline", true),
    DRAFT("draft", true),
    RULE_CHECK("rule-check", true),
    /** Week 4 (SP-031 part 2): image description. */
    IMAGE_PROMPT("image-prompt", false),
    /** Week 4, image or template from SP-032. */
    IMAGE("image", false),
    /** Week 4: immutable text/image/metadata package for the editor. */
    PACKAGE("package", false);

    /** Used in {@code stepId}; stable across releases because it is stored in the ledger. */
    public final String stepId;
    /** Implemented by the week-3 text pipeline. */
    public final boolean textStage;

    PipelineStage(String stepId, boolean textStage) {
        this.stepId = stepId;
        this.textStage = textStage;
    }
}
