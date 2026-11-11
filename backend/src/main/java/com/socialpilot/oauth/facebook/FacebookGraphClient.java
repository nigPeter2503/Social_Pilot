package com.socialpilot.oauth.facebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.socialpilot.oauth.flow.OAuthFlowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public final class FacebookGraphClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(FacebookGraphClient.class);
    private final FacebookOAuthProperties properties;
    private final RestClient graph;

    public FacebookGraphClient(FacebookOAuthProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.graph = builder.baseUrl("https://graph.facebook.com").build();
    }

    public PageIdentity exchangeCodeForTargetPage(String code) {
        properties.requireConfigured();
        String stage = "code_exchange";
        try {
            JsonNode tokenResponse = graph.get().uri(uri -> uri
                    .pathSegment(properties.getGraphApiVersion(), "oauth", "access_token")
                    .queryParam("client_id", properties.getAppId())
                    .queryParam("client_secret", properties.getAppSecret())
                    .queryParam("redirect_uri", properties.getRedirectUri())
                    .queryParam("code", code).build())
                    .retrieve().body(JsonNode.class);
            String userToken = text(tokenResponse, "access_token");

            stage = "page_list";
            JsonNode pageList = graph.get().uri(uri -> uri
                    .pathSegment(properties.getGraphApiVersion(), "me", "accounts")
                    .queryParam("fields", "id,name,tasks,access_token").build())
                    .headers(headers -> headers.setBearerAuth(userToken))
                    .retrieve().body(JsonNode.class);
            JsonNode data = pageList == null ? null : pageList.get("data");
            if (data == null || !data.isArray()) throw OAuthFlowException.accessDenied();
            for (JsonNode page : data) {
                if (!properties.getTargetPageId().equals(text(page, "id"))) continue;
                String pageName = text(page, "name");
                String pageToken = text(page, "access_token");
                if (!hasContentTask(page.get("tasks"))) throw OAuthFlowException.accessDenied();

                stage = "page_identity";
                JsonNode identity = graph.get().uri(uri -> uri
                        .pathSegment(properties.getGraphApiVersion(), properties.getTargetPageId())
                        .queryParam("fields", "id,name").build())
                        .headers(headers -> headers.setBearerAuth(pageToken))
                        .retrieve().body(JsonNode.class);
                if (!properties.getTargetPageId().equals(text(identity, "id"))
                        || !pageName.equals(text(identity, "name"))) throw OAuthFlowException.providerError();
                return new PageIdentity(properties.getTargetPageId(), pageName, pageToken);
            }
            throw OAuthFlowException.accessDenied();
        } catch (OAuthFlowException exception) {
            LOGGER.warn("Facebook OAuth stopped at stage={} code={}", stage, exception.getCode());
            throw exception;
        } catch (RestClientResponseException exception) {
            LOGGER.warn("Facebook Graph API failed at stage={} httpStatus={}", stage, exception.getStatusCode().value());
            throw OAuthFlowException.providerError();
        } catch (RuntimeException exception) {
            // Do not log exception messages, request URLs, bodies, tokens, authorization codes, or secrets.
            LOGGER.warn("Facebook Graph API failed at stage={} exceptionType={}", stage,
                    exception.getClass().getSimpleName());
            throw OAuthFlowException.providerError();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) throw OAuthFlowException.providerError();
        return value.asText();
    }

    private static boolean hasContentTask(JsonNode tasks) {
        if (tasks == null || !tasks.isArray()) return false;
        for (JsonNode task : tasks) {
            if ("CREATE_CONTENT".equals(task.asText()) || "MANAGE".equals(task.asText())) return true;
        }
        return false;
    }

    public static final class PageIdentity {
        public final String id;
        public final String name;
        public final String accessToken;
        private PageIdentity(String id, String name, String accessToken) {
            this.id = id; this.name = name; this.accessToken = accessToken;
        }
    }
}
