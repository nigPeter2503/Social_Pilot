package com.socialpilot.app.domain.contract.ai;

/**
 * Logical AI tasks that pick their own provider/model. Business prompts belong to the module
 * owners (content: Thiện; comments/report: Nghị); all of them go through the same gateway.
 */
public enum AiTaskType {
    CONTENT_TEXT(AiRequest.Task.TEXT),
    COMMENT_CLASSIFICATION(AiRequest.Task.TEXT),
    COMMENT_REPLY(AiRequest.Task.TEXT),
    ANALYTICS_REPORT(AiRequest.Task.TEXT),
    /** Separate capability; generation and template fallback belong to SP-032. */
    IMAGE(AiRequest.Task.IMAGE);

    public final AiRequest.Task task;

    AiTaskType(AiRequest.Task task) {
        this.task = task;
    }
}
