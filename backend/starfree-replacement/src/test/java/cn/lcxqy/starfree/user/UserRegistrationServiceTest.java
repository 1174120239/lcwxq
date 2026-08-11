package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.economy.EconomyOperationJournal;
import cn.lcxqy.starfree.security.PhpassPasswordVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {
    private static final String NAME = "test-user";
    private static final String MAIL = "user@example.com";
    private static final String CODE = "123456";
    private static final String OPERATION_KEY = "user-register:key";
    private static final long NOW = 1_800_000_000L;

    @Mock
    private EconomyLockExecutor lock;
    @Mock
    private EconomyOperationJournal journal;
    @Mock
    private UserRegistrationRepository repository;
    @Mock
    private PhpassPasswordVerifier passwords;
    @Mock
    private LegacyRegistrationRedis redis;
    @Mock
    private Connection connection;

    private UserRegistrationService service;

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
        service = new UserRegistrationService(
                lock, journal, repository, passwords, redis, clock);
        when(lock.execute(any())).thenAnswer(invocation -> {
            EconomyLockExecutor.SqlWork<?> work = invocation.getArgument(0);
            return work.execute(connection);
        });
        when(passwords.hash("secret")).thenReturn("$P$Bgenerated-password-hash");
        when(journal.fixedKey(eq("user-register"), anyString())).thenReturn(OPERATION_KEY);
    }

    @Test
    void invitationCreatesUserRelationshipAndRewardsInviterOnce() throws Exception {
        RegistrationConfig config = config(true, true, 1, 10);
        arrangeConfig(config);
        arrangeStarted();
        when(redis.verificationCode(MAIL)).thenReturn(CODE);
        when(repository.availableInvitations(connection, "INVITE01"))
                .thenReturn(Collections.singletonList(row("id", 4L, "uid", 7L)));
        when(repository.user(connection, 7L)).thenReturn(row("uid", 7L, "assets", 20L));
        when(repository.consumeInvitation(connection, 4L)).thenReturn(1);
        when(repository.insertUser(eq(connection), any())).thenReturn(11L);
        when(repository.setAssets(connection, 7L, 30L)).thenReturn(1);
        when(repository.insertRebatePaylog(
                connection, 7L, 10, OPERATION_KEY, NOW)).thenReturn(15L);

        Map<String, Object> result = service.register(request(CODE, "INVITE01"), "203.0.113.7");

        assertThat(result).containsEntry("rows", 1)
                .containsEntry("uid", 11L)
                .containsEntry("invitationUser", 7L)
                .containsEntry("rebate", 10);
        ArgumentCaptor<UserRegistrationRepository.RegistrationUser> user =
                ArgumentCaptor.forClass(UserRegistrationRepository.RegistrationUser.class);
        verify(repository).insertUser(eq(connection), user.capture());
        assertThat(user.getValue().getInviterUid()).isEqualTo(7L);
        assertThat(user.getValue().getCampusId()).isEqualTo(2L);
        assertThat(user.getValue().getGradeId()).isEqualTo(3L);
        verify(journal).commit(eq(connection), eq(OPERATION_KEY), any());
        verify(redis).consumeVerificationCode(MAIL);
    }

    @Test
    void reusableInvitationRewardsPointsAndExperienceWithoutConsumingCode() throws Exception {
        RegistrationConfig config = config(false, false, 0, 0);
        arrangeConfig(config);
        arrangeStarted();
        when(repository.invitationRewardConfig(connection))
                .thenReturn(new UserRegistrationRepository.InvitationRewardConfig(true, 12, 30));
        when(repository.reusableInvitation(connection, "LYCODE1234"))
                .thenReturn(row("uid", 7L, "invite_code", "LYCODE1234"));
        when(repository.user(connection, 7L)).thenReturn(row(
                "uid", 7L, "assets", 20L, "points", 8L, "experience", 40L));
        when(repository.insertUser(eq(connection), any())).thenReturn(11L);
        when(repository.insertInvitationRecord(
                connection, 7L, 11L, "LYCODE1234", 12, 30)).thenReturn(1);
        when(repository.setInvitationRewards(connection, 7L, 20L, 70L)).thenReturn(1);

        Map<String, Object> result = service.register(
                request("", "lycode1234"), "203.0.113.7");

        assertThat(result).containsEntry("uid", 11L)
                .containsEntry("invitationUser", 7L)
                .containsEntry("rewardPoints", 12)
                .containsEntry("rewardExperience", 30)
                .containsEntry("rebate", 0);
        verify(repository).setInvitationRewards(connection, 7L, 20L, 70L);
        verify(repository, never()).consumeInvitation(any(), anyLong());
        verify(repository, never()).setAssets(any(), anyLong(), anyLong());
    }

    @Test
    void reusableInvitationCanRecordSuccessWhenBothRewardsAreZero() throws Exception {
        RegistrationConfig config = config(false, false, 0, 0);
        arrangeConfig(config);
        arrangeStarted();
        when(repository.invitationRewardConfig(connection))
                .thenReturn(new UserRegistrationRepository.InvitationRewardConfig(true, 0, 0));
        when(repository.reusableInvitation(connection, "LYCODE1234"))
                .thenReturn(row("uid", 7L, "invite_code", "LYCODE1234"));
        when(repository.user(connection, 7L)).thenReturn(row(
                "uid", 7L, "assets", 20L, "points", 8L, "experience", 40L));
        when(repository.insertUser(eq(connection), any())).thenReturn(11L);
        when(repository.insertInvitationRecord(
                connection, 7L, 11L, "LYCODE1234", 0, 0)).thenReturn(1);

        Map<String, Object> result = service.register(
                request("", "LYCODE1234"), "203.0.113.7");

        assertThat(result).containsEntry("rewardPoints", 0)
                .containsEntry("rewardExperience", 0);
        verify(repository, never()).setInvitationRewards(any(), anyLong(), anyLong(), anyLong());
        verify(journal).commit(eq(connection), eq(OPERATION_KEY), any());
    }

    @Test
    void committedRegistrationReplayDoesNotCreateOrRewardAgain() throws Exception {
        RegistrationConfig config = config(true, true, 1, 10);
        arrangeConfig(config);
        Map<String, Object> committed = row("rows", 1, "uid", 11L,
                "invitationUser", 7L, "rebate", 10);
        when(journal.begin(eq(connection), eq(OPERATION_KEY), anyString(),
                anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(EconomyOperationJournal.BeginResult.replay(committed));

        Map<String, Object> result = service.register(request(CODE, "INVITE01"), "203.0.113.7");

        assertThat(result).isEqualTo(committed);
        verify(repository, never()).nameExists(any(), anyString());
        verify(repository, never()).insertUser(any(), any());
        verify(repository, never()).setAssets(any(), anyLong(), anyLong());
    }

    @Test
    void duplicateUsernameFailsBeforeAnyMyisamWrite() throws Exception {
        RegistrationConfig config = config(false, false, 0, 0);
        arrangeConfig(config);
        arrangeStarted();
        when(repository.nameExists(connection, NAME)).thenReturn(true);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.register(request("", ""), "203.0.113.7"));

        assertThat(error.getMessage()).isEqualTo("\u8be5\u7528\u6237\u540d\u5df2\u6ce8\u518c");
        verify(journal).fail(eq(connection), eq(OPERATION_KEY), eq(error));
        verify(repository, never()).insertUser(any(), any());
    }

    @Test
    void incorrectVerificationCodeCannotConsumeInvitation() throws Exception {
        RegistrationConfig config = config(true, true, 1, 10);
        arrangeConfig(config);
        arrangeStarted();
        when(redis.verificationCode(MAIL)).thenReturn("654321");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.register(request(CODE, "INVITE01"), "203.0.113.7"));

        assertThat(error.getMessage()).isEqualTo("\u9a8c\u8bc1\u7801\u4e0d\u6b63\u786e");
        verify(repository, never()).availableInvitations(any(), anyString());
        verify(repository, never()).consumeInvitation(any(), anyLong());
    }

    @Test
    void duplicateInvitationRowsAreRejectedAsConfigurationError() throws Exception {
        RegistrationConfig config = config(false, true, 1, 10);
        arrangeConfig(config);
        arrangeStarted();
        when(repository.availableInvitations(connection, "INVITE01"))
                .thenReturn(Arrays.asList(
                        row("id", 4L, "uid", 7L), row("id", 5L, "uid", 7L)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.register(request("", "INVITE01"), "203.0.113.7"));

        assertThat(error.getMessage()).contains("\u914d\u7f6e\u91cd\u590d");
        verify(repository, never()).consumeInvitation(any(), anyLong());
    }

    @Test
    void failedPaylogInsertRestoresEveryCompletedMyisamProjection() throws Exception {
        RegistrationConfig config = config(false, true, 1, 10);
        arrangeConfig(config);
        arrangeStarted();
        when(repository.availableInvitations(connection, "INVITE01"))
                .thenReturn(Collections.singletonList(row("id", 4L, "uid", 7L)));
        when(repository.user(connection, 7L)).thenReturn(row("uid", 7L, "assets", 20L));
        when(repository.consumeInvitation(connection, 4L)).thenReturn(1);
        when(repository.insertUser(eq(connection), any())).thenReturn(11L);
        when(repository.setAssets(connection, 7L, 30L)).thenReturn(1);
        when(repository.insertRebatePaylog(
                connection, 7L, 10, OPERATION_KEY, NOW))
                .thenThrow(new SQLException("paylog unavailable"));
        when(repository.setAssets(connection, 7L, 20L)).thenReturn(1);
        when(repository.deleteUser(connection, 11L)).thenReturn(1);
        when(repository.releaseInvitation(connection, 4L)).thenReturn(1);

        SQLException error = assertThrows(SQLException.class,
                () -> service.register(request("", "INVITE01"), "203.0.113.7"));

        assertThat(error.getMessage()).isEqualTo("paylog unavailable");
        verify(repository).setAssets(connection, 7L, 20L);
        verify(repository).deleteUser(connection, 11L);
        verify(repository).releaseInvitation(connection, 4L);
        verify(journal).fail(eq(connection), eq(OPERATION_KEY), eq(error));
    }

    @Test
    void ambiguousJournalCommitKeepsCompletedProjectionsForManualReview() throws Exception {
        RegistrationConfig config = config(false, false, 0, 0);
        arrangeConfig(config);
        arrangeStarted();
        when(repository.insertUser(eq(connection), any())).thenReturn(11L);
        org.mockito.Mockito.doThrow(new SQLException("journal unavailable"))
                .when(journal).commit(eq(connection), eq(OPERATION_KEY), any());
        SQLException error = assertThrows(SQLException.class,
                () -> service.register(request("", ""), "203.0.113.7"));

        assertThat(error.getMessage()).contains("journal commit");
        verify(journal).needsReview(eq(connection), eq(OPERATION_KEY), any());
        verify(journal, never()).fail(eq(connection), eq(OPERATION_KEY), any());
        verify(repository, never()).deleteUser(connection, 11L);
    }

    @Test
    void failedCompensationMarksOperationForManualReview() throws Exception {
        RegistrationConfig config = config(false, true, 1, 10);
        arrangeConfig(config);
        arrangeStarted();
        when(repository.availableInvitations(connection, "INVITE01"))
                .thenReturn(Collections.singletonList(row("id", 4L, "uid", 7L)));
        when(repository.user(connection, 7L)).thenReturn(row("uid", 7L, "assets", 20L));
        when(repository.consumeInvitation(connection, 4L)).thenReturn(1);
        when(repository.insertUser(eq(connection), any())).thenReturn(11L);
        when(repository.setAssets(connection, 7L, 30L)).thenReturn(1);
        when(repository.insertRebatePaylog(
                connection, 7L, 10, OPERATION_KEY, NOW))
                .thenThrow(new SQLException("paylog unavailable"));
        when(repository.setAssets(connection, 7L, 20L)).thenReturn(1);
        when(repository.deleteUser(connection, 11L)).thenReturn(0);
        when(repository.releaseInvitation(connection, 4L)).thenReturn(1);

        SQLException error = assertThrows(SQLException.class,
                () -> service.register(request("", "INVITE01"), "203.0.113.7"));

        assertThat(error.getMessage()).contains("manual review");
        verify(journal).needsReview(eq(connection), eq(OPERATION_KEY), any());
        verify(journal, never()).fail(eq(connection), eq(OPERATION_KEY), any());
    }

    @Test
    void disabledCampusIsRejectedBeforeUserInsert() throws Exception {
        RegistrationConfig config = config(false, false, 0, 0);
        arrangeConfig(config);
        arrangeStarted();
        when(repository.enabledIdentityOption(connection, 2L, "campus")).thenReturn(false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.register(request("", ""), "203.0.113.7"));

        assertThat(error.getMessage()).isEqualTo("请选择当前启用的校区");
        verify(repository, never()).insertUser(any(), any());
    }

    private void arrangeConfig(RegistrationConfig config) throws SQLException {
        when(repository.config()).thenReturn(config);
        when(repository.config(connection)).thenReturn(config);
        lenient().when(repository.invitationRewardConfig(connection))
                .thenReturn(new UserRegistrationRepository.InvitationRewardConfig(false, 0, 0));
        lenient().when(repository.enabledIdentityOption(connection, 2L, "campus")).thenReturn(true);
        lenient().when(repository.enabledIdentityOption(connection, 3L, "grade")).thenReturn(true);
    }

    private void arrangeStarted() throws SQLException {
        when(journal.begin(eq(connection), eq(OPERATION_KEY), eq("user-register"),
                eq(0L), eq(0L), eq(0L), any()))
                .thenReturn(EconomyOperationJournal.BeginResult.started());
    }

    private RegistrationConfig config(boolean email, boolean invite,
                                      int rebateLevel, int rebateNum) {
        return new RegistrationConfig(row(
                "isEmail", email ? 1 : 0,
                "isInvite", invite ? 1 : 0,
                "isPhone", 0,
                "forbidden", "",
                "rebateLevel", rebateLevel,
                "rebateNum", rebateNum,
                "banRobots", 0,
                "silenceTime", 600));
    }

    private Map<String, Object> request(String code, String inviteCode) {
        return row(
                "name", NAME,
                "password", "secret",
                "mail", MAIL,
                "code", code,
                "inviteCode", inviteCode,
                "campusId", 2,
                "gradeId", 3,
                "assets", 999999,
                "points", 999999,
                "experience", 999999);
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }
}
