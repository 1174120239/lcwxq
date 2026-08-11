package cn.lcxqy.starfree.bot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class BotImageUploadService {
    private static final int MAX_IMAGES = 9;
    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final String legacyBaseUrl;
    private final String webKey;

    public BotImageUploadService(RestTemplate restTemplate, ObjectMapper mapper,
                                 @Value("${legacy.api.base-url}") String legacyBaseUrl,
                                 @Value("${webinfo.key:}") String webKey) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.legacyBaseUrl = legacyBaseUrl.replaceAll("/$", "");
        this.webKey = resolveWebKey(webKey, legacyPropertiesPath());
    }

    public List<String> upload(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        if (images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("动态图片最多 9 张");
        }
        if (webKey.isEmpty()) {
            throw new IllegalStateException("论坛图片上传服务未配置");
        }
        List<String> urls = new ArrayList<>();
        for (int index = 0; index < images.size(); index++) {
            urls.add(uploadOne(images.get(index), index + 1));
        }
        return urls;
    }

    private String uploadOne(MultipartFile image, int index) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("第 " + index + " 张图片为空");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("第 " + index + " 张图片不能超过 8 MB");
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("第 " + index + " 个文件不是图片");
        }
        final byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException error) {
            throw new IllegalArgumentException("第 " + index + " 张图片读取失败", error);
        }
        String filename = safeFilename(image.getOriginalFilename(), index, contentType);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("webkey", webKey);
        form.add("file", new HttpEntity<ByteArrayResource>(resource, fileHeaders));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> response = restTemplate.postForEntity(
                URI.create(legacyBaseUrl + "/upload/full"),
                new HttpEntity<MultiValueMap<String, Object>>(form, headers), String.class);
        try {
            Map<String, Object> parsed = mapper.readValue(response.getBody() == null ? "" : response.getBody(),
                    new TypeReference<Map<String, Object>>() {});
            if (number(parsed.get("code")) != 1 || !(parsed.get("data") instanceof Map)) {
                throw new IllegalStateException(message(parsed, "论坛图片上传失败"));
            }
            Object url = ((Map<?, ?>) parsed.get("data")).get("url");
            if (url == null || String.valueOf(url).trim().isEmpty()) {
                throw new IllegalStateException("论坛图片上传结果缺少 URL");
            }
            return String.valueOf(url).trim();
        } catch (IOException error) {
            throw new IllegalStateException("论坛图片上传响应格式不正确", error);
        }
    }

    private String safeFilename(String original, int index, String contentType) {
        String value = original == null ? "" : original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!value.isEmpty() && value.contains(".")) {
            return value;
        }
        String subtype = contentType.substring("image/".length()).toLowerCase();
        if (subtype.contains("jpeg")) {
            subtype = "jpg";
        } else if (!subtype.matches("[a-z0-9]+")) {
            subtype = "png";
        }
        return "qqbot-" + index + "." + subtype;
    }

    private int number(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private String message(Map<String, Object> parsed, String fallback) {
        Object value = parsed.get("msg");
        return value == null || String.valueOf(value).trim().isEmpty()
                ? fallback : String.valueOf(value).trim();
    }

    static String resolveWebKey(String configured, Path legacyProperties) {
        String value = configured == null ? "" : configured.trim();
        if (!value.isEmpty() || legacyProperties == null || !Files.isRegularFile(legacyProperties)) {
            return value;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(legacyProperties, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties.getProperty("webinfo.key", "").trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    private static Path legacyPropertiesPath() {
        String configured = System.getenv("LEGACY_PROPERTIES_PATH");
        return Paths.get(configured == null || configured.trim().isEmpty()
                ? "/opt/application.properties" : configured.trim());
    }
}
