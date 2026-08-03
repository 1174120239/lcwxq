package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserRegistrationControllerCompatibilityTest {
    @Test
    void registrationKeepsLegacyEnvelopeAndUsesForwardedClientAddress() {
        UserRegistrationService registration = mock(UserRegistrationService.class);
        UserController controller = new UserController(
                mock(LegacyTokenService.class),
                mock(JdbcTemplate.class),
                mock(UserAuthenticationService.class),
                registration,
                mock(AccountMaintenanceService.class),
                mock(UserInteractionService.class),
                new ObjectMapper());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.7");
        Map<String, String> params = Collections.singletonMap(
                "params", "{\"name\":\"alice\",\"password\":\"secret\"}");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", 1);
        when(registration.register(any(), eq("203.0.113.7"))).thenReturn(result);

        ApiResponse response = controller.register(params, request);

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEqualTo("\u6ce8\u518c\u6210\u529f");
        assertThat(response.getData()).isEqualTo(1);
    }

    @Test
    void registrationKeepsLegacyGetAndPostCompatibility() throws Exception {
        RequestMapping mapping = UserController.class
                .getMethod("register", Map.class, HttpServletRequest.class)
                .getAnnotation(RequestMapping.class);

        assertThat(mapping.method()).containsExactlyInAnyOrder(
                RequestMethod.GET, RequestMethod.POST);
    }

    @Test
    void internalFailureStillReturnsTheLegacyJsonEnvelope() {
        UserRegistrationService registration = mock(UserRegistrationService.class);
        UserController controller = new UserController(
                mock(LegacyTokenService.class),
                mock(JdbcTemplate.class),
                mock(UserAuthenticationService.class),
                registration,
                mock(AccountMaintenanceService.class),
                mock(UserInteractionService.class),
                new ObjectMapper());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(registration.register(any(), eq("127.0.0.1")))
                .thenThrow(new IllegalStateException("database unavailable"));

        ApiResponse response = controller.register(
                Collections.singletonMap("params", "{\"name\":\"alice\"}"), request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getMsg()).contains("\u8054\u7cfb\u7ba1\u7406\u5458");
    }
}
