package com.socialpilot.app;
import android.app.Application;
import com.socialpilot.app.core.AppContainer;
public final class SocialPilotApplication extends Application {
    private AppContainer container;
    @Override public void onCreate() { super.onCreate(); container = new AppContainer(this); }
    public AppContainer container() { return container; }
}
