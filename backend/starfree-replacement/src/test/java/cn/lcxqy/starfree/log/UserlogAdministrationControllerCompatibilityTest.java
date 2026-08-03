package cn.lcxqy.starfree.log;

import cn.lcxqy.starfree.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserlogAdministrationControllerCompatibilityTest {
    @Test
    void allThreeRoutesKeepLegacyGetAndPostCompatibility() throws Exception {
        assertGetAndPost("buyerOrders", Map.class);
        assertGetAndPost("sellerOrders", Map.class);
        assertGetAndPost("clean", Map.class);
    }

    @Test
    void buyerOrdersKeepPagedEnvelope() {
        UserlogAdministrationService service = mock(UserlogAdministrationService.class);
        Map<String, Object> order = Collections.<String, Object>singletonMap("id", 7);
        when(service.buyerOrders("token")).thenReturn(
                new UserlogAdministrationService.Page(Collections.singletonList(order), 12));

        ApiResponse result = new UserlogAdministrationController(service)
                .buyerOrders(map("token", "token"));

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getCount()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualTo(12);
        assertThat(result.getData()).isEqualTo(Collections.singletonList(order));
    }

    @Test
    void cleanupReturnsAffectedCountForOperatorReview() {
        UserlogAdministrationService service = mock(UserlogAdministrationService.class);
        when(service.clean("token", 4)).thenReturn(17);

        ApiResponse result = new UserlogAdministrationController(service)
                .clean(map("token", "token", "clean", "4"));

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getMsg()).isEqualTo("清理成功");
        assertThat(result.getData()).isEqualTo(17);
    }

    private void assertGetAndPost(String method, Class<?>... parameterTypes) throws Exception {
        RequestMapping mapping = UserlogAdministrationController.class
                .getMethod(method, parameterTypes).getAnnotation(RequestMapping.class);
        assertThat(mapping.method()).containsExactlyInAnyOrder(
                RequestMethod.GET, RequestMethod.POST);
    }

    private Map<String, String> map(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }
}
