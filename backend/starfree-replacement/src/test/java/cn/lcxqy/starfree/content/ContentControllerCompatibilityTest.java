package cn.lcxqy.starfree.content;

import cn.lcxqy.starfree.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContentControllerCompatibilityTest {
    @Test
    void listUsesLegacyPagedEnvelope() {
        ContentService service = mock(ContentService.class);
        Map<String, Object> item = new HashMap<>();
        item.put("cid", 1);
        when(service.page("{}", 5, 2, "created", "hello", 0, ""))
                .thenReturn(new ContentService.ContentPage(Collections.singletonList(item), 7));
        Map<String, String> params = new HashMap<>();
        params.put("searchParams", "{}");
        params.put("limit", "5");
        params.put("page", "2");
        params.put("order", "created");
        params.put("searchKey", "hello");

        ApiResponse response = new ContentController(service, mock(LegacyContentReadTracker.class)).list(params);

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEmpty();
        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getTotal()).isEqualTo(7);
    }

    @Test
    void infoReturnsTheLegacyRawObjectAndCountsAfterBuildingIt() {
        ContentService service = mock(ContentService.class);
        LegacyContentReadTracker reads = mock(LegacyContentReadTracker.class);
        Map<String, Object> content = new HashMap<>();
        content.put("cid", 9L);
        content.put("title", "detail");
        content.put("views", 0L);
        when(service.detail(9, 0, "")).thenReturn(content);
        when(reads.firstRead(9, "203.0.113.7", "compat-test")).thenReturn(true);

        Map<String, String> params = new HashMap<>();
        params.put("key", "9");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.7");
        request.addHeader("User-Agent", "compat-test");

        Object response = new ContentController(service, reads).info(params, request);

        assertThat(response).isSameAs(content);
        verify(service).incrementViews(9);
        assertThat(content.get("views")).isEqualTo(1L);
    }

    @Test
    void missingInfoKeepsTheLegacyFailureEnvelopeWithoutCreatingAReadKey() {
        ContentService service = mock(ContentService.class);
        LegacyContentReadTracker reads = mock(LegacyContentReadTracker.class);
        when(service.detail(404, 0, "")).thenReturn(null);
        Map<String, String> params = new HashMap<>();
        params.put("key", "404");

        Object response = new ContentController(service, reads)
                .info(params, new MockHttpServletRequest());

        assertThat(response).isInstanceOf(ApiResponse.class);
        assertThat(((ApiResponse) response).getCode()).isZero();
        verifyNoInteractions(reads);
    }

    @Test
    void addKeepsTheLegacyIntegerDataPayload() {
        ContentService service = mock(ContentService.class);
        LegacyContentReadTracker reads = mock(LegacyContentReadTracker.class);
        Map<String, String> params = new HashMap<>();
        params.put("token", "token");
        params.put("params", "{\"title\":\"ordinary\",\"sid\":-1}");
        Map<String, Object> created = new HashMap<>();
        created.put("cid", 12L);
        created.put("status", "waiting");
        when(service.add(params, "203.0.113.8")).thenReturn(created);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.8");

        Object result = new ContentController(service, reads).add(params, request);
        assertThat(result).isInstanceOf(ApiResponse.class);
        ApiResponse response = (ApiResponse) result;

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEqualTo("发布成功");
        assertThat(response.getData()).isEqualTo(1);
    }

    @Test
    void paidContentIsDelegatedWithoutCallingTheReplacementWriter() {
        ContentService service = mock(ContentService.class);
        LegacyContentReadTracker reads = mock(LegacyContentReadTracker.class);
        ContentAddRoutingPolicy routing = mock(ContentAddRoutingPolicy.class);
        LegacyContentWriteForwarder legacy = mock(LegacyContentWriteForwarder.class);
        Map<String, String> params = new HashMap<>();
        params.put("token", "token");
        params.put("params", "{\"title\":\"paid\"}");
        params.put("isPaid", "1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        ResponseEntity<byte[]> delegated = new ResponseEntity<byte[]>(
                "{\"code\":1}".getBytes(), HttpStatus.OK);
        when(routing.useReplacement(params)).thenReturn(false);
        when(legacy.forwardAdd(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.same(request))).thenReturn(delegated);

        Object response = new ContentController(service, reads, routing, legacy).add(params, request);

        assertThat(response).isSameAs(delegated);
        verifyNoInteractions(service);
    }

    @Test
    void updateKeepsTheLegacyIntegerDataPayload() {
        ContentService service = mock(ContentService.class);
        LegacyContentReadTracker reads = mock(LegacyContentReadTracker.class);
        Map<String, String> params = new HashMap<>();
        params.put("token", "token");
        params.put("params", "{\"cid\":12,\"title\":\"updated\",\"sid\":-1}");
        when(service.isOrdinaryUpdate(params)).thenReturn(true);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.setAll(params);

        Object result = new ContentController(service, reads)
                .update(form, new MockHttpServletRequest());

        assertThat(result).isInstanceOf(ApiResponse.class);
        ApiResponse response = (ApiResponse) result;
        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEqualTo("修改成功");
        assertThat(response.getData()).isEqualTo(1);
        verify(service).updateOrdinary(params);
    }
}
