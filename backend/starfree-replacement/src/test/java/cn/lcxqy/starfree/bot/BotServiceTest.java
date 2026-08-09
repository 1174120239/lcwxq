package cn.lcxqy.starfree.bot;

import cn.lcxqy.starfree.economy.SigninService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.PhpassPasswordVerifier;
import cn.lcxqy.starfree.space.SpaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

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

class BotServiceTest {
    @Test
    void bindLoginDoesNotCreateOrRevokeNormalForumLoginToken() {
        Fixture fixture = new Fixture();
        when(fixture.jdbc.queryForList(startsWith("SELECT bind_token,platform,qq_user_id"),
                eq("bind-token"))).thenReturn(Collections.singletonList(row(
                "platform", "qq", "qq_user_id", "10001")));
        when(fixture.jdbc.queryForList(startsWith("SELECT uid,name,password,bantime"),
                eq("alice"), eq("alice"))).thenReturn(Collections.singletonList(row(
                "uid", 7L, "name", "alice", "password", "hash", "bantime", 0)));
        when(fixture.passwords.matches("secret", "hash")).thenReturn(true);
        when(fixture.jdbc.update(startsWith("INSERT INTO lcxqy_bot_bindings"), any(Object[].class)))
                .thenReturn(1);
        when(fixture.jdbc.update(startsWith("UPDATE lcxqy_bot_bind_challenge"), any(Object[].class)))
                .thenReturn(1);

        Map<String, String> request = new HashMap<>();
        request.put("token", "bind-token");
        request.put("account", "alice");
        request.put("password", "secret");

        String html = fixture.service.bindLogin(request);

        assertThat(html).contains("绑定成功");
        verify(fixture.jdbc, never()).update(contains("authCode"), any(Object[].class));
    }

    @Test
    void addSpaceUsesBoundForumUidAndForcesNormalDynamicFields() {
        Fixture fixture = new Fixture();
        fixture.config("enabled", "1", "bot_secret", "test-secret",
                "tool_add_space", "1", "h5_base_url", "https://prev.lcxqy.cn");
        fixture.binding(77L);
        when(fixture.jdbc.update(startsWith("INSERT INTO lcxqy_bot_operation_log"), any(Object[].class)))
                .thenReturn(1);
        when(fixture.jdbc.update(startsWith("UPDATE lcxqy_bot_operation_log"), any(Object[].class)))
                .thenReturn(1);
        when(fixture.jdbc.update(startsWith("UPDATE lcxqy_bot_bindings"), any(Object[].class)))
                .thenReturn(1);
        when(fixture.jdbc.query(eq("SELECT id FROM starfree_space WHERE uid=? ORDER BY id DESC LIMIT 1"),
                any(Object[].class), any(RowMapper.class))).thenReturn(Collections.singletonList(88L));
        when(fixture.spaces.addForBotUid(eq(77L), any(), eq("127.0.0.1"))).thenReturn(false);

        Map<String, String> request = botRequest("test-secret", "10001");
        request.put("requestId", "request-1");
        request.put("text", "今天操场晚霞很好看");
        request.put("type", "3");
        request.put("toid", "999");

        Map<String, Object> result = fixture.service.addSpace(request, "127.0.0.1");

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.spaces).addForBotUid(eq(77L), captor.capture(), eq("127.0.0.1"));
        assertThat(captor.getValue()).containsEntry("type", "0")
                .containsEntry("toid", "0")
                .containsEntry("text", "今天操场晚霞很好看");
        assertThat(result).containsEntry("spaceId", 88L);
    }

    @Test
    void latestSpacesFiltersToPublicApprovedDynamicsOnly() {
        Fixture fixture = new Fixture();
        fixture.config("bot_secret", "test-secret", "h5_base_url", "https://prev.lcxqy.cn");
        when(fixture.jdbc.queryForList(startsWith("SELECT enabled,max_images,summary_length"),
                eq("qq"), eq("7788"))).thenReturn(Collections.singletonList(row(
                "enabled", 1, "max_images", 2, "summary_length", 80)));
        when(fixture.jdbc.queryForList(contains("FROM starfree_space s"), eq(10L), eq(5)))
                .thenReturn(Collections.singletonList(row(
                        "id", 11L, "uid", 7L, "created", 1L, "modified", 1L,
                        "text", "一条新动态", "pic", "a.png,b.png,c.png", "type", 0,
                        "views", 0, "likes", 0, "user_screenName", "Alice")));
        when(fixture.jdbc.queryForList(startsWith("SELECT m.mid AS id"), eq(11L)))
                .thenReturn(Collections.emptyList());

        Map<String, String> request = botRequest("test-secret", "10001");
        request.put("groupId", "7788");
        request.put("afterId", "10");
        request.put("limit", "5");

        Map<String, Object> response = fixture.service.latestSpaces(request);

        List<?> spaces = (List<?>) response.get("spaces");
        assertThat(spaces).hasSize(1);
        verify(fixture.jdbc).queryForList(contains("s.status=1 AND s.onlyMe=0 AND s.type<>3"),
                eq(10L), eq(5));
        Map<?, ?> first = (Map<?, ?>) spaces.get(0);
        assertThat(first.get("images")).isEqualTo(Arrays.asList("a.png", "b.png"));
    }

    @Test
    void registerGroupGeneratesOneBotOriginWhenAdminDoesNotProvideIt() {
        Fixture fixture = new Fixture();
        fixture.config("bot_secret", "test-secret");
        when(fixture.jdbc.update(startsWith("INSERT INTO lcxqy_bot_group_sync"), any(Object[].class)))
                .thenReturn(1);
        Map<String, String> request = botRequest("test-secret", "10001");
        request.put("groupId", "638978650");
        request.put("groupName", "聊城一中论坛");

        Map<String, Object> response = fixture.service.registerGroup(request);

        verify(fixture.jdbc).update(startsWith("INSERT INTO lcxqy_bot_group_sync"),
                eq("qq"), eq("638978650"), eq("聊城一中论坛"),
                eq("lcxqy_onebot:GroupMessage:638978650"),
                any(java.sql.Timestamp.class), any(java.sql.Timestamp.class));
        assertThat(response).containsEntry("unifiedMsgOrigin", "lcxqy_onebot:GroupMessage:638978650");
    }

    @Test
    void registerGroupRejectsNonNumericQqGroupId() {
        Fixture fixture = new Fixture();
        fixture.config("bot_secret", "test-secret");
        Map<String, String> request = botRequest("test-secret", "10001");
        request.put("groupId", "not-a-group");

        assertThatThrownBy(() -> fixture.service.registerGroup(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("QQ群号格式不正确");
    }

    private static Map<String, String> botRequest(String secret, String qqUserId) {
        Map<String, String> request = new HashMap<>();
        request.put("botSecret", secret);
        request.put("platform", "qq");
        request.put("qqUserId", qqUserId);
        return request;
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        return row;
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final PhpassPasswordVerifier passwords = mock(PhpassPasswordVerifier.class);
        private final LegacyTokenService tokens = mock(LegacyTokenService.class);
        private final SpaceService spaces = mock(SpaceService.class);
        private final SigninService signin = mock(SigninService.class);
        private final BotService service = new BotService(
                jdbc, new ObjectMapper(), passwords, tokens, spaces, signin);

        private void config(String... pairs) {
            Map<String, Object> rows[] = new Map[pairs.length / 2];
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                rows[i / 2] = row("config_key", pairs[i], "config_value", pairs[i + 1]);
            }
            when(jdbc.queryForList(eq("SELECT config_key,config_value FROM lcxqy_bot_config")))
                    .thenReturn(java.util.Arrays.asList(rows));
        }

        private void binding(long uid) {
            when(jdbc.queryForList(startsWith("SELECT platform,qq_user_id,forum_uid"),
                    eq("qq"), eq("10001"))).thenReturn(Collections.singletonList(row(
                    "platform", "qq", "qq_user_id", "10001", "forum_uid", uid)));
        }
    }
}
