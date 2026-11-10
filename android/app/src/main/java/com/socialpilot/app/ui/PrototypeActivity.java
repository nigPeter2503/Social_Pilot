package com.socialpilot.app.ui;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkInfo;
import com.socialpilot.app.R;
import com.socialpilot.app.data.prototype.SampleResultEntity;
import com.socialpilot.app.databinding.ActivityPrototypeBinding;
import java.util.List;

/** Labelled sample UI; it does not call an AI provider or publish to a channel. */
public final class PrototypeActivity extends AppCompatActivity {
    private ActivityPrototypeBinding binding;
    private ConnectivityManager connectivity;
    private boolean networkCallbackRegistered;
    private SampleResultEntity latestA;
    private SampleResultEntity latestB;
    private List<WorkInfo> latestWorkA;
    private List<WorkInfo> latestWorkB;
    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(@NonNull Network network) {
                    runOnUiThread(() -> refreshNetworkStatus());
                }
                @Override public void onLost(@NonNull Network network) {
                    runOnUiThread(() -> refreshNetworkStatus());
                }
            };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPrototypeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        PrototypeViewModel model = new ViewModelProvider(this).get(PrototypeViewModel.class);
        binding.startA.setOnClickListener(view -> model.startA());
        binding.startB.setOnClickListener(view -> model.startB());
        binding.cancelA.setOnClickListener(view -> model.cancelA());
        binding.cancelB.setOnClickListener(view -> model.cancelB());
        binding.failA.setOnClickListener(view -> model.failA());
        binding.workA.setOnClickListener(view -> model.enqueueSample("A"));
        binding.workB.setOnClickListener(view -> model.enqueueSample("B"));
        model.results().observe(this, this::showResults);
        model.workA().observe(this, work -> {
            latestWorkA = work;
            binding.workStatusA.setText(workText(work, latestA));
        });
        model.workB().observe(this, work -> {
            latestWorkB = work;
            binding.workStatusB.setText(workText(work, latestB));
        });
    }

    @Override protected void onResume() {
        super.onResume();
        refreshNetworkStatus();
        if (connectivity != null && !networkCallbackRegistered) {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            connectivity.registerNetworkCallback(request, networkCallback);
            networkCallbackRegistered = true;
        }
    }

    @Override protected void onPause() {
        if (connectivity != null && networkCallbackRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback);
            networkCallbackRegistered = false;
        }
        super.onPause();
    }

    private void refreshNetworkStatus() {
        if (connectivity == null) return;
        Network active = connectivity.getActiveNetwork();
        NetworkCapabilities capabilities = active == null ? null : connectivity.getNetworkCapabilities(active);
        binding.networkStatus.setText(capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                ? R.string.prototype_online : R.string.prototype_offline);
    }

    private void showResults(List<SampleResultEntity> results) {
        SampleResultEntity a = null;
        SampleResultEntity b = null;
        if (results != null) {
            for (SampleResultEntity result : results) {
                if ("A".equals(result.channelId)) a = result;
                if ("B".equals(result.channelId)) b = result;
            }
        }
        latestA = a;
        latestB = b;
        showChannel(a, true);
        showChannel(b, false);
        binding.workStatusA.setText(workText(latestWorkA, a));
        binding.workStatusB.setText(workText(latestWorkB, b));
    }

    private void showChannel(SampleResultEntity result, boolean first) {
        int state = stateText(result == null ? null : result.state);
        String content = result == null || result.text.isEmpty()
                ? getString(R.string.prototype_empty) : result.text;
        String checkpoint = result == null || "NOT_STARTED".equals(result.checkpoint)
                ? getString(R.string.prototype_checkpoint_empty)
                : getString(R.string.prototype_checkpoint_done);
        if (first) {
            binding.stateA.setText(state);
            binding.resultA.setText(content);
            binding.checkpointA.setText(checkpoint);
            binding.workA.setEnabled(result != null && "COMPLETE".equals(result.state));
        } else {
            binding.stateB.setText(state);
            binding.resultB.setText(content);
            binding.checkpointB.setText(checkpoint);
            binding.workB.setEnabled(result != null && "COMPLETE".equals(result.state));
        }
    }

    private int stateText(String state) {
        if ("RUNNING".equals(state)) return R.string.prototype_running;
        if ("COMPLETE".equals(state)) return R.string.prototype_complete;
        if ("CANCELLED".equals(state)) return R.string.prototype_cancelled;
        if ("INTERRUPTED".equals(state)) return R.string.prototype_interrupted;
        if ("ERROR".equals(state)) return R.string.prototype_error;
        return R.string.prototype_idle;
    }

    private String workText(List<WorkInfo> work, SampleResultEntity result) {
        if (work == null || work.isEmpty()) return getString(R.string.prototype_work_idle);
        WorkInfo.State state = work.get(0).getState();
        if (state == WorkInfo.State.SUCCEEDED && (result == null
                || "NOT_STARTED".equals(result.checkpoint))) {
            return getString(R.string.prototype_work_idle);
        }
        if (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED) {
            return getString(R.string.prototype_work_waiting);
        }
        if (state == WorkInfo.State.RUNNING) return getString(R.string.prototype_work_running);
        if (state == WorkInfo.State.SUCCEEDED) return getString(R.string.prototype_work_done);
        return getString(R.string.prototype_work_failed);
    }
}
