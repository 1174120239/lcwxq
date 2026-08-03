package cn.lcxqy.starfree.ads;

import cn.lcxqy.starfree.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdsControllerCompatibilityTest {
    @Test
    void configKeepsLegacyEmptyMessage() {
        AdsService service = mock(AdsService.class);
        Map<String, Object> config = new HashMap<>();
        config.put("pushAdsPrice", 100);
        when(service.config()).thenReturn(config);

        ApiResponse response = new AdsController(service).config();

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEmpty();
        assertThat(response.getData()).isEqualTo(config);
    }

    @Test
    void listUsesLegacyPagedEnvelope() {
        AdsService service = mock(AdsService.class);
        Map<String, Object> item = new HashMap<>();
        item.put("aid", 1);
        when(service.page("{}", 8, 2, "banner", "token"))
                .thenReturn(new AdsService.AdsPage(Collections.singletonList(item), 5));

        Map<String, String> params = new HashMap<>();
        params.put("searchParams", "{}");
        params.put("limit", "8");
        params.put("page", "2");
        params.put("searchKey", "banner");
        params.put("token", "token");

        ApiResponse response = new AdsController(service).list(params);

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEmpty();
        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getTotal()).isEqualTo(5);
    }

    @Test
    void infoReturnsRawAdObjectForLegacyFrontend() {
        AdsService service = mock(AdsService.class);
        Map<String, Object> ad = new HashMap<>();
        ad.put("name", "ad");
        when(service.info(3, "token")).thenReturn(ad);

        Map<String, String> params = new HashMap<>();
        params.put("id", "3");
        params.put("token", "token");

        assertThat(new AdsController(service).info(params)).isSameAs(ad);
    }
}
