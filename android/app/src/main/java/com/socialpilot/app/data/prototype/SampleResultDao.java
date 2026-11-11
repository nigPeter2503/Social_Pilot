package com.socialpilot.app.data.prototype;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SampleResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(SampleResultEntity result);

    @Query("SELECT * FROM sample_results ORDER BY channelId")
    LiveData<List<SampleResultEntity>> observeAll();

    @Query("SELECT * FROM sample_results WHERE channelId = :channelId LIMIT 1")
    SampleResultEntity get(String channelId);

    @Query("UPDATE sample_results SET state = 'INTERRUPTED', updatedAt = :now "
            + "WHERE state = 'RUNNING'")
    int markInterrupted(long now);

    @Query("UPDATE sample_results SET text = :text, state = :state, updatedAt = :now "
            + "WHERE channelId = :channelId AND runId = :runId")
    int updateRun(String channelId, String runId, String text, String state, long now);

    @Query("UPDATE sample_results SET workRequestId = :workRequestId, checkpoint = 'NOT_STARTED', "
            + "updatedAt = :now WHERE channelId = :channelId AND runId = :runId "
            + "AND state = 'COMPLETE'")
    int assignWork(String channelId, String runId, String workRequestId, long now);

    @Query("UPDATE sample_results SET workRequestId = NULL, updatedAt = :now "
            + "WHERE channelId = :channelId AND runId = :runId")
    int invalidateWork(String channelId, String runId, long now);

    @Query("UPDATE sample_results SET checkpoint = :checkpoint, updatedAt = :now "
            + "WHERE channelId = :channelId AND runId = :runId "
            + "AND workRequestId = :workRequestId AND state = 'COMPLETE'")
    int completeCheckpoint(String channelId, String runId, String workRequestId,
                           String checkpoint, long now);
}
