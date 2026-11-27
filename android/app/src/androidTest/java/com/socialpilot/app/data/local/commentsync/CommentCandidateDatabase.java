package com.socialpilot.app.data.local.commentsync;

import androidx.room.Database;
import com.socialpilot.app.data.local.*;

/** Instrumentation-only registration. Its version is private to this test database. */
@Database(entities = {DraftEntity.class, ChannelEntity.class, ChannelSecretCleanup.class,
        CommentTables.Post.class, CommentTables.Comment.class, CommentTables.QueryState.class,
        CommentTables.Membership.class}, version = 1, exportSchema = false)
public abstract class CommentCandidateDatabase extends AppDatabase implements CommentRoomStore.Schema {
    @Override public abstract CommentSyncDao commentSync();
}
