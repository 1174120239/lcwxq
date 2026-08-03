package cn.lcxqy.starfree.space;

import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceServiceTest {
    @Test
    void anonymousListUsesSamePublicVisibilityForRowsAndTotal() {
        Fixture fixture = new Fixture();
        when(fixture.jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(fixture.jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());

        SpaceService.SpacePage page = fixture.service.page("", 1, 15, "", "created", 0, "");

        assertThat(page.getTotal()).isZero();
        verify(fixture.jdbc).queryForObject(
                contains("s.status = 1 AND s.onlyMe = 0"),
                eq(Integer.class), any(Object[].class));
        verify(fixture.jdbc).queryForList(
                contains("s.status = 1 AND s.onlyMe = 0 AND s.type <> 3"),
                eq(0), eq(15));
    }

    @Test
    void loggedInListAddsOwnPendingAndPrivateRows() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        when(fixture.jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(fixture.jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());

        fixture.service.page("", 1, 15, "", "created", 0, "token");

        verify(fixture.jdbc).queryForObject(
                contains("((s.status = 1 AND s.onlyMe = 0) OR s.uid = ?)"),
                eq(Integer.class), eq(7L));
    }

    @Test
    void nonStaffCannotEnableManagementReplyView() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        when(fixture.jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(fixture.jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());

        fixture.service.page("", 1, 15, "", "created", 1, "token");

        verify(fixture.jdbc).queryForObject(contains("s.type <> 3"),
                eq(Integer.class), eq(7L));
    }

    @Test
    void staffManagementViewCanIncludePendingPrivateAndReplyRows() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "administrator");
        when(fixture.jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);
        when(fixture.jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());

        fixture.service.page("", 1, 15, "", "created", 1, "token");

        verify(fixture.jdbc).queryForObject(eq("SELECT COUNT(*) FROM starfree_space s WHERE 1 = 1"),
                eq(Integer.class), any(Object[].class));
    }

    @Test
    void anonymousInfoRejectsPrivateSpace() {
        Fixture fixture = new Fixture();
        Map<String, Object> row = space(11L, 8L, 1, 1, 0);
        when(fixture.jdbc.queryForList(contains("LEFT JOIN starfree_users"), eq(11L)))
                .thenReturn(Collections.singletonList(row));

        assertThatThrownBy(() -> fixture.service.info(11L, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u52a8\u6001\u4e0d\u5b58\u5728\u6216\u65e0\u6743\u67e5\u770b");

        verify(fixture.jdbc, never()).queryForList(startsWith("SELECT spaceMinExp"));
    }

    @Test
    void publicInfoIncrementsAndReturnsViews() {
        Fixture fixture = new Fixture();
        Map<String, Object> row = space(11L, 8L, 1, 0, 0);
        row.put("views", 4);
        when(fixture.jdbc.queryForList(contains("LEFT JOIN starfree_users"), eq(11L)))
                .thenReturn(Collections.singletonList(row));
        when(fixture.jdbc.update(
                eq("UPDATE starfree_space SET views = COALESCE(views, 0) + 1 WHERE id = ?"),
                eq(11L))).thenReturn(1);
        when(fixture.jdbc.queryForList(startsWith("SELECT spaceMinExp")))
                .thenReturn(Collections.singletonList(config()));
        when(fixture.jdbc.queryForObject(
                startsWith("SELECT COUNT(*) FROM starfree_space WHERE toid = ?"),
                eq(Integer.class), eq(11L), anyInt())).thenReturn(0);

        Map<String, Object> result = fixture.service.info(11L, "");

        assertThat(result.get("views")).isEqualTo(5L);
        verify(fixture.jdbc).update(
                eq("UPDATE starfree_space SET views = COALESCE(views, 0) + 1 WHERE id = ?"),
                eq(11L));
    }

    @Test
    void pluginSpaceTypeIsRejectedBeforeDatabaseWrites() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("type", "6");
        request.put("text", "plugin payload");

        assertThatThrownBy(() -> fixture.service.add(request, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u53c2\u6570\u4e0d\u6b63\u786e");

        verify(fixture.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void imageOnlySpaceIsAcceptedWithoutSyntheticTextMarker() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        when(fixture.jdbc.queryForList(startsWith("SELECT spaceMinExp")))
                .thenReturn(Collections.singletonList(config()));
        when(fixture.jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM starfree_space"),
                eq(Integer.class), eq(7L), anyLong())).thenReturn(0);
        when(fixture.jdbc.update(startsWith("INSERT INTO starfree_space"),
                eq(7L), anyLong(), anyLong(), eq(""), eq("image.png"), eq(0), eq(0),
                eq(0), eq(1), eq(0))).thenReturn(1);

        Map<String, String> request = addRequest("");
        request.put("pic", "image.png");

        assertThat(fixture.service.add(request, "127.0.0.1")).isFalse();
    }

    @Test
    void emptySpaceWithoutTextOrMediaIsRejectedBeforeDatabaseWrites() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        Map<String, String> request = addRequest("");

        assertThatThrownBy(() -> fixture.service.add(request, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u52a8\u6001\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a");
        verify(fixture.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void forbiddenSpaceRecordsLegacyStrikeBeforeAnyWrite() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        Map<String, Object> configured = config();
        configured.put("forbidden", "blocked");
        when(fixture.jdbc.queryForList(startsWith("SELECT spaceMinExp")))
                .thenReturn(Collections.singletonList(configured));
        when(fixture.abuseGuard.recordForbidden(7L, 3600)).thenReturn(true);

        Map<String, String> request = addRequest("this is blocked text");

        assertThatThrownBy(() -> fixture.service.add(request, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u4f60\u5df2\u591a\u6b21\u53d1\u5e03\u8fdd\u7981\u8bcd\uff0c\u5df2\u88ab\u7981\u8a00");
        verify(fixture.abuseGuard).recordForbidden(7L, 3600);
        verify(fixture.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void failedMyisamSpaceInsertCancelsLegacyPostReservation() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        when(fixture.jdbc.queryForList(startsWith("SELECT spaceMinExp")))
                .thenReturn(Collections.singletonList(config()));
        when(fixture.jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM starfree_space"),
                eq(Integer.class), eq(7L), anyLong())).thenReturn(0);
        LegacySpaceAbuseGuard.PostReservation reservation =
                mock(LegacySpaceAbuseGuard.PostReservation.class);
        when(fixture.abuseGuard.reservePost(7L, false, 999, 0)).thenReturn(reservation);
        when(fixture.jdbc.update(startsWith("INSERT INTO starfree_space"),
                eq(7L), anyLong(), anyLong(), eq("valid post text"), any(), eq(0), eq(0),
                eq(0), eq(1), eq(0))).thenReturn(0);

        assertThatThrownBy(() -> fixture.service.add(addRequest("valid post text"), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Space insert did not affect exactly one row");
        verify(reservation).cancel();
    }

    @Test
    void publishedMyisamSpaceSurvivesSecondaryUserUpdateFailure() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        when(fixture.jdbc.queryForList(startsWith("SELECT spaceMinExp")))
                .thenReturn(Collections.singletonList(config()));
        when(fixture.jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM starfree_space"),
                eq(Integer.class), eq(7L), anyLong())).thenReturn(0);
        LegacySpaceAbuseGuard.PostReservation reservation =
                mock(LegacySpaceAbuseGuard.PostReservation.class);
        when(fixture.abuseGuard.reservePost(7L, false, 999, 0)).thenReturn(reservation);
        when(fixture.jdbc.update(startsWith("INSERT INTO starfree_space"),
                eq(7L), anyLong(), anyLong(), eq("valid post text"), any(), eq(0), eq(0),
                eq(0), eq(1), eq(0))).thenReturn(1);
        when(fixture.jdbc.update(startsWith("UPDATE starfree_users SET posttime"),
                anyLong(), eq("127.0.0.1"), eq(7L)))
                .thenThrow(new DataAccessResourceFailureException("offline"));

        assertThat(fixture.service.add(addRequest("valid post text"), "127.0.0.1")).isFalse();
        verify(reservation, never()).cancel();
    }

    @Test
    void staffEditPreservesOriginalOwnerAndImmutableType() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "editor");
        Map<String, Object> existing = space(11L, 99L, 1, 0, 0);
        existing.put("pic", "old.png");
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(existing));
        when(fixture.jdbc.queryForList(startsWith("SELECT spaceMinExp")))
                .thenReturn(Collections.singletonList(config()));
        when(fixture.jdbc.update(startsWith("UPDATE starfree_space SET text"),
                eq("edited text"), eq("new.png"), eq(0), eq(1), anyLong(), eq(11L)))
                .thenReturn(1);

        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("id", "11");
        request.put("type", "0");
        request.put("text", "edited text");
        request.put("pic", "new.png");
        request.put("onlyMe", "1");

        int changed = fixture.service.edit(request);

        assertThat(changed).isOne();
        verify(fixture.jdbc).update(
                eq("UPDATE starfree_space SET text = ?,pic = ?,toid = ?,onlyMe = ?,modified = ? WHERE id = ?"),
                eq("edited text"), eq("new.png"), eq(0), eq(1), anyLong(), eq(11L));
    }

    @Test
    void editRejectsTypeMismatchInsteadOfChangingTargetSemantics() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        Map<String, Object> existing = space(11L, 7L, 1, 0, 0);
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(existing));

        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("id", "11");
        request.put("type", "3");
        request.put("toid", "12");
        request.put("text", "edited text");

        assertThatThrownBy(() -> fixture.service.edit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u53c2\u6570\u4e0d\u6b63\u786e");
        verify(fixture.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void editRejectsExistingPluginRows() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        Map<String, Object> existing = space(11L, 7L, 1, 0, 6);
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(existing));

        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("id", "11");
        request.put("type", "6");
        request.put("text", "plugin edit text");

        assertThatThrownBy(() -> fixture.service.edit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u53c2\u6570\u4e0d\u6b63\u786e");
        verify(fixture.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void nonStaffCannotReviewSpace() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("id", "11");
        request.put("type", "1");

        assertThatThrownBy(() -> fixture.service.review(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u4f60\u6ca1\u6709\u64cd\u4f5c\u6743\u9650");
        verify(fixture.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void staffCanRejectPendingSpaceAndWriteSystemNotice() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "administrator");
        Map<String, Object> pending = space(11L, 8L, 0, 0, 0);
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(pending));
        when(fixture.jdbc.update(eq("DELETE FROM starfree_space WHERE id = ?"), eq(11L)))
                .thenReturn(1);
        when(fixture.jdbc.update(startsWith("INSERT INTO starfree_inbox"),
                any(Object[].class))).thenReturn(1);

        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("id", "11");
        request.put("type", "0");

        assertThat(fixture.service.review(request)).isOne();
        verify(fixture.jdbc).update(eq("DELETE FROM starfree_space WHERE id = ?"), eq(11L));
        verify(fixture.jdbc).update(startsWith("INSERT INTO starfree_inbox"),
                eq("system"), eq(7L), contains("\u5df2\u88ab\u5220\u9664"), eq(8L), eq(0), eq(0),
                anyLong(), eq(0));
    }

    @Test
    void nonStaffCannotLockSpace() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");

        assertThatThrownBy(() -> fixture.service.lock(lockRequest("2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u4f60\u6ca1\u6709\u64cd\u4f5c\u6743\u9650");
        verify(fixture.jdbc, never()).queryForList(startsWith("SELECT id,uid"), anyLong());
        verify(fixture.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void staffCannotLockPendingSpace() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "editor");
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(space(11L, 8L, 0, 0, 0)));

        assertThatThrownBy(() -> fixture.service.lock(lockRequest("2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u52a8\u6001\u672a\u8fc7\u5ba1\uff0c\u6682\u65e0\u6cd5\u64cd\u4f5c");
        verify(fixture.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void staffCannotRepeatSpaceLockState() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "administrator");
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(space(11L, 8L, 2, 0, 0)));

        assertThatThrownBy(() -> fixture.service.lock(lockRequest("2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u52a8\u6001\u5df2\u88ab\u8fdb\u884c\u76f8\u540c\u64cd\u4f5c");
        verify(fixture.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void staffCanLockPublishedSpaceAndWriteSystemNotice() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "administrator");
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(space(11L, 8L, 1, 0, 0)));
        when(fixture.jdbc.update(eq("UPDATE starfree_space SET status = ? WHERE id = ?"),
                eq(2), eq(11L))).thenReturn(1);
        when(fixture.jdbc.update(startsWith("INSERT INTO starfree_inbox"),
                any(Object[].class))).thenReturn(1);

        assertThat(fixture.service.lock(lockRequest("2"))).isOne();
        verify(fixture.jdbc).update(eq("UPDATE starfree_space SET status = ? WHERE id = ?"),
                eq(2), eq(11L));
        verify(fixture.jdbc).update(startsWith("INSERT INTO starfree_inbox"),
                eq("system"), eq(7L), contains("ID:11\u3011\u5df2\u88ab\u9501\u5b9a"), eq(8L),
                eq(0), eq(0), anyLong(), eq(0));
    }

    @Test
    void nonOwnerCannotDeleteSpace() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(space(11L, 8L, 1, 0, 0)));

        assertThatThrownBy(() -> fixture.service.delete(deleteRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u4f60\u6ca1\u6709\u64cd\u4f5c\u6743\u9650");
        verify(fixture.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void ownerCanDeleteSpaceWithoutSystemNotice() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(space(11L, 7L, 1, 0, 0)));
        when(fixture.jdbc.update(eq("DELETE FROM starfree_space WHERE id = ?"), eq(11L)))
                .thenReturn(1);

        assertThat(fixture.service.delete(deleteRequest())).isOne();
        verify(fixture.jdbc).update(eq("DELETE FROM starfree_space WHERE id = ?"), eq(11L));
        verify(fixture.jdbc, never()).update(startsWith("INSERT INTO starfree_inbox"),
                any(Object[].class));
    }

    @Test
    void staffCanDeleteAnotherUsersSpaceAndWriteSystemNotice() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "editor");
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(space(11L, 8L, 1, 0, 0)));
        when(fixture.jdbc.update(eq("DELETE FROM starfree_space WHERE id = ?"), eq(11L)))
                .thenReturn(1);
        when(fixture.jdbc.update(startsWith("INSERT INTO starfree_inbox"),
                any(Object[].class))).thenReturn(1);

        assertThat(fixture.service.delete(deleteRequest())).isOne();
        verify(fixture.jdbc).update(startsWith("INSERT INTO starfree_inbox"),
                eq("system"), eq(7L), contains("\u52a8\u6001\u3010test space\u3011\u5df2\u88ab\u5220\u9664"),
                eq(8L), eq(0), eq(0), anyLong(), eq(0));
    }

    @Test
    void completedDeleteSurvivesSystemNoticeFailure() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "administrator");
        when(fixture.jdbc.queryForList(startsWith("SELECT id,uid"), eq(11L)))
                .thenReturn(Collections.singletonList(space(11L, 8L, 1, 0, 0)));
        when(fixture.jdbc.update(eq("DELETE FROM starfree_space WHERE id = ?"), eq(11L)))
                .thenReturn(1);
        when(fixture.jdbc.update(startsWith("INSERT INTO starfree_inbox"),
                any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("offline"));

        assertThat(fixture.service.delete(deleteRequest())).isOne();
        verify(fixture.jdbc).update(eq("DELETE FROM starfree_space WHERE id = ?"), eq(11L));
    }

    @Test
    void followedListUsesSamePublicNonReplyVisibilityForRowsAndTotal() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        when(fixture.jdbc.queryForObject(
                contains("s.status = 1 AND s.onlyMe = 0 AND s.type <> 3"),
                eq(Integer.class), eq(7L))).thenReturn(0);
        when(fixture.jdbc.queryForList(
                contains("s.status = 1 AND s.onlyMe = 0 AND s.type <> 3"),
                eq(7L), eq(0), eq(15)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());
        when(fixture.jdbc.queryForList(startsWith("SELECT spaceMinExp")))
                .thenReturn(Collections.singletonList(config()));

        SpaceService.SpacePage page = fixture.service.followed(1, 15, "token");

        assertThat(page.getTotal()).isZero();
        verify(fixture.jdbc).queryForObject(
                contains("s.status = 1 AND s.onlyMe = 0 AND s.type <> 3"),
                eq(Integer.class), eq(7L));
        verify(fixture.jdbc).queryForList(
                contains("s.status = 1 AND s.onlyMe = 0 AND s.type <> 3"),
                eq(7L), eq(0), eq(15));
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedLikeCounterCompensatesInsertedMyisamLog() throws Exception {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        Connection connection = mock(Connection.class);
        PreparedStatement lock = statementWithSingleIntResult(1);
        PreparedStatement target = mock(PreparedStatement.class);
        ResultSet targetResult = mock(ResultSet.class);
        when(target.executeQuery()).thenReturn(targetResult);
        when(targetResult.next()).thenReturn(true);
        when(targetResult.getLong("uid")).thenReturn(8L);
        when(targetResult.getInt("status")).thenReturn(1);
        when(targetResult.getInt("onlyMe")).thenReturn(0);
        PreparedStatement duplicate = statementWithSingleIntResult(0);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet generatedKeys = mock(ResultSet.class);
        when(insert.executeUpdate()).thenReturn(1);
        when(insert.getGeneratedKeys()).thenReturn(generatedKeys);
        when(generatedKeys.next()).thenReturn(true);
        when(generatedKeys.getLong(1)).thenReturn(55L);
        PreparedStatement update = mock(PreparedStatement.class);
        when(update.executeUpdate()).thenReturn(0);
        PreparedStatement compensate = mock(PreparedStatement.class);
        when(compensate.executeUpdate()).thenReturn(1);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet released = mock(ResultSet.class);
        when(release.executeQuery()).thenReturn(released);

        when(connection.prepareStatement(startsWith("SELECT GET_LOCK"))).thenReturn(lock);
        when(connection.prepareStatement(startsWith("SELECT uid,status"))).thenReturn(target);
        when(connection.prepareStatement(startsWith("SELECT COUNT(*) FROM starfree_userlog")))
                .thenReturn(duplicate);
        when(connection.prepareStatement(startsWith("INSERT INTO starfree_userlog"),
                eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(insert);
        when(connection.prepareStatement(startsWith("UPDATE starfree_space SET likes")))
                .thenReturn(update);
        when(connection.prepareStatement(startsWith("DELETE FROM starfree_userlog")))
                .thenReturn(compensate);
        when(connection.prepareStatement(startsWith("SELECT RELEASE_LOCK"))).thenReturn(release);
        when(fixture.jdbc.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            ConnectionCallback<Integer> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });

        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("id", "11");

        assertThatThrownBy(() -> fixture.service.like(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Space like counter update failed");

        verify(compensate).setLong(1, 55L);
        verify(compensate).executeUpdate();
        verify(release).executeQuery();
    }

    @Test
    @SuppressWarnings("unchecked")
    void existingLikeLogRejectsDuplicateWithoutCounterWriteAndReleasesLock() throws Exception {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        Connection connection = mock(Connection.class);
        PreparedStatement lock = statementWithSingleIntResult(1);
        PreparedStatement target = mock(PreparedStatement.class);
        ResultSet targetResult = mock(ResultSet.class);
        when(target.executeQuery()).thenReturn(targetResult);
        when(targetResult.next()).thenReturn(true);
        when(targetResult.getLong("uid")).thenReturn(8L);
        when(targetResult.getInt("status")).thenReturn(1);
        when(targetResult.getInt("onlyMe")).thenReturn(0);
        PreparedStatement duplicate = statementWithSingleIntResult(1);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet released = mock(ResultSet.class);
        when(release.executeQuery()).thenReturn(released);

        when(connection.prepareStatement(startsWith("SELECT GET_LOCK"))).thenReturn(lock);
        when(connection.prepareStatement(startsWith("SELECT uid,status"))).thenReturn(target);
        when(connection.prepareStatement(startsWith("SELECT COUNT(*) FROM starfree_userlog")))
                .thenReturn(duplicate);
        when(connection.prepareStatement(startsWith("SELECT RELEASE_LOCK"))).thenReturn(release);
        when(fixture.jdbc.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            ConnectionCallback<Integer> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });

        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("id", "11");

        assertThatThrownBy(() -> fixture.service.like(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u4f60\u5df2\u7ecf\u70b9\u8d5e\u8fc7\u4e86");

        // A durable legacy log is authoritative: no second log and no counter change.
        verify(connection, never()).prepareStatement(
                startsWith("INSERT INTO starfree_userlog"),
                eq(Statement.RETURN_GENERATED_KEYS));
        verify(connection, never()).prepareStatement(startsWith("UPDATE starfree_space SET likes"));
        verify(release).executeQuery();
    }

    private static PreparedStatement statementWithSingleIntResult(int value) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(value);
        return statement;
    }

    private static Map<String, Object> space(long id, long uid, int status,
                                             int onlyMe, int type) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("uid", uid);
        row.put("created", 100L);
        row.put("modified", 100L);
        row.put("text", "test space");
        row.put("type", type);
        row.put("views", 0);
        row.put("likes", 0);
        row.put("toid", 0);
        row.put("status", status);
        row.put("onlyMe", onlyMe);
        return row;
    }

    private static Map<String, Object> config() {
        Map<String, Object> row = new HashMap<>();
        row.put("spaceMinExp", 0);
        row.put("spaceAudit", 0);
        row.put("postMax", 999);
        row.put("postExp", 0);
        row.put("forbidden", "");
        row.put("identifysmPost", 0);
        row.put("identifylvPost", 0);
        row.put("banRobots", 0);
        row.put("silenceTime", 600);
        row.put("interceptTime", 3600);
        return row;
    }

    private static Map<String, String> addRequest(String text) {
        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("type", "0");
        request.put("text", text);
        request.put("onlyMe", "0");
        return request;
    }

    private static Map<String, String> lockRequest(String type) {
        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("id", "11");
        request.put("type", type);
        return request;
    }

    private static Map<String, String> deleteRequest() {
        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("id", "11");
        return request;
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final LegacyTokenService tokens = mock(LegacyTokenService.class);
        private final LegacySpaceAbuseGuard abuseGuard = mock(LegacySpaceAbuseGuard.class);
        private final SpaceService service = new SpaceService(
                jdbc, new ObjectMapper(), tokens, abuseGuard);

        private Fixture() {
            when(abuseGuard.reservePost(anyLong(), anyBoolean(), anyInt(), anyInt()))
                    .thenReturn(LegacySpaceAbuseGuard.PostReservation.noop());
        }

        private void login(long uid, String group) {
            Map<String, Object> user = new HashMap<>();
            user.put("uid", uid);
            user.put("group", group);
            user.put("experience", 100);
            when(tokens.userId("token")).thenReturn(uid);
            when(tokens.userById(uid)).thenReturn(user);
        }
    }
}
