package cn.lcxqy.starfree.feed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeedServiceTest {
    private StubJdbcTemplate jdbc;
    private FeedService service;

    @BeforeEach
    void setUp() {
        jdbc = new StubJdbcTemplate();
        service = new FeedService(jdbc);
    }

    @Test
    void mergesPublicSourcesByLatestActivityAndProjectsLatestAnswer() {
        jdbc.rows.put("space", Collections.singletonList(row(
                "id", 1, "uid", 11, "created", 100, "modified", 100, "text", "动态正文",
                "pic", "", "type", 0, "views", 4, "likes", 2,
                "user_uid", 11, "user_name", "space-user", "user_screenName", "", "user_avatar", "")));
        jdbc.rows.put("question", Collections.singletonList(row(
                "id", 3, "title", "怎么申请图书馆座位", "description", "<p>需要帮忙</p>", "topic", "校园生活",
                "cover_url", "", "created", 70, "modified", 80, "answer_count", 1,
                "answer_text", "<p>可以在服务号里预约。</p>", "answer_id", 31, "answer_uid", 13,
                "answer_created", 150, "user_uid", 13, "user_name", "question-user",
                "user_screenName", "", "user_avatar", "")));
        jdbc.rows.put("task", Collections.singletonList(row(
                "id", 4, "uid", 14, "kind", 1, "category", 3, "title", "求借计算器",
                "description", "明天下午考试借用", "image_url", "", "created", 95,
                "modified", 110, "status", 1, "user_uid", 14, "user_name", "task-user",
                "user_screenName", "", "user_avatar", "")));

        FeedService.Page page = service.feedList(1, 3, "");

        assertThat(page.getTotal()).isEqualTo(7);
        assertThat(page.getData()).extracting(item -> item.get("feedType"))
                .containsExactly("question", "task", "space");
        assertThat(page.getData().get(0))
                .containsEntry("title", "怎么申请图书馆座位")
                .containsEntry("description", "需要帮忙")
                .containsEntry("answerCount", 1L)
                .containsEntry("lastActivityType", "answer");
		Map<?, ?> latestAnswer = (Map<?, ?>) page.getData().get(0).get("latestAnswer");
		assertThat(String.valueOf(latestAnswer.get("text"))).isEqualTo("可以在服务号里预约。");
        assertThat(jdbc.listSql).anySatisfy(sql -> assertThat(sql)
                .contains("WHERE q.status=1")
                .contains("a.status=1"));
        assertThat(jdbc.listSql).anySatisfy(sql -> assertThat(sql)
                .contains("WHERE i.status=1 AND i.created>=?"));
        assertThat(jdbc.listSql).anySatisfy(sql -> assertThat(sql)
                .contains("CASE WHEN s.modified > s.created THEN s.modified ELSE s.created END"));
        long expectedExpiryCutoff = Instant.now().getEpochSecond() - 45L * 86400L;
        assertThat(((Number) jdbc.listArgs.get(3).get(0)).longValue())
                .isBetween(expectedExpiryCutoff - 2, expectedExpiryCutoff + 2);
        assertThat(jdbc.listArgs.get(3).get(1)).isEqualTo(60);
    }

    @Test
    void typeFilterAvoidsOtherSourcesAndLargePageDoesNotOverflow() {
        jdbc.rows.put("question", Collections.singletonList(row(
                "id", 3, "title", "问题标题", "description", "", "topic", "", "cover_url", "",
                "created", 70, "modified", 80, "answer_count", 0, "answer_text", null,
                "answer_id", null, "answer_uid", null, "answer_created", null,
                "user_uid", 13, "user_name", "question-user", "user_screenName", "", "user_avatar", "")));

        FeedService.Page page = service.feedList(Integer.MAX_VALUE, 300, "question");

        assertThat(page.getData()).isEmpty();
        assertThat(page.getTotal()).isEqualTo(4);
        assertThat(jdbc.listSql).hasSize(1);
        assertThat(jdbc.listSql.get(0)).contains("FROM starfree_qa_questions q");
        assertThat(jdbc.listArgs.get(0)).containsExactly(5000);
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<String, List<Map<String, Object>>>();
        private final List<String> listSql = new ArrayList<String>();
        private final List<List<Object>> listArgs = new ArrayList<List<Object>>();

        @Override
        public List<Map<String, Object>> queryForList(String sql) {
            return queryForList(sql, new Object[0]);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            listSql.add(sql);
            listArgs.add(Arrays.asList(args));
            if (sql.contains("FROM starfree_lost_found_config")) {
                return Collections.singletonList(row("item_expiry_days", 45));
            }
            if (sql.contains("FROM starfree_space s ")) return rows("space");
            if (sql.contains("FROM starfree_qa_questions q ")) return rows("question");
            if (sql.contains("FROM starfree_lost_found_items i ")) return rows("task");
            return Collections.emptyList();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType) {
            return scalar(sql, requiredType);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return scalar(sql, requiredType);
        }

        private <T> T scalar(String sql, Class<T> requiredType) {
            int value = sql.contains("FROM starfree_space WHERE") ? 2
                    : sql.contains("FROM starfree_qa_questions WHERE") ? 4
                    : sql.contains("FROM starfree_lost_found_items WHERE") ? 1 : 0;
            return requiredType.cast(Integer.valueOf(value));
        }

        private List<Map<String, Object>> rows(String key) {
            List<Map<String, Object>> result = rows.get(key);
            return result == null ? Collections.<Map<String, Object>>emptyList() : result;
        }
    }
}
