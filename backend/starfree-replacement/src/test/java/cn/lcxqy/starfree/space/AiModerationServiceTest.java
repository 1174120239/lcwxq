package cn.lcxqy.starfree.space;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
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
    void galleryVisionSendsImagesToTheVisionModelAndKeepsPerImageRisk() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        AiModerationService service = new AiModerationService(jdbc, new ObjectMapper(), http);
        Map<String, Object> config = config(1);
        config.put("vision_enabled", 1);
        config.put("vision_model", "deepseek-flash");
        when(jdbc.queryForList(startsWith("SELECT c.enabled")))
                .thenReturn(Collections.singletonList(config));
        when(http.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"{\\\"safe\\\":false,"
                        + "\\\"category\\\":\\\"学生信息\\\",\\\"reason\\\":\\\"第2张图包含学号\\\","
                        + "\\\"images\\\":[{\\\"index\\\":1,\\\"safe\\\":true,\\\"category\\\":\\\"正常\\\",\\\"reason\\\":\\\"正常\\\"},"
                        + "{\\\"index\\\":2,\\\"safe\\\":false,\\\"category\\\":\\\"学生信息\\\",\\\"reason\\\":\\\"包含学号\\\"}]}\"}}]}");
        when(jdbc.queryForObject(startsWith("SELECT id FROM starfree_ai_moderation_reviews"),
                eq(Long.class), eq("gallery"), eq(42L), anyString())).thenReturn(99L);

        AiModerationService.Decision decision = service.reviewGallery(42L, 7L,
                "图集标题：校园日常\n图集描述：随手拍\n图集标签：校园",
                Arrays.asList("https://img.example/1.jpg", "https://img.example/2.jpg"));

        assertThat(decision.isSafe()).isFalse();
        assertThat(decision.getImageSummary()).contains("第2张").contains("学号");
        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).postForObject(eq("https://api.deepseek.com/chat/completions"),
                request.capture(), eq(String.class));
        Map<?, ?> body = (Map<?, ?>) request.getValue().getBody();
        assertThat(body.get("model")).isEqualTo("deepseek-flash");
        assertThat(body.containsKey("response_format")).isFalse();
        List<?> messages = (List<?>) body.get("messages");
        List<?> content = (List<?>) ((Map<?, ?>) messages.get(1)).get("content");
        assertThat(content).hasSize(3);
        assertThat(((Map<?, ?>) content.get(0)).get("type")).isEqualTo("text");
        assertThat(((Map<?, ?>) content.get(1)).get("type")).isEqualTo("image_url");
        assertThat(String.valueOf(content.get(2))).contains("https://img.example/2.jpg");
    }

    @Test
    void galleryVisionInlinesDownloadedPublicImagesForTheProvider() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        AiModerationService service = new AiModerationService(jdbc, new ObjectMapper(), http);
        Map<String, Object> config = config(1);
        config.put("vision_enabled", 1);
        when(jdbc.queryForList(startsWith("SELECT c.enabled")))
                .thenReturn(Collections.singletonList(config));
        when(http.getForObject(eq("https://frp.lcxqy.cn/uploads/demo.png"), eq(byte[].class)))
                .thenReturn(pngBytes());
        when(http.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"{\\\"safe\\\":true,"
                        + "\\\"category\\\":\\\"正常\\\",\\\"reason\\\":\\\"图片正常\\\"}\"}}]}");
        when(jdbc.queryForObject(startsWith("SELECT id FROM starfree_ai_moderation_reviews"),
                eq(Long.class), eq("gallery"), eq(43L), anyString())).thenReturn(100L);

        AiModerationService.Decision decision = service.reviewGallery(43L, 7L,
                "图集标题：测试", Collections.singletonList("https://frp.lcxqy.cn/uploads/demo.png"));

        assertThat(decision.isSafe()).isTrue();
        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).postForObject(eq("https://api.deepseek.com/chat/completions"),
                request.capture(), eq(String.class));
        Map<?, ?> body = (Map<?, ?>) request.getValue().getBody();
        List<?> messages = (List<?>) body.get("messages");
        List<?> content = (List<?>) ((Map<?, ?>) messages.get(1)).get("content");
        Map<?, ?> imageUrl = (Map<?, ?>) ((Map<?, ?>) content.get(1)).get("image_url");
        assertThat(String.valueOf(imageUrl.get("url")))
                .startsWith("data:image/jpeg;base64,");
    }

    @Test
    void galleryScopeRequiresItsOwnSwitch() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        AiModerationService service = new AiModerationService(jdbc, new ObjectMapper(), http);
        Map<String, Object> config = config(1);
        config.put("gallery_enabled", 0);
        when(jdbc.queryForList(startsWith("SELECT c.enabled")))
                .thenReturn(Collections.singletonList(config));

        assertThat(service.enabledForGallery()).isFalse();
        assertThat(service.enabledForSpace()).isTrue();
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

    private byte[] pngBytes() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                20, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private Map<String, Object> config(int globalAudit) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("spaceAudit", globalAudit);
        result.put("enabled", 1);
        result.put("space_enabled", 1);
        result.put("question_enabled", 1);
        result.put("comment_enabled", 1);
        result.put("gallery_enabled", 1);
        result.put("vision_enabled", 0);
        result.put("vision_model", "deepseek-flash");
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
