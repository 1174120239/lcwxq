package cn.lcxqy.starfree.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void invalidBusinessRequestUsesLegacyFailureEnvelope() {
        ApiResponse response = new ApiExceptionHandler()
                .handleInvalidRequest(new IllegalArgumentException("invalid token"));

        assertThat(response.getCode()).isZero();
        assertThat(response.getMsg()).isEqualTo("invalid token");
    }
}
