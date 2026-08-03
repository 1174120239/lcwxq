package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.system.VipPackageController;
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
import static org.mockito.Mockito.when;

/** Compatibility checks for endpoint methods and legacy response shapes, not database behavior. */
class ShopCatalogControllerCompatibilityTest {
    @Test
    void allCatalogRoutesAcceptLegacyGetAndPostForms() throws Exception {
        assertGetAndPost("list", Map.class);
        assertGetAndPost("info", Map.class);
        assertGetAndPost("add", Map.class);
        assertGetAndPost("edit", Map.class);
        assertGetAndPost("delete", Map.class);
        assertGetAndPost("audit", Map.class);
        assertGetAndPost("mount", Map.class);

        RequestMapping vip = VipPackageController.class.getMethod("list")
                .getAnnotation(RequestMapping.class);
        assertThat(vip.method()).containsExactlyInAnyOrder(RequestMethod.GET, RequestMethod.POST);
    }

    @Test
    void listKeepsLegacyPagedEnvelope() {
        ShopCatalogService service = mock(ShopCatalogService.class);
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("id", 8);
        when(service.page(Collections.<String, Object>emptyMap(), "", "", 1, 15, ""))
                .thenReturn(new ShopCatalogService.ShopPage(Collections.singletonList(product), 3));

        ApiResponse response = new ShopCatalogController(service, new ObjectMapper())
                .list(new HashMap<String, String>());

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEmpty();
        assertThat(response.getData()).isEqualTo(Collections.singletonList(product));
        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getTotal()).isEqualTo(3);
    }

    @Test
    void shopInfoRemainsANakedObject() {
        ShopCatalogService service = mock(ShopCatalogService.class);
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("id", 8);
        product.put("title", "Source");
        when(service.info(8, "buyer-token")).thenReturn(product);
        Map<String, String> params = new HashMap<>();
        params.put("key", "8");
        params.put("token", "buyer-token");

        assertThat(new ShopCatalogController(service, new ObjectMapper()).info(params))
                .isSameAs(product)
                .doesNotContainKey("code");
    }

    @Test
    void vipListUsesTopLevelVipArrayInsteadOfData() {
        ShopCatalogService service = mock(ShopCatalogService.class);
        Map<String, Object> packageRow = new LinkedHashMap<>();
        packageRow.put("id", 1);
        when(service.vipPackages()).thenReturn(Arrays.asList(packageRow));

        Map<String, Object> response = new VipPackageController(service).list();

        assertThat(response).containsEntry("code", 1).containsEntry("msg", "")
                .containsEntry("vip", Arrays.asList(packageRow)).containsEntry("count", 1)
                .doesNotContainKey("data");
    }

    private void assertGetAndPost(String method, Class<?>... parameterTypes) throws Exception {
        RequestMapping mapping = ShopCatalogController.class.getMethod(method, parameterTypes)
                .getAnnotation(RequestMapping.class);
        assertThat(mapping.method()).containsExactlyInAnyOrder(RequestMethod.GET, RequestMethod.POST);
    }
}
