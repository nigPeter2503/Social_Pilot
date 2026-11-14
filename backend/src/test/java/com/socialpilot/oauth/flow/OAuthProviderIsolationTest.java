package com.socialpilot.oauth.flow;
import com.socialpilot.oauth.provider.*;
import com.socialpilot.oauth.contract.OAuthContract.*;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;
import static org.junit.jupiter.api.Assertions.*;
class OAuthProviderIsolationTest {
    static String param(String u,String n){return UriComponentsBuilder.fromUriString(u).build().getQueryParams().getFirst(n);}
    @Test void wrongProviderCannotConsumeStateOrHandoff() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        var service=new OAuthSessionService(List.of(adapter("FACEBOOK",calls),adapter("LINKEDIN",calls)),Clock.systemUTC());
        String verifier="a".repeat(43),challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        var start=service.start(new OAuthStartRequest("LINKEDIN","android-dev",challenge));String state=param(start.authorizationUrl,"state");
        assertThrows(OAuthFlowException.class,()->service.completeCallback("facebook","code",state,null));assertEquals(0,calls.get());
        URI cb=service.completeCallback("linkedin","code",state,null);assertEquals(1,calls.get());
        var req=new OAuthHandoffExchangeRequest(start.sessionId,param(cb.toString(),"handoffCode"),verifier);
        assertThrows(OAuthFlowException.class,()->service.exchange("facebook",req));
        assertEquals("LINKEDIN",service.exchange("linkedin",req).provider);
        assertThrows(OAuthFlowException.class,()->service.exchange("linkedin",req));
    }
    static OAuthProviderAdapter adapter(String p,AtomicInteger calls){return new OAuthProviderAdapter(){
        public String provider(){return p;}
        public URI authorizationUri(String state){return URI.create("https://example.test/auth?state="+state);}
        public ProviderIdentity exchangeCode(String code){calls.incrementAndGet();return new ProviderIdentity(p,"id",p.equals("FACEBOOK")?"PAGE":"PERSON","Name","fake-token",null,"Bearer",null);}
    };}
}
