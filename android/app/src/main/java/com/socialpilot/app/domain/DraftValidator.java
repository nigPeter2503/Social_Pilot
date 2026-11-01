package com.socialpilot.app.domain;
public final class DraftValidator {
    private DraftValidator() {}
    public static String normalize(String topic) {
        if (topic == null || topic.trim().isEmpty()) throw new IllegalArgumentException("Topic required");
        return topic.trim();
    }
}
