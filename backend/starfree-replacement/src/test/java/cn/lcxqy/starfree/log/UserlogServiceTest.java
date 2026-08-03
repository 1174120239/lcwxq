package cn.lcxqy.starfree.log;

import cn.lcxqy.starfree.content.ContentService;
import cn.lcxqy.starfree.economy.EconomyService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserlogServiceTest {

    @Test
    void financialDispatchDoesNotHoldAnOuterDatabaseTransaction() throws Exception {
        Transactional annotation = UserlogService.class
                .getDeclaredMethod("add", Map.class)
                .getAnnotation(Transactional.class);

        assertThat(annotation).isNull();
    }

    @Test
    void duplicateLikeIsRejectedWithoutIncrementingContent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        ContentService contents = mock(ContentService.class);
        EconomyService economy = mock(EconomyService.class);
        when(tokens.userId("valid-token")).thenReturn(7L);
        when(jdbc.queryForList(anyString(), eq(7L), eq(1L), eq("likes")))
                .thenReturn(Collections.<Map<String, Object>>singletonList(
                        Collections.<String, Object>singletonMap("id", 42L)));

        Map<String, String> request = new HashMap<>();
        request.put("token", "valid-token");
        request.put("params", "{\"cid\":1,\"type\":\"likes\"}");

        UserlogService service = new UserlogService(jdbc, new ObjectMapper(), tokens, contents, economy);

        assertThatThrownBy(() -> service.add(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("距离上次操作不到24小时！");
        verify(jdbc, never()).update(anyString(), eq(1L));
    }
}
