package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.PhpassPasswordVerifier;
import cn.lcxqy.starfree.security.SessionTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {
    private static final String ACCOUNT = "test-user";
    private static final String PASSWORD = "correct password";
    private static final String HASH = "$P$Bad577wyjiXQA8T5lp182gfH.ZR1Qo/";
    private static final long NOW_SECONDS = 1_800_000_000L;

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private PhpassPasswordVerifier passwords;
    @Mock
    private SessionTokenGenerator tokenGenerator;
    @Mock
    private LegacyTokenService tokens;

    private UserAuthenticationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(NOW_SECONDS), ZoneOffset.UTC);
        service = new UserAuthenticationService(jdbc, passwords, tokenGenerator, tokens, clock);
    }

    @Test
    void productionConstructorIsMarkedForSpringInjection() {
        boolean found = false;
        for (Constructor<?> constructor : UserAuthenticationService.class.getConstructors()) {
            if (constructor.getParameterCount() == 5
                    && constructor.isAnnotationPresent(
                    org.springframework.beans.factory.annotation.Autowired.class)) {
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    void validPasswordIssuesTokenAndReturnsSafeUser() {
        arrangeKnownUser(true);
        when(tokenGenerator.generate(ACCOUNT)).thenReturn(ACCOUNT + "11111111111111111111111111111111");
        when(tokens.userById(2L)).thenReturn(profile());

        Map<String, Object> result = service.login(ACCOUNT, PASSWORD, "127.0.0.1");

        assertEquals(2L, result.get("uid"));
        assertEquals(ACCOUNT + "11111111111111111111111111111111", result.get("token"));
        assertEquals(NOW_SECONDS * 1000, result.get("time"));
        assertEquals(0, result.get("isvip"));
        verify(jdbc).update(anyString(),
                eq(ACCOUNT + "11111111111111111111111111111111"),
                eq(NOW_SECONDS), eq(NOW_SECONDS), eq("127.0.0.1"), eq(2L));
    }

    @Test
    void invalidPasswordDoesNotIssueToken() {
        arrangeKnownUser(false);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(ACCOUNT, "wrong", "127.0.0.1"));

        assertEquals("用户名或密码错误", error.getMessage());
        verifyNoInteractions(tokenGenerator, tokens);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void unknownUserReturnsSameCredentialError() {
        when(jdbc.queryForList(anyString(), eq(ACCOUNT), eq(ACCOUNT)))
                .thenReturn(Collections.emptyList());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(ACCOUNT, PASSWORD, "127.0.0.1"));

        assertEquals("用户名或密码错误", error.getMessage());
        verifyNoInteractions(passwords, tokenGenerator, tokens);
    }

    @Test
    void repeatedLoginRotatesTheSingleStoredToken() {
        arrangeKnownUser(true);
        when(tokenGenerator.generate(ACCOUNT)).thenReturn(
                ACCOUNT + "11111111111111111111111111111111",
                ACCOUNT + "22222222222222222222222222222222");
        when(tokens.userById(2L)).thenAnswer(invocation -> profile());

        Map<String, Object> first = service.login(ACCOUNT, PASSWORD, "127.0.0.1");
        Map<String, Object> second = service.login(ACCOUNT, PASSWORD, "127.0.0.1");

        assertEquals(ACCOUNT + "11111111111111111111111111111111", first.get("token"));
        assertEquals(ACCOUNT + "22222222222222222222222222222222", second.get("token"));
        verify(jdbc, times(2)).update(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void signOutInvalidatesOnlyAnExistingToken() {
        String token = ACCOUNT + "11111111111111111111111111111111";
        when(jdbc.update(anyString(), eq(token))).thenReturn(1, 0);

        service.signOut(token);
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.signOut(token));

        assertEquals("用户未登录或Token验证失败", error.getMessage());
        verify(jdbc, times(2)).update(anyString(), eq(token));
    }

    private void arrangeKnownUser(boolean passwordMatches) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uid", 2L);
        row.put("name", ACCOUNT);
        row.put("password", HASH);
        row.put("bantime", 0);
        row.put("vip", 0);
        when(jdbc.queryForList(anyString(), eq(ACCOUNT), eq(ACCOUNT)))
                .thenReturn(Collections.singletonList(row));
        when(passwords.matches(anyString(), eq(HASH))).thenReturn(passwordMatches);
    }

    private Map<String, Object> profile() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("uid", 2L);
        user.put("name", ACCOUNT);
        return user;
    }
}
