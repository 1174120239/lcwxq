package cn.lcxqy.starfree.lostfound;

import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LostFoundCommentService {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbc;
    private final StaffAccess access;
    private final LegacyTokenService tokens;
    private final LostFoundConfigService config;

    public LostFoundCommentService(JdbcTemplate jdbc, StaffAccess access,
                                   LegacyTokenService tokens, LostFoundConfigService config) {
        this.jdbc = jdbc;
        this.access = access;
        this.tokens = tokens;
        this.config = config;
    }

    public List<Map<String, Object>> comments(long itemId) {
        requireVisibleItem(itemId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,item_id,uid,parent_id,root_id,text,created,modified "
                        + "FROM starfree_lost_found_comments WHERE item_id=? AND status=1 "
                        + "ORDER BY created ASC,id ASC", itemId);
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<Long, Map<String, Object>>();
        List<Map<String, Object>> roots = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> comment = normalize(row);
            byId.put(number(rowValue(row, "id")), comment);
        }
        for (Map<String, Object> comment : byId.values()) {
            long parentId = number(comment.get("parentId"));
            Map<String, Object> parent = byId.get(parentId);
            if (parentId > 0 && parent != null) {
                children(parent).add(comment);
            } else {
                roots.add(comment);
            }
        }
        return roots;
    }

    @Transactional
    public Map<String, Object> add(String token, long itemId, long parentId, String rawText) {
        StaffAccess.Actor actor = config.requireParticipant(token);
        Map<String, Object> item = requireActiveItem(itemId);
        ensureNotExpired(item);
        String text = rawText == null ? "" : rawText.trim();
        if (text.length() < 2) {
            throw new IllegalArgumentException("评论至少需要2个字");
        }
        if (text.length() > 1000) {
            throw new IllegalArgumentException("评论不能超过1000个字");
        }
        long rootId = 0;
        if (parentId > 0) {
            Map<String, Object> parent = requireComment(parentId, itemId);
            rootId = number(rowValue(parent, "root_id"));
            if (rootId <= 0) {
                rootId = parentId;
            }
        }
        long now = Instant.now().getEpochSecond();
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_lost_found_comments "
                        + "WHERE item_id=? AND uid=? AND text=? AND status=1 AND created>=?",
                Integer.class, itemId, actor.getUid(), text, now - 10);
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("评论已提交，请勿重复发送");
        }
        long id = insertKey("INSERT INTO starfree_lost_found_comments"
                        + "(item_id,uid,parent_id,root_id,text,status,created,modified) "
                        + "VALUES(?,?,?,?,?,1,?,?)",
                itemId, actor.getUid(), parentId, rootId, text, now, now);
        return comment(id);
    }

    @Transactional
    public void delete(String token, long commentId) {
        StaffAccess.Actor actor = access.requireUser(token);
        Map<String, Object> comment = requireComment(commentId, 0);
        long ownerUid = number(rowValue(comment, "uid"));
        if (actor.getUid() != ownerUid && !actor.isStaff()) {
            throw new IllegalArgumentException("你没有操作权限");
        }
        jdbc.update("UPDATE starfree_lost_found_comments SET status=0,modified=? WHERE id=?",
                Instant.now().getEpochSecond(), commentId);
        jdbc.update("DELETE FROM starfree_lost_found_contact_grants WHERE comment_id=?", commentId);
    }

    @Transactional
    public Map<String, Object> shareContact(String token, long itemId, long commentId) {
        StaffAccess.Actor actor = config.requireParticipant(token);
        LostFoundConfigService.Config settings = config.config();
        if (!settings.isContactEnabled()) {
            throw new IllegalArgumentException("联系方式交换暂未开放");
        }
        Map<String, Object> item = requireActiveItem(itemId);
        ensureNotExpired(item);
        Map<String, Object> comment = requireComment(commentId, itemId);
        long itemOwner = number(rowValue(item, "uid"));
        long commentOwner = number(rowValue(comment, "uid"));
        long receiverUid;
        if (actor.getUid() == itemOwner) {
            receiverUid = commentOwner;
        } else if (actor.getUid() == commentOwner) {
            receiverUid = itemOwner;
        } else {
            throw new IllegalArgumentException("只能向与你交流的用户发送联系方式");
        }
        if (receiverUid <= 0 || receiverUid == actor.getUid()) {
            throw new IllegalArgumentException("不能向自己发送联系方式");
        }
        String qq = qqNumber(actor.getUid());
        if (qq.isEmpty()) {
            throw new IllegalArgumentException("当前账号未绑定有效的QQ邮箱");
        }
        long dayStart = LocalDate.now(CAMPUS_ZONE).atStartOfDay(CAMPUS_ZONE).toEpochSecond();
        Integer sentToday = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_lost_found_contact_grants "
                        + "WHERE sender_uid=? AND created>=?", Integer.class, actor.getUid(), dayStart);
        if (sentToday != null && sentToday >= settings.getDailyContactLimit()) {
            throw new IllegalArgumentException("今天发送联系方式的次数已达上限");
        }
        try {
            jdbc.update("INSERT INTO starfree_lost_found_contact_grants"
                            + "(item_id,comment_id,sender_uid,receiver_uid,created,viewed) "
                            + "VALUES(?,?,?,?,?,0)",
                    itemId, commentId, actor.getUid(), receiverUid,
                    Instant.now().getEpochSecond());
        } catch (DuplicateKeyException ignored) {
            // Repeated taps are idempotent and never consume another daily quota.
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("commentId", commentId);
        result.put("receiverUid", receiverUid);
        result.put("sent", 1);
        return result;
    }

    @Transactional
    public Map<String, Object> contactAccess(String token, long itemId) {
        StaffAccess.Actor actor = config.requireParticipant(token);
        requireVisibleItem(itemId);
        List<Map<String, Object>> receivedRows = jdbc.queryForList(
                "SELECT id,comment_id,sender_uid,receiver_uid,created,viewed "
                        + "FROM starfree_lost_found_contact_grants "
                        + "WHERE item_id=? AND receiver_uid=? ORDER BY created ASC",
                itemId, actor.getUid());
        List<Map<String, Object>> sentRows = jdbc.queryForList(
                "SELECT comment_id,receiver_uid,created FROM starfree_lost_found_contact_grants "
                        + "WHERE item_id=? AND sender_uid=? ORDER BY created ASC",
                itemId, actor.getUid());
        List<Map<String, Object>> received = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : receivedRows) {
            long senderUid = number(rowValue(row, "sender_uid"));
            String qq = qqNumber(senderUid);
            if (qq.isEmpty()) {
                continue;
            }
            Map<String, Object> grant = new LinkedHashMap<String, Object>();
            grant.put("commentId", number(rowValue(row, "comment_id")));
            grant.put("senderUid", senderUid);
            grant.put("qq", qq);
            grant.put("userJson", publicUser(senderUid));
            grant.put("created", number(rowValue(row, "created")));
            received.add(grant);
        }
        if (!receivedRows.isEmpty()) {
            jdbc.update("UPDATE starfree_lost_found_contact_grants SET viewed=? "
                            + "WHERE item_id=? AND receiver_uid=? AND viewed=0",
                    Instant.now().getEpochSecond(), itemId, actor.getUid());
        }
        List<Map<String, Object>> sent = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : sentRows) {
            Map<String, Object> grant = new LinkedHashMap<String, Object>();
            grant.put("commentId", number(rowValue(row, "comment_id")));
            grant.put("receiverUid", number(rowValue(row, "receiver_uid")));
            grant.put("created", number(rowValue(row, "created")));
            sent.add(grant);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("received", received);
        result.put("sent", sent);
        return result;
    }

    private Map<String, Object> requireVisibleItem(long itemId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,uid,status,created,modified FROM starfree_lost_found_items "
                        + "WHERE id=? AND status IN (1,2) LIMIT 1", itemId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("互助信息不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireActiveItem(long itemId) {
        Map<String, Object> item = requireVisibleItem(itemId);
        if (number(rowValue(item, "status")) != LostFoundService.STATUS_ACTIVE) {
            throw new IllegalArgumentException("已解决的信息不能继续交流");
        }
        return item;
    }

    private void ensureNotExpired(Map<String, Object> item) {
        long expiresAt = number(rowValue(item, "created"))
                + config.config().getItemExpiryDays() * 86400L;
        if (expiresAt < Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("该互助信息已超过有效期");
        }
    }

    private Map<String, Object> requireComment(long commentId, long itemId) {
        if (commentId <= 0) {
            throw new IllegalArgumentException("评论不存在");
        }
        String sql = "SELECT id,item_id,uid,parent_id,root_id,text,status,created,modified "
                + "FROM starfree_lost_found_comments WHERE id=? AND status=1";
        List<Map<String, Object>> rows = itemId > 0
                ? jdbc.queryForList(sql + " AND item_id=? LIMIT 1", commentId, itemId)
                : jdbc.queryForList(sql + " LIMIT 1", commentId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("评论不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> comment(long id) {
        Map<String, Object> row = requireComment(id, 0);
        return normalize(row);
    }

    private Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        long uid = number(rowValue(row, "uid"));
        result.put("id", number(rowValue(row, "id")));
        result.put("itemId", number(rowValue(row, "item_id")));
        result.put("uid", uid);
        result.put("parentId", number(rowValue(row, "parent_id")));
        result.put("rootId", number(rowValue(row, "root_id")));
        result.put("text", text(rowValue(row, "text")));
        result.put("created", number(rowValue(row, "created")));
        result.put("modified", number(rowValue(row, "modified")));
        result.put("userJson", publicUser(uid));
        result.put("children", new ArrayList<Map<String, Object>>());
        return result;
    }

    private Map<String, Object> publicUser(long uid) {
        Map<String, Object> source = tokens.publicUserById(uid);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("uid", uid);
        if (source == null) {
            result.put("name", "已注销用户");
            result.put("avatar", "");
            return result;
        }
        String screenName = text(source.get("screenName"));
        result.put("name", screenName.isEmpty() ? text(source.get("name")) : screenName);
        result.put("avatar", text(source.get("avatar")));
        result.put("campus", text(source.get("campus")));
        result.put("grade", text(source.get("grade")));
        return result;
    }

    private String qqNumber(long uid) {
        Map<String, Object> user = tokens.userById(uid);
        String mail = user == null ? "" : text(user.get("mail")).trim().toLowerCase(Locale.ROOT);
        if (!mail.matches("^[1-9][0-9]{4,11}@qq\\.com$")) {
            return "";
        }
        return mail.substring(0, mail.length() - 7);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> children(Map<String, Object> comment) {
        return (List<Map<String, Object>>) comment.get("children");
    }

    private long insertKey(final String sql, final Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, holder);
        Number key = holder.getKey();
        if (key == null || key.longValue() <= 0) {
            throw new IllegalStateException("未能创建评论");
        }
        return key.longValue();
    }

    private Object rowValue(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
