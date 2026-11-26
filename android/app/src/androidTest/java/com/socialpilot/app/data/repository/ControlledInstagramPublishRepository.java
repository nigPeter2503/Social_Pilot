package com.socialpilot.app.data.repository;

import com.socialpilot.app.data.local.AppDatabase;
import com.socialpilot.app.domain.contract.secret.SecretStore;

import android.content.Context;
import com.socialpilot.app.data.remote.instagram.ControlledInstagramGraph;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import okhttp3.Interceptor;

public final class ControlledInstagramPublishRepository {
    private ControlledInstagramPublishRepository() {}

    public static ChannelRepository channels(AppDatabase database, SecretStore secrets, Interceptor interceptor) {
        return new ChannelRepository(database, secrets, ControlledInstagramGraph.withResponse(interceptor));
    }

    public static InstagramPublishRepository create(Context context, EncryptedOAuthStore connections,
            Interceptor interceptor) {
        return new InstagramPublishRepository(context, connections, ControlledInstagramGraph.withResponse(interceptor));
    }
}
