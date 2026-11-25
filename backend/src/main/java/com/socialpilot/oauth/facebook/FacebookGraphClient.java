package com.socialpilot.oauth.facebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialpilot.oauth.flow.OAuthFlowException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
    static final int MAX_PAGE_LIST_REQUESTS = 25;
    private static final long PAGE_LIST_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(30);
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
        return exchangeCode(code, false);
    }

    public PageIdentity exchangeCodeForInstagram(String code) {
        return exchangeCode(code, true);
    }

    private PageIdentity exchangeCode(String code, boolean instagram) {
        properties.requireConfigured();
        String stage = "code_exchange";
        try {
            ResponseEntity<byte[]> tokenResponse = graph.get()
                    .uri(FacebookOAuthUrlBuilder.tokenExchangeUri(properties, code))
                    .retrieve().toEntity(byte[].class);
            JsonNode tokenJson = parseGraphJson(tokenResponse, stage);
            String userToken = text(tokenJson, "access_token");
            long originalUserLifetime = 0;
            long longLivedUserLifetime = 0;
            if (instagram) {
                stage = "instagram_token_exchange";
                ResponseEntity<byte[]> longLivedResponse = graph.get()
                        .uri(FacebookOAuthUrlBuilder.longLivedUserTokenUri(properties, userToken))
                        .retrieve().toEntity(byte[].class);
                JsonNode longLived = parseGraphJson(longLivedResponse, stage);
                userToken = text(longLived, "access_token");
                originalUserLifetime = tokenJson.path("expires_in").asLong(0);
                longLivedUserLifetime = longLived.path("expires_in").asLong(0);
            }

            stage = "page_list";
            JsonNode page = findTargetPage(userToken, instagram);
            JsonNode tasks = page.get("tasks");
            if (!hasContentTask(tasks)) {
                LOGGER.warn("OAuth Page grant rejected: provider={} reason=PAGE_CONTENT_TASK_MISSING taskCount={}",
                        instagram ? "INSTAGRAM" : "FACEBOOK", tasks != null && tasks.isArray() ? tasks.size() : 0);
                throw OAuthFlowException.accessDenied();
            }
            String pageName = text(page, "name");
            String pageToken = text(page, "access_token");

            if (instagram) {
                stage = "instagram_identity";
                JsonNode linked = page.path("instagram_business_account");
                String instagramId = linked.path("id").asText("");
                String username = linked.path("username").asText("");
                if (!instagramId.matches("[0-9]{5,30}") || instagramId.equals(properties.getTargetPageId())
                        || !username.matches("[A-Za-z0-9._]{1,30}")) {
                    throw OAuthFlowException.accessDenied();
                }
                stage = "instagram_token";
                JsonNode metadata = instagramTokenMetadata(pageToken);
                List<String> scopes = new ArrayList<>();
                for (JsonNode scope : metadata.path("scopes")) {
                    if (scope.isTextual() && scope.asText().matches("[a-z][a-z0-9_]*")) scopes.add(scope.asText());
                }
                if (!scopes.containsAll(List.of("instagram_basic", "instagram_content_publish",
                        "pages_read_engagement"))) throw OAuthFlowException.accessDenied();
                LOGGER.info("Instagram token exchange verified: originalUserExpiresInSeconds={} "
                                + "longLivedUserExpiresInSeconds={} pageExpiresAtEpoch={} pageDataAccessExpiresAtEpoch={}",
                        originalUserLifetime, longLivedUserLifetime, metadata.path("expires_at").asLong(0),
                        metadata.path("data_access_expires_at").asLong(0));
                return new PageIdentity(properties.getTargetPageId(), pageName, pageToken, instagramId,
                        username, scopes, positiveEpoch(metadata, "expires_at"),
                        positiveEpoch(metadata, "data_access_expires_at"));
            }

            stage = "page_identity";
            ResponseEntity<byte[]> identityResponse = graph.get().uri(uri -> uri
                    .pathSegment(properties.getGraphApiVersion(), properties.getTargetPageId())
                    .queryParam("fields", "id,name").build())
                    .headers(headers -> headers.setBearerAuth(pageToken))
                    .retrieve().toEntity(byte[].class);
            JsonNode identity = parseGraphJson(identityResponse, stage);
            if (!properties.getTargetPageId().equals(text(identity, "id"))
                    || !pageName.equals(text(identity, "name"))) throw OAuthFlowException.providerError();
            return new PageIdentity(properties.getTargetPageId(), pageName, pageToken, null, null, List.of(), null, null);
        } catch (OAuthFlowException exception) {
            LOGGER.warn("Facebook OAuth stopped at stage={} code={}", stage, exception.getCode());
            throw exception;
        } catch (GraphPayloadException exception) {
            LOGGER.warn("Facebook Graph API returned a non-JSON response at stage={} httpStatus={} contentType={} bodyLength={}",
                    exception.stage, exception.httpStatus, exception.contentType, exception.bodyLength);
            throw OAuthFlowException.providerError();
        } catch (RestClientResponseException exception) {
            if ("instagram_token".equals(stage) || "instagram_token_exchange".equals(stage)) {
                LOGGER.warn("Instagram token request failed at stage={} httpStatus={}",
                        stage, exception.getStatusCode().value());
                throw OAuthFlowException.providerError();
            }
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

    private JsonNode instagramTokenMetadata(String pageToken) {
        // Pass app credentials as an Authorization header. Do not log the request or its response.
        ResponseEntity<byte[]> response = graph.get().uri(uri -> uri
                .pathSegment(properties.getGraphApiVersion(), "debug_token")
                .queryParam("input_token", "{pageToken}").build(pageToken))
                .headers(headers -> headers.setBearerAuth(properties.getAppId() + "|" + properties.getAppSecret()))
                .retrieve().toEntity(byte[].class);
        JsonNode data = parseGraphJson(response, "instagram_token").path("data");
        if (!data.path("is_valid").asBoolean(false) || !properties.getAppId().equals(data.path("app_id").asText())) {
            throw OAuthFlowException.accessDenied();
        }
        return data;
    }

    private static Instant positiveEpoch(JsonNode metadata, String field) {
        long expiry = metadata.path(field).asLong(0);
        return expiry > 0 ? Instant.ofEpochSecond(expiry) : null;
    }

    private JsonNode findTargetPage(String userToken, boolean instagram) {
        String fields = instagram ? "id,name,tasks,access_token,instagram_business_account{id,username}"
                : "id,name,tasks,access_token";
        String after = null;
        Set<String> seenCursors = new HashSet<>();
        long startedAt = System.nanoTime();
        int returnedPages = 0;
        for (int request = 0; request < MAX_PAGE_LIST_REQUESTS; request++) {
            if (System.nanoTime() - startedAt >= PAGE_LIST_BUDGET_NANOS) throw OAuthFlowException.providerError();
            String cursor = after;
            // Never follow paging.next: it is untrusted and may embed a token or another host.
            ResponseEntity<byte[]> response = graph.get().uri(uri -> {
                uri.pathSegment(properties.getGraphApiVersion(), "me", "accounts")
                        .queryParam("fields", instagram ? "{fields}" : fields);
                if (cursor != null) uri.queryParam("after", "{cursor}");
                if (instagram) return cursor == null ? uri.build(fields) : uri.build(fields, cursor);
                return cursor == null ? uri.build() : uri.build(cursor);
            }).headers(headers -> headers.setBearerAuth(userToken)).retrieve().toEntity(byte[].class);
            JsonNode pageList = parseGraphJson(response, "page_list");
            JsonNode data = pageList.get("data");
            if (data == null || !data.isArray()) throw OAuthFlowException.providerError();
            returnedPages += data.size();
            for (JsonNode page : data) {
                if (properties.getTargetPageId().equals(text(page, "id"))) return page;
            }
            JsonNode paging = pageList.path("paging");
            JsonNode next = paging.get("next");
            if (next == null || next.isNull() || (next.isTextual() && next.asText().isBlank())) {
                LOGGER.warn("OAuth Page grant rejected: provider={} reason=TARGET_PAGE_NOT_RETURNED pageCount={}",
                        instagram ? "INSTAGRAM" : "FACEBOOK", returnedPages);
                throw OAuthFlowException.accessDenied();
            }
            after = paging.path("cursors").path("after").asText("");
            if (!next.isTextual() || after.isBlank() || after.length() > 4096 || !seenCursors.add(after)) {
                throw OAuthFlowException.providerError();
            }
        }
        throw OAuthFlowException.providerError();
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
            if (Set.of("CREATE_CONTENT", "MANAGE", "PROFILE_PLUS_CREATE_CONTENT",
                    "PROFILE_PLUS_FULL_CONTROL").contains(task.asText())) return true;
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
        public final String instagramId;
        public final String instagramUsername;
        public final List<String> grantedScopes;
        public final Instant tokenExpiresAt;
        public final Instant dataAccessExpiresAt;
        private PageIdentity(String id, String name, String accessToken, String instagramId,
                String instagramUsername, List<String> grantedScopes,
                Instant tokenExpiresAt, Instant dataAccessExpiresAt) {
            this.id = id; this.name = name; this.accessToken = accessToken;
            this.instagramId = instagramId; this.instagramUsername = instagramUsername;
            this.grantedScopes = List.copyOf(grantedScopes);
            this.tokenExpiresAt = tokenExpiresAt;
            this.dataAccessExpiresAt = dataAccessExpiresAt;
        }

        public Instant effectiveExpiry() {
            if (tokenExpiresAt == null) return dataAccessExpiresAt;
            if (dataAccessExpiresAt == null || tokenExpiresAt.isBefore(dataAccessExpiresAt)) {
                return tokenExpiresAt;
            }
            return dataAccessExpiresAt;
        }
    }
}
