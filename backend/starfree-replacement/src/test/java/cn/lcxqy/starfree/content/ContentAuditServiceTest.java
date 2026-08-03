package cn.lcxqy.starfree.content;

import cn.lcxqy.starfree.economy.EconomyService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentAuditServiceTest {

    @Test
    void approvingWaitingContentPublishesAndGrantsExperience() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        EconomyService economy = mock(EconomyService.class);
        LegacyContentCacheInvalidator cache = mock(LegacyContentCacheInvalidator.class);
        when(tokens.userId("admin-token")).thenReturn(2L);
        when(tokens.userById(2L)).thenReturn(user("administrator"));
        when(tokens.userById(7L)).thenReturn(user("contributor"));
        when(economy.isStaff("administrator")).thenReturn(true);

        Map<String, Object> content = new HashMap<>();
        content.put("cid", 11L);
        content.put("title", "待审核帖子");
        content.put("status", "waiting");
        content.put("authorId", 7L);
        when(jdbc.queryForList(anyString(), eq(11L))).thenReturn(Collections.singletonList(content));
        when(jdbc.update(anyString(), eq("publish"), anyLong(), eq(11L))).thenReturn(1);

        Map<String, String> request = new HashMap<>();
        request.put("token", "admin-token");
        request.put("key", "11");

        Map<String, Object> result = new ContentService(jdbc, new ObjectMapper(), tokens, economy,
                LegacyContentAbuseGuard.disabled(), cache).audit(request);

        assertThat(result).containsEntry("status", "publish");
        verify(jdbc).update(anyString(), eq("publish"), anyLong(), eq(11L));
        verify(economy).grantPostExperience(7L);
        verify(cache).afterContentWrite(11L);
    }

    @Test
    void deletingContentEvictsCachesAndRefreshesItsCategoryCounts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        EconomyService economy = mock(EconomyService.class);
        LegacyContentCacheInvalidator cache = mock(LegacyContentCacheInvalidator.class);
        when(tokens.userId("admin-token")).thenReturn(2L);
        when(tokens.userById(2L)).thenReturn(user("administrator"));
        when(economy.isStaff("administrator")).thenReturn(true);
        when(jdbc.queryForList(
                "SELECT mid FROM starfree_relationships WHERE cid = ? ORDER BY mid",
                Integer.class, 11L)).thenReturn(Collections.singletonList(3));
        when(jdbc.update("DELETE FROM starfree_contents WHERE cid = ?", 11L)).thenReturn(1);

        Map<String, Object> content = new HashMap<>();
        content.put("cid", 11L);
        content.put("title", "待删除帖子");
        content.put("authorId", 7L);
        ContentService service = spy(new ContentService(jdbc, new ObjectMapper(), tokens, economy,
                LegacyContentAbuseGuard.disabled(), cache));
        doReturn(content).when(service).info(11L, false);

        Map<String, String> request = new HashMap<>();
        request.put("token", "admin-token");
        request.put("key", "11");
        service.delete(request);

        verify(cache).afterContentWrite(11L);
        verify(jdbc).update(
                "UPDATE starfree_metas SET count = (SELECT COUNT(*) FROM starfree_relationships WHERE mid = ?) WHERE mid = ?",
                3, 3);
        verify(economy).deductDeleteExperience(7L);
    }

    private Map<String, Object> user(String group) {
        Map<String, Object> user = new HashMap<>();
        user.put("group", group);
        return user;
    }
}
