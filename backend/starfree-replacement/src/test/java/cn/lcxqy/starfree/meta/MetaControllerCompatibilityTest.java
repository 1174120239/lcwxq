package cn.lcxqy.starfree.meta;

import cn.lcxqy.starfree.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaControllerCompatibilityTest {
    @Test
    void managementRoutesKeepLegacyGetAndPostCompatibility() throws Exception {
        assertGetAndPost("add", Map.class);
        assertGetAndPost("edit", Map.class);
        assertGetAndPost("delete", Map.class);
        assertGetAndPost("recommend", Map.class);
    }

    @Test
    void listUsesLegacyPagedEnvelope() {
        MetaService service = mock(MetaService.class);
        Map<String, Object> item = new HashMap<>();
        item.put("mid", 1);
        when(service.page("{}", 5, 2, "order", "hello"))
                .thenReturn(new MetaService.MetaPage(Collections.singletonList(item), 3));
        Map<String, String> params = new HashMap<>();
        params.put("searchParams", "{}");
        params.put("limit", "5");
        params.put("page", "2");
        params.put("order", "order");
        params.put("searchKey", "hello");

        ApiResponse response = new MetaController(service, new ObjectMapper()).list(params);

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEmpty();
        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getTotal()).isNull();
    }

    @Test
    void addKeepsLegacySuccessCodeAndAffectedRows() {
        MetaService service = mock(MetaService.class);
        when(service.add("token", Collections.<String, Object>singletonMap("name", "Java")))
                .thenReturn(1);
        Map<String, String> params = new HashMap<>();
        params.put("token", "token");
        params.put("params", "{\"name\":\"Java\"}");

        ApiResponse response = new MetaController(service, new ObjectMapper()).add(params);

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEqualTo("操作成功");
        assertThat(response.getData()).isEqualTo(1);
    }

    private void assertGetAndPost(String method, Class<?>... parameterTypes) throws Exception {
        RequestMapping mapping = MetaController.class.getMethod(method, parameterTypes)
                .getAnnotation(RequestMapping.class);
        assertThat(mapping.method()).containsExactlyInAnyOrder(
                RequestMethod.GET, RequestMethod.POST);
    }
}
