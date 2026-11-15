package com.socialpilot.app.domain.editor;

/** One immutable channel-specific preview. Persistence is handled separately. */
public final class ContentVersion {
    private final String versionId;
    private final String briefId;
    private final String channelId;
    private final String parentVersionId;
    private final String text;
    private final String imageReference;
    private final long createdAtEpochMillis;

    public ContentVersion(String versionId, String briefId, String channelId,
                          String parentVersionId, String text, String imageReference,
                          long createdAtEpochMillis) {
        this.versionId = required("versionId", versionId);
        this.briefId = required("briefId", briefId);
        this.channelId = required("channelId", channelId);
        this.parentVersionId = optional(parentVersionId);
        if (this.versionId.equals(this.parentVersionId)) {
            throw new IllegalArgumentException("a version cannot be its own parent");
        }
        this.text = required("text", text);
        this.imageReference = optional(imageReference);
        if (createdAtEpochMillis <= 0) {
            throw new IllegalArgumentException("createdAtEpochMillis must be positive");
        }
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    public String versionId() { return versionId; }
    public String briefId() { return briefId; }
    public String channelId() { return channelId; }
    public String parentVersionId() { return parentVersionId; }
    public String text() { return text; }
    public String imageReference() { return imageReference; }
    public long createdAtEpochMillis() { return createdAtEpochMillis; }

    private static String required(String name, String value) {
        String result = optional(value);
        if (result == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return result;
    }

    private static String optional(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
}
