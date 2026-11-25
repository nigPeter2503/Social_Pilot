package com.socialpilot.app.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Coarse connectivity check used to show an offline state instead of a misleading error. */
public interface NetworkStatus {
    boolean isOnline();

    static NetworkStatus of(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        return () -> {
            if (manager == null) return true; // Unknown: let the request decide.
            try {
                Network network = manager.getActiveNetwork();
                NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
                return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } catch (RuntimeException unavailable) {
                return true;
            }
        };
    }
}
