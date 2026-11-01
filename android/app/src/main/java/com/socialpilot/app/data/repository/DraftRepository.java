package com.socialpilot.app.data.repository;
import androidx.lifecycle.LiveData;
import com.socialpilot.app.data.local.*;
import com.socialpilot.app.domain.DraftValidator;
import java.util.List;
import java.util.concurrent.ExecutorService;
public final class DraftRepository {
    public interface Completion { void complete(boolean saved); }
    private final DraftDao dao;
    private final ExecutorService executor;
    public DraftRepository(DraftDao dao, ExecutorService executor) { this.dao = dao; this.executor = executor; }
    public LiveData<List<DraftEntity>> observeRecent() { return dao.observeRecent(); }
    public void save(String topic, Completion completion) {
        String normalized = DraftValidator.normalize(topic);
        executor.execute(() -> {
            boolean saved;
            try { dao.insert(new DraftEntity(normalized, System.currentTimeMillis())); saved = true; }
            catch (RuntimeException error) { saved = false; }
            completion.complete(saved);
        });
    }
}
