package cn.lcxqy.starfree.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyTokenServiceTest {
    private static final String TOKEN = "sf2_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @SuppressWarnings("unchecked")
    @Test
    void enabledRedisIsTheAuthoritativeSessionStore() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacySessionBridge sessions = mock(LegacySessionBridge.class);
        when(sessions.available()).thenReturn(true);
        when(sessions.userId(TOKEN)).thenReturn(7L);

        Long uid = new LegacyTokenService(jdbc, sessions).userId(TOKEN);

        assertThat(uid).isEqualTo(7L);
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    @Test
    void expiredRedisSessionCannotBeRevivedByMysqlAuthCode() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacySessionBridge sessions = mock(LegacySessionBridge.class);
        when(sessions.available()).thenReturn(true);
        when(sessions.userId(TOKEN)).thenReturn(null);

        Long uid = new LegacyTokenService(jdbc, sessions).userId(TOKEN);

        assertThat(uid).isNull();
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    @Test
    void legacyTokenFormatIsRejectedBeforeAnySessionLookup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacySessionBridge sessions = mock(LegacySessionBridge.class);

        Long uid = new LegacyTokenService(jdbc, sessions).userId("alice0123456789abcdef");

        assertThat(uid).isNull();
        org.mockito.Mockito.verifyNoInteractions(jdbc, sessions);
    }

    @Test
    void publicProjectionExcludesCredentialsContactEconomyAndNetworkFields() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", 7L);
        row.put("name", "alice");
        row.put("screenName", "Alice");
        row.put("group", "administrator");
        row.put("mail", "alice@example.com");
        row.put("assets", 99);
        row.put("logged", 1800000000L);
        row.put("ip", "203.0.113.9");
        row.put("clientId", "push-secret");
        when(jdbc.queryForList(anyString(), eq(7L)))
                .thenReturn(Collections.singletonList(row));

        Map<String, Object> result = new LegacyTokenService(jdbc, LegacySessionBridge.NOOP)
                .publicUserById(7L);

        assertThat(result).containsEntry("uid", 7L).containsEntry("name", "alice")
                .containsEntry("screenName", "Alice")
                .doesNotContainKeys("mail", "assets", "logged", "ip", "clientId", "group");
    }
}
