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

    @Query("SELECT * FROM sample_results WHERE workRequestId IS NOT NULL")
    List<SampleResultEntity> withAssignedWork();

    @Query("UPDATE sample_results SET state = 'INTERRUPTED', updatedAt = :now "
            + "WHERE state = 'RUNNING'")
    int markInterrupted(long now);

    @Query("UPDATE sample_results SET text = :text, state = :state, updatedAt = :now "
            + "WHERE channelId = :channelId AND runId = :runId")
    int updateRun(String channelId, String runId, String text, String state, long now);

    @Query("UPDATE sample_results SET workRequestId = :workRequestId, workEnqueueFailed = 0, "
            + "updatedAt = :now WHERE channelId = :channelId AND runId = :runId "
            + "AND state = 'COMPLETE'")
    int assignWork(String channelId, String runId, String workRequestId, long now);

    @Query("UPDATE sample_results SET workRequestId = NULL, workEnqueueFailed = 0, updatedAt = :now "
            + "WHERE channelId = :channelId AND runId = :runId "
            + "AND workRequestId = :workRequestId")
    int invalidateWork(String channelId, String runId, String workRequestId, long now);

    @Query("UPDATE sample_results SET workRequestId = NULL, workEnqueueFailed = 1, "
            + "updatedAt = :now WHERE channelId = :channelId AND runId = :runId "
            + "AND workRequestId = :workRequestId")
    int markWorkEnqueueFailed(String channelId, String runId, String workRequestId, long now);

    @Query("UPDATE sample_results SET checkpoint = :checkpoint, updatedAt = :now "
            + "WHERE channelId = :channelId AND runId = :runId "
            + "AND workRequestId = :workRequestId AND state = 'COMPLETE'")
    int completeCheckpoint(String channelId, String runId, String workRequestId,
                           String checkpoint, long now);
}
