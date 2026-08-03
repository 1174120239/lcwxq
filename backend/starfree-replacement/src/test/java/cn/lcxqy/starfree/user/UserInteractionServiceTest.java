package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.security.LegacyTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserInteractionServiceTest {

    @Test
    void duplicateFollowDoesNotInsertAnotherRelation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        when(tokens.userById(9L)).thenReturn(Collections.<String, Object>singletonMap("uid", 9L));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L), eq(9L))).thenReturn(1);

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("touid", "9");
        request.put("type", "1");

        int changed = new UserInteractionService(jdbc, tokens).follow(request);

        assertThat(changed).isZero();
        verify(jdbc, never()).update(anyString(), eq(7L), eq(9L));
    }

    @Test
    void followRejectsSelfBeforeDatabaseWrites() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        when(tokens.userById(7L)).thenReturn(Collections.<String, Object>singletonMap("uid", 7L));

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("touid", "7");
        request.put("type", "1");

        assertThatThrownBy(() -> new UserInteractionService(jdbc, tokens).follow(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You cannot follow yourself");
        verify(jdbc, never()).update(anyString(), eq(7L), eq(7L));
    }
}
