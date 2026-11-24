package com.socialpilot.app.data.repository;

import android.content.Context;
import com.socialpilot.app.data.remote.instagram.ControlledInstagramGraph;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import okhttp3.Interceptor;

public final class ControlledInstagramPublishRepository {
    private ControlledInstagramPublishRepository() {}

    public static InstagramPublishRepository create(Context context, EncryptedOAuthStore connections,
            Interceptor interceptor) {
        return new InstagramPublishRepository(context, connections, ControlledInstagramGraph.withResponse(interceptor));
    }
}
