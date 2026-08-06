package cn.lcxqy.starfree.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Optional UniPush 2.0 sender. Inbox persistence remains authoritative when push is unavailable. */
@Service
public class UniPushService {
    private static final Logger LOG = LoggerFactory.getLogger(UniPushService.class);

    private final JdbcTemplate jdbc;
    private final RestTemplate http;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String appId;
    private final String appKey;
    private final String appSecret;
    private final String apiBase;

    public UniPushService(
            JdbcTemplate jdbc,
            @Qualifier("externalReadRestTemplate")
            RestTemplate http,
            ObjectMapper mapper,
            @Value("${unipush.enabled:false}") boolean enabled,
            @Value("${unipush.app-id:}") String appId,
            @Value("${unipush.app-key:}") String appKey,
            @Value("${unipush.app-secret:}") String appSecret,
            @Value("${unipush.api-base:https://restapi.getui.com}") String apiBase) {
        this.jdbc = jdbc;
        this.http = http;
        this.mapper = mapper;
        this.enabled = enabled;
        this.appId = appId;
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.apiBase = apiBase.replaceAll("/+$", "");
    }

    public void sendComment(long toUid, String title, String body, String payload) {
        if (!enabled || blank(appId) || blank(appKey) || blank(appSecret) || toUid <= 0) {
            return;
        }
        try {
            String clientId = jdbc.queryForObject(
                    "SELECT clientId FROM starfree_users WHERE uid=? LIMIT 1", String.class, toUid);
            if (blank(clientId)) {
                return;
            }
            String authToken = authenticate();
            if (blank(authToken)) {
                return;
            }
            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("title", title);
            notification.put("body", body);
            notification.put("click_type", "payload");
            notification.put("payload", payload);
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("notification", notification);
            Map<String, Object> audience = new LinkedHashMap<>();
            audience.put("cid", Collections.singletonList(clientId));
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("request_id", UUID.randomUUID().toString().replace("-", ""));
            request.put("audience", audience);
            request.put("message", message);
            request.put("setting", Collections.singletonMap("strategy", Collections.singletonMap("default", 1)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("token", authToken);
            http.postForEntity(apiBase + "/v2/" + appId + "/push/single/cid",
                    new HttpEntity<>(request, headers), String.class);
        } catch (Exception error) {
            LOG.warn("UniPush delivery failed for uid {}", toUid, error);
        }
    }

    private String authenticate() throws Exception {
        long timestamp = Instant.now().toEpochMilli();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sign", sha256(appKey + timestamp + appSecret));
        request.put("timestamp", timestamp);
        request.put("appkey", appKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String raw = http.postForObject(apiBase + "/v2/" + appId + "/auth",
                new HttpEntity<>(request, headers), String.class);
        Map<String, Object> response = mapper.readValue(raw, new TypeReference<Map<String, Object>>() { });
        Object data = response.get("data");
        if (!(data instanceof Map)) {
            return "";
        }
        Object token = ((Map<?, ?>) data).get("auth_token");
        return token == null ? "" : String.valueOf(token);
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
