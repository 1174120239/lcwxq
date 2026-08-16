package cn.lcxqy.starfree.space;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCommentModerationSchedulerTest {
    @Test
    void riskyDynamicCommentIsHiddenAndIncludedInDailySummary() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AiModerationService moderation = mock(AiModerationService.class);
        AiCommentModerationScheduler scheduler = new AiCommentModerationScheduler(
                jdbc, moderation, new ObjectMapper());
        Map<String, Object> comment = new LinkedHashMap<String, Object>();
        comment.put("id", 11L);
        comment.put("toid", 3L);
        comment.put("uid", 7L);
        comment.put("text", "风险评论");
        when(jdbc.queryForList(startsWith("SELECT id,toid,uid,text FROM starfree_space"),
                anyLong(), anyLong(), eq(0L), eq(200)))
                .thenReturn(Collections.singletonList(comment));
        when(jdbc.queryForList(startsWith("SELECT id,question_id,uid,text FROM starfree_qa_answers"),
                anyLong(), anyLong(), eq(0L), eq(200)))
                .thenReturn(Collections.emptyList());
        when(jdbc.queryForList(startsWith("SELECT id,answer_id,uid,text FROM starfree_qa_comments"),
                anyLong(), anyLong(), eq(0L), eq(200)))
                .thenReturn(Collections.emptyList());
        when(moderation.reviewComment(AiModerationService.TYPE_SPACE_COMMENT,
                11L, 3L, 7L, "风险评论"))
                .thenReturn(AiModerationService.Decision.rejected("威胁辱骂", "包含攻击性内容", 91L));
        when(jdbc.update("UPDATE starfree_space SET status=0 WHERE id=? AND type=3 AND status=1", 11L))
                .thenReturn(1);

        scheduler.runDailyReview(LocalDate.of(2026, 8, 16),
                new AiModerationService.CommentPolicy(true, "03:30", "hide", null, 0, 0));

        verify(jdbc).update("UPDATE starfree_space SET status=0 WHERE id=? AND type=3 AND status=1", 11L);
        verify(moderation).markContentStatus(91L, 0);
    }
}
