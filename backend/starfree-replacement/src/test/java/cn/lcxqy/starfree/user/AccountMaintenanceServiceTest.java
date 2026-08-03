package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.security.LegacySessionBridge;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.PhpassPasswordVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountMaintenanceServiceTest {
    @Mock
    private EconomyLockExecutor lock;
    @Mock
    private UserRegistrationRepository registrationRepository;
    @Mock
    private AccountMaintenanceRepository repository;
    @Mock
    private PhpassPasswordVerifier passwords;
    @Mock
    private LegacyRegistrationRedis codes;
    @Mock
    private LegacyTokenService tokens;
    @Mock
    private LegacySessionBridge sessions;
    @Mock
    private Connection connection;

    private AccountMaintenanceService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AccountMaintenanceService(lock, registrationRepository, repository,
                passwords, codes, tokens, sessions);
        lenient().when(lock.execute(any())).thenAnswer(invocation -> {
            EconomyLockExecutor.SqlWork<?> work = invocation.getArgument(0);
            return work.execute(connection);
        });
    }

    @Test
    void publicConfigKeepsTheThreeLegacyFields() {
        Map<String, Object> configured = row("ISEMAIL", 1, "isInvite", 0, "isPhone", 2);
        when(repository.publicRegistrationConfig()).thenReturn(configured);

        assertThat(service.registrationConfig()).containsExactly(
                entry("isEmail", 1), entry("isInvite", 0), entry("isPhone", 2));
    }

    @Test
    void passwordResetUsesUsernameCodeConsumesItAndRevokesTheOldSession() throws Exception {
        arrangeConfig(config(true, ""));
        when(passwords.hash("new-secret")).thenReturn("$P$Bnew-hash");
        when(repository.accountByMail(connection, "alice@example.com"))
                .thenReturn(account("old-token"));
        when(codes.verificationCode("alice")).thenReturn("123456");
        when(repository.update(eq(connection), eq(7L), any())).thenReturn(1);

        int rows = service.forgotPassword(row(
                "name", "alice@example.com", "code", "123456", "password", "new-secret"));

        assertThat(rows).isEqualTo(1);
        verify(codes).consumeVerificationCode("alice");
        verify(sessions).removeAccounts("alice", "alice@example.com", "");
        verify(sessions).remove("old-token");
        ArgumentCaptor<Map<String, Object>> changes = mapCaptor();
        verify(repository).update(eq(connection), eq(7L), changes.capture());
        assertThat(changes.getValue()).containsEntry("password", "$P$Bnew-hash")
                .containsEntry("authCode", null);
    }

    @Test
    void passwordResetRejectsAnIncorrectCodeBeforeAnyWrite() throws Exception {
        arrangeConfig(config(true, ""));
        when(passwords.hash("new-secret")).thenReturn("$P$Bnew-hash");
        when(repository.accountByName(connection, "alice")).thenReturn(account("old-token"));
        when(codes.verificationCode("alice")).thenReturn("123456");

        assertThrows(IllegalArgumentException.class, () -> service.forgotPassword(row(
                "name", "alice", "code", "000000", "password", "new-secret")));

        verify(repository, never()).update(any(), eq(7L), any());
        verify(codes, never()).consumeVerificationCode("alice");
        verify(sessions, never()).removeAccounts(any());
        verify(sessions, never()).remove("old-token");
    }

    @Test
    void editBindsUidToTokenAndIgnoresProtectedEconomyFields() throws Exception {
        arrangeAuthenticated(config(false, ""));
        when(repository.accountByUid(connection, 7L)).thenReturn(account("token"));
        when(repository.update(eq(connection), eq(7L), any())).thenReturn(1);
        when(tokens.userById(7L)).thenReturn(row("uid", 7, "name", "alice"));

        AccountMaintenanceService.EditResult result = service.edit("token", row(
                "uid", 7, "name", "forged-name", "screenName", "Alice New",
                "assets", 999999, "points", 999999, "experience", 999999,
                "vip", 999999, "group", "administrator"));

        assertThat(result.getRows()).isEqualTo(1);
        ArgumentCaptor<Map<String, Object>> changes = mapCaptor();
        verify(repository).update(eq(connection), eq(7L), changes.capture());
        assertThat(changes.getValue()).containsExactly(entry("screenName", "Alice New"));
        verify(sessions).store(eq("alice"), eq("token"), any());
    }

    @Test
    void editRejectsAUidThatDoesNotBelongToTheToken() {
        when(tokens.userId("token")).thenReturn(7L);

        assertThrows(IllegalArgumentException.class,
                () -> service.edit("token", row("uid", 8, "screenName", "other")));

        verify(lock, never()).execute(any());
    }

    @Test
    void forbiddenIntroductionIsSkippedWhileOtherProfileFieldsAreSaved() throws Exception {
        arrangeAuthenticated(config(false, "blocked"));
        when(repository.accountByUid(connection, 7L)).thenReturn(account("token"));
        when(repository.update(eq(connection), eq(7L), any())).thenReturn(1);
        when(tokens.userById(7L)).thenReturn(row("uid", 7, "name", "alice"));

        AccountMaintenanceService.EditResult result = service.edit("token", row(
                "uid", 7, "screenName", "Alice", "introduce", "contains blocked text"));

        assertThat(result.getMessage()).contains("\u8fdd\u7981\u8bcd");
        ArgumentCaptor<Map<String, Object>> changes = mapCaptor();
        verify(repository).update(eq(connection), eq(7L), changes.capture());
        assertThat(changes.getValue()).containsExactly(entry("screenName", "Alice"));
    }

    @Test
    void mailChangeChecksUniquenessConsumesCodeAndRevokesSession() throws Exception {
        arrangeAuthenticated(config(true, ""));
        when(repository.accountByUid(connection, 7L)).thenReturn(account("token"));
        when(repository.valueExists(connection, "mail", "new@example.com", 7L))
                .thenReturn(false);
        when(codes.verificationCode("new@example.com")).thenReturn("123456");
        when(repository.update(eq(connection), eq(7L), any())).thenReturn(1);

        AccountMaintenanceService.EditResult result = service.edit("token", row(
                "uid", 7, "mail", "new@example.com", "code", "123456"));

        assertThat(result.getRows()).isEqualTo(1);
        verify(codes).consumeVerificationCode("new@example.com");
        verify(sessions).remove("token");
        verify(sessions, never()).store(eq("alice"), eq("token"), any());
        ArgumentCaptor<Map<String, Object>> changes = mapCaptor();
        verify(repository).update(eq(connection), eq(7L), changes.capture());
        assertThat(changes.getValue()).containsEntry("mail", "new@example.com")
                .containsEntry("authCode", null);
    }

    @Test
    void phoneChangeUsesThePhoneColumnAndLegacySmsCode() throws Exception {
        arrangeAuthenticated(config(false, ""));
        when(repository.accountByUid(connection, 7L)).thenReturn(account("token"));
        when(repository.valueExists(connection, "phone", "13800138000", 7L))
                .thenReturn(false);
        when(codes.phoneVerificationCode("13800138000")).thenReturn("654321");
        when(repository.update(eq(connection), eq(7L), any())).thenReturn(1);
        when(tokens.userById(7L)).thenReturn(row("uid", 7, "name", "alice"));

        service.edit("token", row(
                "uid", 7, "phone", "13800138000", "code", "654321"));

        verify(repository).valueExists(connection, "phone", "13800138000", 7L);
        verify(codes).consumePhoneVerificationCode("13800138000");
    }

    @Test
    void clientIdUpdateRefreshesTheLegacySession() throws Exception {
        when(tokens.userId("token")).thenReturn(7L);
        when(repository.accountByUid(connection, 7L)).thenReturn(account("token"));
        when(repository.update(eq(connection), eq(7L), any())).thenReturn(1);
        when(tokens.userById(7L)).thenReturn(row("uid", 7, "name", "alice"));

        assertThat(service.setClientId("token", "push-client")).isEqualTo(1);

        ArgumentCaptor<Map<String, Object>> changes = mapCaptor();
        verify(repository).update(eq(connection), eq(7L), changes.capture());
        assertThat(changes.getValue()).containsExactly(entry("clientId", "push-client"));
        verify(sessions).store(eq("alice"), eq("token"), any());
    }

    private void arrangeAuthenticated(RegistrationConfig config) throws Exception {
        when(tokens.userId("token")).thenReturn(7L);
        arrangeConfig(config);
    }

    private void arrangeConfig(RegistrationConfig config) throws Exception {
        when(registrationRepository.config()).thenReturn(config);
        when(registrationRepository.config(connection)).thenReturn(config);
    }

    private RegistrationConfig config(boolean email, String forbidden) {
        return new RegistrationConfig(row(
                "isEmail", email ? 1 : 0,
                "isInvite", 0,
                "isPhone", 0,
                "forbidden", forbidden,
                "rebateLevel", 0,
                "rebateNum", 0,
                "banRobots", 0,
                "silenceTime", 600));
    }

    private AccountMaintenanceRepository.AccountRecord account(String token) {
        return new AccountMaintenanceRepository.AccountRecord(
                7L, "alice", "$P$Bold", "alice@example.com", "", token);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }

    private Map.Entry<String, Object> entry(String key, Object value) {
        return new java.util.AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }
}
