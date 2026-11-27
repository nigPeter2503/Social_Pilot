package com.socialpilot.app.data.remote.social;

import com.socialpilot.app.domain.contract.social.Platform;
import java.util.concurrent.Executor;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public final class InstagramCommentAdapter extends CommentAdapter {
    public InstagramCommentAdapter(OkHttpClient client, ConnectionSource connections,
            CredentialSource credentials, Clock clock, Executor backgroundExecutor,
            CommentCursorCodec cursors, String sourceRef) {
        this(client, connections, credentials, clock, backgroundExecutor, cursors,
                HttpUrl.get("https://graph.facebook.com/"), CommentRecord.Source.API_THAT, sourceRef);
    }
    InstagramCommentAdapter(OkHttpClient client, ConnectionSource connections, CredentialSource credentials,
            Clock clock, Executor executor, CommentCursorCodec cursors, HttpUrl endpoint,
            CommentRecord.Source source, String ref) {
        super(Platform.INSTAGRAM, client, connections, credentials, clock, executor, endpoint, cursors, source, ref);
    }
}
