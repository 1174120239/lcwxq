package cn.lcxqy.starfree.bot;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotImageUploadServiceTest {
    @Test
    void uploadsImageThroughLegacyWebKeyWithoutExposingItToPlugin() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.postForEntity(eq(URI.create("http://127.0.0.1:8081/upload/full")),
                any(HttpEntity.class), eq(String.class))).thenAnswer(invocation -> {
            HttpEntity<?> request = invocation.getArgument(1);
            @SuppressWarnings("unchecked")
            MultiValueMap<String, Object> form = (MultiValueMap<String, Object>) request.getBody();
            assertThat(form.getFirst("webkey")).isEqualTo("server-only-key");
            assertThat(form.getFirst("file")).isInstanceOf(HttpEntity.class);
            return ResponseEntity.ok("{\"code\":1,\"data\":{\"url\":\"https://frp.lcxqy.cn/upload/a.jpg\"}}");
        });
        BotImageUploadService service = new BotImageUploadService(
                rest, new ObjectMapper(), "http://127.0.0.1:8081", "server-only-key");

        List<String> urls = service.upload(Collections.singletonList(new MockMultipartFile(
                "images", "qq.jpg", "image/jpeg", "image-data".getBytes(StandardCharsets.UTF_8))));

        assertThat(urls).containsExactly("https://frp.lcxqy.cn/upload/a.jpg");
    }

    @Test
    void rejectsNonImageFilesBeforeCallingLegacyUpload() {
        BotImageUploadService service = new BotImageUploadService(
                mock(RestTemplate.class), new ObjectMapper(), "http://127.0.0.1:8081", "key");

        assertThatThrownBy(() -> service.upload(Collections.singletonList(new MockMultipartFile(
                "images", "note.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是图片");
    }
}
