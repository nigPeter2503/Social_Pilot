package com.socialpilot.app.ui;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.socialpilot.app.R;
import com.socialpilot.app.databinding.ActivityDashboardBinding;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** A labelled SP-020 fixture preview; production binding is owned by SP-046. */
public final class DashboardActivity extends AppCompatActivity {
    private ActivityDashboardBinding binding;
    private DashboardViewModel model;
    private boolean observingNetwork;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(android.net.Network network) {
                    postNetworkRefresh();
                }
                @Override public void onLost(android.net.Network network) {
                    postNetworkRefresh();
                }
                @Override public void onCapabilitiesChanged(android.net.Network network,
                        NetworkCapabilities capabilities) {
                    postNetworkRefresh();
                }
            };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        model = new ViewModelProvider(this).get(DashboardViewModel.class);
        ArrayAdapter<String> metrics = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{getString(R.string.dashboard_followers),
                        getString(R.string.dashboard_engagement)});
        metrics.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.metricSelector.setAdapter(metrics);
        binding.metricSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                model.selectMetric(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        model.selectedMetric().observe(this, metric -> {
            if (binding.metricSelector.getSelectedItemPosition() != metric) {
                binding.metricSelector.setSelection(metric, false);
            }
            renderFixture(metric);
        });
    }

    @Override protected void onStart() {
        super.onStart();
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager != null) {
            try {
                manager.registerNetworkCallback(new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                        networkCallback);
                observingNetwork = true;
            } catch (RuntimeException ignored) {
                observingNetwork = false;
            }
        }
        refreshNetworkStatus();
    }

    @Override protected void onStop() {
        boolean wasObserving = observingNetwork;
        observingNetwork = false;
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (wasObserving && manager != null) {
            try {
                manager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
                // Registration may have failed in onStart.
            }
        }
        mainHandler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    private void postNetworkRefresh() {
        mainHandler.post(() -> {
            if (observingNetwork) refreshNetworkStatus();
        });
    }

    private void refreshNetworkStatus() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        binding.networkStatus.setText(NetworkStatus.isOnline(manager)
                ? R.string.dashboard_online : R.string.dashboard_offline);
    }

    private void renderFixture(int metric) {
        DashboardViewModel.PreviewValue preview = model.previewValue(metric);
        if (preview.value != null) {
            binding.metricValue.setText(String.valueOf(preview.value));
            binding.metricDetail.setText(getString(R.string.dashboard_observed_at,
                    formatObservedAt(preview.observedAt)));
            binding.timelineEnd.setText(getString(R.string.dashboard_sample_at,
                    formatObservedAt(preview.observedAt), preview.value));
        } else {
            binding.metricValue.setText("—");
            binding.metricDetail.setText(getString(R.string.dashboard_missing_reason,
                    missingReasonText(preview.missingReason)));
            binding.timelineEnd.setText(getString(R.string.dashboard_missing_reason,
                    missingReasonText(preview.missingReason)));
        }
        binding.timelineStart.setText(getString(R.string.dashboard_gap_at,
                formatObservedAt(preview.gapAt), missingReasonText(preview.gapReason)));
        binding.growthStatus.setText(R.string.dashboard_no_growth);
    }

    private String missingReasonText(String reason) {
        if ("NOT_SYNCED".equals(reason)) return getString(R.string.dashboard_reason_not_synced);
        return reason == null ? getString(R.string.dashboard_reason_unknown) : reason;
    }

    private String formatObservedAt(String value) {
        if (value == null) return getString(R.string.dashboard_time_unknown);
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        input.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date observed = input.parse(value);
            Locale locale = getResources().getConfiguration().locale;
            return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
                    .format(observed);
        } catch (ParseException error) {
            return value;
        }
    }
}
