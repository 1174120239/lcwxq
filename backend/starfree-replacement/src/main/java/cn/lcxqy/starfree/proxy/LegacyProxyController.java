package cn.lcxqy.starfree.proxy;

import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * 未重建接口的最后兜底代理。
 *
 * <p>由于控制器优先级最低，只有没有被本项目其他控制器匹配的路径才会进入这里。
 * 请求方法、查询串、请求体和除 Host/Content-Length 外的请求头会原样转发到
 * {@code legacy.api.base-url}。这使验证码、支付、上传、聊天和未迁移的管理接口可以
 * 继续使用旧 Java 后端，但也意味着“本地返回成功”不等于该接口已经完成重建。
 *
 * <p>官方支付创建与回调路径会额外取得全局经济锁，使旧后端对 MyISAM 钱包表的写入
 * 不会与新后端的积分/资产写入并发。不要随意删除 {@code LEGACY_ECONOMY_PATHS} 中的路径。
 */
@Controller
@Order(Ordered.LOWEST_PRECEDENCE)
public class LegacyProxyController {
    private static final Set<String> LEGACY_ECONOMY_PATHS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "/pay/scancodePayStar",
                    "/pay/WxPayStar",
                    "/pay/tokenPay",
                    "/pay/tokenPayStar",
                    "/pay/EPayStar",
                    "/pay/qrCodeStar",
                    "/pay/notify",
                    "/pay/wxPayNotify",
                    "/pay/EPayNotify")));

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final EconomyLockExecutor economyLock;

    public LegacyProxyController(RestTemplate restTemplate,
                                 @Value("${legacy.api.base-url}") String baseUrl,
                                 EconomyLockExecutor economyLock) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.economyLock = economyLock;
    }

    /**
     * ANY {@code /**}：转发所有未命中的请求；OPTIONS 直接返回 204。
     *
     * <p>参数和鉴权规则完全由旧后端决定，本方法不解析业务参数、不改写旧响应包络。
     * 它只复制旧响应的 HTTP 状态、Content-Type 和响应体，其他响应头不会透传。
     * 生产环境的 {@code legacy.api.base-url} 必须指向环回地址，不能再指向公网域名，
     * 否则同域 Nginx 配置错误时可能形成代理回环。
     */
    @RequestMapping("/**")
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (requiresEconomyLock(request.getRequestURI())) {
            try {
                economyLock.execute(connection -> {
                    try {
                        forward(request, response);
                    } catch (IOException error) {
                        throw new UncheckedIOException(error);
                    }
                    return null;
                });
            } catch (UncheckedIOException error) {
                throw error.getCause();
            }
            return;
        }
        forward(request, response);
    }

    private void forward(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpMethod method = HttpMethod.resolve(request.getMethod());
        if (method == null) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        String query = request.getQueryString();
        String target = baseUrl + request.getRequestURI() + (query == null ? "" : "?" + query);
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            for (String name : Collections.list(names)) {
                if (!"host".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                    headers.put(name, Collections.list(request.getHeaders(name)));
                }
            }
        }
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        ResponseEntity<byte[]> result = restTemplate.exchange(URI.create(target), method,
                new HttpEntity<>(body, headers), byte[].class);
        response.setStatus(result.getStatusCodeValue());
        MediaType contentType = result.getHeaders().getContentType();
        if (contentType != null) {
            response.setContentType(contentType.toString());
        }
        byte[] resultBody = result.getBody();
        if (resultBody != null) {
            response.getOutputStream().write(resultBody);
        }
    }

    static boolean requiresEconomyLock(String path) {
        return path != null && LEGACY_ECONOMY_PATHS.contains(path);
    }
}
