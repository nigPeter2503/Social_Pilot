package com.socialpilot.app.ui;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.socialpilot.app.R;
import com.socialpilot.app.databinding.ActivityDashboardBinding;

/** A labelled SP-020 fixture preview; production binding is owned by SP-046. */
public final class DashboardActivity extends AppCompatActivity {
    private ActivityDashboardBinding binding;
    private DashboardViewModel model;

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
                binding.metricSelector.setSelection(metric);
            }
            renderFixture(metric);
        });
    }

    @Override protected void onResume() {
        super.onResume();
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = manager == null ? null
                : manager.getNetworkCapabilities(manager.getActiveNetwork());
        boolean online = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        binding.networkStatus.setText(online
                ? R.string.dashboard_online : R.string.dashboard_offline);
    }

    private void renderFixture(int metric) {
        DashboardViewModel.PreviewValue preview = model.previewValue(metric);
        if (preview.value != null) {
            binding.metricValue.setText(String.valueOf(preview.value));
            binding.metricDetail.setText(R.string.dashboard_observed);
            binding.timelineStart.setText(R.string.dashboard_gap);
            binding.timelineEnd.setText(R.string.dashboard_sample);
        } else {
            binding.metricValue.setText("—");
            binding.metricDetail.setText(R.string.dashboard_missing);
            binding.timelineStart.setText(R.string.dashboard_gap);
            binding.timelineEnd.setText(R.string.dashboard_missing);
        }
        binding.growthStatus.setText(R.string.dashboard_no_growth);
    }
}
