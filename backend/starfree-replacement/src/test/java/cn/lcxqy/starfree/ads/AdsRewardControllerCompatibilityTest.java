package cn.lcxqy.starfree.ads;

import cn.lcxqy.starfree.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdsRewardControllerCompatibilityTest {
    @Test
    void videoStartKeepsTheLegacySuccessEnvelope() {
        AdsRewardService service = mock(AdsRewardService.class);
        Map<String, String> request = Collections.singletonMap("token", "token");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("adpid", "ad-1");
        data.put("logid", 3L);
        when(service.start(request)).thenReturn(data);

        ApiResponse response = new AdsRewardController(service).start(request);

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEmpty();
        assertThat(response.getData()).isSameAs(data);
    }

    @Test
    void serverCallbackAlwaysUsesTheProviderResponseShape() {
        AdsRewardService service = mock(AdsRewardService.class);
        Map<String, String> request = Collections.singletonMap("trans_id", "tx-1");
        when(service.serverNotify(request)).thenReturn(true);

        Map<String, Object> response = new AdsRewardController(service).serverNotify(request);

        assertThat(response).containsOnlyKeys("isValid");
        assertThat(response.get("isValid")).isEqualTo(true);
    }

    @Test
    void serverCallbackConvertsInternalFailureToProviderRejection() {
        AdsRewardService service = mock(AdsRewardService.class);
        Map<String, String> request = Collections.singletonMap("trans_id", "tx-1");
        when(service.serverNotify(request)).thenThrow(new IllegalStateException("database"));

        Map<String, Object> response = new AdsRewardController(service).serverNotify(request);

        assertThat(response.get("isValid")).isEqualTo(false);
    }
}
