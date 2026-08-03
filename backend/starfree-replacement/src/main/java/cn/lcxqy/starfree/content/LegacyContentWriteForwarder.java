package cn.lcxqy.starfree.content;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;

/**
 * 将受控混合路由中的封闭内容写入原样转发给旧 API。
 *
 * <p>MultiValueMap 用于保留重复表单字段，不能先转成 JSON 再重构。响应 body 和 HTTP 状态保持
 * 旧值，只增加 {@code X-Starfree-Delegate} 供审计。生产 baseUrl 必须是环回 8081，防止经公网
 * Nginx 再次进入 contentsAdd/Update 形成代理循环。
 */
@Service
public class LegacyContentWriteForwarder {
    static final String DELEGATE_HEADER = "X-Starfree-Delegate";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public LegacyContentWriteForwarder(RestTemplate restTemplate,
                                       @Value("${legacy.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    /** POST 原始表单到旧 `/SFreeContents/contentsAdd`。 */
    public ResponseEntity<byte[]> forwardAdd(MultiValueMap<String, String> form,
                                             HttpServletRequest request) {
        return forward("/SFreeContents/contentsAdd", "legacy-contents-add", form, request);
    }

    /** POST 原始表单到旧 `/SFreeContents/contentsUpdate`。 */
    public ResponseEntity<byte[]> forwardUpdate(MultiValueMap<String, String> form,
                                                HttpServletRequest request) {
        return forward("/SFreeContents/contentsUpdate", "legacy-contents-update", form, request);
    }

    private ResponseEntity<byte[]> forward(String path, String delegate,
                                           MultiValueMap<String, String> form,
                                           HttpServletRequest request) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        copyRequestHeader(request, requestHeaders, HttpHeaders.ACCEPT);
        copyRequestHeader(request, requestHeaders, HttpHeaders.USER_AGENT);
        copyRequestHeader(request, requestHeaders, "X-Real-IP");
        copyRequestHeader(request, requestHeaders, "X-Forwarded-For");

        ResponseEntity<byte[]> legacy = restTemplate.exchange(URI.create(baseUrl + path),
                HttpMethod.POST, new HttpEntity<MultiValueMap<String, String>>(form, requestHeaders),
                byte[].class);

        HttpHeaders responseHeaders = new HttpHeaders();
        MediaType contentType = legacy.getHeaders().getContentType();
        responseHeaders.setContentType(contentType == null ? MediaType.APPLICATION_JSON : contentType);
        responseHeaders.set(DELEGATE_HEADER, delegate);
        return new ResponseEntity<byte[]>(legacy.getBody(), responseHeaders, legacy.getStatusCode());
    }

    private void copyRequestHeader(HttpServletRequest request, HttpHeaders targetHeaders, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.trim().isEmpty()) {
            targetHeaders.put(name, Arrays.asList(value));
        }
    }
}
