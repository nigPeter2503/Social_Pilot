package com.socialpilot.oauth.provider;
import com.socialpilot.oauth.contract.OAuthContract.OAuthExchangeResponse;
/** Internal credentials; never log or serialize outside the authenticated handoff. */
public final class ProviderIdentity {
    public final String provider,id,accountType,name,accessToken,refreshToken,tokenType,expiresAt;
    public ProviderIdentity(String p,String id,String type,String name,String access,String refresh,String tokenType,String expiry) {
        new OAuthExchangeResponse("validation",p,id,type,name,access,refresh,tokenType,expiry);
        this.provider=p;this.id=id;this.accountType=type;this.name=name;this.accessToken=access;
        this.refreshToken=refresh;this.tokenType=tokenType;this.expiresAt=expiry;
    }
    @Override public String toString(){return "ProviderIdentity{redacted}";}
}
