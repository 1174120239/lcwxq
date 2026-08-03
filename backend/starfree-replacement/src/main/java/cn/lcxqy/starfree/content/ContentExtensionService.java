package cn.lcxqy.starfree.content;

import cn.lcxqy.starfree.security.StaffAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Local implementation of content management flags, custom fields, dashboard counts and helpers.
 *
 * <p>These routes are kept out of {@link ContentService} because they are administrative projections
 * rather than the ordinary publish/edit lifecycle. Every write resolves the token from the server,
 * validates the target row, uses a fixed SQL column allowlist, and invalidates the legacy detail/list
 * caches. The database uses MyISAM, so one-statement updates are preferred over delete-then-insert.
 */
@Service
public class ContentExtensionService {
    private final JdbcTemplate jdbc;
    private final StaffAccess access;
    private final LegacyContentCacheInvalidator caches;

    public ContentExtensionService(JdbcTemplate jdbc, StaffAccess access,
                                   LegacyContentCacheInvalidator caches) {
        this.jdbc = jdbc;
        this.access = access;
        this.caches = caches;
    }

    /**
     * Returns true when the authenticated user authored or commented on the selected content.
     * This controls reply-hidden rendering only; it does not grant edit, moderation, or paid access.
     */
    public boolean hasCommentedOrAuthored(String token, long cid) {
        StaffAccess.Actor actor = access.requireUser(token);
        if (cid <= 0) {
            throw new IllegalArgumentException("参数错误");
        }
        Integer matched = jdbc.queryForObject(
                "SELECT CASE WHEN "
                        + "EXISTS(SELECT 1 FROM starfree_contents WHERE cid=? AND authorId=?) "
                        + "OR EXISTS(SELECT 1 FROM starfree_comments WHERE cid=? AND authorId=?) "
                        + "THEN 1 ELSE 0 END",
                Integer.class, cid, actor.getUid(), cid, actor.getUid());
        return matched != null && matched == 1;
    }

    /** Administrator/editor recommendation flag; value must be exactly zero or one. */
    public int setRecommended(String token, long cid, int value) {
        return setPresentationFlag(token, cid, "isrecommend", value);
    }

    /** Administrator/editor pin flag; value must be exactly zero or one. */
    public int setTop(String token, long cid, int value) {
        return setPresentationFlag(token, cid, "istop", value);
    }

    /** Administrator/editor carousel flag; value must be exactly zero or one. */
    public int setSwiper(String token, long cid, int value) {
        return setPresentationFlag(token, cid, "isswiper", value);
    }

    /**
     * Upserts one string custom field for a content row.
     *
     * <p>The actor must own the content or be administrator/editor. The config {@code fields} value
     * is treated as the legacy reserved-field list: exact comma-separated names are rejected. Field
     * names use a conservative identifier syntax, and values are capped before entering a TEXT row.
     */
    public int setStringField(String token, long cid, String name, String value) {
        StaffAccess.Actor actor = access.requireUser(token);
        Map<String, Object> content = requireContent(cid);
        if (number(get(content, "authorId")) != actor.getUid() && !actor.isStaff()) {
            throw new IllegalArgumentException("你无权进行此操作");
        }
        String field = name == null ? "" : name.trim();
        if (!field.matches("[A-Za-z][A-Za-z0-9_-]{0,199}")) {
            throw new IllegalArgumentException("字段名称不正确");
        }
        String fieldValue = value == null ? "" : value;
        if (fieldValue.length() > 65535) {
            throw new IllegalArgumentException("字段内容过长");
        }
        if (reservedFields().contains(field)) {
            throw new IllegalArgumentException("操作失败，字段未被定义");
        }
        int changed = jdbc.update(
                "INSERT INTO starfree_fields(cid,name,type,str_value,int_value,float_value) "
                        + "VALUES(?,?,'str',?,0,0) ON DUPLICATE KEY UPDATE "
                        + "type='str',str_value=VALUES(str_value),int_value=0,float_value=0",
                cid, field, fieldValue);
        if (changed > 0) {
            caches.afterContentWrite(cid);
        }
        // MySQL reports 2 for an updated row; the old client expects a success row count of 1.
        return changed > 0 ? 1 : 0;
    }

    /** Public deletion policy used by the user's post/comment pages. */
    public Map<String, Object> publicConfig() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT allowDelete FROM starfree_apiconfig ORDER BY id LIMIT 1");
        if (rows.isEmpty()) {
            throw new IllegalStateException("系统配置不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allowDelete", integer(get(rows.get(0), "allowDelete")));
        return result;
    }

    /**
     * Returns the complete management dashboard count contract from one read-only SQL statement.
     * Staff authentication is required because pending moderation and account totals are not public.
     */
    public Map<String, Object> dashboard(String token) {
        access.requireStaff(token);
        Map<String, Object> raw = jdbc.queryForMap(
                "SELECT "
                        + "(SELECT COUNT(*) FROM starfree_contents WHERE type='post' AND status='publish') allContents,"
                        + "(SELECT COUNT(*) FROM starfree_comments) allComments,"
                        + "(SELECT COUNT(*) FROM starfree_users) allUsers,"
                        + "(SELECT COUNT(*) FROM starfree_shop) allShop,"
                        + "(SELECT COUNT(*) FROM starfree_space) allSpace,"
                        + "(SELECT COUNT(*) FROM starfree_ads) allAds,"
                        + "(SELECT COUNT(*) FROM starfree_inbox WHERE type='selfDelete') selfDelete,"
                        + "(SELECT COUNT(*) FROM starfree_contents WHERE type='post' AND status='waiting') upcomingContents,"
                        + "(SELECT COUNT(*) FROM starfree_comments WHERE status='waiting') upcomingComments,"
                        + "(SELECT COUNT(*) FROM starfree_shop WHERE status=0) upcomingShop,"
                        + "(SELECT COUNT(*) FROM starfree_space WHERE status=0) upcomingSpace,"
                        + "(SELECT COUNT(*) FROM starfree_ads WHERE status=0) upcomingAds,"
                        + "(SELECT COUNT(*) FROM starfree_userlog WHERE type='withdraw' AND cid=-1) upcomingWithdraw");
        String[] names = {
                "allContents", "allComments", "allUsers", "allShop", "allSpace", "allAds",
                "upcomingContents", "selfDelete", "upcomingComments", "upcomingShop",
                "upcomingSpace", "upcomingAds", "upcomingWithdraw"
        };
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : names) {
            result.put(name, number(get(raw, name)));
        }
        return result;
    }

    private int setPresentationFlag(String token, long cid, String column, int value) {
        access.requireStaff(token);
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("参数错误");
        }
        requireContent(cid);
        // column is supplied only by the three private wrapper methods above, never by a request.
        int changed = jdbc.update("UPDATE starfree_contents SET `" + column
                        + "`=?,modified=? WHERE cid=?",
                value, Instant.now().getEpochSecond(), cid);
        if (changed > 0) {
            caches.afterContentWrite(cid);
        }
        return changed;
    }

    private Map<String, Object> requireContent(long cid) {
        if (cid <= 0) {
            throw new IllegalArgumentException("数据不存在");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT cid,authorId,type,status FROM starfree_contents WHERE cid=? LIMIT 1", cid);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("数据不存在");
        }
        return rows.get(0);
    }

    private Set<String> reservedFields() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT fields FROM starfree_apiconfig ORDER BY id LIMIT 1");
        if (rows.isEmpty()) {
            return Collections.emptySet();
        }
        String configured = text(get(rows.get(0), "fields"));
        if (configured.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String item : configured.split(",")) {
            if (!item.trim().isEmpty()) {
                values.add(item.trim());
            }
        }
        return values;
    }

    private Object get(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private int integer(Object value) {
        return (int) number(value);
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
