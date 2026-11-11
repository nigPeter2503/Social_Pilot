package com.socialpilot.oauth.facebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialpilot.oauth.flow.OAuthFlowException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public final class FacebookGraphClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(FacebookGraphClient.class);
    private static final Pattern SAFE_ERROR_TYPE = Pattern.compile("[A-Za-z0-9_]{1,60}");
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,120}");
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(client_secret|access_token|authorization[_ ]?code|\\bcode)\\s*([=:]\\s*)[^\\s,;&]+");
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s]+");
    private static final Pattern LONG_TOKEN = Pattern.compile("[A-Za-z0-9_-]{40,}");
    private final FacebookOAuthProperties properties;
    private final RestClient graph;
    private final ObjectMapper objectMapper;

    public FacebookGraphClient(FacebookOAuthProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.graph = builder.baseUrl("https://graph.facebook.com").build();
        this.objectMapper = objectMapper;
    }

    public PageIdentity exchangeCodeForTargetPage(String code) {
        properties.requireConfigured();
        String stage = "code_exchange";
        try {
            ResponseEntity<byte[]> tokenResponse = graph.get()
                    .uri(FacebookOAuthUrlBuilder.tokenExchangeUri(properties, code))
                    .retrieve().toEntity(byte[].class);
            JsonNode tokenJson = parseGraphJson(tokenResponse, stage);
            String userToken = text(tokenJson, "access_token");

            stage = "page_list";
            ResponseEntity<byte[]> pageListResponse = graph.get().uri(uri -> uri
                    .pathSegment(properties.getGraphApiVersion(), "me", "accounts")
                    .queryParam("fields", "id,name,tasks,access_token").build())
                    .headers(headers -> headers.setBearerAuth(userToken))
                    .retrieve().toEntity(byte[].class);
            JsonNode pageList = parseGraphJson(pageListResponse, stage);
            JsonNode data = pageList == null ? null : pageList.get("data");
            if (data == null || !data.isArray()) throw OAuthFlowException.accessDenied();
            for (JsonNode page : data) {
                if (!properties.getTargetPageId().equals(text(page, "id"))) continue;
                String pageName = text(page, "name");
                String pageToken = text(page, "access_token");
                if (!hasContentTask(page.get("tasks"))) throw OAuthFlowException.accessDenied();

                stage = "page_identity";
                ResponseEntity<byte[]> identityResponse = graph.get().uri(uri -> uri
                        .pathSegment(properties.getGraphApiVersion(), properties.getTargetPageId())
                        .queryParam("fields", "id,name").build())
                        .headers(headers -> headers.setBearerAuth(pageToken))
                        .retrieve().toEntity(byte[].class);
                JsonNode identity = parseGraphJson(identityResponse, stage);
                if (!properties.getTargetPageId().equals(text(identity, "id"))
                        || !pageName.equals(text(identity, "name"))) throw OAuthFlowException.providerError();
                return new PageIdentity(properties.getTargetPageId(), pageName, pageToken);
            }
            throw OAuthFlowException.accessDenied();
        } catch (OAuthFlowException exception) {
            LOGGER.warn("Facebook OAuth stopped at stage={} code={}", stage, exception.getCode());
            throw exception;
        } catch (GraphPayloadException exception) {
            LOGGER.warn("Facebook Graph API returned a non-JSON response at stage={} httpStatus={} contentType={} bodyLength={}",
                    exception.stage, exception.httpStatus, exception.contentType, exception.bodyLength);
            throw OAuthFlowException.providerError();
        } catch (RestClientResponseException exception) {
            byte[] body = exception.getResponseBodyAsByteArray();
            String requestCode = "code_exchange".equals(stage) ? code : null;
            String codeFingerprint = "code_exchange".equals(stage) ? codeFingerprint(code) : "n/a";
            GraphError error = safeGraphError(body, requestCode);
            if (error.parsed()) {
                LOGGER.warn("Facebook Graph API failed at stage={} httpStatus={} errorType={} errorCode={} "
                                + "errorSubcode={} errorMessage={} fbtraceId={} codeFp={}",
                        stage, exception.getStatusCode().value(), error.type(), error.code(), error.subcode(),
                        error.message(), error.fbtraceId(), codeFingerprint);
            } else {
                LOGGER.warn("Facebook Graph API failed at stage={} httpStatus={} errorBodyLength={} codeFp={}",
                        stage, exception.getStatusCode().value(), error.bodyLength(), codeFingerprint);
            }
            throw OAuthFlowException.providerError();
        } catch (RuntimeException exception) {
            // Do not log exception messages, request URLs, bodies, tokens, authorization codes, or secrets.
            LOGGER.warn("Facebook Graph API failed at stage={} exceptionType={}", stage,
                    exception.getClass().getSimpleName());
            throw OAuthFlowException.providerError();
        }
    }

    JsonNode parseGraphJson(ResponseEntity<byte[]> response, String stage) {
        byte[] body = response.getBody();
        int bodyLength = body == null ? 0 : body.length;
        try {
            JsonNode json = objectMapper.readTree(body);
            if (json == null) throw new IllegalArgumentException("empty Graph response");
            return json;
        } catch (Exception ignored) {
            throw new GraphPayloadException(stage, response.getStatusCode().value(),
                    safeContentType(response.getHeaders().getContentType()), bodyLength);
        }
    }

    private static String safeContentType(MediaType contentType) {
        if (contentType == null) return "unknown";
        String value = contentType.getType() + "/" + contentType.getSubtype();
        return value.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")
                ? value.toLowerCase(Locale.ROOT) : "other";
    }

    static final class GraphPayloadException extends RuntimeException {
        final String stage;
        final int httpStatus;
        final String contentType;
        final int bodyLength;

        GraphPayloadException(String stage, int httpStatus, String contentType, int bodyLength) {
            super("Graph API response was not valid JSON");
            this.stage = stage;
            this.httpStatus = httpStatus;
            this.contentType = contentType;
            this.bodyLength = bodyLength;
        }
    }
    GraphError safeGraphError(byte[] body, String authorizationCode) {
        int bodyLength = body == null ? 0 : body.length;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root == null ? null : root.get("error");
            if (error == null || !error.isObject()) return GraphError.unparsed(bodyLength);

            int code = error.path("code").asInt(-1);
            int subcode = error.path("error_subcode").asInt(-1);
            String type = safeValue(error.path("type").asText("unknown"), SAFE_ERROR_TYPE,
                    authorizationCode);
            String traceId = safeValue(error.path("fbtrace_id").asText("unknown"), SAFE_TRACE_ID,
                    authorizationCode);
            String message = sanitizeMessage(error.path("message").asText("unknown"), authorizationCode);
            return new GraphError(true, code, subcode, type, message, traceId, bodyLength);
        } catch (Exception ignored) {
            return GraphError.unparsed(bodyLength);
        }
    }

    private String sanitizeMessage(String message, String authorizationCode) {
        String safe = message == null || message.isBlank() ? "unknown" : message;
        safe = replaceSensitive(safe, properties.getAppSecret());
        safe = replaceSensitive(safe, authorizationCode);
        safe = URL.matcher(safe).replaceAll("[URL]");
        safe = SENSITIVE_FIELD.matcher(safe).replaceAll("$1$2[REDACTED]");
        safe = LONG_TOKEN.matcher(safe).replaceAll("[REDACTED]");
        safe = safe.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return safe.length() <= 240 ? safe : safe.substring(0, 240);
    }

    private static String replaceSensitive(String message, String value) {
        if (value == null || value.isBlank()) return message;
        return message.replace(value, "[REDACTED]");
    }

    private String safeValue(String value, Pattern pattern, String authorizationCode) {
        if (value == null || value.equals(properties.getAppSecret())
                || value.equals(authorizationCode) || !pattern.matcher(value).matches()) return "unknown";
        return value;
    }

    static String codeFingerprint(String code) {
        if (code == null) return "n/a";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
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

    record GraphError(boolean parsed, int code, int subcode, String type, String message, String fbtraceId, int bodyLength) {
        private static GraphError unparsed(int bodyLength) {
            return new GraphError(false, -1, -1, "unknown", "unknown", "unknown", bodyLength);
        }
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
