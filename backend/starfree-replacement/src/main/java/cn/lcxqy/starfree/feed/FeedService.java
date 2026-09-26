package cn.lcxqy.starfree.feed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only projection for the community stream.
 *
 * <p>The source tables remain independent. We deliberately project only public rows and
 * keep the existing detail ids/routes so this endpoint cannot change publishing semantics.
 */
@Service
public class FeedService {
    private static final int MAX_LIMIT = 30;
    private static final int MAX_MERGE_CANDIDATES = 5000;

    private final JdbcTemplate jdbc;

    public FeedService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page feedList(int requestedPage, int requestedLimit, String requestedType) {
        int page = Math.max(1, requestedPage);
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        String type = normalizeType(requestedType);
        long offset = ((long) page - 1L) * limit;
        int candidates = (int) Math.max(60L, Math.min(MAX_MERGE_CANDIDATES, offset + limit));
        long now = Instant.now().getEpochSecond();

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        int total = 0;
        if (type.isEmpty() || "space".equals(type)) {
            rows.addAll(spaces(candidates));
            total += countSpaces();
        }
        if (type.isEmpty() || "question".equals(type)) {
            rows.addAll(questions(candidates));
            total += countQuestions();
        }
        if (type.isEmpty() || "task".equals(type)) {
            long taskActiveSince = now - expirySeconds();
            rows.addAll(tasks(candidates, taskActiveSince));
            total += countTasks(taskActiveSince);
        }
        // The projection deliberately caps merge work. Keep the advertised page range
        // aligned with that cap so a huge page cannot report endless empty pages.
        total = Math.min(total, MAX_MERGE_CANDIDATES);

        Collections.sort(rows, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                int activity = Long.compare(number(right.get("lastActivity")), number(left.get("lastActivity")));
                if (activity != 0) return activity;
                return Long.compare(number(right.get("id")), number(left.get("id")));
            }
        });
        int from = (int) Math.min(offset, rows.size());
        int to = Math.min(from + limit, rows.size());
        return new Page(new ArrayList<Map<String, Object>>(rows.subList(from, to)), total);
    }

    private List<Map<String, Object>> spaces(int limit) {
        List<Map<String, Object>> source = jdbc.queryForList(
                "SELECT s.id,s.uid,s.created,s.modified,s.text,s.pic,s.type,s.views,s.likes,"
                        + "u.uid AS user_uid,u.name AS user_name,u.screenName AS user_screenName,u.avatar AS user_avatar "
                        + "FROM starfree_space s LEFT JOIN starfree_users u ON u.uid=s.uid "
                        + "WHERE s.status=1 AND s.onlyMe=0 AND s.type NOT IN (3,6) "
                        + "ORDER BY CASE WHEN s.modified > s.created THEN s.modified ELSE s.created END DESC,s.id DESC LIMIT ?", limit);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : source) {
            Map<String, Object> item = base("space", number(row.get("id")),
                    Math.max(number(row.get("created")), number(row.get("modified"))));
            item.put("id", number(row.get("id")));
            item.put("title", "");
            item.put("text", preview(text(row.get("text")), 220));
            item.put("pic", text(row.get("pic")));
            item.put("spaceType", number(row.get("type")));
            item.put("created", number(row.get("created")));
            item.put("modified", number(row.get("modified")));
            item.put("views", number(row.get("views")));
            item.put("likes", number(row.get("likes")));
            item.put("userJson", user(row));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> questions(int limit) {
        List<Map<String, Object>> source = jdbc.queryForList(
                "SELECT q.id,q.title,q.description,q.topic,q.cover_url,q.created,q.modified,"
                        + "(SELECT COUNT(*) FROM starfree_qa_answers ac WHERE ac.question_id=q.id AND ac.status=1) AS answer_count,"
                        + "latest_answer.text AS answer_text,latest_answer.id AS answer_id,"
                        + "latest_answer.uid AS answer_uid,latest_answer.created AS answer_created,"
                        + "u.uid AS user_uid,u.name AS user_name,u.screenName AS user_screenName,u.avatar AS user_avatar "
                        + "FROM starfree_qa_questions q LEFT JOIN starfree_qa_answers latest_answer "
                        + "ON latest_answer.id=(SELECT a.id FROM starfree_qa_answers a "
                        + "WHERE a.question_id=q.id AND a.status=1 ORDER BY a.created DESC,a.id DESC LIMIT 1) "
                        + "LEFT JOIN starfree_users u ON u.uid=q.created_by "
                        + "WHERE q.status=1 ORDER BY GREATEST(q.modified,COALESCE(latest_answer.created,0)) DESC,q.id DESC LIMIT ?", limit);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : source) {
            long answerCreated = number(row.get("answer_created"));
            Map<String, Object> item = base("question", number(row.get("id")),
                    Math.max(number(row.get("modified")), answerCreated));
            item.put("id", number(row.get("id")));
            item.put("title", text(row.get("title")));
            item.put("description", preview(text(row.get("description")), 220));
            item.put("topic", text(row.get("topic")));
            item.put("coverUrl", text(row.get("cover_url")));
            item.put("created", number(row.get("created")));
            item.put("modified", number(row.get("modified")));
            item.put("answerCount", number(row.get("answer_count")));
            item.put("lastActivityType", answerCreated > number(row.get("modified")) ? "answer" : "question");
            Map<String, Object> answer = new LinkedHashMap<String, Object>();
            answer.put("id", number(row.get("answer_id")));
            answer.put("uid", number(row.get("answer_uid")));
            answer.put("text", preview(text(row.get("answer_text")), 180));
            answer.put("created", answerCreated);
            item.put("latestAnswer", number(row.get("answer_id")) > 0 ? answer : null);
            item.put("userJson", user(row));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> tasks(int limit, long activeSince) {
        List<Map<String, Object>> source = jdbc.queryForList(
                "SELECT i.id,i.uid,i.kind,i.category,i.title,i.description,i.image_url,i.created,i.modified,i.status,"
                        + "u.uid AS user_uid,u.name AS user_name,u.screenName AS user_screenName,u.avatar AS user_avatar "
                        + "FROM starfree_lost_found_items i LEFT JOIN starfree_users u ON u.uid=i.uid "
                        + "WHERE i.status=1 AND i.created>=? "
                        + "ORDER BY CASE WHEN i.modified > i.created THEN i.modified ELSE i.created END DESC,i.id DESC LIMIT ?",
                activeSince, limit);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : source) {
            Map<String, Object> item = base("task", number(row.get("id")),
                    Math.max(number(row.get("created")), number(row.get("modified"))));
            item.put("id", number(row.get("id")));
            item.put("title", text(row.get("title")));
            item.put("description", preview(text(row.get("description")), 220));
            item.put("kind", number(row.get("kind")));
            item.put("category", number(row.get("category")));
            item.put("imageUrl", text(row.get("image_url")));
            item.put("created", number(row.get("created")));
            item.put("modified", number(row.get("modified")));
            item.put("status", number(row.get("status")));
            item.put("userJson", user(row));
            result.add(item);
        }
        return result;
    }

    private int countSpaces() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_space WHERE status=1 AND onlyMe=0 AND type NOT IN (3,6)", Integer.class);
        return count == null ? 0 : count;
    }

    private int countQuestions() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_qa_questions WHERE status=1", Integer.class);
        return count == null ? 0 : count;
    }

    private int countTasks(long activeSince) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_lost_found_items WHERE status=1 AND created>=?", Integer.class, activeSince);
        return count == null ? 0 : count;
    }

    private long expirySeconds() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT item_expiry_days FROM starfree_lost_found_config WHERE id=1 LIMIT 1");
        long days = rows.isEmpty() ? 30 : number(rows.get(0).get("item_expiry_days"));
        return Math.max(1, days) * 86400L;
    }

    private Map<String, Object> base(String type, long id, long activity) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("feedType", type);
        item.put("id", id);
        item.put("lastActivity", activity);
        return item;
    }

    private Map<String, Object> user(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("uid", number(row.get("user_uid")));
        String name = text(row.get("user_screenName"));
        result.put("name", name.isEmpty() ? text(row.get("user_name")) : name);
        result.put("avatar", text(row.get("user_avatar")));
        return result;
    }

    private String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase();
        return "space".equals(type) || "question".equals(type) || "task".equals(type) ? type : "";
    }

    private String preview(String value, int max) {
        String text = value == null ? "" : value.replace("<!--markdown-->", "").replaceAll("<[^>]+>", "").replace("||rn||", " ").trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private long number(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try { return value == null ? 0 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }

    public static final class Page {
        private final List<Map<String, Object>> data;
        private final int total;

        Page(List<Map<String, Object>> data, int total) { this.data = data; this.total = total; }
        public List<Map<String, Object>> getData() { return data; }
        public int getTotal() { return total; }
    }
}
