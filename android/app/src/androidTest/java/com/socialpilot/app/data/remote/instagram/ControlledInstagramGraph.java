package com.socialpilot.app.data.remote.instagram;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

public final class ControlledInstagramGraph {
    private ControlledInstagramGraph() {}

    public static InstagramGraphClient withResponse(Interceptor interceptor) {
        return new InstagramGraphClient(new OkHttpClient.Builder().addInterceptor(interceptor).build());
    }
}
