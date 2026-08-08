package cn.lcxqy.starfree.space;

import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.notify.EmailNotificationService;
import cn.lcxqy.starfree.push.UniPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpaceAnonymousAddTest {

    @Test
    void anonymousPostPublishesUnderAnonymousUidAndMapsRealOwner() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        fixture.configRow();
        when(fixture.jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM starfree_space"),
                eq(Integer.class), eq(7L), anyLong())).thenReturn(0);
        fixture.generatedKey(101L);

        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("type", "0");
        request.put("text", "匿名动态正文");
        request.put("pic", "");

        boolean pending = fixture.service.addAnonymous(request, "127.0.0.1", 5L, false);

        assertThat(pending).isFalse();
        verify(fixture.jdbc).update(
                eq("INSERT INTO starfree_anonymous_posts (uid, sid, created) VALUES (?, ?, ?)"),
                eq(7L), eq(101L), anyLong());
        verify(fixture.abuseGuard).reservePost(eq(7L), eq(false), eq(999), eq(0));
    }

    @Test
    void anonymousPostWaitsWhenReviewRequiredOrGlobalAuditOn() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        Map<String, Object> config = config();
        config.put("spaceAudit", 1);
        fixture.configRow(config);
        when(fixture.jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM starfree_space"),
                eq(Integer.class), eq(7L), anyLong())).thenReturn(0);
        fixture.generatedKey(102L);

        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("type", "0");
        request.put("text", "匿名动态正文");
        request.put("pic", "");

        assertThat(fixture.service.addAnonymous(request, "127.0.0.1", 5L, false)).isTrue();
    }

    @Test
    void anonymousPostRejectsReplyAndPluginTypes() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        fixture.configRow();

        for (String type : new String[]{"3", "5", "6"}) {
            Map<String, String> request = new HashMap<>();
            request.put("token", "token");
            request.put("type", type);
            request.put("text", "匿名动态正文");
            assertThatThrownBy(() -> fixture.service.addAnonymous(request, "127.0.0.1", 5L, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("参数不正确");
        }
    }

    @Test
    void anonymousPostCancelsReservationWhenInsertFails() {
        Fixture fixture = new Fixture();
        fixture.login(7L, "contributor");
        fixture.configRow();
        when(fixture.jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM starfree_space"),
                eq(Integer.class), eq(7L), anyLong())).thenReturn(0);
        LegacySpaceAbuseGuard.PostReservation reservation =
                mock(LegacySpaceAbuseGuard.PostReservation.class);
        when(fixture.abuseGuard.reservePost(7L, false, 999, 0)).thenReturn(reservation);
        when(fixture.jdbc.update(any(org.springframework.jdbc.core.PreparedStatementCreator.class),
                any(KeyHolder.class))).thenReturn(1);

        Map<String, String> request = new HashMap<>();
        request.put("token", "token");
        request.put("type", "0");
        request.put("text", "匿名动态正文");
        request.put("pic", "");

        assertThatThrownBy(() -> fixture.service.addAnonymous(request, "127.0.0.1", 5L, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not return an id");
        verify(reservation).cancel();
    }

    private static Map<String, Object> config() {
        Map<String, Object> row = new HashMap<>();
        row.put("spaceMinExp", 0);
        row.put("spaceAudit", 0);
        row.put("postMax", 999);
        row.put("postExp", 0);
        row.put("forbidden", "");
        row.put("identifysmPost", 0);
        row.put("identifylvPost", 0);
        row.put("banRobots", 0);
        row.put("silenceTime", 600);
        row.put("interceptTime", 3600);
        return row;
    }

    private static final class Fixture {
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final LegacyTokenService tokens = mock(LegacyTokenService.class);
        final LegacySpaceAbuseGuard abuseGuard = mock(LegacySpaceAbuseGuard.class);
        final UniPushService push = mock(UniPushService.class);
        final EmailNotificationService email = mock(EmailNotificationService.class);
        final SpaceService service = new SpaceService(
                jdbc, new ObjectMapper(), tokens, abuseGuard, push, email);

        Fixture() {
            when(abuseGuard.reservePost(anyLong(), anyBoolean(), anyInt(), anyInt()))
                    .thenReturn(LegacySpaceAbuseGuard.PostReservation.noop());
        }

        void login(long uid, String group) {
            Map<String, Object> user = new HashMap<>();
            user.put("uid", uid);
            user.put("group", group);
            user.put("experience", 100);
            when(tokens.userId("token")).thenReturn(uid);
            when(tokens.userById(uid)).thenReturn(user);
        }

        void configRow() {
            configRow(config());
        }

        void configRow(Map<String, Object> row) {
            when(jdbc.queryForList(startsWith("SELECT spaceMinExp")))
                    .thenReturn(Collections.singletonList(row));
        }

        void generatedKey(long id) {
            doAnswer(invocation -> {
                KeyHolder holder = invocation.getArgument(1);
                holder.getKeyList().add(Collections.singletonMap("GENERATED_KEY", id));
                return 1;
            }).when(jdbc).update(
                    any(org.springframework.jdbc.core.PreparedStatementCreator.class),
                    any(KeyHolder.class));
        }
    }
}
