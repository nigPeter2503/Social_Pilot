package com.socialpilot.app.ui.channels;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.socialpilot.app.R;
import com.socialpilot.app.data.repository.ChannelDirectory.Channel;
import com.socialpilot.app.databinding.ActivityMyChannelsBinding;
import com.socialpilot.app.databinding.ItemChannelBinding;
import com.socialpilot.app.databinding.ItemChannelCapabilityBinding;
import com.socialpilot.app.domain.contract.social.CapabilityAvailability;
import com.socialpilot.app.domain.contract.social.ChannelCapability;
import com.socialpilot.app.domain.contract.social.ConnectionStatus;
import com.socialpilot.app.domain.contract.social.Platform;
import com.socialpilot.app.domain.contract.social.SocialOperation;
import com.socialpilot.app.ui.MainActivity;
import com.socialpilot.app.ui.channels.MyChannelsViewModel.State;
import com.socialpilot.app.ui.channels.MyChannelsViewModel.Status;

public final class MyChannelsActivity extends AppCompatActivity {
    private ActivityMyChannelsBinding binding;
    private MyChannelsViewModel model;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyChannelsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        model = new ViewModelProvider(this).get(MyChannelsViewModel.class);
        binding.channelsBack.setOnClickListener(view -> finish());
        binding.channelsRefresh.setOnClickListener(view -> model.refresh());
        binding.channelsConnectFacebook.setOnClickListener(view -> MainActivity.openConnection(this, Platform.FACEBOOK));
        binding.channelsConnectInstagram.setOnClickListener(view -> MainActivity.openConnection(this, Platform.INSTAGRAM));
        model.state().observe(this, this::render);
    }

    @Override protected void onRestart() {
        super.onRestart();
        model.refresh();
    }

    private void render(State state) {
        boolean hasChannels = !state.channels.isEmpty();
        boolean loading = state.status == Status.LOADING;
        binding.channelsProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.channelsRefresh.setEnabled(!loading);
        binding.channelsRefresh.setText(state.status == Status.ERROR || state.status == Status.OFFLINE
                ? R.string.channels_retry : R.string.channels_refresh);
        int emptyActions = state.status == Status.READY && !hasChannels ? View.VISIBLE : View.GONE;
        binding.channelsConnectFacebook.setVisibility(emptyActions);
        binding.channelsConnectInstagram.setVisibility(emptyActions);

        int message = switch (state.status) {
            case LOADING -> R.string.channels_loading;
            case ERROR -> hasChannels ? R.string.channels_error_cached : R.string.channels_error;
            case OFFLINE -> hasChannels ? R.string.channels_offline_cached : R.string.channels_offline_empty;
            case READY -> hasChannels ? 0 : R.string.channels_empty;
        };
        binding.channelsMessage.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) binding.channelsMessage.setText(message);

        binding.channelsList.removeAllViews();
        for (Channel channel : state.channels) {
            renderChannel(channel, state.status == Status.READY);
        }
    }

    private void renderChannel(Channel channel, boolean online) {
        ItemChannelBinding item = ItemChannelBinding.inflate(getLayoutInflater(), binding.channelsList, false);
        boolean facebookPage = "FACEBOOK".equals(channel.identity.platform)
                && "PAGE".equals(channel.identity.accountType);
        boolean instagramProfessional = "INSTAGRAM".equals(channel.identity.platform)
                && "PROFESSIONAL".equals(channel.identity.accountType);
        item.channelName.setText(channel.displayName);
        item.channelAccount.setText(facebookPage ? R.string.channels_facebook_page
                : instagramProfessional ? R.string.channels_instagram_professional : R.string.channels_other_account);
        item.channelAccountId.setText(getString(R.string.channels_account_id, channel.identity.externalAccountId));
        item.channelStatus.setText(switch (channel.status) {
            case CONNECTED -> R.string.channels_connected;
            case REAUTH_REQUIRED -> R.string.channels_reauth_required;
            case DISCONNECTED -> R.string.channels_disconnected;
        });
        boolean connectionNeeded = channel.status != ConnectionStatus.CONNECTED;
        item.channelCachedNotice.setVisibility(!online || connectionNeeded ? View.VISIBLE : View.GONE);
        boolean permissionRequired = false;
        for (SocialOperation operation : SocialOperation.values()) {
            ChannelCapability capability = channel.capability(operation);
            permissionRequired |= capability.availability == CapabilityAvailability.PERMISSION_REQUIRED;
            renderCapability(item, operationLabel(operation), capability);
        }
        // F1.B2 includes mention, which does not yet have a SocialOperation in the shared contract.
        renderCapability(item, R.string.channels_op_mention, null);

        if (online && (connectionNeeded || permissionRequired) && (facebookPage || instagramProfessional)) {
            item.channelOpenConnection.setVisibility(View.VISIBLE);
            item.channelOpenConnection.setText(facebookPage
                    ? R.string.channels_open_facebook : R.string.channels_open_instagram);
            item.channelOpenConnection.setOnClickListener(view -> MainActivity.openConnection(this,
                    facebookPage ? Platform.FACEBOOK : Platform.INSTAGRAM));
        }
        binding.channelsList.addView(item.getRoot());
    }

    private void renderCapability(ItemChannelBinding item, int label, ChannelCapability capability) {
        ItemChannelCapabilityBinding row = ItemChannelCapabilityBinding.inflate(
                getLayoutInflater(), item.channelCapabilities, false);
        row.capabilityOperation.setText(label);
        row.capabilityStatus.setText(capability == null ? R.string.channels_unverified : switch (capability.availability) {
            case AVAILABLE -> R.string.channels_available;
            case PERMISSION_REQUIRED -> R.string.channels_permission_required;
            case UNSUPPORTED -> R.string.channels_unsupported;
            case UNVERIFIED -> R.string.channels_unverified;
        });
        int reason = capability == null ? R.string.channels_mention_pending : reasonLabel(capability);
        if (reason != 0) {
            row.capabilityReason.setVisibility(View.VISIBLE);
            row.capabilityReason.setText(reason);
        }
        if (capability != null && capability.verifiedAt != null) {
            row.capabilityVerified.setVisibility(View.VISIBLE);
            row.capabilityVerified.setText(getString(R.string.channels_verified_at, capability.verifiedAt));
        }
        item.channelCapabilities.addView(row.getRoot());
    }

    private static int reasonLabel(ChannelCapability capability) {
        if ("AUTH_REQUIRED".equals(capability.reasonCode)) return R.string.channels_reason_auth;
        if ("MISSING_FOLLOWER_PERMISSION".equals(capability.reasonCode)) {
            return R.string.channels_reason_follower_permission;
        }
        if ("PROVIDER_OMITTED".equals(capability.reasonCode)) return R.string.channels_reason_provider_omitted;
        return switch (capability.availability) {
            case AVAILABLE, UNVERIFIED -> 0;
            case PERMISSION_REQUIRED -> R.string.channels_reason_permission;
            case UNSUPPORTED -> R.string.channels_reason_unsupported;
        };
    }

    private static int operationLabel(SocialOperation operation) {
        return switch (operation) {
            case PUBLISH_TEXT -> R.string.channels_op_text;
            case PUBLISH_IMAGE -> R.string.channels_op_image;
            case SET_ALT_TEXT -> R.string.channels_op_alt_text;
            case READ_COMMENTS -> R.string.channels_op_read_comments;
            case REPLY_COMMENT -> R.string.channels_op_reply_comment;
            case READ_POST_METRICS -> R.string.channels_op_post_metrics;
            case READ_FOLLOWER_COUNT -> R.string.channels_op_followers;
        };
    }
}
