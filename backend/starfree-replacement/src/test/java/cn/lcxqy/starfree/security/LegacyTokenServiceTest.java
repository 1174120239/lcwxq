package cn.lcxqy.starfree.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyTokenServiceTest {
    @SuppressWarnings("unchecked")
    @Test
    void fallsBackToLegacyRedisWhenMysqlHasNoAuthCode() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacySessionBridge sessions = mock(LegacySessionBridge.class);
        when(jdbc.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        when(sessions.userId("legacy-token")).thenReturn(7L);

        Long uid = new LegacyTokenService(jdbc, sessions).userId("legacy-token");

        assertThat(uid).isEqualTo(7L);
    }
}