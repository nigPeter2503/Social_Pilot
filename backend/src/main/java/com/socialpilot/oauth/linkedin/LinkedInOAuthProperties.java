package com.socialpilot.oauth.linkedin;
import java.net.URI;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.socialpilot.oauth.flow.OAuthFlowException;
@Component
@ConfigurationProperties(prefix="socialpilot.linkedin")
public final class LinkedInOAuthProperties {
    private boolean enabled;
    private String clientId="",clientSecret="",redirectUri="";
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getClientId(){return clientId;} public void setClientId(String v){clientId=v;}
    public String getClientSecret(){return clientSecret;} public void setClientSecret(String v){clientSecret=v;}
    public String getRedirectUri(){return redirectUri;} public void setRedirectUri(String v){redirectUri=v;}
    public boolean isConfigured(){
        try {
            URI u=URI.create(redirectUri);
            return enabled && clientId!=null && clientId.matches("[A-Za-z0-9]+") && clientSecret!=null
                && !clientSecret.isBlank() && clientSecret.chars().noneMatch(Character::isWhitespace)
                && "https".equals(u.getScheme()) && u.getHost()!=null && u.getUserInfo()==null
                && u.getQuery()==null && u.getFragment()==null && "/api/oauth/linkedin/callback".equals(u.getPath());
        } catch(RuntimeException e){return false;}
    }
    @PostConstruct public void validateConfiguration(){if(enabled&&!isConfigured()) throw new IllegalStateException("Invalid LinkedIn OAuth configuration; check client ID, secret and HTTPS callback");}
    public void requireConfigured(){if(!isConfigured()) throw OAuthFlowException.notConfigured();}
}
