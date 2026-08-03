package cn.lcxqy.starfree.system;

import cn.lcxqy.starfree.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LivenessControllerTest {

    @Test
    void livenessDoesNotRequireDatabase() {
        ApiResponse response = new LivenessController().live();

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(((Map<?, ?>) response.getData()).get("status")).isEqualTo("UP");
    }
}
