package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.cache.LegacyProjectionCacheInvalidator;
import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.security.LegacyRedisKeyStore;
import cn.lcxqy.starfree.security.LegacySessionBridge;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.PhpassPasswordVerifier;
import cn.lcxqy.starfree.security.SessionTokenGenerator;
import cn.lcxqy.starfree.security.StaffAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {
    private StubJdbcTemplate jdbc;
    @Mock
    private LegacyTokenService tokens;
    @Mock
    private LegacySessionBridge sessions;
    @Mock
    private LegacyRegistrationRedis verificationCodes;
    @Mock
    private LegacyRedisKeyStore redisKeys;
    @Mock
    private SessionTokenGenerator tokenGenerator;
    @Mock
    private PhpassPasswordVerifier passwords;
    @Mock
    private EconomyLockExecutor economyLock;
    @Mock
    private LegacyProjectionCacheInvalidator caches;

    private UserAdministrationService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubJdbcTemplate();
        service = new UserAdministrationService(
                jdbc, tokens, new StaffAccess(tokens), sessions, verificationCodes, redisKeys,
                tokenGenerator, passwords, economyLock, new ObjectMapper(), caches);
    }

    @Test
    void anonymousUserListRemovesPrivateAndEconomicFields() {
        jdbc.count = 1;
        jdbc.rows = Collections.singletonList(row(
                        "uid", 7, "name", "alice", "mail", "alice@example.com",
                        "phone", "13800138000", "assets", 900, "points", 50,
                        "address", "private", "pay", "private", "clientId", "push",
                        "ip", "127.0.0.1", "local", "city", "invitationCode", "secret",
                        "vip", 0));

        UserAdministrationService.Page result = service.users(
                stringRow("page", "1", "limit", "500", "searchKey", "alice"));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0))
                .containsEntry("uid", 7)
                .containsEntry("name", "alice")
                .containsEntry("isvip", 0)
                .doesNotContainKeys("mail", "phone", "assets", "points", "address", "pay",
                        "clientId", "ip", "local", "invitationCode");
    }

    @Test
    void sensitiveManagementEditRevokesEveryLegacySessionAndInvalidatesReads() {
        arrangeActor("admin-token", 1, "root", "administrator");
        jdbc.rows = Collections.singletonList(row(
                "uid", 7, "name", "alice", "mail", "old@example.com",
                "phone", "13800138000", "authCode", "old-token", "group", "contributor"));
        jdbc.updateResult = 1;

        int changed = service.manageEdit("admin-token", row("uid", 7, "group", "editor"));

        assertThat(changed).isEqualTo(1);
        verify(sessions).removeAccounts("alice", "old@example.com", "13800138000");
        verify(sessions).remove("old-token");
        verify(sessions, never()).store(anyString(), anyString(), any());
        verify(caches).afterUserWrite(7, "alice");
    }

    @Test
    void ordinaryManagementEditRefreshesTheExistingSharedSession() {
        arrangeActor("admin-token", 1, "root", "administrator");
        jdbc.rows = Collections.singletonList(row(
                "uid", 7, "name", "alice", "mail", "old@example.com",
                "phone", "13800138000", "authCode", "old-token", "group", "contributor"));
        jdbc.updateResult = 1;
        Map<String, Object> refreshed = row(
                "uid", 7, "name", "alice", "screenName", "Alice New");
        when(tokens.userById(7L)).thenReturn(refreshed);

        int changed = service.manageEdit(
                "admin-token", row("uid", 7, "screenName", "Alice New"));

        assertThat(changed).isEqualTo(1);
        verify(sessions).store("alice", "old-token", refreshed);
        verify(sessions, never()).remove("old-token");
        verify(caches).afterUserWrite(7, "alice");
    }

    @Test
    void editorCannotBanAnotherEditorOrAnAdministrator() {
        arrangeActor("editor-token", 2, "moderator", "editor");
        jdbc.rows = Collections.singletonList(row(
                "uid", 7, "name", "target", "group", "editor", "bantime", 0));

        assertThrows(IllegalArgumentException.class,
                () -> service.ban("editor-token", 7, 3600, "manager", "reason"));

        assertThat(jdbc.updateCalls).isZero();
        verify(caches, never()).afterUserWrite(anyLong(), anyString());
    }

    @Test
    void invitationGenerationIsAdministratorOnlyAndInvalidatesLegacyPages() {
        arrangeActor("admin-token", 1, "root", "administrator");
        jdbc.count = 0;
        jdbc.updateResult = 1;

        assertThat(service.generateInvitations("admin-token", 2)).isEqualTo(2);

        assertThat(jdbc.updateCalls).isEqualTo(2);
        verify(caches).afterInvitationWrite();
    }

    @Test
    void accountDeletionRejectsTheCurrentAdministratorBeforeTakingTheEconomyLock() {
        arrangeActor("admin-token", 1, "root", "administrator");

        assertThrows(IllegalArgumentException.class,
                () -> service.deleteUser("admin-token", 1));

        verify(economyLock, never()).execute(any());
    }

    private void arrangeActor(String token, long uid, String name, String group) {
        when(tokens.userId(token)).thenReturn(uid);
        when(tokens.userById(uid)).thenReturn(row(
                "uid", uid, "name", name, "group", group));
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }

    private Map<String, String> stringRow(String... values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(values[index], values[index + 1]);
        }
        return row;
    }

    /** Minimal in-memory JDBC boundary; SQL behavior is tested without matching Java varargs. */
    private static final class StubJdbcTemplate extends JdbcTemplate {
        private List<Map<String, Object>> rows = Collections.emptyList();
        private int count;
        private int updateResult;
        private int updateCalls;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return rows;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return (T) Integer.valueOf(count);
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalls++;
            return updateResult;
        }
    }
}
