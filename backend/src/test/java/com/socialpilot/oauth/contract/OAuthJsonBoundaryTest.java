package com.socialpilot.oauth.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"socialpilot.facebook.app-id=1650819950076669","socialpilot.facebook.app-secret=test-only-placeholder","socialpilot.facebook.redirect-uri=https://test-tunnel.example/api/oauth/facebook/callback","socialpilot.facebook.target-page-id=1234567890","socialpilot.facebook.graph-api-version=v27.0"})
class OAuthJsonBoundaryTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private RequestMappingHandlerAdapter handlerAdapter;

    @ParameterizedTest
    @CsvSource(value = {
        "oauthStartRequest|OAuthStartRequest|provider|BAD",
        "oauthStartRequest|OAuthStartRequest|redirectId|''",
        "oauthStartRequest|OAuthStartRequest|sessionChallenge|short",
        "oauthStartResponse|OAuthStartResponse|authorizationUrl|http://example.com/auth",
        "oauthHandoffExchangeRequest|OAuthHandoffExchangeRequest|sessionVerifier|short",
        "oauthExchangeResponse|OAuthExchangeResponse|accessToken|''",
        "oauthFailure|OAuthFailure|code|BAD"
    }, delimiter = '|')
    void duplicatePropertiesAreRejectedAtTheApplicationBoundary(
            String fixture, String dto, String field, String invalidValue) throws Exception {
        Class<?> type = Class.forName(OAuthContract.class.getName() + "$" + dto);
        String valid = mapper.writeValueAsString(mapper.readTree(Files.readString(
                Path.of("../docs/contracts/oauth-examples.json"))).get(fixture));
        String duplicate = valid.substring(0, valid.length() - 1) + ",\"" + field + "\":"
                + mapper.writeValueAsString(invalidValue) + "}";
        assertNotNull(mapper.readValue(valid, type));
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(duplicate, type));

        // Exercise the JSON converter registered in MVC, not a mapper configured only in this test.
        MappingJackson2HttpMessageConverter converter = handlerAdapter.getMessageConverters().stream()
                .filter(c -> c instanceof MappingJackson2HttpMessageConverter
                        && c.canRead(type, MediaType.APPLICATION_JSON))
                .map(c -> (MappingJackson2HttpMessageConverter) c).findFirst().orElseThrow();
        assertNotNull(converter.read(type, new MockHttpInputMessage(valid.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThrows(org.springframework.http.converter.HttpMessageNotReadableException.class,
                () -> converter.read(type, new MockHttpInputMessage(duplicate.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}
