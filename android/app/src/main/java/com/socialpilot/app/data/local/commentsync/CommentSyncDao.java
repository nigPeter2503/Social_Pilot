package com.socialpilot.app.data.local.commentsync;

import androidx.room.*;
import java.util.List;
import com.socialpilot.app.data.local.commentsync.CommentTables.*;

@Dao
public interface CommentSyncDao {
    @Query("SELECT payload FROM published_posts WHERE channelId=:channel AND externalPostId=:external")
    String post(String channel, String external);
    @Query("SELECT payload FROM published_posts WHERE id=:id")
    String post(String id);
    @Insert void insert(Post post);
    @Update int update(Post post);

    @Query("SELECT * FROM comments WHERE platform=:platform AND channelId=:channel AND commentId=:id")
    Comment comment(String platform, String channel, String id);
    @Upsert void save(Comment comment);

    @Insert(onConflict = OnConflictStrategy.IGNORE) void remember(QueryState query);
    @Query("SELECT definition FROM comment_queries WHERE fingerprint=:fingerprint")
    String definition(String fingerprint);
    @Query("SELECT checkpoint FROM comment_queries WHERE fingerprint=:fingerprint")
    String checkpoint(String fingerprint);
    @Query("UPDATE comment_queries SET checkpoint=:checkpoint WHERE fingerprint=:fingerprint")
    int checkpoint(String fingerprint, String checkpoint);
    @Query("SELECT attempt FROM comment_queries WHERE fingerprint=:fingerprint")
    String attempt(String fingerprint);
    @Query("UPDATE comment_queries SET attempt=:attempt, rateLimitedUntil=:until WHERE fingerprint=:fingerprint")
    int attempt(String fingerprint, String attempt, Long until);
    @Query("SELECT MAX(rateLimitedUntil) FROM comment_queries WHERE channelId=:channel AND revision=:revision AND rateLimitedUntil>:now")
    Long embargo(String channel, long revision, long now);
    @Insert(onConflict = OnConflictStrategy.IGNORE) void include(Membership membership);

    @Query("SELECT c.payload FROM comment_membership m JOIN comment_queries q ON q.fingerprint=m.fingerprint"
            + " JOIN comments c ON c.localPostId=q.localPostId AND c.channelId=q.channelId AND c.revision=q.revision"
            + " AND c.commentId=m.commentId WHERE q.fingerprint=:fingerprint AND (:handled IS NULL OR c.handled=:handled)"
            + " AND (:since IS NULL OR c.createdAt IS NULL OR c.createdAt>=:since)")
    List<String> comments(String fingerprint, Boolean handled, Long since);
    @Query("SELECT fingerprint FROM comment_queries WHERE channelId=:channel AND revision=:revision")
    List<String> fingerprints(String channel, long revision);
    @Query("DELETE FROM comment_queries WHERE channelId=:channel AND revision=:revision")
    void deleteQueries(String channel, long revision);
    @Query("DELETE FROM comments WHERE channelId=:channel AND revision=:revision")
    void deleteComments(String channel, long revision);
}
