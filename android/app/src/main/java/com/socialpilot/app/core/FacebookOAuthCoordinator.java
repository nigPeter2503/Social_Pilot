package com.socialpilot.app.core;
import android.content.Context;
import java.util.concurrent.ExecutorService;
/** Source-compatible Facebook facade. */
public final class FacebookOAuthCoordinator extends OAuthCoordinator {
    public FacebookOAuthCoordinator(Context context,ExecutorService io,String url){super(context,io,url);}
    public void start(Listener listener){super.start("FACEBOOK",listener);}
    public String connectedPageSummary(){return summary("FACEBOOK");}
}
