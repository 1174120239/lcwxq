package cn.lcxqy.starfree.qa;

import cn.lcxqy.starfree.push.UniPushService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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

    private static final class Fixture {
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final StaffAccess access = mock(StaffAccess.class);
        private final LegacyTokenService tokens = mock(LegacyTokenService.class);
        private final UniPushService push = mock(UniPushService.class);
        private final QaService service = new QaService(jdbc, access, tokens, push);
    }
}
