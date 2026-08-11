package cn.lcxqy.starfree.bot;

import cn.lcxqy.starfree.security.LegacySessionBridge;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.SessionTokenGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotImageUploadServiceTest {
    @Test
    void uploadsImageThroughDetachedUserSessionWithoutReplacingLogin() {
        RestTemplate rest = mock(RestTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        LegacySessionBridge sessions = mock(LegacySessionBridge.class);
        SessionTokenGenerator tokenGenerator = mock(SessionTokenGenerator.class);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("uid", 77L);
        user.put("name", "alice");
        when(tokens.userById(77L)).thenReturn(user);
        when(sessions.available()).thenReturn(true);
        when(tokenGenerator.generate("qqbot-77")).thenReturn("temporary-upload-token");
        when(rest.postForEntity(eq(URI.create("http://127.0.0.1:8081/upload/full")),
                any(HttpEntity.class), eq(String.class))).thenAnswer(invocation -> {
            HttpEntity<?> request = invocation.getArgument(1);
            @SuppressWarnings("unchecked")
            MultiValueMap<String, Object> form = (MultiValueMap<String, Object>) request.getBody();
            assertThat(form.getFirst("token")).isEqualTo("temporary-upload-token");
            assertThat(form).doesNotContainKey("webkey");
            assertThat(form.getFirst("file")).isInstanceOf(HttpEntity.class);
            return ResponseEntity.ok("{\"code\":1,\"data\":{\"url\":\"https://frp.lcxqy.cn/upload/a.jpg\"}}");
        });
        BotImageUploadService service = new BotImageUploadService(
                rest, new ObjectMapper(), "http://127.0.0.1:8081",
                tokens, sessions, tokenGenerator);

        List<String> urls = service.upload(77L, Collections.singletonList(new MockMultipartFile(
                "images", "qq.jpg", "image/jpeg", "image-data".getBytes(StandardCharsets.UTF_8))));

        assertThat(urls).containsExactly("https://frp.lcxqy.cn/upload/a.jpg");
        verify(sessions).storeDetached(eq("temporary-upload-token"), any(Map.class));
        verify(sessions).remove("temporary-upload-token");
    }

    @Test
    void rejectsNonImageFilesBeforeCallingLegacyUpload() {
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        LegacySessionBridge sessions = mock(LegacySessionBridge.class);
        SessionTokenGenerator tokenGenerator = mock(SessionTokenGenerator.class);
        when(tokens.userById(77L)).thenReturn(Collections.<String, Object>singletonMap("uid", 77L));
        when(sessions.available()).thenReturn(true);
        when(tokenGenerator.generate("qqbot-77")).thenReturn("temporary-upload-token");
        BotImageUploadService service = new BotImageUploadService(
                mock(RestTemplate.class), new ObjectMapper(), "http://127.0.0.1:8081",
                tokens, sessions, tokenGenerator);

        assertThatThrownBy(() -> service.upload(77L, Collections.singletonList(new MockMultipartFile(
                "images", "note.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是图片");
        verify(sessions).remove("temporary-upload-token");
    }
}
