package cn.lcxqy.starfree.bot;

import cn.lcxqy.starfree.security.LegacySessionBridge;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.SessionTokenGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BotImageUploadService {
    private static final Logger LOG = LoggerFactory.getLogger(BotImageUploadService.class);
    private static final int MAX_IMAGES = 9;
    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final String legacyBaseUrl;
    private final LegacyTokenService tokens;
    private final LegacySessionBridge sessions;
    private final SessionTokenGenerator tokenGenerator;

    @Autowired
    public BotImageUploadService(RestTemplate restTemplate, ObjectMapper mapper,
                                 @Value("${legacy.api.base-url}") String legacyBaseUrl,
                                 LegacyTokenService tokens,
                                 LegacySessionBridge sessions,
                                 SessionTokenGenerator tokenGenerator) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.legacyBaseUrl = legacyBaseUrl.replaceAll("/$", "");
        this.tokens = tokens;
        this.sessions = sessions == null ? LegacySessionBridge.NOOP : sessions;
        this.tokenGenerator = tokenGenerator;
    }

    public List<String> upload(long uid, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        if (images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("动态图片最多 9 张");
        }
        if (!available()) {
            throw new IllegalStateException("论坛图片上传会话未启用");
        }
        Map<String, Object> user = tokens.userById(uid);
        if (user == null) {
            throw new IllegalArgumentException("绑定的论坛账号不存在");
        }
        String uploadToken = tokenGenerator.generate("qqbot-" + uid);
        Map<String, Object> session = new LinkedHashMap<>(user);
        session.put("uid", uid);
        session.put("token", uploadToken);
        sessions.storeDetached(uploadToken, session);
        try {
            List<String> urls = new ArrayList<>();
            for (int index = 0; index < images.size(); index++) {
                urls.add(uploadOne(images.get(index), index + 1, uploadToken));
            }
            return urls;
        } finally {
            try {
                sessions.remove(uploadToken);
            } catch (RuntimeException cleanupFailure) {
                LOG.warn("Could not remove temporary QQBot upload session; TTL will expire it",
                        cleanupFailure);
            }
        }
    }

    public boolean available() {
        return sessions.available();
    }

    private String uploadOne(MultipartFile image, int index, String uploadToken) {
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
        form.add("token", uploadToken);
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

}
