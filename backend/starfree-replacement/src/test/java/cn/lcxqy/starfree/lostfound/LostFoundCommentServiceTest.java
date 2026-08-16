package cn.lcxqy.starfree.lostfound;

import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class LostFoundCommentServiceTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LegacyTokenService tokens;

    private LostFoundCommentService service;

    @BeforeEach
    void setUp() {
        StaffAccess access = new StaffAccess(tokens);
        LostFoundConfigService config = new LostFoundConfigService(jdbc, access, tokens);
        service = new LostFoundCommentService(jdbc, access, tokens, config);
        lenient().when(jdbc.queryForList(contains("FROM starfree_lost_found_config")))
                .thenReturn(Collections.<Map<String, Object>>emptyList());
    }

    @Test
    void publicCommentListNeverContainsPrivateContactData() {
        when(jdbc.queryForList(contains("FROM starfree_lost_found_items"), eq(9L)))
                .thenReturn(Collections.singletonList(item(9, 7, 1)));
        when(jdbc.queryForList(contains("FROM starfree_lost_found_comments WHERE item_id"), eq(9L)))
                .thenReturn(Collections.singletonList(comment(12, 9, 8)));
        when(tokens.publicUserById(8)).thenReturn(user(8, "helper", "contributor", 50));

        List<Map<String, Object>> comments = service.comments(9);

        assertThat(comments).singleElement().satisfies(comment -> assertThat(comment)
                .containsEntry("uid", 8L)
                .doesNotContainKeys("qq", "mail", "contact", "contactCard"));
    }

    @Test
    void commenterCanShareBoundQqOnlyWithItemOwner() {
        authenticated("helper-token", 8, "contributor", 50, "12345678@qq.com");
        when(jdbc.queryForList(contains("FROM starfree_lost_found_items"), eq(9L)))
                .thenReturn(Collections.singletonList(item(9, 7, 1)));
        when(jdbc.queryForList(contains("FROM starfree_lost_found_comments WHERE id"),
                eq(12L), eq(9L))).thenReturn(Collections.singletonList(comment(12, 9, 8)));
        when(jdbc.queryForObject(contains("sender_uid=? AND created>=?"), eq(Integer.class),
                eq(8L), any(Long.class))).thenReturn(0);

        Map<String, Object> result = service.shareContact("helper-token", 9, 12);

        assertThat(result).containsEntry("receiverUid", 7L).doesNotContainKey("qq");
        verify(jdbc).update(contains("INSERT INTO starfree_lost_found_contact_grants"),
                eq(9L), eq(12L), eq(8L), eq(7L), any(Long.class));
    }

    @Test
    void receiverContactAccessContainsQqButSentProjectionDoesNot() {
        authenticated("owner-token", 7, "contributor", 50, "7654321@qq.com");
        when(jdbc.queryForList(contains("FROM starfree_lost_found_items"), eq(9L)))
                .thenReturn(Collections.singletonList(item(9, 7, 1)));
        when(jdbc.queryForList(contains("receiver_uid=? ORDER BY"), eq(9L), eq(7L)))
                .thenReturn(Collections.singletonList(row("id", 2, "comment_id", 12,
                        "sender_uid", 8, "receiver_uid", 7, "created", 100, "viewed", 0)));
        when(jdbc.queryForList(contains("sender_uid=? ORDER BY"), eq(9L), eq(7L)))
                .thenReturn(Collections.singletonList(row("comment_id", 15,
                        "receiver_uid", 10, "created", 101)));
        when(tokens.userById(8)).thenReturn(user(8, "helper", "contributor", 50,
                "12345678@qq.com"));
        when(tokens.publicUserById(8)).thenReturn(user(8, "helper", "contributor", 50));

        Map<String, Object> access = service.contactAccess("owner-token", 9);

        assertThat((List<Map<String, Object>>) access.get("received"))
                .singleElement().satisfies(grant -> assertThat(grant).containsEntry("qq", "12345678"));
        assertThat((List<Map<String, Object>>) access.get("sent"))
                .singleElement().satisfies(grant -> assertThat(grant).doesNotContainKey("qq"));
    }

    private void authenticated(String token, long uid, String group, long experience, String mail) {
        when(tokens.userId(token)).thenReturn(uid);
        when(tokens.userById(uid)).thenReturn(user(uid, "user" + uid, group, experience, mail));
    }

    private Map<String, Object> item(long id, long uid, int status) {
        return row("id", id, "uid", uid, "status", status,
                "created", Instant.now().getEpochSecond(), "modified", Instant.now().getEpochSecond());
    }

    private Map<String, Object> comment(long id, long itemId, long uid) {
        return row("id", id, "item_id", itemId, "uid", uid, "parent_id", 0,
                "root_id", 0, "text", "我可以帮忙", "status", 1, "created", 100, "modified", 100);
    }

    private Map<String, Object> user(long uid, String name, String group, long experience) {
        return user(uid, name, group, experience, "");
    }

    private Map<String, Object> user(long uid, String name, String group,
                                     long experience, String mail) {
        return row("uid", uid, "name", name, "group", group, "experience", experience,
                "bantime", 0, "mail", mail, "avatar", "");
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }
}
