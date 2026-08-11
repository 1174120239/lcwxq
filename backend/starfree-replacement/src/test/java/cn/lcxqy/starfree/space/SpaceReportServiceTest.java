package cn.lcxqy.starfree.space;

import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceReportServiceTest {
    @Test
    void userCanReportVisibleDynamicOnce() {
        Fixture fixture = new Fixture("visitor");
        when(fixture.jdbc.queryForList(contains("FROM starfree_space WHERE id=?"), eq(11L)))
                .thenReturn(Collections.singletonList(row(
                        "id", 11L, "uid", 9L, "type", 0, "status", 1, "onlyMe", 0)));
        when(fixture.jdbc.queryForObject(contains("starfree_space_reports"), eq(Integer.class),
                eq(11L), eq(7L))).thenReturn(0);
        when(fixture.jdbc.update(contains("INSERT INTO starfree_space_reports"),
                eq(11L), eq(7L), eq("广告营销"), eq(""),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(1);

        Map<String, String> request = request("user-token", 11L);
        request.put("reason", "广告营销");

        assertThat(fixture.service.add(request)).isEqualTo(1);
        verify(fixture.jdbc).update(contains("INSERT INTO starfree_space_reports"),
                eq(11L), eq(7L), eq("广告营销"), eq(""),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void duplicateReportIsRejectedBeforeInsert() {
        Fixture fixture = new Fixture("visitor");
        when(fixture.jdbc.queryForList(contains("FROM starfree_space WHERE id=?"), eq(11L)))
                .thenReturn(Collections.singletonList(row(
                        "id", 11L, "uid", 9L, "type", 0, "status", 1, "onlyMe", 0)));
        when(fixture.jdbc.queryForObject(contains("starfree_space_reports"), eq(Integer.class),
                eq(11L), eq(7L))).thenReturn(1);
        Map<String, String> request = request("user-token", 11L);
        request.put("reason", "其他");

        assertThatThrownBy(() -> fixture.service.add(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("你已经举报过这条动态");
        verify(fixture.jdbc, never()).update(contains("INSERT INTO starfree_space_reports"),
                org.mockito.ArgumentMatchers.<Object[]>any());
    }

    @Test
    void ordinaryUserCannotReadReviewQueue() {
        Fixture fixture = new Fixture("visitor");
        Map<String, String> request = new HashMap<String, String>();
        request.put("token", "user-token");

        assertThatThrownBy(() -> fixture.service.list(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("你没有操作权限");
    }

    @Test
    void staffDeleteReviewUsesExistingDynamicDeletionAndClosesAllPendingReports() {
        Fixture fixture = new Fixture("editor");
        when(fixture.jdbc.queryForList(contains("FROM starfree_space_reports"), eq(5L)))
                .thenReturn(Collections.singletonList(row(
                        "id", 5L, "space_id", 11L, "reporter_uid", 8L, "status", 0)));
        when(fixture.jdbc.queryForObject(contains("FROM starfree_space WHERE id=?"),
                eq(Integer.class), eq(11L))).thenReturn(1);
        when(fixture.spaces.delete(org.mockito.ArgumentMatchers.<Map<String, String>>any()))
                .thenReturn(1);
        when(fixture.jdbc.update(contains("UPDATE starfree_space_reports SET status=1"),
                eq(7L), eq(""), org.mockito.ArgumentMatchers.anyLong(), eq(11L))).thenReturn(2);

        Map<String, String> request = request("user-token", 5L);
        request.put("action", "delete");

        assertThat(fixture.service.review(request)).isEqualTo(2);
        verify(fixture.spaces).delete(org.mockito.ArgumentMatchers.<Map<String, String>>any());
    }

    private static Map<String, String> request(String token, long id) {
        Map<String, String> request = new HashMap<String, String>();
        request.put("token", token);
        request.put("id", String.valueOf(id));
        return request;
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final LegacyTokenService tokens = mock(LegacyTokenService.class);
        private final StaffAccess access = new StaffAccess(tokens);
        private final SpaceService spaces = mock(SpaceService.class);
        private final SpaceReportService service;

        private Fixture(String group) {
            when(tokens.userId("user-token")).thenReturn(7L);
            when(tokens.userById(7L)).thenReturn(row(
                    "uid", 7L, "name", "account", "screenName", "审核员",
                    "group", group, "avatar", "", "mail", ""));
            service = new SpaceReportService(jdbc, access, tokens, spaces);
        }
    }
}
