package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.LegacyTokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserInteractionService {
    private final JdbcTemplate jdbc;
    private final LegacyTokenService tokens;

    public UserInteractionService(JdbcTemplate jdbc, LegacyTokenService tokens) {
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    public InboxPage inbox(Map<String, String> request) {
        long uid = requireUser(request);
        String type = RequestValues.text(request, "type");
        int limit = bounded(RequestValues.integer(request, "limit", 10), 40);
        int page = Math.max(1, RequestValues.integer(request, "page", 1));

        List<Object> filters = new ArrayList<>();
        String where = inboxTypeWhere(type, filters);
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(uid);
        countArgs.addAll(filters);
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_inbox WHERE touid = ?" + where,
                Integer.class, countArgs.toArray());

        List<Object> rowArgs = new ArrayList<>(countArgs);
        rowArgs.add((page - 1) * limit);
        rowArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,type,uid,text,touid,isread,value,created,cid FROM starfree_inbox WHERE touid = ?"
                        + where + " ORDER BY created DESC,id DESC LIMIT ?, ?",
                rowArgs.toArray());

        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            data.add(toInbox(row));
        }
        return new InboxPage(data, total == null ? 0 : total);
    }

    public int unread(Map<String, String> request) {
        long uid = requireUser(request);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_inbox WHERE touid = ? AND isread = 0",
                Integer.class, uid);
        return count == null ? 0 : count;
    }

    public int markRead(Map<String, String> request) {
        long uid = requireUser(request);
        String type = RequestValues.text(request, "type");
        if (type.isEmpty() || "all".equals(type)) {
            return jdbc.update("UPDATE starfree_inbox SET isread = 1 WHERE touid = ? AND isread = 0", uid);
        }
        if ("comment".equals(type)) {
            return jdbc.update("UPDATE starfree_inbox SET isread = 1 WHERE touid = ? AND isread = 0 "
                    + "AND type IN ('comment', 'postComment')", uid);
        }
        if ("finance".equals(type) || "system".equals(type) || "fan".equals(type)) {
            return jdbc.update("UPDATE starfree_inbox SET isread = 1 WHERE touid = ? AND isread = 0 AND type = ?",
                    uid, type);
        }
        if ("chat".equals(type)) {
            return 0;
        }
        throw new IllegalArgumentException("Unsupported notification type");
    }

    public boolean isFollowing(String token, long targetUid) {
        Long uid = tokens.userId(token);
        if (uid == null || targetUid <= 0 || uid == targetUid) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_fan WHERE uid = ? AND touid = ?",
                Integer.class, uid, targetUid);
        return count != null && count > 0;
    }

    public int follow(Map<String, String> request) {
        long uid = requireUser(request);
        long targetUid = RequestValues.integer(request, "touid", 0);
        int type = RequestValues.integer(request, "type", -1);
        if (targetUid <= 0 || tokens.userById(targetUid) == null) {
            throw new IllegalArgumentException("Target user does not exist");
        }
        if (uid == targetUid) {
            throw new IllegalArgumentException("You cannot follow yourself");
        }

        if (type == 1) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM starfree_fan WHERE uid = ? AND touid = ?",
                    Integer.class, uid, targetUid);
            if (count != null && count > 0) {
                return 0;
            }
            long now = Instant.now().getEpochSecond();
            int changed = jdbc.update("INSERT INTO starfree_fan (created,uid,touid) VALUES (?,?,?)",
                    now, uid, targetUid);
            if (changed > 0) {
                jdbc.update("INSERT INTO starfree_inbox (type,uid,text,touid,isread,value,created,cid) "
                                + "VALUES (?,?,?,?,?,?,?,?)",
                        "fan", uid, "Followed you", targetUid, 0, 0, now, 0);
            }
            return changed;
        }
        if (type == 0) {
            return jdbc.update("DELETE FROM starfree_fan WHERE uid = ? AND touid = ?", uid, targetUid);
        }
        throw new IllegalArgumentException("Invalid follow operation");
    }

    public FollowPage followList(long uid, int limit, int page) {
        return followPage(uid, limit, page, false);
    }

    public FollowPage fanList(long targetUid, int limit, int page) {
        return followPage(targetUid, limit, page, true);
    }

    private FollowPage followPage(long userId, int limit, int page, boolean fans) {
        if (userId <= 0) {
            return new FollowPage(Collections.<Map<String, Object>>emptyList(), 0);
        }
        int safeLimit = bounded(limit, 50);
        int safePage = Math.max(1, page);
        String relationColumn = fans ? "touid" : "uid";
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_fan WHERE " + relationColumn + " = ?",
                Integer.class, userId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,created,uid,touid FROM starfree_fan WHERE " + relationColumn
                        + " = ? ORDER BY created DESC,id DESC LIMIT ?, ?",
                userId, (safePage - 1) * safeLimit, safeLimit);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> result = new LinkedHashMap<>(row);
            long profileUid = number(row.get(fans ? "uid" : "touid"));
            result.put("userJson", publicUser(profileUid));
            data.add(result);
        }
        return new FollowPage(data, total == null ? 0 : total);
    }

    private Map<String, Object> toInbox(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("userJson", publicUser(number(row.get("uid"))));
        if ("comment".equals(String.valueOf(row.get("type")))) {
            List<Map<String, Object>> contents = jdbc.queryForList(
                    "SELECT cid,slug,title,type FROM starfree_contents WHERE cid = ? LIMIT 1",
                    number(row.get("value")));
            if (contents.isEmpty()) {
                result.put("contenTitle", "Content deleted");
            } else {
                Map<String, Object> content = contents.get(0);
                result.put("contenTitle", content.get("title"));
                result.put("contentsInfo", new LinkedHashMap<>(content));
            }
        }
        return result;
    }

    private Map<String, Object> publicUser(long uid) {
        Map<String, Object> source = tokens.userById(uid);
        if (source == null) {
            Map<String, Object> removed = new LinkedHashMap<>();
            removed.put("uid", uid);
            removed.put("name", "Deleted user");
            removed.put("avatar", "");
            removed.put("group", "visitor");
            removed.put("groupKey", "visitor");
            removed.put("isvip", 0);
            return removed;
        }
        Map<String, Object> result = new LinkedHashMap<>(source);
        String group = String.valueOf(source.get("group"));
        result.put("groupKey", group);
        result.put("isvip", isVip(source.get("vip")) ? 1 : 0);
        return result;
    }

    private long requireUser(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("User login is required");
        }
        return uid;
    }

    private String inboxTypeWhere(String type, List<Object> filters) {
        if (type == null || type.isEmpty() || "all".equals(type)) {
            return "";
        }
        if ("comment".equals(type)) {
            return " AND type IN ('comment', 'postComment')";
        }
        filters.add(type);
        return " AND type = ?";
    }

    private int bounded(int requested, int maximum) {
        return Math.max(1, Math.min(requested, maximum));
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private boolean isVip(Object value) {
        long vip = number(value);
        return vip == 1 || vip > Instant.now().getEpochSecond();
    }

    public static final class InboxPage {
        private final List<Map<String, Object>> data;
        private final int total;

        InboxPage(List<Map<String, Object>> data, int total) {
            this.data = data;
            this.total = total;
        }

        public List<Map<String, Object>> getData() {
            return data;
        }

        public int getTotal() {
            return total;
        }
    }

    public static final class FollowPage {
        private final List<Map<String, Object>> data;
        private final int total;

        FollowPage(List<Map<String, Object>> data, int total) {
            this.data = data;
            this.total = total;
        }

        public List<Map<String, Object>> getData() {
            return data;
        }

        public int getTotal() {
            return total;
        }
    }
}
