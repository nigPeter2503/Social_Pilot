package com.socialpilot.oauth.facebook;
import com.socialpilot.oauth.provider.*;
import java.net.URI;
import org.springframework.stereotype.Component;
@Component
public final class FacebookOAuthAdapter implements OAuthProviderAdapter {
    private final FacebookOAuthProperties properties;
    private final FacebookGraphClient graph;
    public FacebookOAuthAdapter(FacebookOAuthProperties p,FacebookGraphClient g){properties=p;graph=g;}
    public String provider(){return "FACEBOOK";}
    public URI authorizationUri(String state){properties.requireConfigured();return FacebookOAuthUrlBuilder.authorizationUri(properties,state);}
    public ProviderIdentity exchangeCode(String code){
        var p=graph.exchangeCodeForTargetPage(code);
        return new ProviderIdentity(provider(),p.id,"PAGE",p.name,p.accessToken,null,"bearer",null);
    }
}
