package com.socialpilot.oauth.flow;
import com.socialpilot.oauth.facebook.*;
import com.socialpilot.oauth.contract.OAuthContract.*;
import java.net.URI;
import java.time.Clock;
import java.util.List;
/** Compatibility facade for the Facebook regression suite. */
public final class FacebookOAuthSessionService extends OAuthSessionService {
    public FacebookOAuthSessionService(FacebookOAuthProperties p,FacebookGraphClient g){this(p,g,Clock.systemUTC());}
    FacebookOAuthSessionService(FacebookOAuthProperties p,FacebookGraphClient g,Clock clock){super(List.of(new FacebookOAuthAdapter(p,g)),clock);}
    public URI completeCallback(String code,String state,String error){return super.completeCallback("FACEBOOK",code,state,error);}
    public OAuthExchangeResponse exchange(OAuthHandoffExchangeRequest r){return super.exchange("FACEBOOK",r);}
}
