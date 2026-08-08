package cn.lcxqy.starfree.anonymous;

import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import cn.lcxqy.starfree.space.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnonymousPostServiceTest {

    @Test
    void publicConfigHidesIdentityAndDisablesWhenNoAnonymousAccount() {
        Fixture fixture = new Fixture();
        fixture.configRow(0, 0);

        Map<String, Object> config = fixture.service.publicConfig();

        assertThat(config).containsEntry("enabled", false);
        assertThat(config.keySet()).containsExactly("enabled");
    }

    @Test
    void publicConfigReportsEnabledWhenAnonymousAccountConfigured() {
        Fixture fixture = new Fixture();
        fixture.configRow(5, 1);

        Map<String, Object> config = fixture.service.publicConfig();

        assertThat(config).containsEntry("enabled", true);
        assertThat(config.keySet()).containsExactly("enabled");
    }

    @Test
    void postRequiresLogin() {
        Fixture fixture = new Fixture();
        when(fixture.tokens.userId("")).thenReturn(null);

        assertThatThrownBy(() -> fixture.service.post(new HashMap<>(), "1.2.3.4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户未登录");
    }

    @Test
    void postRejectsWhenAnonymousDisabled() {
        Fixture fixture = new Fixture();
        fixture.login(7L);
        fixture.configRow(0, 0);

        Map<String, String> request = new HashMap<>();
        request.put("token", "user-token");
        request.put("text", "匿名动态正文");

        assertThatThrownBy(() -> fixture.service.post(request, "1.2.3.4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("暂未开放");
    }

    @Test
    void postDelegatesToSpaceServiceWithAnonymousAccountAndReview() {
        Fixture fixture = new Fixture();
        fixture.login(7L);
        fixture.configRow(5, 1);
        when(fixture.tokens.userById(5L)).thenReturn(user(5L));
        when(fixture.spaces.addAnonymous(any(Map.class), eq("1.2.3.4"), eq(5L), eq(true)))
                .thenReturn(true);

        Map<String, String> request = new HashMap<>();
        request.put("token", "user-token");
        request.put("text", "匿名动态正文");

        Map<String, Object> result = fixture.service.post(request, "1.2.3.4");

        assertThat(result).containsEntry("status", "waiting");
        verify(fixture.spaces).addAnonymous(eq(request), eq("1.2.3.4"), eq(5L), eq(true));
    }

    @Test
    void postReportsPublishWhenReviewOffAndGlobalRulesAllow() {
        Fixture fixture = new Fixture();
        fixture.login(7L);
        fixture.configRow(5, 0);
        when(fixture.tokens.userById(5L)).thenReturn(user(5L));
        when(fixture.spaces.addAnonymous(any(Map.class), anyString(), anyLong(), eq(false)))
                .thenReturn(false);

        Map<String, String> request = new HashMap<>();
        request.put("token", "user-token");
        request.put("text", "匿名动态正文");

        Map<String, Object> result = fixture.service.post(request, "1.2.3.4");

        assertThat(result).containsEntry("status", "publish");
    }

    @Test
    void ownerIsOnlyVisibleToPosterOrStaff() {
        Fixture fixture = new Fixture();
        fixture.login(7L);
        when(fixture.jdbc.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(7L));

        Map<String, Object> result = fixture.service.owner("user-token", 11L);

        assertThat(result).containsEntry("uid", 7L);
        assertThat(result).containsEntry("screenName", "匿名小号");
    }

    @Test
    void ownerThrowsForOtherUsersToAvoidIdentityEnumeration() {
        Fixture fixture = new Fixture();
        fixture.login(9L);
        when(fixture.jdbc.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.singletonList(7L));

        assertThatThrownBy(() -> fixture.service.owner("user-token", 11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权查看");
    }

    @Test
    void ownerRejectsNonAnonymousDynamics() {
        Fixture fixture = new Fixture();
        fixture.login(7L);
        when(fixture.jdbc.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> fixture.service.owner("user-token", 11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是匿名动态");
    }

    @Test
    void adminConfigRequiresAdministrator() {
        Fixture fixture = new Fixture();
        doThrow(new IllegalArgumentException("你没有操作权限"))
                .when(fixture.staff).requireAdministrator("editor-token");

        assertThatThrownBy(() -> fixture.service.adminConfig("editor-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有操作权限");
    }

    @Test
    void adminUpdateValidatesAnonymousAccount() {
        Fixture fixture = new Fixture();
        when(fixture.tokens.userById(5L)).thenReturn(user(5L));

        Map<String, String> form = new HashMap<>();
        form.put("fid", "5");
        form.put("review", "1");
        fixture.service.updateAdminConfig("admin-token", form);

        verify(fixture.jdbc).update(contains("UPDATE starfree_anonymous_config"),
                eq(5L), eq(1), anyLong());
    }

    @Test
    void adminUpdateRejectsMissingAnonymousAccount() {
        Fixture fixture = new Fixture();
        when(fixture.tokens.userById(9L)).thenReturn(null);

        Map<String, String> form = new HashMap<>();
        form.put("fid", "9");
        form.put("review", "0");

        assertThatThrownBy(() -> fixture.service.updateAdminConfig("admin-token", form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("匿名账号不存在");
        verify(fixture.jdbc, never()).update(contains("UPDATE starfree_anonymous_config"),
                any(), any(), any());
    }

    private static Map<String, Object> user(long uid) {
        Map<String, Object> row = new HashMap<>();
        row.put("uid", uid);
        row.put("name", "anonym-owner");
        row.put("screenName", "匿名小号");
        row.put("group", "contributor");
        return row;
    }

    private static final class Fixture {
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final LegacyTokenService tokens = mock(LegacyTokenService.class);
        final StaffAccess staff = mock(StaffAccess.class);
        final SpaceService spaces = mock(SpaceService.class);
        final AnonymousPostService service =
                new AnonymousPostService(jdbc, tokens, staff, spaces);

        Fixture() {
            when(spaces.addAnonymous(any(Map.class), anyString(), anyLong(),
                    org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(false);
        }

        void login(long uid) {
            when(tokens.userId("user-token")).thenReturn(uid);
            when(tokens.userById(uid)).thenReturn(user(uid));
        }

        void configRow(long fid, int review) {
            Map<String, Object> row = new HashMap<>();
            row.put("fid", fid);
            row.put("review", review);
            when(jdbc.queryForList(anyString())).thenReturn(Collections.singletonList(row));
        }
    }
}
