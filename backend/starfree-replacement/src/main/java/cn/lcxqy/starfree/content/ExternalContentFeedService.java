package cn.lcxqy.starfree.content;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Read-only adapter for the two fixed third-party feeds used by the existing frontend.
 *
 * <p>Hosts and paths are constants, so caller input cannot turn this class into an SSRF proxy.
 * Pexels authorization is read from the first apiconfig row and sent only in the Authorization
 * header. Responses are returned unchanged because both frontend pages consume provider-native JSON.
 */
@Service
public class ExternalContentFeedService {
    private static final String PEXELS_HOST = "https://api.pexels.com";
    private static final String FOREVER_BLOG_HOST = "https://www.foreverblog.cn";

    private final JdbcTemplate jdbc;
    private final RestTemplate http;
    private final ExternalContentReadStore store;

    public ExternalContentFeedService(
            JdbcTemplate jdbc,
            @Qualifier("externalReadRestTemplate") RestTemplate http,
            ExternalContentReadStore store) {
        this.jdbc = jdbc;
        this.http = http;
        this.store = store;
    }

    /**
     * Fetches a Pexels curated/search page with shared rate limiting and a six-hour response cache.
     * page is bounded to 1..1000 and searchKey to 100 characters before URI encoding.
     */
    public Object pexels(int requestedPage, String requestedSearch,
                         String clientAddress, String userAgent) {
        int page = Math.max(1, Math.min(1000, requestedPage));
        String search = requestedSearch == null ? "" : requestedSearch.trim();
        if (search.length() > 100) {
            throw new IllegalArgumentException("搜索内容过长");
        }
        store.claimPexels(safe(clientAddress, 255) + "|" + safe(userAgent, 500));
        String material = page + "|" + search;
        Object cached = store.get("pexels", material);
        if (cached != null) {
            return cached;
        }

        String key = pexelsKey();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("图片接口未配置");
        }
        UriComponentsBuilder builder;
        if (search.isEmpty()) {
            builder = UriComponentsBuilder.fromHttpUrl(PEXELS_HOST + "/v1/curated")
                    .queryParam("per_page", 15).queryParam("page", page);
        } else {
            builder = UriComponentsBuilder.fromHttpUrl(PEXELS_HOST + "/v1/search")
                    .queryParam("query", search).queryParam("per_page", 15)
                    .queryParam("page", page);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", key);
        Object response = exchange(builder.build().encode().toUri(), headers, "图片接口异常");
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            if (map.containsKey("code") || map.containsKey("error")) {
                throw new IllegalArgumentException("图片获取失败，请重试");
            }
        }
        store.put("pexels", material, response, 21600);
        return response;
    }

    /** Fetches one Forever Blog feed page with a two-minute shared cache. */
    public Object foreverBlog(int requestedPage) {
        int page = Math.max(1, Math.min(1000, requestedPage));
        String material = String.valueOf(page);
        Object cached = store.get("foreverblog", material);
        if (cached != null) {
            return cached;
        }
        URI uri = UriComponentsBuilder.fromHttpUrl(
                        FOREVER_BLOG_HOST + "/api/v1/blog/feeds")
                .queryParam("page", page).build().encode().toUri();
        Object response = exchange(uri, new HttpHeaders(), "接口异常");
        store.put("foreverblog", material, response, 120);
        return response;
    }

    private Object exchange(URI uri, HttpHeaders headers, String message) {
        try {
            ResponseEntity<Object> result = http.exchange(
                    uri, HttpMethod.GET, new HttpEntity<Void>(headers), Object.class);
            if (!result.getStatusCode().is2xxSuccessful() || result.getBody() == null) {
                throw new IllegalArgumentException(message);
            }
            return result.getBody();
        } catch (RestClientException error) {
            throw new IllegalArgumentException(message);
        }
    }

    private String pexelsKey() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT pexelsKey FROM starfree_apiconfig ORDER BY id LIMIT 1");
        if (rows.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, Object> entry : rows.get(0).entrySet()) {
            if (entry.getKey().equalsIgnoreCase("pexelsKey")) {
                return entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            }
        }
        return "";
    }

    private String safe(String value, int maximum) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }
}
