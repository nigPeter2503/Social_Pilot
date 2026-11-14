package com.socialpilot.app.data.repository.social;

import com.google.gson.JsonObject;
import com.socialpilot.app.domain.contract.*;
import com.socialpilot.app.domain.contract.social.*;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;

/** Text-only spike adapter. A durable attempt ledger must be supplied by its caller. */
public final class LinkedInSocialConnector implements SocialConnector {
    public interface Credentials {
        ChannelInfo current();
        String accessToken();
        void requireReauth(long revision);
    }
    public interface ApprovedSnapshots { Snapshot resolve(PublishRequest request); }
    public static final class Snapshot {
        public final String approvedVersionId,text;
        public final boolean approved;
        public Snapshot(String version,String text,boolean approved){this.approvedVersionId=version;this.text=text;this.approved=approved;}
    }
    public interface Attempts {
        boolean begin(PublishRequest request);
        void record(PublishResult result);
    }
    private final ChannelInfo channel;
    private final Credentials credentials;
    private final ApprovedSnapshots snapshots;
    private final Attempts attempts;
    private final OkHttpClient client;
    private final HttpUrl endpoint;
    private final String version;
    private final ExecutorService io;
    private volatile String verifiedAt;
    public LinkedInSocialConnector(ChannelInfo channel,Credentials credentials,ApprovedSnapshots snapshots,
            Attempts attempts,OkHttpClient client,HttpUrl endpoint,String version,ExecutorService io){
        if(channel.platform!=Platform.LINKEDIN||channel.accountType!=AccountType.PERSON)throw new IllegalArgumentException("LinkedIn person required");
        if(!version.matches("[0-9]{6}"))throw new IllegalArgumentException("API version required");
        this.channel=channel;this.credentials=credentials;this.snapshots=snapshots;this.attempts=attempts;
        this.client=client.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
        this.endpoint=Objects.requireNonNull(endpoint);this.version=version;this.io=io;
    }
    public ChannelInfo channel(){return channel;}
    public ChannelCapability capability(SocialOperation op){
        return new ChannelCapability(op,op==SocialOperation.PUBLISH_TEXT&&verifiedAt!=null?CapabilityAvailability.AVAILABLE:CapabilityAvailability.UNVERIFIED,
            op==SocialOperation.PUBLISH_TEXT&&verifiedAt!=null?null:"NOT_VERIFIED",op==SocialOperation.PUBLISH_TEXT?verifiedAt:null);
    }
    public Cancelable publish(PublishRequest request,Result callback){
        AtomicBoolean cancelled=new AtomicBoolean();AtomicReference<Call> active=new AtomicReference<>();
        io.execute(()->{
            PublishResult result;boolean reserved=false;
            try{
                ChannelInfo current=credentials.current();
                if(!channel.channelId.equals(request.channelId)||channel.connectionRevision!=request.connectionRevision
                    ||current==null||!current.channelId.equals(channel.channelId)||current.connectionRevision!=channel.connectionRevision
                    ||current.connectionStatus==ConnectionStatus.DISCONNECTED){deliver(callback,cancelled,reject(request,SocialError.Code.CHANNEL_DISCONNECTED,SocialError.NextAction.RECONNECT));return;}
                if(current.connectionStatus==ConnectionStatus.REAUTH_REQUIRED){deliver(callback,cancelled,reject(request,SocialError.Code.AUTH_REQUIRED,SocialError.NextAction.RECONNECT));return;}
                Snapshot snapshot=snapshots.resolve(request);
                if(snapshot==null||!snapshot.approved||!request.approvedVersionId.equals(snapshot.approvedVersionId)){
                    deliver(callback,cancelled,reject(request,SocialError.Code.APPROVAL_REQUIRED,SocialError.NextAction.APPROVE_VERSION));return;}
                if(snapshot.text==null||snapshot.text.trim().isEmpty()){
                    deliver(callback,cancelled,reject(request,SocialError.Code.INVALID_CONTENT,SocialError.NextAction.EDIT_CONTENT));return;}
                String token=credentials.accessToken();if(token==null||token.trim().isEmpty()){
                    deliver(callback,cancelled,reject(request,SocialError.Code.AUTH_REQUIRED,SocialError.NextAction.RECONNECT));return;}
                if(cancelled.get())return;
                if(!attempts.begin(request)){deliver(callback,cancelled,PublishResult.unknown(request));return;}
                reserved=true;
                JsonObject json=new JsonObject();json.addProperty("author","urn:li:person:"+channel.externalAccountId);
                json.addProperty("commentary",snapshot.text);json.addProperty("visibility","PUBLIC");json.addProperty("lifecycleState","PUBLISHED");json.addProperty("isReshareDisabledByAuthor",false);
                JsonObject distribution=new JsonObject();distribution.addProperty("feedDistribution","MAIN_FEED");
                distribution.add("targetEntities",new com.google.gson.JsonArray());distribution.add("thirdPartyDistributionChannels",new com.google.gson.JsonArray());json.add("distribution",distribution);
                Request http=new Request.Builder().url(endpoint).header("Authorization","Bearer "+token)
                    .header("LinkedIn-Version",version).header("X-Restli-Protocol-Version","2.0.0")
                    .post(RequestBody.create(json.toString(),MediaType.get("application/json; charset=utf-8"))).build();
                Call call=client.newCall(http);active.set(call);if(cancelled.get())call.cancel();
                try(Response response=call.execute()){
                    if(response.code()==201){String id=response.header("x-restli-id");
                        if(id==null||!id.matches("urn:li:(share|ugcPost):[0-9]+"))result=PublishResult.unknown(request);
                        else{verifiedAt=utcNow();result=PublishResult.publishedUrlPending(request,id);}
                    }else if(response.code()==401){credentials.requireReauth(channel.connectionRevision);result=reject(request,SocialError.Code.AUTH_REQUIRED,SocialError.NextAction.RECONNECT);}
                    else if(response.code()==403)result=reject(request,SocialError.Code.PERMISSION_REQUIRED,SocialError.NextAction.RESOLVE_PERMISSION);
                    else if(response.code()==429)result=reject(request,SocialError.Code.RATE_LIMITED,SocialError.NextAction.WAIT);
                    else if(response.code()==400||response.code()==422)result=reject(request,SocialError.Code.INVALID_CONTENT,SocialError.NextAction.EDIT_CONTENT);
                    else result=PublishResult.unknown(request);
                }
            }catch(Exception e){result=reserved?PublishResult.unknown(request):reject(request,SocialError.Code.NETWORK_ERROR,SocialError.NextAction.RETRY_WHEN_SAFE);}
            if(reserved){try{attempts.record(result);}catch(RuntimeException e){result=PublishResult.unknown(request);}}
            deliver(callback,cancelled,result);
        });
        return ()->{cancelled.set(true);Call call=active.get();if(call!=null)call.cancel();};
    }
    private static void deliver(Result cb,AtomicBoolean cancelled,PublishResult r){if(!cancelled.get()){try{cb.onResult(r);}catch(RuntimeException ignored){ /* Consumer failure must not deliver twice. */ }}}
    private static PublishResult reject(PublishRequest r,SocialError.Code code,SocialError.NextAction action){return PublishResult.rejected(r,new SocialError(code,action));}
    private static String utcNow(){java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",java.util.Locale.ROOT);f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));return f.format(new java.util.Date());}
}
