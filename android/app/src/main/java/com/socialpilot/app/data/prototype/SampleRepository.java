package com.socialpilot.app.data.prototype;

import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;

/** Serializes demo writes so a late UI event cannot reorder saved stream states. */
public final class SampleRepository {
    private final SampleResultDao dao;
    private final ExecutorService io;

    public SampleRepository(SampleResultDao dao, ExecutorService io) {
        this.dao = dao;
        this.io = io;
    }

    public LiveData<List<SampleResultEntity>> observeAll() { return dao.observeAll(); }

    public void save(String channelId, String text, String state) {
        io.execute(() -> dao.save(new SampleResultEntity(channelId, text, state,
                "NOT_STARTED", System.currentTimeMillis())));
    }

    /** A new process never resumes a sample stream or issues an AI request. */
    public void markInterruptedOnOpen() {
        io.execute(() -> dao.markInterrupted(System.currentTimeMillis()));
    }
}
