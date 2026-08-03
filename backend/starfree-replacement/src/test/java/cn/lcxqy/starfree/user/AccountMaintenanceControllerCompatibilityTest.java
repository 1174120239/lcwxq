package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountMaintenanceControllerCompatibilityTest {
    @Test
    void accountRoutesKeepLegacyGetAndPostCompatibility() throws Exception {
        assertGetAndPost("registrationConfig");
        assertGetAndPost("forgotPassword", Map.class);
        assertGetAndPost("edit", Map.class);
        assertGetAndPost("setClientId", Map.class);
    }

    @Test
    void configAndEditKeepLegacyResponseEnvelopes() {
        AccountMaintenanceService maintenance = mock(AccountMaintenanceService.class);
        UserController controller = controller(maintenance);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("isEmail", 1);
        config.put("isInvite", 0);
        config.put("isPhone", 0);
        when(maintenance.registrationConfig()).thenReturn(config);
        when(maintenance.edit("token", Collections.<String, Object>singletonMap("uid", 7)))
                .thenReturn(new AccountMaintenanceService.EditResult(
                        1, "\u64cd\u4f5c\u6210\u529f", false, "alice"));

        ApiResponse configResponse = controller.registrationConfig();
        ApiResponse editResponse = controller.edit(row(
                "token", "token", "params", "{\"uid\":7}"));

        assertThat(configResponse.getCode()).isEqualTo(1);
        assertThat(configResponse.getMsg()).isEmpty();
        assertThat(configResponse.getData()).isEqualTo(config);
        assertThat(editResponse.getCode()).isEqualTo(1);
        assertThat(editResponse.getData()).isEqualTo(1);
    }

    private void assertGetAndPost(String method, Class<?>... parameterTypes) throws Exception {
        RequestMapping mapping = UserController.class.getMethod(method, parameterTypes)
                .getAnnotation(RequestMapping.class);
        assertThat(mapping.method()).containsExactlyInAnyOrder(
                RequestMethod.GET, RequestMethod.POST);
    }

    private UserController controller(AccountMaintenanceService maintenance) {
        return new UserController(
                mock(LegacyTokenService.class),
                mock(JdbcTemplate.class),
                mock(UserAuthenticationService.class),
                mock(UserRegistrationService.class),
                maintenance,
                mock(UserInteractionService.class),
                new ObjectMapper());
    }

    private Map<String, String> row(String... values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(values[index], values[index + 1]);
        }
        return row;
    }
}
