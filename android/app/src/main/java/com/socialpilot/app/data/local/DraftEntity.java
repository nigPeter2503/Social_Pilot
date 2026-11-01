package com.socialpilot.app.data.local;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "drafts")
public class DraftEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public String topic;
    public long createdAt;
    public DraftEntity(String topic, long createdAt) { this.topic = topic; this.createdAt = createdAt; }
}
