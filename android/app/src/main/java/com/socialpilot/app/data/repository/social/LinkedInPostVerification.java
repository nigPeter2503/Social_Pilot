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
        if(!"https".equals(u.getScheme())||!"www.linkedin.com".equals(u.getHost())||u.getPort()!=-1||u.getUserInfo()!=null||u.getFragment()!=null
            ||!u.getPath().matches("/feed/update/urn:li:(activity|share|ugcPost):[0-9]+/?"))throw new IllegalArgumentException("Invalid LinkedIn post URL");
        JsonObject json=JsonParser.parseString(payload).getAsJsonObject();
        if(!"PUBLISHED_URL_PENDING".equals(json.get("outcome").getAsString()))throw new IllegalStateException("Post is not pending URL verification");
        String postId=json.get("postId").getAsString();
        if(!postId.matches("urn:li:(share|ugcPost):[0-9]+"))throw new IllegalArgumentException("Post ID required");
        PublishRequest request=new PublishRequest(json.get("requestId").getAsString(),json.get("channelId").getAsString(),json.get("connectionRevision").getAsLong(),"manual-url-verification");
        return PublishResult.success(request,postId,url.trim());
    }
}
