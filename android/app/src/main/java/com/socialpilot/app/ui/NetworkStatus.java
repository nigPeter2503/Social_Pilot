package com.socialpilot.app.ui;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

/** Shared app-level definition of an internet connection validated by the system. */
final class NetworkStatus {
    private NetworkStatus() { }

    static boolean isOnline(ConnectivityManager manager) {
        NetworkCapabilities capabilities = manager == null ? null
                : manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
