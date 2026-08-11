package cn.lcxqy.starfree.qa;

import cn.lcxqy.starfree.push.UniPushService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaServiceTest {
    @Test
    void publicQuestionListOnlyReadsEnabledQuestions() {
        Fixture fixture = new Fixture();
        when(fixture.jdbc.queryForObject(contains("q.status=1"), eq(Integer.class), any()))
                .thenReturn(1);
        when(fixture.jdbc.queryForList(contains("q.status=1"), eq((Object) 0), eq((Object) 6)))
                .thenReturn(Collections.singletonList(row(
                        "id", 3L, "title", "食堂哪道菜最值得推荐？", "description", "",
                        "topic", "校园生活", "cover_url", "", "status", 1,
                        "recommended", 1, "sort_order", 20, "created_by", 2,
                        "created", 1700000000L, "modified", 1700000000L, "answer_count", 4)));

        QaService.Page page = fixture.service.questionList(new HashMap<String, String>());

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0)).containsEntry("answerCount", 4L);
    }

    @Test
    void questionManagementRequiresStaff() {
        Fixture fixture = new Fixture();
        Map<String, String> request = new HashMap<String, String>();
        request.put("token", "staff-token");
        when(fixture.jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        when(fixture.jdbc.queryForList(anyString(), eq((Object) 0), eq((Object) 20)))
                .thenReturn(Collections.<Map<String, Object>>emptyList());

        fixture.service.questionManage(request);

        verify(fixture.access).requireStaff("staff-token");
    }

    @Test
    void ordinaryUserCannotSaveQuestion() {
        Fixture fixture = new Fixture();
        when(fixture.access.requireStaff("user-token"))
                .thenThrow(new IllegalArgumentException("你没有操作权限"));

        assertThatThrownBy(() -> fixture.service.questionSave("user-token", row(
                "title", "这是一个校园问题", "description", "问题说明")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("你没有操作权限");

        verify(fixture.jdbc, never()).update(contains("starfree_qa_questions"), any(Object[].class));
    }

    @Test
    void ordinaryUserQuestionIsAlwaysCreatedPendingReview() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.access.requireUser("user-token")).thenReturn(actor(7L, "contributor", 0L));
        when(fixture.jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM starfree_qa_questions"),
                eq(Integer.class), eq(7L), eq("校园里哪里适合安静自习？"), eq("希望晚上也开放"), anyLong()))
                .thenReturn(0);
        when(fixture.jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
                .thenAnswer(invocation -> {
                    KeyHolder holder = invocation.getArgument(1);
                    holder.getKeyList().add(Collections.<String, Object>singletonMap("GENERATED_KEY", 42L));
                    return 1;
                });

        Map<String, Object> result = fixture.service.questionAdd("user-token", row(
                "title", "校园里哪里适合安静自习？", "description", "希望晚上也开放", "topic", "学习",
                "status", 1, "recommended", 1, "sortOrder", 999, "createdBy", 999));

        assertThat(result).containsEntry("id", 42L).containsEntry("status", 0)
                .containsEntry("createdBy", 7L);
        ArgumentCaptor<PreparedStatementCreator> creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(fixture.jdbc).update(creator.capture(), any(KeyHolder.class));
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(statement);
        creator.getValue().createPreparedStatement(connection);
        verify(statement).setObject(1, "校园里哪里适合安静自习？");
        verify(statement).setObject(2, "希望晚上也开放");
        verify(statement).setObject(3, "学习");
        verify(statement).setObject(4, 7L);
    }

    @Test
    void duplicateQuestionIsRejectedBeforeInsert() {
        Fixture fixture = new Fixture();
        when(fixture.access.requireUser("user-token")).thenReturn(actor(7L, "contributor", 0L));
        when(fixture.jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM starfree_qa_questions"),
                eq(Integer.class), eq(7L), eq("校园里哪里适合安静自习？"), eq(""), anyLong()))
                .thenReturn(1);

        assertThatThrownBy(() -> fixture.service.questionAdd("user-token", row(
                "title", "校园里哪里适合安静自习？")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("问题已提交，请勿重复发送");

        verify(fixture.jdbc, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void unauthenticatedOrBannedUserCannotCreateQuestion() {
        Fixture fixture = new Fixture();
        when(fixture.access.requireUser("missing-token"))
                .thenThrow(new IllegalArgumentException("用户未登录或Token验证失败"));
        when(fixture.access.requireUser("banned-token")).thenReturn(actor(8L, "contributor", 1L));

        assertThatThrownBy(() -> fixture.service.questionAdd("missing-token", row(
                "title", "校园里哪里适合安静自习？")))
                .hasMessage("用户未登录或Token验证失败");
        assertThatThrownBy(() -> fixture.service.questionAdd("banned-token", row(
                "title", "校园里哪里适合安静自习？")))
                .hasMessage("账号当前不可发布内容");

        verify(fixture.jdbc, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void shortAnswerIsRejectedBeforeDatabaseInsert() {
        Fixture fixture = new Fixture();
        when(fixture.access.requireUser("user-token")).thenReturn(null);

        assertThatThrownBy(() -> fixture.service.answerAdd("user-token", row(
                "questionId", 1L, "text", "好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("回答至少需要4个字");

        verify(fixture.jdbc, never()).update(contains("starfree_qa_answers"), any(Object[].class));
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static StaffAccess.Actor actor(long uid, String group, long bannedUntil) {
        try {
            Constructor<StaffAccess.Actor> constructor = StaffAccess.Actor.class.getDeclaredConstructor(
                    long.class, String.class, String.class, Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(uid, "user" + uid, group,
                    row("uid", uid, "name", "user" + uid, "group", group, "bantime", bannedUntil));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final StaffAccess access = mock(StaffAccess.class);
        private final LegacyTokenService tokens = mock(LegacyTokenService.class);
        private final UniPushService push = mock(UniPushService.class);
        private final QaService service = new QaService(jdbc, access, tokens, push);
    }
}
