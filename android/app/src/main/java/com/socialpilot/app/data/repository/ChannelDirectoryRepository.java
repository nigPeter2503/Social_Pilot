package com.socialpilot.app.data.repository;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import com.socialpilot.app.core.FacebookOAuthCoordinator;
import com.socialpilot.app.data.repository.ChannelDirectory.Channel;
import com.socialpilot.app.data.repository.ChannelDirectory.Identity;
import com.socialpilot.app.data.security.EncryptedOAuthStore.PageMetadata;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;

public final class ChannelDirectoryRepository {
    public interface Callback {
        void onLoaded(List<Channel> channels, boolean online);
        void onFailure();
    }

    private final ConnectivityManager connectivity;
    private final FacebookOAuthCoordinator facebook;
    private final Executor io;
    private final Handler main = new Handler(Looper.getMainLooper());

    public ChannelDirectoryRepository(Context context, FacebookOAuthCoordinator facebook, Executor io) {
        connectivity = context.getSystemService(ConnectivityManager.class);
        this.facebook = facebook;
        this.io = io;
    }

    public void load(Callback callback) {
        io.execute(() -> {
            try {
                NetworkCapabilities network = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
                boolean online = network != null && network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                PageMetadata page = facebook.savedPageMetadata();
                List<Channel> channels = page == null ? Collections.emptyList() : Collections.singletonList(
                        new Channel(new Identity(null, "FACEBOOK", "PAGE", page.externalAccountId, null),
                                page.displayName, connectionStatus(page.expiresAt, System.currentTimeMillis()),
                                Collections.emptyList()));
                main.post(() -> callback.onLoaded(channels, online));
            } catch (ParseException | RuntimeException error) {
                main.post(callback::onFailure);
            }
        });
    }

    static ConnectionStatus connectionStatus(String expiresAt, long now) throws ParseException {
        if (expiresAt == null) return ConnectionStatus.CONNECTED;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        return format.parse(expiresAt).getTime() <= now
                ? ConnectionStatus.REAUTH_REQUIRED : ConnectionStatus.CONNECTED;
    }
}
