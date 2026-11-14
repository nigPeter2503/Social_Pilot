package com.socialpilot.oauth.linkedin;
import com.socialpilot.oauth.flow.OAuthFlowException;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class LinkedInOAuthClientTest {
    LinkedInOAuthProperties p;LinkedInOAuthClient client;MockRestServiceServer server;
    @BeforeEach void setup(){
        p=new LinkedInOAuthProperties();p.setEnabled(true);p.setClientId("fakeid");p.setClientSecret("fake-secret");p.setRedirectUri("https://example.test/api/oauth/linkedin/callback");
        var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();client=new LinkedInOAuthClient(p,builder.build());
    }
    void token(String json){server.expect(requestTo("https://www.linkedin.com/oauth/v2/accessToken"))
        .andExpect(method(HttpMethod.POST)).andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=fake-secret")))
        .andRespond(withSuccess(json,MediaType.APPLICATION_JSON));}
    void identity(String json){server.expect(requestTo("https://api.linkedin.com/v2/userinfo"))
        .andExpect(header("Authorization","Bearer fake-access")).andRespond(withSuccess(json,MediaType.APPLICATION_JSON));}
    @Test void exchangesCodeAndIdentifiesPersonWithoutRefresh(){
        token("{\"access_token\":\"fake-access\",\"expires_in\":3600,\"scope\":\"openid profile w_member_social\"}");
        identity("{\"sub\":\"member-id\",\"name\":\"Test Member\"}");
        var r=client.exchangeCode("fake-code");assertEquals("PERSON",r.accountType);assertEquals("member-id",r.id);assertNull(r.refreshToken);
        assertTrue(Instant.parse(r.expiresAt).isAfter(Instant.now()));assertFalse(r.toString().contains("fake-access"));server.verify();
    }
    @Test void usesOptionalRefreshAndUnknownExpiryHonestly(){
        token("{\"access_token\":\"fake-access\",\"refresh_token\":\"fake-refresh\"}");identity("{\"sub\":\"id\",\"name\":\"Member\"}");
        var r=client.exchangeCode("fake-code");assertEquals("fake-refresh",r.refreshToken);assertNull(r.expiresAt);server.verify();
    }
    @Test void rejectsMissingTokenWithoutUserInfoCall(){token("{}");assertEquals("PROVIDER_ERROR",assertThrows(OAuthFlowException.class,()->client.exchangeCode("x")).getCode());server.verify();}
    @Test void rejectsMissingGrantedScope(){token("{\"access_token\":\"fake-access\",\"scope\":\"openid profile\"}");assertEquals("ACCESS_DENIED",assertThrows(OAuthFlowException.class,()->client.exchangeCode("x")).getCode());server.verify();}
    @Test void rejectsNegativeExpiry(){token("{\"access_token\":\"fake-access\",\"expires_in\":-1}");assertThrows(OAuthFlowException.class,()->client.exchangeCode("x"));server.verify();}
    @Test void rejectsMissingIdentity(){token("{\"access_token\":\"fake-access\"}");identity("{\"name\":\"Member\"}");assertThrows(OAuthFlowException.class,()->client.exchangeCode("x"));server.verify();}
    @Test void errorsDoNotContainProviderSecrets(){server.expect(requestTo("https://www.linkedin.com/oauth/v2/accessToken")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("fake-secret fake-access"));
        var e=assertThrows(OAuthFlowException.class,()->client.exchangeCode("fake-code"));assertEquals("PROVIDER_ERROR",e.getMessage());server.verify();}
    @Test void onlyConfiguredProviderCanStart(){p.setEnabled(false);assertEquals("AUTH_REQUIRED",assertThrows(OAuthFlowException.class,()->client.authorizationUri("state")).getCode());}
    @Test void authorizationUrlHasExactRedirectAndMinimalScopes(){var u=client.authorizationUri("state-value");assertEquals("www.linkedin.com",u.getHost());assertTrue(u.toString().contains("scope=openid%20profile%20w_member_social"));}
    @Test void invalidEnabledConfigFailsButDisabledConfigDoesNot(){p.setRedirectUri("http://wrong.test/api/oauth/linkedin/callback");assertThrows(IllegalStateException.class,p::validateConfiguration);p.setEnabled(false);assertDoesNotThrow(p::validateConfiguration);}
}
