package com.socialpilot.app.data.local.commentsync;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.socialpilot.app.data.local.ChannelEntity;

/** Candidate tables compiled against the Channel v2 key. Slot assignment controls registration in AppDatabase. */
public final class CommentTables {
    private CommentTables() {}

    @Entity(tableName = "published_posts", foreignKeys = @ForeignKey(entity = ChannelEntity.class,
            parentColumns = "channelId", childColumns = "channelId"),
            indices = @Index(value = {"channelId", "externalPostId"}, unique = true))
    public static class Post {
        @PrimaryKey @NonNull public String id = "";
        @NonNull public String channelId = "";
        @NonNull public String externalPostId = "";
        // Normalized metadata and per-field provenance from PublishedPost (no OAuth fields).
        @NonNull public String payload = "";
    }

    @Entity(tableName = "comments", primaryKeys = {"platform", "channelId", "commentId"},
            foreignKeys = @ForeignKey(entity = Post.class, parentColumns = "id", childColumns = "localPostId"),
            indices = {@Index("localPostId"), @Index({"channelId", "revision"})})
    public static class Comment {
        @NonNull public String platform = "";
        @NonNull public String channelId = "";
        @NonNull public String commentId = "";
        @NonNull public String localPostId = "";
        public long revision;
        public boolean handled;
        public Long createdAt;
        @NonNull public String payload = "";
    }

    @Entity(tableName = "comment_queries", foreignKeys = @ForeignKey(entity = Post.class,
            parentColumns = "id", childColumns = "localPostId"),
            indices = {@Index("localPostId"), @Index({"channelId", "revision"})})
    public static class QueryState {
        @PrimaryKey @NonNull public String fingerprint = "";
        @NonNull public String localPostId = "";
        @NonNull public String channelId = "";
        public long revision;
        @NonNull public String definition = "";
        public String checkpoint;
        public String attempt;
        public Long rateLimitedUntil;
    }

    @Entity(tableName = "comment_membership", primaryKeys = {"fingerprint", "commentId"},
            foreignKeys = @ForeignKey(entity = QueryState.class, parentColumns = "fingerprint",
                    childColumns = "fingerprint", onDelete = ForeignKey.CASCADE))
    public static class Membership {
        @NonNull public String fingerprint = "";
        @NonNull public String commentId = "";
    }
}
