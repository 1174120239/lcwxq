package cn.lcxqy.starfree.lostfound;

import cn.lcxqy.starfree.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LostFoundControllerCompatibilityTest {
    @Test
    void allRoutesAcceptGetAndPost() throws Exception {
        assertGetAndPost("config");
        assertGetAndPost("configManage");
        assertGetAndPost("configSave");
        assertGetAndPost("itemList");
        assertGetAndPost("itemInfo");
        assertGetAndPost("itemAdd");
        assertGetAndPost("itemEdit");
        assertGetAndPost("itemStatus");
        assertGetAndPost("itemDelete");
        assertGetAndPost("itemManage");
        assertGetAndPost("itemAudit");
        assertGetAndPost("commentList");
        assertGetAndPost("commentAdd");
        assertGetAndPost("commentDelete");
        assertGetAndPost("contactShare");
        assertGetAndPost("contactAccess");
    }

    @Test
    void listUsesStandardPagedEnvelope() {
        LostFoundService service = mock(LostFoundService.class);
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", 7L);
        when(service.itemList(Collections.<String, String>emptyMap()))
                .thenReturn(page(Collections.singletonList(item), 3));

        ApiResponse response = new LostFoundController(service, mock(LostFoundCommentService.class),
                mock(LostFoundConfigService.class), new ObjectMapper())
                .itemList(Collections.<String, String>emptyMap());

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getTotal()).isEqualTo(3);
    }

    @Test
    void addPassesJsonBodyAndTokenToService() {
        LostFoundService service = mock(LostFoundService.class);
        Map<String, Object> saved = new LinkedHashMap<String, Object>();
        saved.put("status", 0);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("title", "捡到校园卡");
        when(service.itemAdd("user-token", body)).thenReturn(saved);
        Map<String, String> params = new HashMap<String, String>();
        params.put("token", "user-token");
        params.put("params", "{\"title\":\"捡到校园卡\"}");

        ApiResponse response = new LostFoundController(service, mock(LostFoundCommentService.class),
                mock(LostFoundConfigService.class), new ObjectMapper()).itemAdd(params);

        assertThat(response.getMsg()).isEqualTo("信息已提交，等待审核");
        verify(service).itemAdd("user-token", body);
    }

    private LostFoundService.Page page(java.util.List<Map<String, Object>> data, int total) {
        try {
            java.lang.reflect.Constructor<LostFoundService.Page> constructor =
                    LostFoundService.Page.class.getDeclaredConstructor(java.util.List.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(data, total);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertGetAndPost(String method) throws Exception {
        RequestMapping mapping = LostFoundController.class
                .getMethod(method, Map.class).getAnnotation(RequestMapping.class);
        assertThat(Arrays.asList(mapping.method()))
                .containsExactlyInAnyOrder(RequestMethod.GET, RequestMethod.POST);
    }
}
