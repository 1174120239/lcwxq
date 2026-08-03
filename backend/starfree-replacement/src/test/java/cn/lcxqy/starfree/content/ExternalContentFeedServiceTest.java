package cn.lcxqy.starfree.content;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalContentFeedServiceTest {
    @Test
    void pexelsUsesFixedEncodedHostAndAuthorizationHeader() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        ExternalContentReadStore store = mock(ExternalContentReadStore.class);
        when(jdbc.queryForList("SELECT pexelsKey FROM starfree_apiconfig ORDER BY id LIMIT 1"))
                .thenReturn(Collections.singletonList(
                        Collections.<String, Object>singletonMap("pexelsKey", "secret-key")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("photos", Collections.emptyList());
        when(http.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(Object.class))).thenReturn(ResponseEntity.<Object>ok(body));
        ExternalContentFeedService service = new ExternalContentFeedService(jdbc, http, store);

        Object result = service.pexels(2, "green forest", "127.0.0.1", "test-agent");

        assertThat(result).isEqualTo(body);
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).exchange(uri.capture(), eq(HttpMethod.GET), entity.capture(), eq(Object.class));
        assertThat(uri.getValue().getHost()).isEqualTo("api.pexels.com");
        assertThat(uri.getValue().getPath()).isEqualTo("/v1/search");
        assertThat(uri.getValue().getRawQuery()).contains("query=green%20forest").contains("page=2");
        assertThat(entity.getValue().getHeaders().getFirst("Authorization")).isEqualTo("secret-key");
        verify(store).claimPexels("127.0.0.1|test-agent");
        verify(store).put("pexels", "2|green forest", body, 21600);
    }

    @Test
    void foreverBlogCacheHitDoesNotCallTheProvider() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RestTemplate http = mock(RestTemplate.class);
        ExternalContentReadStore store = mock(ExternalContentReadStore.class);
        Map<String, Object> cached = Collections.<String, Object>singletonMap("data", "cached");
        when(store.get("foreverblog", "3")).thenReturn(cached);

        Object result = new ExternalContentFeedService(jdbc, http, store).foreverBlog(3);

        assertThat(result).isEqualTo(cached);
        verify(http, never()).exchange(any(URI.class), any(HttpMethod.class),
                any(HttpEntity.class), eq(Object.class));
    }
}
