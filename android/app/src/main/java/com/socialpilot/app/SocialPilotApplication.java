package com.socialpilot.app;
import android.app.Application;
import com.socialpilot.app.core.AppContainer;
import com.socialpilot.app.core.CommentSyncWorker;
import com.socialpilot.app.data.repository.comment.CommentRepository;

public final class SocialPilotApplication extends Application implements CommentSyncWorker.Provider {
    @Override public CommentRepository comments() { return container.comments(); }
    private AppContainer container;
    @Override public void onCreate() { super.onCreate(); container = new AppContainer(this); }
    public AppContainer container() { return container; }
}
