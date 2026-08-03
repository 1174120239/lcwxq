package cn.lcxqy.starfree.comment;

import cn.lcxqy.starfree.economy.EconomyService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentAuditServiceTest {

    @Test
    void approvingWaitingCommentGrantsExperienceAndNotifiesOwner() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        EconomyService economy = mock(EconomyService.class);
        when(tokens.userId("admin-token")).thenReturn(2L);
        when(tokens.userById(2L)).thenReturn(user("administrator"));
        when(economy.isStaff("administrator")).thenReturn(true);

        Map<String, Object> comment = new HashMap<>();
        comment.put("coid", 21L);
        comment.put("cid", 11L);
        comment.put("authorId", 7L);
        comment.put("ownerId", 9L);
        comment.put("text", "待审核评论");
        comment.put("status", "waiting");
        comment.put("parent", 0L);
        when(jdbc.queryForList(anyString(), eq(21L))).thenReturn(Collections.singletonList(comment));

        Map<String, String> request = new HashMap<>();
        request.put("token", "admin-token");
        request.put("key", "21");

        Map<String, Object> result = new CommentService(jdbc, new ObjectMapper(), tokens, economy).audit(request);

        assertThat(result).containsEntry("status", "approved");
        verify(jdbc).update(anyString(), eq(21L));
        verify(economy).grantCommentExperience(7L);
        verify(economy).sendInbox("comment", 7L, 9L, "待审核评论", 11L, 21L);
    }

    private Map<String, Object> user(String group) {
        Map<String, Object> user = new HashMap<>();
        user.put("group", group);
        return user;
    }
}
