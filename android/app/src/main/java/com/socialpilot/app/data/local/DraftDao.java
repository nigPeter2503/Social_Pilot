package com.socialpilot.app.data.local;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;
@Dao public interface DraftDao {
    @Insert long insert(DraftEntity draft);
    @Query("SELECT * FROM drafts ORDER BY createdAt DESC, id DESC LIMIT 20") LiveData<List<DraftEntity>> observeRecent();
}
