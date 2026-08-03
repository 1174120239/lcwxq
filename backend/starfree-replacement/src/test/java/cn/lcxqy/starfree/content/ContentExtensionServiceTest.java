package cn.lcxqy.starfree.content;

import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentExtensionServiceTest {
    @Mock
    private LegacyTokenService tokens;
    @Mock
    private LegacyContentCacheInvalidator caches;

    private StubJdbcTemplate jdbc;
    private ContentExtensionService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubJdbcTemplate();
        service = new ContentExtensionService(jdbc, new StaffAccess(tokens), caches);
    }

    @Test
    void staffFlagWriteUsesOnlyThePrivateColumnAllowlistAndInvalidatesContent() {
        arrangeActor("staff-token", 1, "editor");
        jdbc.content = row("cid", 7, "authorId", 9, "type", "post", "status", "publish");
        jdbc.updateResult = 1;

        assertThat(service.setRecommended("staff-token", 7, 1)).isEqualTo(1);

        assertThat(jdbc.lastUpdateSql)
                .startsWith("UPDATE starfree_contents SET `isrecommend`=?")
                .doesNotContain("istop").doesNotContain("isswiper");
        assertThat(jdbc.lastUpdateArgs.get(0)).isEqualTo(1);
        assertThat(jdbc.lastUpdateArgs.get(2)).isEqualTo(7L);
        verify(caches).afterContentWrite(7);
    }

    @Test
    void fieldUpsertAllowsOwnerAndNormalizesMySqlUpdatedRowCount() {
        arrangeActor("owner-token", 9, "contributor");
        jdbc.content = row("cid", 7, "authorId", 9, "type", "post", "status", "publish");
        jdbc.configuredFields = "able,reserved";
        jdbc.updateResult = 2;

        int changed = service.setStringField("owner-token", 7, "abcimg", "cover");

        assertThat(changed).isEqualTo(1);
        assertThat(jdbc.lastUpdateSql).contains("ON DUPLICATE KEY UPDATE");
        assertThat(jdbc.lastUpdateArgs).containsExactly(7L, "abcimg", "cover");
        verify(caches).afterContentWrite(7);
    }

    @Test
    void fieldUpsertRejectsConfiguredReservedNameBeforeWriting() {
        arrangeActor("owner-token", 9, "contributor");
        jdbc.content = row("cid", 7, "authorId", 9);
        jdbc.configuredFields = "abcimg,other";

        assertThrows(IllegalArgumentException.class,
                () -> service.setStringField("owner-token", 7, "abcimg", "cover"));

        assertThat(jdbc.updateCalls).isZero();
        verify(caches, never()).afterContentWrite(7);
    }

    @Test
    void hiddenReplyCheckUsesAuthenticatedUid() {
        arrangeActor("user-token", 9, "contributor");
        jdbc.scalar = 1;

        assertThat(service.hasCommentedOrAuthored("user-token", 7)).isTrue();
        assertThat(jdbc.lastQueryArgs).containsExactly(7L, 9L, 7L, 9L);
    }

    @Test
    void dashboardRestoresExactLegacyKeyCasing() {
        arrangeActor("staff-token", 1, "administrator");
        jdbc.dashboard.put("ALLCONTENTS", 5L);
        jdbc.dashboard.put("allcomments", 6L);
        jdbc.dashboard.put("upcomingwithdraw", 2L);

        Map<String, Object> result = service.dashboard("staff-token");

        assertThat(result).containsEntry("allContents", 5L)
                .containsEntry("allComments", 6L)
                .containsEntry("upcomingWithdraw", 2L)
                .containsKeys("allUsers", "allShop", "allSpace", "allAds", "selfDelete",
                        "upcomingContents", "upcomingComments", "upcomingShop", "upcomingSpace",
                        "upcomingAds");
    }

    private void arrangeActor(String token, long uid, String group) {
        when(tokens.userId(token)).thenReturn(uid);
        when(tokens.userById(uid)).thenReturn(row(
                "uid", uid, "name", "actor", "group", group));
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }

    /** SQL stub that separates content/config/dashboard reads by their fixed query text. */
    private static final class StubJdbcTemplate extends JdbcTemplate {
        private Map<String, Object> content;
        private String configuredFields = "";
        private int scalar;
        private int updateResult;
        private int updateCalls;
        private String lastUpdateSql;
        private List<Object> lastUpdateArgs = Collections.emptyList();
        private List<Object> lastQueryArgs = Collections.emptyList();
        private final Map<String, Object> dashboard = new LinkedHashMap<>();

        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            return queryForList(sql, new Object[0]);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            lastQueryArgs = new ArrayList<>();
            Collections.addAll(lastQueryArgs, args);
            if (sql.contains("FROM starfree_contents WHERE cid=?")) {
                return content == null ? Collections.<Map<String, Object>>emptyList()
                        : Collections.singletonList(content);
            }
            if (sql.contains("SELECT fields FROM starfree_apiconfig")) {
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("fields", configuredFields);
                return Collections.singletonList(config);
            }
            if (sql.contains("SELECT allowDelete FROM starfree_apiconfig")) {
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("allowDelete", 1);
                return Collections.singletonList(config);
            }
            return Collections.emptyList();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            lastQueryArgs = new ArrayList<>();
            Collections.addAll(lastQueryArgs, args);
            return (T) Integer.valueOf(scalar);
        }

        @Override
        public Map<String, Object> queryForMap(String sql) {
            return dashboard;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalls++;
            lastUpdateSql = sql;
            lastUpdateArgs = new ArrayList<>();
            Collections.addAll(lastUpdateArgs, args);
            return updateResult;
        }
    }
}
