package com.socialpilot.app.data.repository;

import androidx.lifecycle.LiveData;
import com.socialpilot.app.data.local.BriefDao;
import com.socialpilot.app.data.local.BriefDao.BriefSnapshot;
import com.socialpilot.app.data.local.BriefDao.BriefListItem;
import com.socialpilot.app.data.local.BriefEntity;
import com.socialpilot.app.data.local.BriefSourceEntity;
import com.socialpilot.app.data.local.BriefTargetEntity;
import com.socialpilot.app.domain.editor.Brief;
import com.socialpilot.app.domain.editor.BriefForm;
import com.socialpilot.app.domain.editor.BriefFormValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public final class BriefRepository {
    public interface Completion { void complete(String briefId, boolean saved); }

    private final BriefDao dao;
    private final ExecutorService io;

    public BriefRepository(BriefDao dao, ExecutorService io) {
        this.dao = dao;
        this.io = io;
    }

    public LiveData<List<BriefEntity>> observeRecent() { return dao.observeRecent(); }
    public LiveData<List<BriefListItem>> observeRecentDetails() {
        return dao.observeRecentDetails();
    }

    public void saveDraft(BriefForm form, Completion completion) {
        if (BriefFormValidator.validate(form, false).hasErrors())
            throw new IllegalArgumentException("Invalid brief draft");
        String briefId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        BriefEntity brief = new BriefEntity(briefId, null, form.topic, form.goal,
                form.audience, form.tone, form.contentLanguage, now, now);
        List<BriefSourceEntity> sources = new ArrayList<>();
        for (int index = 0; index < form.sources.size(); index++) {
            Brief.Source source = form.sources.get(index);
            sources.add(new BriefSourceEntity(UUID.randomUUID().toString(), briefId,
                    source.kind(), source.value(), index));
        }
        List<BriefTargetEntity> targets = new ArrayList<>();
        for (Brief.Target target : form.targets) {
            targets.add(new BriefTargetEntity(briefId, target.channelId(), target.platform()));
        }
        io.execute(() -> {
            try {
                dao.insert(brief, sources, targets);
                completion.complete(briefId, true);
            } catch (RuntimeException error) {
                completion.complete(briefId, false);
            }
        });
    }

    /** Reads one stable validated payload for a later AI admission; never reads live Views. */
    public Brief loadValidated(String briefId) {
        BriefSnapshot snapshot = dao.snapshot(briefId);
        if (snapshot == null) return null;
        BriefEntity row = snapshot.brief;
        List<Brief.Target> targets = new ArrayList<>();
        for (BriefTargetEntity target : snapshot.targets) {
            targets.add(new Brief.Target(target.channelId, target.platform));
        }
        List<Brief.Source> sources = new ArrayList<>();
        for (BriefSourceEntity source : snapshot.sources) {
            sources.add(new Brief.Source(source.kind, source.value));
        }
        return new Brief(briefId, row.topic, row.goal, row.audience, row.tone,
                row.contentLanguage, targets, sources);
    }
}
