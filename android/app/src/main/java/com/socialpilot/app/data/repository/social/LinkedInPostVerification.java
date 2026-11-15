package com.socialpilot.app.data.repository.social;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialpilot.app.domain.contract.social.*;
import java.net.URI;
/** Validates the shape of owner-confirmed evidence; does not claim remote API read-back. */
public final class LinkedInPostVerification {
    private LinkedInPostVerification(){}
    public static PublishResult ownerConfirmed(String payload,String url){
        URI u=URI.create(url.trim());
        if(!"https".equals(u.getScheme())||!("www.linkedin.com".equals(u.getHost())||"linkedin.com".equals(u.getHost()))||u.getPort()!=-1||u.getUserInfo()!=null||u.getFragment()!=null
            ||u.getPath()==null||!(u.getPath().matches("/feed/update/urn:li:(activity|share|ugcPost):[0-9]+/?")
                ||u.getPath().matches("/posts/[^/]+/?")))throw new IllegalArgumentException("Full LinkedIn post URL required");
        JsonObject json=JsonParser.parseString(payload).getAsJsonObject();
        boolean alreadyVerified="SUCCESS".equals(json.get("outcome").getAsString())
            &&json.has("postUrl")&&!json.get("postUrl").isJsonNull()&&url.trim().equals(json.get("postUrl").getAsString());
        if(!alreadyVerified&&!"PUBLISHED_URL_PENDING".equals(json.get("outcome").getAsString()))throw new IllegalStateException("Post is not pending URL verification");
        String postId=json.get("postId").getAsString();
        if(!postId.matches("urn:li:(share|ugcPost):[0-9]+"))throw new IllegalArgumentException("Post ID required");
        PublishRequest request=new PublishRequest(json.get("requestId").getAsString(),json.get("channelId").getAsString(),json.get("connectionRevision").getAsLong(),"manual-url-verification");
        return PublishResult.success(request,postId,url.trim());
    }

    /** Repeated taps retain known evidence without another POST or inventing a successful result. */
    public static PublishResult previousResult(PublishRequest request,String payload){
        try{
            JsonObject json=JsonParser.parseString(payload).getAsJsonObject();
            if(!request.channelId.equals(json.get("channelId").getAsString()))return null;
            String outcome=json.get("outcome").getAsString();
            if("PUBLISHED_URL_PENDING".equals(outcome))return PublishResult.publishedUrlPending(request,json.get("postId").getAsString());
            if("SUCCESS".equals(outcome))return PublishResult.success(request,json.get("postId").getAsString(),json.get("postUrl").getAsString());
        }catch(RuntimeException ignored){ /* An incomplete ledger remains uncertain. */ }
        return null;
    }
}
