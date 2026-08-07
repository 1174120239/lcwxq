package cn.lcxqy.starfree.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class UniPushServiceTest {
    @Test
    void disabledPushDoesNotReadClientIdOrCallProvider() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        UniPushService push = new UniPushService(
                jdbc, http, new ObjectMapper(), false,
                "app-id", "app-key", "app-secret", "https://restapi.getui.com", "v1");

        push.sendComment(7L, "title", "body", "spaceComment:11");

        verifyNoInteractions(jdbc, http);
    }

    @Test
    @SuppressWarnings("unchecked")
    void v1ProtocolAuthenticatesWithAuthSignAndPushesSingleCid() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), any()))
                .thenReturn("cid-123");
        when(http.postForObject(contains("/v1/app-id/auth_sign"), any(HttpEntity.class),
                eq(String.class))).thenReturn("{\"result\":\"ok\",\"auth_token\":\"tok-1\"}");

        UniPushService push = new UniPushService(
                jdbc, http, new ObjectMapper(), true,
                "app-id", "app-key", "app-secret", "https://restapi.getui.com", "v1");

        push.sendComment(7L, "title", "body", "spaceComment:11");

        ArgumentCaptor<HttpEntity> auth = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).postForObject(contains("/v1/app-id/auth_sign"), auth.capture(), eq(String.class));
        Map<String, Object> authBody = (Map<String, Object>) auth.getValue().getBody();
        assertThat(authBody).containsKeys("sign", "timestamp", "appkey");
        assertThat(authBody.get("appkey")).isEqualTo("app-key");
        String expectedSign = sha256("app-key" + authBody.get("timestamp") + "app-secret");
        assertThat(authBody.get("sign")).isEqualTo(expectedSign);

        ArgumentCaptor<HttpEntity> sent = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).postForObject(contains("/v1/app-id/push_single"), sent.capture(), eq(String.class));
        HttpEntity<?> entity = sent.getValue();
        assertThat(entity.getHeaders().getFirst("authtoken")).isEqualTo("tok-1");
        Map<String, Object> body = (Map<String, Object>) entity.getBody();
        assertThat(body).containsKeys("message", "notification", "cid", "requestid");
        assertThat(body.get("cid")).isEqualTo("cid-123");
        Map<String, Object> message = (Map<String, Object>) body.get("message");
        assertThat(message.get("appkey")).isEqualTo("app-key");
        assertThat(message.get("msgtype")).isEqualTo("notification");
        Map<String, Object> notification = (Map<String, Object>) body.get("notification");
        Map<String, Object> style = (Map<String, Object>) notification.get("style");
        assertThat(style.get("title")).isEqualTo("title");
        assertThat(style.get("text")).isEqualTo("body");
        assertThat(notification.get("transmission_content")).isEqualTo("spaceComment:11");
    }

    @Test
    @SuppressWarnings("unchecked")
    void v2ProtocolKeepsGeTuiV2Endpoints() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(String.class), any()))
                .thenReturn("cid-123");
        when(http.postForObject(contains("/v2/app-id/auth"), any(HttpEntity.class),
                eq(String.class))).thenReturn("{\"data\":{\"auth_token\":\"tok-2\"}}");

        UniPushService push = new UniPushService(
                jdbc, http, new ObjectMapper(), true,
                "app-id", "app-key", "app-secret", "https://restapi.getui.com", "v2");

        push.sendComment(7L, "title", "body", "spaceComment:11");

        verify(http).postForObject(contains("/v2/app-id/auth"), any(HttpEntity.class), eq(String.class));
        ArgumentCaptor<HttpEntity> sent = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).postForObject(contains("/v2/app-id/push/single/cid"), sent.capture(), eq(String.class));
        assertThat(sent.getValue().getHeaders().getFirst("token")).isEqualTo("tok-2");
        Map<String, Object> body = (Map<String, Object>) sent.getValue().getBody();
        assertThat(body.get("request_id")).isNotNull();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }
}
