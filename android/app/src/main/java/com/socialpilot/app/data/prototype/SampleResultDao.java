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

    @Query("UPDATE sample_results SET checkpoint = :checkpoint, updatedAt = :now "
            + "WHERE channelId = :channelId AND state = 'COMPLETE'")
    int completeCheckpoint(String channelId, String checkpoint, long now);
}
