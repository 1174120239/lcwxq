package cn.lcxqy.starfree.lostfound;

import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LostFoundServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LegacyTokenService tokens;

    private LostFoundService service;

    @BeforeEach
    void setUp() {
        StaffAccess access = new StaffAccess(tokens);
        lenient().when(jdbc.queryForList(startsWith("SELECT enabled,minimum_level")))
                .thenReturn(Collections.<Map<String, Object>>emptyList());
        service = new LostFoundService(jdbc, access, tokens,
                new LostFoundConfigService(jdbc, access, tokens));
    }

    @Test
    void publicListUsesAllowlistedFiltersAndParameterizedKeyword() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Long.class), eq(1), eq(2),
                eq("%校园卡%"), eq("%校园卡%"), eq("%校园卡%"))).thenReturn(1);
        when(jdbc.queryForList(contains("ORDER BY i.modified"),
                any(Long.class), eq(1), eq(2), eq("%校园卡%"), eq("%校园卡%"),
                eq("%校园卡%"), eq(0), eq(10)))
                .thenReturn(Collections.singletonList(item(9, 7, 1)));
        when(tokens.publicUserById(7)).thenReturn(user(7, "student", "contributor"));
        Map<String, String> request = row("kind", "1", "category", "2", "keyword", "校园卡");

        LostFoundService.Page page = service.itemList(request);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getData()).singleElement().satisfies(item -> assertThat(item)
                .containsEntry("id", 9L).containsEntry("status", 1L));
    }

    @Test
    void pendingItemIsHiddenFromAnonymousViewer() {
        when(jdbc.queryForList(contains("WHERE i.id=?"), eq(9L)))
                .thenReturn(Collections.singletonList(item(9, 7, 0)));

        assertThrows(IllegalArgumentException.class, () -> service.itemInfo(9, ""));
    }

    @Test
    void addDerivesOwnerAndPendingStatusFromToken() {
        authenticated("owner-token", 7, "contributor");
        when(jdbc.queryForObject(contains("WHERE uid=?"), eq(Integer.class),
                eq(7L), eq(1), eq("丢失的黑色水杯"), any(Long.class))).thenReturn(0);
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder holder = invocation.getArgument(1);
                    holder.getKeyList().add(Collections.<String, Object>singletonMap("GENERATED_KEY", 9L));
                    return 1;
                });
        when(jdbc.queryForList(contains("WHERE i.id=?"), eq(9L)))
                .thenReturn(Collections.singletonList(item(9, 7, 0)));
        when(tokens.user("owner-token")).thenReturn(user(7, "student", "contributor"));
        when(tokens.publicUserById(7)).thenReturn(user(7, "student", "contributor"));
        Map<String, Object> body = values();
        body.put("uid", 999);
        body.put("status", 1);

        Map<String, Object> result = service.itemAdd("owner-token", body);

        assertThat(result).containsEntry("uid", 7L).containsEntry("status", 0L);
        verify(jdbc).update(contains("starfree_lost_found_actions"),
                eq(9L), eq(7L), eq(0), eq(0), eq("create"), eq(""), any(Long.class));
    }

    @Test
    void ordinaryUserCannotEditAnotherUsersItem() {
        authenticated("owner-token", 7, "contributor");
        when(jdbc.queryForList(contains("WHERE i.id=?"), eq(9L)))
                .thenReturn(Collections.singletonList(item(9, 8, 1)));
        Map<String, Object> body = values();
        body.put("id", 9);

        assertThrows(IllegalArgumentException.class, () -> service.itemEdit("owner-token", body));

        verify(jdbc, never()).update(contains("UPDATE starfree_lost_found_items SET kind"),
                any(Object[].class));
    }

    @Test
    void ownerCanResolveOnlyAnActiveItem() {
        authenticated("owner-token", 7, "contributor");
        when(jdbc.queryForList(contains("WHERE i.id=?"), eq(9L)))
                .thenReturn(Collections.singletonList(item(9, 7, 0)));

        assertThrows(IllegalArgumentException.class,
                () -> service.itemStatus("owner-token", 9, "resolve"));

        verify(jdbc, never()).update(contains("SET status=?"), any(Object[].class));
    }

    @Test
    void staffRejectionRequiresReasonBeforeWrite() {
        authenticated("staff-token", 2, "editor");
        when(jdbc.queryForList(contains("WHERE i.id=?"), eq(9L)))
                .thenReturn(Collections.singletonList(item(9, 7, 0)));

        assertThrows(IllegalArgumentException.class,
                () -> service.itemAudit("staff-token", 9, "reject", ""));

        verify(jdbc, never()).update(contains("review_reason"), any(Object[].class));
    }

    @Test
    void staffCannotApproveClosedItem() {
        authenticated("staff-token", 2, "editor");
        when(jdbc.queryForList(contains("WHERE i.id=?"), eq(9L)))
                .thenReturn(Collections.singletonList(item(9, 7, 4)));

        assertThrows(IllegalArgumentException.class,
                () -> service.itemAudit("staff-token", 9, "approve", ""));

        verify(jdbc, never()).update(contains("review_reason"), any(Object[].class));
    }

    private void authenticated(String token, long uid, String group) {
        when(tokens.userId(token)).thenReturn(uid);
        when(tokens.userById(uid)).thenReturn(user(uid, "user" + uid, group));
    }

    private Map<String, Object> values() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("kind", 1);
        values.put("category", 5);
        values.put("title", "丢失的黑色水杯");
        values.put("description", "杯子侧面有姓名贴纸");
        values.put("location", "高二教学楼");
        values.put("occurredAt", 0);
        return values;
    }

    private Map<String, Object> item(long id, long uid, int status) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", id);
        item.put("uid", uid);
        item.put("kind", 1);
        item.put("category", 5);
        item.put("title", "丢失的黑色水杯");
        item.put("description", "杯子侧面有姓名贴纸");
        item.put("image_url", "");
        item.put("location", "高二教学楼");
        item.put("occurred_at", 0);
        item.put("status", status);
        item.put("review_reason", "");
        item.put("created", 100);
        item.put("modified", 100);
        return item;
    }

    private Map<String, Object> user(long uid, String name, String group) {
        Map<String, Object> user = new LinkedHashMap<String, Object>();
        user.put("uid", uid);
        user.put("name", name);
        user.put("group", group);
        user.put("bantime", 0);
        user.put("experience", 50);
        return user;
    }

    private Map<String, String> row(String... values) {
        Map<String, String> row = new LinkedHashMap<String, String>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(values[index], values[index + 1]);
        }
        return row;
    }
}
