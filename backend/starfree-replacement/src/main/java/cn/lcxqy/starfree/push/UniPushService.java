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

/**
 * Optional UniPush sender. Supports both the legacy UniPush 1.0 (GeTui REST v1)
 * protocol used by this app and the newer v2 protocol, selected by
 * {@code unipush.protocol=v1|v2}. Inbox persistence remains authoritative when
 * the push provider is unavailable.
 */
@Service
public class UniPushService {
    private static final Logger LOG = LoggerFactory.getLogger(UniPushService.class);

    private final JdbcTemplate jdbc;
    private final RestTemplate http;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final boolean protocolV1;
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
            @Value("${unipush.api-base:https://restapi.getui.com}") String apiBase,
            @Value("${unipush.protocol:v2}") String protocol) {
        this.jdbc = jdbc;
        this.http = http;
        this.mapper = mapper;
        this.enabled = enabled;
        this.protocolV1 = "v1".equalsIgnoreCase(protocol == null ? "" : protocol.trim());
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
            if (protocolV1) {
                String authToken = authenticateV1();
                if (blank(authToken)) {
                    return;
                }
                pushSingleV1(clientId, authToken, title, body, payload);
            } else {
                String authToken = authenticateV2();
                if (blank(authToken)) {
                    return;
                }
                pushSingleV2(clientId, authToken, title, body, payload);
            }
        } catch (Exception error) {
            LOG.warn("UniPush delivery failed for uid {}", toUid, error);
        }
    }

    /**
     * UniPush 1.0 / GeTui REST v1 authentication.
     * <pre>POST {apiBase}/v1/{appid}/auth_sign</pre>
     * body {@code {"sign": sha256(appkey + timestamp + mastersecret), "timestamp": ms, "appkey": appkey}}.
     */
    private String authenticateV1() throws Exception {
        long timestamp = Instant.now().toEpochMilli();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sign", sha256(appKey + timestamp + appSecret));
        request.put("timestamp", timestamp);
        request.put("appkey", appKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String raw = http.postForObject(apiBase + "/v1/" + appId + "/auth_sign",
                new HttpEntity<>(request, headers), String.class);
        return authToken(raw);
    }

    /**
     * UniPush 1.0 single push to one cid.
     * <pre>POST {apiBase}/v1/{appid}/push_single</pre>
     * header {@code authtoken}; the App foreground listener reads {@code transmission_content}
     * as {@code msg.payload}, so the payload keeps the "spaceComment:&lt;id&gt;" convention used by
     * {@code App.vue} to refresh the message center.
     */
    private void pushSingleV1(String clientId, String authToken, String title, String body,
                              String payload) {
        Map<String, Object> style = new LinkedHashMap<>();
        style.put("type", 0);
        style.put("text", body == null ? "" : body);
        style.put("title", title == null ? "" : title);
        style.put("logo", "push.png");
        style.put("is_ring", true);
        style.put("is_vibrate", true);
        style.put("is_clearable", true);
        style.put("channel", "lcxqy-forum");
        style.put("channelName", "\u8bba\u575b\u901a\u77e5");
        style.put("channelLevel", 4);
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("style", style);
        notification.put("transmission_type", false);
        notification.put("transmission_content", payload == null ? "" : payload);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("appkey", appKey);
        message.put("is_offline", true);
        message.put("offline_expire_time", 86400000);
        message.put("msgtype", "notification");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("message", message);
        request.put("notification", notification);
        request.put("cid", clientId);
        request.put("requestid", UUID.randomUUID().toString().replace("-", ""));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authtoken", authToken);
        http.postForEntity(apiBase + "/v1/" + appId + "/push_single",
                new HttpEntity<>(request, headers), String.class);
    }

    private String authenticateV2() throws Exception {
        long timestamp = Instant.now().toEpochMilli();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sign", sha256(appKey + timestamp + appSecret));
        request.put("timestamp", timestamp);
        request.put("appkey", appKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String raw = http.postForObject(apiBase + "/v2/" + appId + "/auth",
                new HttpEntity<>(request, headers), String.class);
        return authToken(raw);
    }

    private void pushSingleV2(String clientId, String authToken, String title, String body,
                              String payload) {
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
    }

    private String authToken(String raw) throws Exception {
        if (blank(raw)) {
            return "";
        }
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
