package com.socialpilot.app.data.repository.social;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.socialpilot.app.data.security.EncryptedOAuthStore;
import com.socialpilot.app.domain.contract.Cancelable;
import com.socialpilot.app.domain.contract.SocialConnector;
import com.socialpilot.app.domain.contract.social.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import okhttp3.OkHttpClient;
import okhttp3.HttpUrl;
/** Explicitly approved text harness. Not a production editor or queue. Never automatically retries. */
public final class LinkedInSpikePublisher {
    private final EncryptedOAuthStore store;
    private final SharedPreferences attempts;
    private final ExecutorService io;
    private final String version;
    private final Gson gson=new Gson();
    public LinkedInSpikePublisher(Context context,ExecutorService io,String version){
        this.io=io;this.version=version;store=EncryptedOAuthStore.shared(context);
        attempts=context.getApplicationContext().getSharedPreferences("linkedin_spike_attempts_v1",Context.MODE_PRIVATE);
    }
    public String lastResult(){return attempts.getString("last_result",null);}
    public Cancelable publishApprovedText(String text,SocialConnector.Result callback){
        ChannelInfo channel=store.channel("LINKEDIN");
        if(channel==null)throw new IllegalStateException("AUTH_REQUIRED");
        if(!version.matches("[0-9]{6}"))throw new IllegalStateException("API_VERSION_REQUIRED");
        String snapshotId=hash(text);
        PublishRequest request=new PublishRequest(UUID.randomUUID().toString(),channel.channelId,channel.connectionRevision,snapshotId);
        LinkedInSocialConnector.Credentials credentials=new LinkedInSocialConnector.Credentials(){
            public ChannelInfo current(){return store.channel("LINKEDIN");}
            public String accessToken(){return store.tokenFor("LINKEDIN",channel.channelId,channel.connectionRevision);}
            public void requireReauth(long rev){store.requireReauth("LINKEDIN",rev);}
        };
        LinkedInSocialConnector.Attempts ledger=new LinkedInSocialConnector.Attempts(){
            public boolean begin(PublishRequest r){
                synchronized(attempts){
                    String key="attempt_"+hash(r.channelId+":"+r.approvedVersionId);
                    if(attempts.contains(key))return false;
                    return attempts.edit().putString(key,"UNKNOWN:"+r.requestId).putString("last_result","UNKNOWN").remove("last_payload").remove("last_key").commit();
                }
            }
            public void record(PublishResult result){
                synchronized(attempts){
                    String key="attempt_"+hash(result.channelId+":"+request.approvedVersionId);
                    String summary=result.outcome.name()+(result.postId==null?"":"\n"+result.postId);
                    if(!attempts.edit().putString(key,gson.toJson(result)).putString("last_result",summary).putString("last_payload",gson.toJson(result)).putString("last_key",key).commit())throw new IllegalStateException("Cannot save result");
                }
            }
        };
        var connector=new LinkedInSocialConnector(channel,credentials,
            r->new LinkedInSocialConnector.Snapshot(snapshotId,text,true),ledger,
            new OkHttpClient(),HttpUrl.get("https://api.linkedin.com/rest/posts"),version,io);
        return connector.publish(request,callback);
    }
    /** Called only after the operator opened the URL and explicitly confirmed author/content. */
    public void confirmOpenedPostUrl(String url){
        synchronized(attempts){
            String raw=attempts.getString("last_payload",null),key=attempts.getString("last_key",null);
            if(raw==null||key==null)throw new IllegalStateException("No post ID to verify");
            PublishResult verified=LinkedInPostVerification.ownerConfirmed(raw,url);
            if(!attempts.edit().putString(key,gson.toJson(verified)).putString("last_payload",gson.toJson(verified))
                .putString("url_verification_source","OWNER_CONFIRMED")
                .putString("last_result","SUCCESS\n"+verified.postId+"\n"+verified.postUrl).commit())throw new IllegalStateException("Cannot save URL verification");
        }
    }
    private static String hash(String value){
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();
        }catch(Exception e){throw new IllegalStateException("Hash unavailable");}
    }
}
