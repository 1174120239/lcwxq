package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.security.LegacyTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserInteractionServiceTest {

    @Test
    void duplicateFollowDoesNotInsertAnotherRelation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        when(tokens.userById(9L)).thenReturn(Collections.<String, Object>singletonMap("uid", 9L));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(7L), eq(9L))).thenReturn(1);

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("touid", "9");
        request.put("type", "1");

        int changed = new UserInteractionService(jdbc, tokens).follow(request);

        assertThat(changed).isZero();
        verify(jdbc, never()).update(anyString(), eq(7L), eq(9L));
    }

    @Test
    void followRejectsSelfBeforeDatabaseWrites() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        when(tokens.userById(7L)).thenReturn(Collections.<String, Object>singletonMap("uid", 7L));

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("touid", "7");
        request.put("type", "1");

        assertThatThrownBy(() -> new UserInteractionService(jdbc, tokens).follow(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("You cannot follow yourself");
        verify(jdbc, never()).update(anyString(), eq(7L), eq(7L));
    }

    @Test
    void commentReadFilterIncludesDynamicCommentNotifications() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        when(jdbc.update(anyString(), eq(7L))).thenReturn(3);

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("type", "comment");

        int changed = new UserInteractionService(jdbc, tokens).markRead(request);

        assertThat(changed).isEqualTo(3);
        verify(jdbc).update(contains("'spaceComment'"), eq(7L));
        verify(jdbc).update(contains("'qaComment'"), eq(7L));
    }

    @Test
    void marksOnlyRequestedNotificationOwnedByCurrentUser() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        when(jdbc.update(anyString(), eq(42), eq(7L))).thenReturn(1);

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("id", "42");

        int changed = new UserInteractionService(jdbc, tokens).markRead(request);

        assertThat(changed).isEqualTo(1);
        verify(jdbc).update(contains("id = ? AND touid = ? AND isread = 0"), eq(42), eq(7L));
    }

    @Test
    void rejectsInvalidNotificationIdWithoutBulkUpdate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("id", "invalid");

        assertThatThrownBy(() -> new UserInteractionService(jdbc, tokens).markRead(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid notification id");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void commentTypeFilterIncludesDynamicCommentNotifications() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("type", "comment");

        new UserInteractionService(jdbc, tokens).inbox(request);

        verify(jdbc).queryForObject(contains("'spaceComment'"), eq(Integer.class), any());
        verify(jdbc).queryForObject(contains("'qaComment'"), eq(Integer.class), any());
    }

    @Test
    void inboxRendersVisibleDynamicCommentNotifications() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("uid", 7L);
        actor.put("name", "Actor");
        actor.put("avatar", "http://avatar");
        actor.put("vip", 0L);
        actor.put("group", "visitor");
        when(tokens.publicUserById(7L)).thenReturn(actor);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("type", "spaceComment");
        row.put("uid", 7L);
        row.put("text", "评论了你的动态：hi");
        row.put("touid", 8L);
        row.put("isread", 0);
        row.put("value", 11L);
        row.put("created", 1700000000L);
        row.put("cid", 22L);
        when(jdbc.queryForList(startsWith("SELECT id,type,uid,text,touid,isread,value,created,cid"),
                any(Object.class))).thenReturn(Collections.singletonList(row));

        Map<String, Object> space = new LinkedHashMap<>();
        space.put("id", 11L);
        space.put("uid", 8L);
        space.put("text", "original dynamic");
        space.put("type", 0);
        space.put("status", 1);
        space.put("onlyMe", 0);
        when(jdbc.queryForList(startsWith("SELECT id,uid,text,type,status,onlyMe"),
                any(Object.class))).thenReturn(Collections.singletonList(space));

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("limit", "8");
        request.put("page", "1");

        UserInteractionService.InboxPage page = new UserInteractionService(jdbc, tokens).inbox(request);
        List<Map<String, Object>> data = page.getData();

        assertThat(data).hasSize(1);
        assertThat(data.get(0)).containsEntry("type", "spaceComment")
                .containsEntry("spaceState", "visible")
                .containsKey("spaceInfo");
        assertThat(data.get(0).get("spaceInfo")).isNotNull();
    }

    @Test
    void inboxMarksDeletedDynamicCommentSource() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("uid", 7L);
        actor.put("name", "Actor");
        actor.put("avatar", "http://avatar");
        actor.put("vip", 0L);
        actor.put("group", "visitor");
        when(tokens.publicUserById(7L)).thenReturn(actor);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("type", "spaceComment");
        row.put("uid", 7L);
        row.put("text", "评论了你的动态：hi");
        row.put("touid", 8L);
        row.put("isread", 0);
        row.put("value", 11L);
        row.put("created", 1700000000L);
        row.put("cid", 22L);
        when(jdbc.queryForList(startsWith("SELECT id,type,uid,text,touid,isread,value,created,cid"),
                any(Object.class))).thenReturn(Collections.singletonList(row));
        when(jdbc.queryForList(startsWith("SELECT id,uid,text,type,status,onlyMe"),
                any(Object.class))).thenReturn(Collections.emptyList());

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");

        UserInteractionService.InboxPage page = new UserInteractionService(jdbc, tokens).inbox(request);
        List<Map<String, Object>> data = page.getData();

        assertThat(data).hasSize(1);
        assertThat(data.get(0)).containsEntry("spaceState", "deleted");
        assertThat(data.get(0).get("spaceInfo")).isNull();
    }

    @Test
    void inboxUsesRenderablePlaceholderWhenActorNoLongerExists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(8L);
        when(tokens.publicUserById(7L)).thenReturn(null);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("type", "system");
        row.put("uid", 7L);
        row.put("text", "notification");
        row.put("touid", 8L);
        row.put("isread", 0);
        row.put("value", 0L);
        row.put("created", 1700000000L);
        row.put("cid", 0L);
        when(jdbc.queryForList(startsWith("SELECT id,type,uid,text,touid,isread,value,created,cid"),
                any(Object.class))).thenReturn(Collections.singletonList(row));

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");

        Map<String, Object> notification = new UserInteractionService(jdbc, tokens)
                .inbox(request).getData().get(0);

        assertThat((Map<String, Object>) notification.get("userJson"))
                .containsEntry("uid", 7L)
                .containsEntry("name", "已注销用户")
                .containsEntry("avatar", "");
    }

    @Test
    void inboxEnrichesVisibleQuestionNotification() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(8L);
        when(tokens.publicUserById(7L)).thenReturn(Collections.<String, Object>singletonMap("name", "Answerer"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("type", "qaComment");
        row.put("uid", 7L);
        row.put("text", "评论了你的回答");
        row.put("touid", 8L);
        row.put("isread", 0);
        row.put("value", 44L);
        row.put("created", 1700000000L);
        row.put("cid", 55L);
        when(jdbc.queryForList(startsWith("SELECT id,type,uid,text,touid,isread,value,created,cid"),
                any(Object.class))).thenReturn(Collections.singletonList(row));
        when(jdbc.queryForList(startsWith("SELECT id,title,status FROM starfree_qa_questions"),
                eq(44L))).thenReturn(Collections.singletonList(row(
                        "id", 44L, "title", "校园问题", "status", 1)));

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");

        Map<String, Object> notification = new UserInteractionService(jdbc, tokens)
                .inbox(request).getData().get(0);

        assertThat(notification).containsEntry("questionState", "visible");
        assertThat((Map<String, Object>) notification.get("questionInfo"))
                .containsEntry("title", "校园问题");
    }

    @Test
    void inboxExposesExactAnswerAndCommentTargetsForQuestionReply() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(8L);
        when(tokens.publicUserById(7L)).thenReturn(Collections.<String, Object>singletonMap("name", "评论者"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);

        Map<String, Object> row = row(
                "id", 2L, "type", "qaComment", "uid", 7L, "text", "回复了你的评论：收到",
                "touid", 8L, "isread", 0, "value", 44L, "created", 1700000000L, "cid", 99L);
        when(jdbc.queryForList(startsWith("SELECT id,type,uid,text,touid,isread,value,created,cid"),
                any(Object.class))).thenReturn(Collections.singletonList(row));
        when(jdbc.queryForList(startsWith("SELECT id,title,status FROM starfree_qa_questions"),
                eq(44L))).thenReturn(Collections.singletonList(row("id", 44L, "title", "校园问题", "status", 1)));
        when(jdbc.queryForList(startsWith("SELECT c.id,c.answer_id,c.status FROM starfree_qa_comments"),
                eq(99L), eq(44L), eq(7L), eq(1700000000L)))
                .thenReturn(Collections.singletonList(row("id", 99L, "answer_id", 55L, "status", 1)));

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");

        Map<String, Object> notification = new UserInteractionService(jdbc, tokens)
                .inbox(request).getData().get(0);

        assertThat(notification).containsEntry("answerId", 55L).containsEntry("commentId", 99L);
    }

    @Test
    void inboxPrefersNicknameAndBuildsQqAvatar() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId("valid-token")).thenReturn(8L);
        when(tokens.publicUserById(7L)).thenReturn(row(
                "uid", 7L, "name", "3910886328", "screenName", "校园昵称",
                "mail", "3910886328@qq.com", "avatar", "", "vip", 0L,
                "group", "visitor"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(1);
        when(jdbc.queryForList(startsWith("SELECT id,type,uid,text,touid,isread,value,created,cid"),
                any(Object.class))).thenReturn(Collections.singletonList(row(
                        "id", 1L, "type", "system", "uid", 7L, "text", "notice",
                        "touid", 8L, "isread", 0, "value", 0L,
                        "created", 1700000000L, "cid", 0L)));

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");

        Map<String, Object> notification = new UserInteractionService(jdbc, tokens)
                .inbox(request).getData().get(0);
        Map<String, Object> user = (Map<String, Object>) notification.get("userJson");

        assertThat(user).containsEntry("name", "校园昵称")
                .containsEntry("avatar", "https://q1.qlogo.cn/g?b=qq&nk=3910886328&s=640");
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
