package com.socialpilot.app.data.repository.social;

import android.content.Context;
import android.content.SharedPreferences;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.SocialConnector;
import com.socialpilot.app.domain.contract.social.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/** Explicit approval and durable attempts for the LinkedIn text spike. */
public final class LinkedInSpikePublisher {
    public static final class Review {
        public final String text, errorCode;
        public final long retryAt;
        private final String key, previousRequestId;
        private final ChannelInfo channel;
        private Review(String text, String key, String previousRequestId, ChannelInfo channel,
                String errorCode, long retryAt) {
            this.text=text; this.key=key; this.previousRequestId=previousRequestId;
            this.channel=channel; this.errorCode=errorCode; this.retryAt=retryAt;
        }
    }
    public static final class Confirmation {
        private final LinkedInAttemptLedger.Target target;
        private Confirmation(LinkedInAttemptLedger.Target target) { this.target=target; }
    }
    public static final class State {
        public final String summary;
        public final Confirmation confirmation;
        private State(String summary, LinkedInAttemptLedger.Target target) {
            this.summary=summary; this.confirmation=target==null ? null : new Confirmation(target);
        }
    }
    private final EncryptedOAuthStore store;
    private final SharedPreferences preferences;
    private final LinkedInAttemptLedger ledger;
    private final ExecutorService io;
    private final String version;
    private final OkHttpClient client;
    private final HttpUrl endpoint;
    public LinkedInSpikePublisher(Context context, ExecutorService io, String version) {
        this(EncryptedOAuthStore.shared(context), context.getApplicationContext()
                .getSharedPreferences("linkedin_spike_attempts_v1", Context.MODE_PRIVATE), io, version,
                new OkHttpClient(), HttpUrl.get("https://api.linkedin.com/rest/posts"));
    }
    LinkedInSpikePublisher(EncryptedOAuthStore store, SharedPreferences preferences, ExecutorService io,
            String version, OkHttpClient client, HttpUrl endpoint) {
        this.store=store; this.preferences=preferences; this.io=io; this.version=version;
        this.client=client; this.endpoint=endpoint;
        ledger=new LinkedInAttemptLedger(preferences, System::currentTimeMillis);
    }
    public State state() {
        synchronized (preferences) {
            return new State(preferences.getString("last_result",null), ledger.target());
        }
    }
    public State stateForResult(Review review, PublishResult result) {
        synchronized (preferences) {
            return new State(summary(result), result.postId == null ? null : ledger.target(review.key));
        }
    }
    public Review review(String text) {
        ChannelInfo channel=store.channel("LINKEDIN");
        if(channel==null) throw new IllegalStateException("AUTH_REQUIRED");
        String snapshotId=LinkedInAttemptLedger.hash(text);
        String key=ledger.key(channel,snapshotId);
        PublishRequest request=new PublishRequest(UUID.randomUUID().toString(),channel.channelId,channel.connectionRevision,snapshotId);
        PublishResult previous=ledger.existing(key,request);
        String retryId=ledger.confirmedRetry(key);
        return new Review(text,key,retryId,channel,
                retryId==null ? null : previous.error.code.name(),ledger.retryAt(key));
    }
    public Cancelable publishApprovedText(Review review, SocialConnector.Result callback) {
        if(!version.matches("[0-9]{6}")) throw new IllegalStateException("API_VERSION_REQUIRED");
        ChannelInfo channel=review.channel;
        String snapshotId=LinkedInAttemptLedger.hash(review.text);
        PublishRequest request=new PublishRequest(UUID.randomUUID().toString(),channel.channelId,channel.connectionRevision,snapshotId);
        LinkedInSocialConnector.Credentials credentials=new LinkedInSocialConnector.Credentials() {
            public ChannelInfo current() { return store.channel("LINKEDIN"); }
            public String accessToken() { return store.tokenFor("LINKEDIN",channel.channelId,channel.connectionRevision); }
            public void requireReauth(long revision) { store.requireReauth("LINKEDIN",channel.channelId,revision); }
        };
        LinkedInSocialConnector.Attempts attempts=new LinkedInSocialConnector.Attempts() {
            private long retryAt;
            public PublishResult existing(PublishRequest r) { return ledger.existing(review.key,r); }
            public boolean begin(PublishRequest r) { return ledger.begin(review.key,r,review.previousRequestId); }
            public void retryAfter(long value) { retryAt=value; }
            public void record(PublishResult result) { ledger.record(review.key,result,retryAt); }
        };
        return new LinkedInSocialConnector(channel,credentials,
                r->new LinkedInSocialConnector.Snapshot(snapshotId,review.text,true),attempts,
                client,endpoint,version,io).publish(request,callback);
    }
    public State confirmOpenedPostUrl(Confirmation confirmation, String url) {
        if (confirmation==null) throw new IllegalStateException("Open the post again");
        synchronized (preferences) {
            PublishResult verified=ledger.confirm(confirmation.target,url);
            return new State(summary(verified),ledger.target(confirmation.target.key));
        }
    }
    public static String summary(PublishResult result) { return LinkedInAttemptLedger.summary(result); }
}
