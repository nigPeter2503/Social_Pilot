package com.socialpilot.oauth.linkedin;
import com.fasterxml.jackson.databind.JsonNode;
import com.socialpilot.oauth.provider.*;
import com.socialpilot.oauth.flow.OAuthFlowException;
import java.net.URI;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
@Component
public final class LinkedInOAuthClient implements OAuthProviderAdapter {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LinkedInOAuthClient.class);
    private final LinkedInOAuthProperties properties;
    private final RestClient client;
    @org.springframework.beans.factory.annotation.Autowired
    public LinkedInOAuthClient(LinkedInOAuthProperties properties,RestClient.Builder builder) {
        this.properties=properties;
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory(){
            @Override protected void prepareConnection(HttpURLConnection connection,String method) throws IOException {
                super.prepareConnection(connection,method);connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(10000); factory.setReadTimeout(15000);
        // Clone preserves the caller's test interception/customizers.
        client=builder.clone().requestFactory(factory).build();
    }
    LinkedInOAuthClient(LinkedInOAuthProperties properties,RestClient client){this.properties=properties;this.client=client;}
    public String provider(){return "LINKEDIN";}
    public URI authorizationUri(String state){
        properties.requireConfigured();
        return UriComponentsBuilder.fromUriString("https://www.linkedin.com/oauth/v2/authorization")
            .queryParam("response_type","code").queryParam("client_id",properties.getClientId())
            .queryParam("redirect_uri",properties.getRedirectUri()).queryParam("state",state)
            .queryParam("scope","openid profile w_member_social").build().encode().toUri();
    }
    public ProviderIdentity exchangeCode(String code){
        properties.requireConfigured();
        Instant requestedAt=Instant.now();
        String stage="token_exchange";
        try {
            var form=new LinkedMultiValueMap<String,String>();
            form.add("grant_type","authorization_code"); form.add("code",code);
            form.add("client_id",properties.getClientId()); form.add("client_secret",properties.getClientSecret());
            form.add("redirect_uri",properties.getRedirectUri());
            JsonNode token=client.post().uri("https://www.linkedin.com/oauth/v2/accessToken")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            String access=text(token,"access_token"),type=token!=null&&!token.has("token_type")?"Bearer":text(token,"token_type");
            if(!"bearer".equalsIgnoreCase(type)) throw OAuthFlowException.providerError();
            stage="scope_validation";
            if(token.has("scope")) {
                var scopes=new HashSet<>(Arrays.asList(text(token,"scope").trim().split("[,\\s]+")));
                if(!scopes.containsAll(Arrays.asList("openid","profile","w_member_social"))) throw OAuthFlowException.accessDenied();
            }
            String expiry=null;
            if(token.has("expires_in")) {
                JsonNode seconds=token.get("expires_in");
                if(!seconds.isIntegralNumber()||!seconds.canConvertToLong()||seconds.asLong()<=0) throw OAuthFlowException.providerError();
                expiry=requestedAt.plusSeconds(seconds.asLong()).truncatedTo(ChronoUnit.SECONDS).toString();
            }
            stage="userinfo";
            JsonNode identity=client.get().uri("https://api.linkedin.com/v2/userinfo")
                .headers(h->h.setBearerAuth(access)).retrieve().body(JsonNode.class);
            return new ProviderIdentity(provider(),text(identity,"sub"),"PERSON",text(identity,"name"),access,
                token.hasNonNull("refresh_token")?text(token,"refresh_token"):null,type,expiry);
        } catch(OAuthFlowException e){
            LOGGER.warn("LinkedIn OAuth stopped stage={} code={}",stage,e.getCode());
            throw e;
        }
        catch(RestClientResponseException e){
            LOGGER.warn("LinkedIn OAuth HTTP failure stage={} status={}",stage,e.getStatusCode().value());
            if(e.getStatusCode().value()==401||e.getStatusCode().value()==403) throw OAuthFlowException.accessDenied();
            throw OAuthFlowException.providerError();
        } catch(RuntimeException e){
            LOGGER.warn("LinkedIn OAuth failed stage={}",stage);
            throw OAuthFlowException.providerError();
        }
    }
    private static String text(JsonNode node,String key){
        if(node==null||!node.path(key).isTextual()||node.path(key).asText().isBlank()) throw OAuthFlowException.providerError();
        return node.get(key).asText();
    }
}
