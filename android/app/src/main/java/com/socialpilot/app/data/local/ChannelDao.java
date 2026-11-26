package com.socialpilot.app.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;
import java.util.List;

@Dao
public interface ChannelDao {
    @Query("SELECT * FROM channels WHERE channelId = :id")
    ChannelEntity find(String id);

    @Query("SELECT * FROM channels WHERE platform = :platform AND accountType = :type AND externalAccountId = :externalId")
    ChannelEntity findIdentity(String platform, String type, String externalId);

    @Query("SELECT * FROM channels WHERE platform = :platform AND selected = 1 LIMIT 1")
    ChannelEntity selected(String platform);

    @Query("SELECT * FROM channels ORDER BY platform, channelId")
    List<ChannelEntity> list();

    @Query("UPDATE channels SET selected = 0 WHERE platform = :platform AND selected = 1")
    void clearSelection(String platform);

    @Upsert
    void save(ChannelEntity channel);

    @Query("UPDATE channels SET status = 'REAUTH_REQUIRED' WHERE channelId = :id AND platform = :platform"
            + " AND connectionRevision = :revision AND status = 'CONNECTED'")
    int requireReconnect(String id, String platform, long revision);

    @Query("UPDATE channels SET capabilitiesJson = :json WHERE channelId = :id AND platform = :platform"
            + " AND connectionRevision = :revision AND status = 'CONNECTED'")
    int saveCapabilities(String id, String platform, long revision, String json);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void queueCleanup(ChannelSecretCleanup reference);

    @Query("SELECT credentialRef FROM channel_secret_cleanup")
    List<String> pendingCleanup();

    @Query("DELETE FROM channel_secret_cleanup WHERE credentialRef = :ref")
    void finishCleanup(String ref);

    @Query("SELECT EXISTS(SELECT 1 FROM channels WHERE credentialRef = :ref)")
    boolean references(String ref);
}
