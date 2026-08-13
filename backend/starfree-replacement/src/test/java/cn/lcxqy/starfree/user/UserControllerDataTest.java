package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerDataTest {

    @Test
    @SuppressWarnings("unchecked")
    void crossAccountUserInfoUsesOnlyThePublicProfile() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        Map<String, Object> publicProfile = new HashMap<>();
        publicProfile.put("uid", 2L);
        publicProfile.put("screenName", "staff");
        when(tokens.userId("token")).thenReturn(7L);
        when(tokens.publicUserById(2L)).thenReturn(publicProfile);

        UserController controller = new UserController(
                tokens, jdbc, mock(UserAuthenticationService.class),
                mock(UserRegistrationService.class), mock(AccountMaintenanceService.class),
                mock(UserInteractionService.class), new ObjectMapper());
        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("uid", "2");

        ApiResponse response = controller.info(request);

        assertThat((Map<String, Object>) response.getData())
                .containsEntry("uid", 2L)
                .doesNotContainKeys("mail", "assets", "logged", "ip", "clientId");
        verify(tokens).publicUserById(2L);
        verify(tokens, never()).userById(2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownInteractionCountIncludesPendingDynamicComments() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("token")).thenReturn(7L);
        when(tokens.userById(7L)).thenReturn(Collections.<String, Object>singletonMap("uid", 7L));
        when(jdbc.queryForObject(contains("starfree_contents"), eq(Integer.class), eq(7L)))
                .thenReturn(2);
        when(jdbc.queryForObject(eq(
                "SELECT COUNT(*) FROM starfree_space WHERE uid = ? AND type = 3"),
                eq(Integer.class), eq(7L))).thenReturn(3);
        when(jdbc.queryForObject(contains("starfree_fan WHERE touid"), eq(Integer.class), eq(7L)))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("starfree_fan WHERE uid"), eq(Integer.class), eq(7L)))
                .thenReturn(0);

        UserController controller = new UserController(
                tokens, jdbc, mock(UserAuthenticationService.class),
                mock(UserRegistrationService.class), mock(AccountMaintenanceService.class),
                mock(UserInteractionService.class), new ObjectMapper());
        Map<String, String> request = new HashMap<>();
        request.put("token", "token");

        ApiResponse response = controller.data(request);

        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data).containsEntry("commentsNum", 3).containsEntry("comments", 3);
        verify(jdbc).queryForObject(
                eq("SELECT COUNT(*) FROM starfree_space WHERE uid = ? AND type = 3"),
                eq(Integer.class), eq(7L));
    }
}
