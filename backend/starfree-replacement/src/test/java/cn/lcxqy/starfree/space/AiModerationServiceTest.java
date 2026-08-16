package cn.lcxqy.starfree.space;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModerationServiceTest {
    @Test
    void imageDynamicStillReviewsItsTextAndStoresApproval() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        AiModerationService service = new AiModerationService(jdbc, new ObjectMapper(), http);
        when(jdbc.queryForList(startsWith("SELECT c.enabled")))
                .thenReturn(Collections.singletonList(config(1)));
        when(http.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"{\\\"safe\\\":true,"
                        + "\\\"category\\\":\\\"正常\\\",\\\"reason\\\":\\\"文字内容正常\\\"}\"}}]}");
        when(jdbc.queryForObject(startsWith("SELECT id FROM starfree_ai_moderation_reviews"),
                eq(Long.class), eq("space"), eq(12L), anyString())).thenReturn(88L);

        AiModerationService.Decision decision = service.reviewSpace(
                12L, 7L, "带图动态的文字说明", "a.jpg||b.jpg");

        assertThat(decision.isSafe()).isTrue();
        assertThat(decision.getReviewId()).isEqualTo(88L);
        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).postForObject(eq("https://api.deepseek.com/chat/completions"),
                request.capture(), eq(String.class));
        Map<?, ?> body = (Map<?, ?>) request.getValue().getBody();
        List<?> messages = (List<?>) body.get("messages");
        assertThat(String.valueOf(((Map<?, ?>) messages.get(1)).get("content")))
                .contains("带图动态的文字说明")
                .contains("只判断文字，不因附件本身拒绝");
    }

    @Test
    void globalAuditSwitchDisablesEveryAiScopeWithoutCallingProvider() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        AiModerationService service = new AiModerationService(jdbc, new ObjectMapper(), http);
        when(jdbc.queryForList(startsWith("SELECT c.enabled")))
                .thenReturn(Collections.singletonList(config(0)));

        assertThat(service.enabledForSpace()).isFalse();
        assertThat(service.enabledForQuestion()).isFalse();
        assertThat(service.enabledForComments()).isFalse();
        verify(http, never()).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
    }

    private Map<String, Object> config(int globalAudit) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("spaceAudit", globalAudit);
        result.put("enabled", 1);
        result.put("space_enabled", 1);
        result.put("question_enabled", 1);
        result.put("comment_enabled", 1);
        result.put("comment_review_time", "03:30");
        result.put("comment_action", "hide");
        result.put("provider", "deepseek");
        result.put("api_url", "https://api.deepseek.com/chat/completions");
        result.put("api_key", "test-key");
        result.put("model", "deepseek-chat");
        result.put("custom_prompt", "");
        result.put("last_comment_review_started", 0);
        result.put("last_comment_review_finished", 0);
        return result;
    }
}
