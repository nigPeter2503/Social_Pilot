package com.socialpilot.app.core;
import com.socialpilot.app.domain.contract.oauth.OAuthContract.OAuthExchangeResponse;
import org.junit.Test;
import static org.junit.Assert.*;
public class OAuthCoordinatorValidationTest {
    OAuthExchangeResponse identity(String session,String provider,String type){return new OAuthExchangeResponse(session,provider,"id",type,"Name","fake-token",null,"Bearer",null);}
    @Test public void rejectsCrossProviderAndCrossSessionResponses(){
        OAuthCoordinator.validateExchange("LINKEDIN","session",identity("session","LINKEDIN","PERSON"));
        assertThrows(IllegalArgumentException.class,()->OAuthCoordinator.validateExchange("LINKEDIN","session",identity("other","LINKEDIN","PERSON")));
        assertThrows(IllegalArgumentException.class,()->OAuthCoordinator.validateExchange("LINKEDIN","session",identity("session","FACEBOOK","PAGE")));
        assertThrows(IllegalArgumentException.class,()->OAuthCoordinator.validateExchange("LINKEDIN","session",identity("session","LINKEDIN","ORGANIZATION")));
    }
    @Test public void browserCanOnlyOpenExpectedProviderAuthorizationHostsAndPaths(){
        OAuthCoordinator.validateAuthorizationUrl("LINKEDIN","https://www.linkedin.com/oauth/v2/authorization?state=fake");
        OAuthCoordinator.validateAuthorizationUrl("FACEBOOK","https://www.facebook.com/v27.0/dialog/oauth?state=fake");
        for(String bad:new String[]{"https://attacker.test/oauth/v2/authorization","http://www.linkedin.com/oauth/v2/authorization","https://www.linkedin.com/other","https://www.linkedin.com:444/oauth/v2/authorization","https://user@www.linkedin.com/oauth/v2/authorization"})
            assertThrows(IllegalArgumentException.class,()->OAuthCoordinator.validateAuthorizationUrl("LINKEDIN",bad));
    }
}
