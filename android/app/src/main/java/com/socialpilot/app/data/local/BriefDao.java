package com.socialpilot.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Embedded;
import androidx.room.Relation;
import androidx.room.Transaction;
import com.socialpilot.app.domain.contract.social.ConnectionLifecycle.Scope;
import java.util.List;

@Dao
public abstract class BriefDao {
    @Insert protected abstract void insertBrief(BriefEntity brief);
    @Insert protected abstract void insertSources(List<BriefSourceEntity> sources);
    @Insert protected abstract void insertTargets(List<BriefTargetEntity> targets);

    @Transaction
    public void insert(BriefEntity brief, List<BriefSourceEntity> sources,
            List<BriefTargetEntity> targets) {
        insertBrief(brief);
        if (!sources.isEmpty()) insertSources(sources);
        if (!targets.isEmpty()) insertTargets(targets);
    }

    @Query("SELECT COUNT(*) FROM channels WHERE channelId = :channelId AND platform = :platform "
            + "AND connectionRevision = :revision AND status = 'CONNECTED' "
            + "AND (effectiveExpiresAtMillis IS NULL OR effectiveExpiresAtMillis > :now)")
    protected abstract int currentChannel(String channelId, String platform, long revision, long now);

    /** A reconnect cannot slip between target validation and the brief write. */
    @Transaction
    public void insertScoped(BriefEntity brief, List<BriefSourceEntity> sources,
            List<BriefTargetEntity> targets, List<Scope> scopes) {
        if (targets.size() != scopes.size()) throw new IllegalArgumentException("Target scope mismatch");
        long now = System.currentTimeMillis();
        for (int index = 0; index < targets.size(); index++) {
            BriefTargetEntity target = targets.get(index);
            Scope scope = scopes.get(index);
            if (scope == null || !target.channelId.equals(scope.channelId)
                    || !target.platform.equals(scope.platform)
                    || currentChannel(scope.channelId, scope.platform, scope.connectionRevision, now) != 1) {
                throw new IllegalStateException("Target connection changed");
            }
        }
        insertBrief(brief);
        if (!sources.isEmpty()) insertSources(sources);
        if (!targets.isEmpty()) insertTargets(targets);
    }

    @Query("SELECT * FROM briefs ORDER BY createdAt DESC, briefId DESC LIMIT 20")
    public abstract LiveData<List<BriefEntity>> observeRecent();

    @Transaction
    @Query("SELECT * FROM briefs ORDER BY createdAt DESC, briefId DESC LIMIT 20")
    public abstract LiveData<List<BriefListItem>> observeRecentDetails();

    @Query("SELECT * FROM briefs WHERE briefId = :briefId LIMIT 1")
    public abstract BriefEntity get(String briefId);

    @Query("SELECT * FROM brief_sources WHERE briefId = :briefId ORDER BY position")
    public abstract List<BriefSourceEntity> sources(String briefId);

    @Query("SELECT * FROM brief_targets WHERE briefId = :briefId ORDER BY platform")
    public abstract List<BriefTargetEntity> targets(String briefId);

    @Query("SELECT COUNT(*) FROM briefs WHERE topic = :topic")
    public abstract int countByTopic(String topic);

    /** Reads one consistent payload even after editing is added in SP-036. */
    @Transaction
    public BriefSnapshot snapshot(String briefId) {
        BriefEntity brief = get(briefId);
        return brief == null ? null : new BriefSnapshot(brief, sources(briefId), targets(briefId));
    }

    public static final class BriefSnapshot {
        public final BriefEntity brief;
        public final List<BriefSourceEntity> sources;
        public final List<BriefTargetEntity> targets;

        BriefSnapshot(BriefEntity brief, List<BriefSourceEntity> sources,
                List<BriefTargetEntity> targets) {
            this.brief = brief;
            this.sources = sources;
            this.targets = targets;
        }
    }

    public static final class BriefListItem {
        @Embedded public BriefEntity brief;
        @Relation(parentColumn = "briefId", entityColumn = "briefId")
        public List<BriefSourceEntity> sources;
        @Relation(parentColumn = "briefId", entityColumn = "briefId")
        public List<BriefTargetEntity> targets;
    }
}
