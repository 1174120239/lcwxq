package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.security.LegacySessionBridge;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.PhpassPasswordVerifier;
import cn.lcxqy.starfree.security.SessionTokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAuthenticationSessionBridgeTest {
    @Test
    void loginPublishesSessionForLegacyEndpoints() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PhpassPasswordVerifier passwords = mock(PhpassPasswordVerifier.class);
        SessionTokenGenerator generator = mock(SessionTokenGenerator.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        LegacySessionBridge sessions = mock(LegacySessionBridge.class);
        Map<String, Object> row = new HashMap<>();
        row.put("uid", 7);
        row.put("name", "alice");
        row.put("password", "hash");
        row.put("bantime", 0);
        row.put("vip", 0);
        when(jdbc.queryForList(anyString(), eq("alice"), eq("alice")))
                .thenReturn(Collections.singletonList(row));
        when(passwords.matches("secret", "hash")).thenReturn(true);
        when(generator.generate("alice")).thenReturn("new-token");
        Map<String, Object> user = new HashMap<>();
        user.put("uid", 7);
        user.put("name", "alice");
        user.put("group", "contributor");
        when(tokens.userById(7)).thenReturn(user);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1700000000L), ZoneOffset.UTC);

        Map<String, Object> result = new UserAuthenticationService(
                jdbc, passwords, generator, tokens, sessions, clock)
                .login("alice", "secret", "127.0.0.1");

        assertThat(result).containsEntry("token", "new-token");
        verify(sessions).store("alice", "new-token", user);
    }

    @Test
    void signOutRemovesLegacySessionBeforeClearingMysqlToken() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacySessionBridge sessions = mock(LegacySessionBridge.class);
        when(jdbc.update(anyString(), eq("token"))).thenReturn(1);
        UserAuthenticationService service = new UserAuthenticationService(
                jdbc, mock(PhpassPasswordVerifier.class), mock(SessionTokenGenerator.class),
                mock(LegacyTokenService.class), sessions, Clock.systemUTC());

        service.signOut("token");

        verify(sessions).remove("token");
    }
}