package cn.lcxqy.starfree.content;

import cn.lcxqy.starfree.economy.EconomyService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentInfoServiceTest {

    @Test
    void detailKeepsTheFullBodyAndLegacyTopLevelShape() {
        Fixture fixture = fixture(content("publish"));

        Map<String, Object> detail = fixture.service.detail(11, 0, "");

        assertThat(detail)
                .containsEntry("cid", 11L)
                .containsEntry("orderKey", 3)
                .containsEntry("markdown", 1)
                .containsEntry("template", "default")
                .containsEntry("text", "# Full body\n<img src=\"https://img.example/a.png\">");
        assertThat(detail).doesNotContainKeys("order", "password", "shop", "authorInfo", "videos");
        assertThat(detail.get("images")).asList().containsExactly("https://img.example/a.png");
        assertThat(detail.get("fields")).asList().containsExactly(fixture.field);
        assertThat(detail.get("category")).asList().isEmpty();
        assertThat(detail.get("tag")).asList().isEmpty();
    }

    @Test
    void isMdOneUsesTheLegacyCommonMarkRenderingMode() {
        Fixture fixture = fixture(content("publish"));

        Map<String, Object> detail = fixture.service.detail(11, 1, "");

        assertThat(detail.get("text").toString())
                .contains("<h1>Full body</h1>")
                .contains("<img src=\"https://img.example/a.png\">");
    }

    @Test
    void unpublishedContentRequiresAStaffRedisSession() {
        Fixture fixture = fixture(content("waiting"));

        assertThatThrownBy(() -> fixture.service.detail(11, 0, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u6587\u7ae0\u6682\u672a\u516c\u5f00\u8bbf\u95ee");

        when(fixture.tokens.userId("staff-token")).thenReturn(2L);
        Map<String, Object> staff = new LinkedHashMap<>();
        staff.put("group", "administrator");
        when(fixture.tokens.userById(2L)).thenReturn(staff);
        when(fixture.economy.isStaff("administrator")).thenReturn(true);
        assertThat(fixture.service.detail(11, 0, "staff-token")).containsEntry("cid", 11L);
    }

    private Fixture fixture(Map<String, Object> content) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        EconomyService economy = mock(EconomyService.class);
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("cid", 11L);
        field.put("name", "cover");
        field.put("type", "str");
        field.put("strValue", "value");
        when(jdbc.queryForList(anyString(), eq(11L))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.startsWith("SELECT c.*")) {
                return Collections.singletonList(content);
            }
            if (sql.startsWith("SELECT cid,name")) {
                return Collections.singletonList(field);
            }
            return Collections.emptyList();
        });
        return new Fixture(new ContentService(jdbc, new ObjectMapper(), tokens, economy), tokens, economy, field);
    }

    private Map<String, Object> content(String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cid", 11L);
        row.put("title", "Detail title");
        row.put("text", "<!--markdown--># Full body\n<img src=\"https://img.example/a.png\">");
        row.put("order", 3);
        row.put("authorId", 7L);
        row.put("type", "post");
        row.put("status", status);
        row.put("password", "secret");
        row.put("template", "default");
        row.put("views", 4);
        return row;
    }

    private static final class Fixture {
        private final ContentService service;
        private final LegacyTokenService tokens;
        private final EconomyService economy;
        private final Map<String, Object> field;

        private Fixture(ContentService service, LegacyTokenService tokens, EconomyService economy,
                        Map<String, Object> field) {
            this.service = service;
            this.tokens = tokens;
            this.economy = economy;
            this.field = field;
        }
    }
}
