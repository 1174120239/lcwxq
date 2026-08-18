package cn.lcxqy.starfree.lostfound;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LostFoundService {
    static final int STATUS_PENDING = 0;
    static final int STATUS_ACTIVE = 1;
    static final int STATUS_RESOLVED = 2;
    static final int STATUS_REJECTED = 3;
    static final int STATUS_CLOSED = 4;
    private static final int MAX_PAGE_SIZE = 30;

    private final JdbcTemplate jdbc;
    private final StaffAccess access;
    private final LegacyTokenService tokens;
    private final LostFoundConfigService config;

    public LostFoundService(JdbcTemplate jdbc, StaffAccess access, LegacyTokenService tokens,
                            LostFoundConfigService config) {
        this.jdbc = jdbc;
        this.access = access;
        this.tokens = tokens;
        this.config = config;
    }

    public Page itemList(Map<String, String> request) {
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        int limit = bounded(RequestValues.integer(request, "limit", 10));
        int kind = ranged(RequestValues.integer(request, "kind", 0), 0, 2, "类型参数错误");
        int category = ranged(RequestValues.integer(request, "category", 0), 0, 5, "分类参数错误");
        int state = ranged(RequestValues.integer(request, "state", 0), 0, 2, "状态参数错误");
        String keyword = RequestValues.text(request, "keyword");
        List<Object> args = new ArrayList<Object>();
        String where = state == STATUS_RESOLVED ? " WHERE i.status=2" : " WHERE i.status=1";
        if (state != STATUS_RESOLVED) {
            where += " AND i.created>=?";
            args.add(Instant.now().getEpochSecond() - config.config().getItemExpiryDays() * 86400L);
        }
        if (kind > 0) {
            where += " AND i.kind=?";
            args.add(kind);
        }
        if (category > 0) {
            where += " AND i.category=?";
            args.add(category);
        }
        if (!keyword.isEmpty()) {
            where += " AND (i.title LIKE ? OR i.description LIKE ? OR i.location LIKE ?)";
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return page(where, args, page, limit);
    }

    public Map<String, Object> itemInfo(long id, String token) {
        if (id <= 0) {
            throw new IllegalArgumentException("信息不存在");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(select() + " WHERE i.id=? LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("信息不存在");
        }
        Map<String, Object> row = rows.get(0);
        int status = integer(value(row, "status"));
        long ownerUid = number(value(row, "uid"));
        Viewer viewer = viewer(token);
        if (status != STATUS_ACTIVE && status != STATUS_RESOLVED && !viewer.canManage(ownerUid)) {
            throw new IllegalArgumentException("信息不存在或正在审核");
        }
        return normalize(row);
    }

    @Transactional
    public Map<String, Object> itemAdd(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = config.requireParticipant(token);
        Values values = values(body);
        long now = Instant.now().getEpochSecond();
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_lost_found_items "
                        + "WHERE uid=? AND kind=? AND title=? AND created>=?",
                Integer.class, actor.getUid(), values.kind, values.title, now - 20);
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("信息已提交，请勿重复发布");
        }
        int status = actor.isStaff() || !config.config().isAuditRequired()
                ? STATUS_ACTIVE : STATUS_PENDING;
        long id = insertKey("INSERT INTO starfree_lost_found_items"
                        + "(uid,kind,category,title,description,image_url,location,occurred_at,status,"
                        + "review_reason,reviewed_by,reviewed_at,created,modified) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,'',0,0,?,?)",
                actor.getUid(), values.kind, values.category, values.title, values.description,
                values.imageUrl, values.location, values.occurredAt, status, now, now);
        audit(id, actor.getUid(), status, status, "create", "");
        return itemInfo(id, token);
    }

    @Transactional
    public Map<String, Object> itemEdit(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = config.requireParticipant(token);
        long id = positive(body.get("id"), "信息不存在");
        Map<String, Object> existing = requireRow(id);
        long ownerUid = number(value(existing, "uid"));
        requireOwnerOrStaff(actor, ownerUid);
        int oldStatus = integer(value(existing, "status"));
        if (oldStatus == STATUS_CLOSED) {
            throw new IllegalArgumentException("已关闭的信息不能修改");
        }
        Values values = values(body);
        int nextStatus = actor.isStaff() ? oldStatus
                : (config.config().isAuditRequired() ? STATUS_PENDING : STATUS_ACTIVE);
        long now = Instant.now().getEpochSecond();
        int changed = jdbc.update("UPDATE starfree_lost_found_items SET kind=?,category=?,title=?,"
                        + "description=?,image_url=?,location=?,occurred_at=?,status=?,review_reason='',"
                        + "reviewed_by=0,reviewed_at=0,modified=? WHERE id=?",
                values.kind, values.category, values.title, values.description, values.imageUrl,
                values.location, values.occurredAt, nextStatus, now, id);
        if (changed != 1) {
            throw new IllegalArgumentException("信息不存在");
        }
        audit(id, actor.getUid(), oldStatus, nextStatus, "edit", "");
        return itemInfo(id, token);
    }

    @Transactional
    public Map<String, Object> itemStatus(String token, long id, String action) {
        StaffAccess.Actor actor = access.requireUser(token);
        Map<String, Object> existing = requireRow(id);
        requireOwnerOrStaff(actor, number(value(existing, "uid")));
        int oldStatus = integer(value(existing, "status"));
        int nextStatus;
        if ("resolve".equals(action) && oldStatus == STATUS_ACTIVE) {
            nextStatus = STATUS_RESOLVED;
        } else if ("reopen".equals(action) && oldStatus == STATUS_RESOLVED) {
            actor = config.requireParticipant(token);
            nextStatus = actor.isStaff() || !config.config().isAuditRequired()
                    ? STATUS_ACTIVE : STATUS_PENDING;
        } else {
            throw new IllegalArgumentException("当前状态不能执行该操作");
        }
        updateStatus(id, actor.getUid(), oldStatus, nextStatus, action, "");
        return itemInfo(id, token);
    }

    @Transactional
    public Map<String, Object> itemDelete(String token, long id) {
        StaffAccess.Actor actor = access.requireUser(token);
        Map<String, Object> existing = requireRow(id);
        requireOwnerOrStaff(actor, number(value(existing, "uid")));
        int oldStatus = integer(value(existing, "status"));
        if (oldStatus == STATUS_CLOSED) {
            throw new IllegalArgumentException("信息已经关闭");
        }
        updateStatus(id, actor.getUid(), oldStatus, STATUS_CLOSED, "close", "");
        return itemInfo(id, token);
    }

    public Page itemManage(Map<String, String> request) {
        StaffAccess.Actor actor = access.requireUser(RequestValues.text(request, "token"));
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        int limit = bounded(RequestValues.integer(request, "limit", 10));
        int status = ranged(RequestValues.integer(request, "status", -1), -1, STATUS_CLOSED,
                "状态参数错误");
        long requestedUid = RequestValues.integer(request, "uid", 0);
        long ownerUid = actor.isStaff() ? requestedUid : actor.getUid();
        List<Object> args = new ArrayList<Object>();
        String where = " WHERE 1=1";
        if (ownerUid > 0) {
            where += " AND i.uid=?";
            args.add(ownerUid);
        }
        if (status >= 0) {
            where += " AND i.status=?";
            args.add(status);
        }
        return page(where, args, page, limit);
    }

    @Transactional
    public Map<String, Object> itemAudit(String token, long id, String action, String rawReason) {
        StaffAccess.Actor actor = access.requireStaff(token);
        Map<String, Object> existing = requireRow(id);
        int oldStatus = integer(value(existing, "status"));
        int nextStatus;
        String reason = optional(rawReason, 500, "审核理由不能超过500个字");
        if ("approve".equals(action)) {
            if (oldStatus != STATUS_PENDING && oldStatus != STATUS_REJECTED) {
                throw new IllegalArgumentException("当前状态不能审核通过");
            }
            nextStatus = STATUS_ACTIVE;
            reason = "";
        } else if ("reject".equals(action)) {
            if (oldStatus != STATUS_PENDING && oldStatus != STATUS_ACTIVE) {
                throw new IllegalArgumentException("当前状态不能审核拒绝");
            }
            if (reason.isEmpty()) {
                throw new IllegalArgumentException("拒绝时必须填写理由");
            }
            nextStatus = STATUS_REJECTED;
        } else {
            throw new IllegalArgumentException("审核动作错误");
        }
        if (oldStatus == nextStatus) {
            throw new IllegalArgumentException("信息已经是当前状态");
        }
        long now = Instant.now().getEpochSecond();
        int changed = jdbc.update("UPDATE starfree_lost_found_items SET status=?,review_reason=?,"
                        + "reviewed_by=?,reviewed_at=?,modified=? WHERE id=?",
                nextStatus, reason, actor.getUid(), now, now, id);
        if (changed != 1) {
            throw new IllegalArgumentException("信息不存在");
        }
        audit(id, actor.getUid(), oldStatus, nextStatus, action, reason);
        notifyOwner(number(value(existing, "uid")), actor.getUid(), id, nextStatus, reason);
        return itemInfo(id, token);
    }

    private Page page(String where, List<Object> args, int page, int limit) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_lost_found_items i" + where,
                Integer.class, args.toArray());
        List<Object> pagedArgs = new ArrayList<Object>(args);
        pagedArgs.add((page - 1) * limit);
        pagedArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(select() + where
                + " ORDER BY i.modified DESC,i.id DESC LIMIT ?,?", pagedArgs.toArray());
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            data.add(normalize(row));
        }
        return new Page(data, total == null ? 0 : total);
    }

    private String select() {
        return "SELECT i.id,i.uid,i.kind,i.category,i.title,i.description,i.image_url,"
                + "i.location,i.occurred_at,i.status,i.review_reason,i.reviewed_by,"
                + "i.reviewed_at,i.created,i.modified FROM starfree_lost_found_items i";
    }

    private Map<String, Object> requireRow(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("信息不存在");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(select() + " WHERE i.id=? LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("信息不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", number(value(row, "id")));
        result.put("uid", number(value(row, "uid")));
        result.put("kind", number(value(row, "kind")));
        result.put("category", number(value(row, "category")));
        result.put("title", text(value(row, "title")));
        result.put("description", text(value(row, "description")));
        result.put("imageUrl", text(value(row, "image_url")));
        result.put("location", text(value(row, "location")));
        result.put("occurredAt", number(value(row, "occurred_at")));
        result.put("status", number(value(row, "status")));
        result.put("reviewReason", text(value(row, "review_reason")));
        result.put("created", number(value(row, "created")));
        result.put("modified", number(value(row, "modified")));
        long expiresAt = number(value(row, "created"))
                + config.config().getItemExpiryDays() * 86400L;
        result.put("expiresAt", expiresAt);
        result.put("expired", expiresAt < Instant.now().getEpochSecond() ? 1 : 0);
        result.put("userJson", publicUser(number(value(row, "uid"))));
        return result;
    }

    private Map<String, Object> publicUser(long uid) {
        Map<String, Object> source = tokens.publicUserById(uid);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("uid", uid);
        if (source == null) {
            result.put("name", "已注销用户");
            result.put("avatar", "");
            result.put("campus", "");
            result.put("grade", "");
            return result;
        }
        String screenName = text(source.get("screenName"));
        result.put("name", screenName.isEmpty() ? text(source.get("name")) : screenName);
        result.put("avatar", text(source.get("avatar")));
        result.put("campus", text(source.get("campus")));
        result.put("grade", text(source.get("grade")));
        return result;
    }

    private Values values(Map<String, Object> body) {
        int kind = ranged(integer(body.get("kind")), 1, 2, "请选择寻物或招领");
        int category = ranged(integer(body.get("category")), 1, 5, "请选择互助分类");
        String title = required(body.get("title"), 4, 120,
                "标题至少需要4个字", "标题不能超过120个字");
        String description = required(body.get("description"), 5, 5000,
                "请补充物品特征", "详细说明不能超过5000个字");
        String imageUrl = optional(body.get("imageUrl"), 500, "图片地址不能超过500个字");
        String location = required(body.get("location"), 2, 120,
                "请填写丢失或拾取地点", "地点不能超过120个字");
        long occurredAt = number(body.get("occurredAt"));
        long now = Instant.now().getEpochSecond();
        if (occurredAt < 0 || occurredAt > now + 86400) {
            throw new IllegalArgumentException("时间参数错误");
        }
        return new Values(kind, category, title, description, imageUrl, location, occurredAt);
    }

    private void updateStatus(long id, long operatorUid, int oldStatus, int nextStatus,
                              String action, String reason) {
        int changed = jdbc.update("UPDATE starfree_lost_found_items SET status=?,modified=? WHERE id=?",
                nextStatus, Instant.now().getEpochSecond(), id);
        if (changed != 1) {
            throw new IllegalArgumentException("信息不存在");
        }
        audit(id, operatorUid, oldStatus, nextStatus, action, reason);
    }

    private void audit(long id, long operatorUid, int oldStatus, int nextStatus,
                       String action, String reason) {
        jdbc.update("INSERT INTO starfree_lost_found_actions"
                        + "(item_id,operator_uid,from_status,to_status,action,reason,created) "
                        + "VALUES(?,?,?,?,?,?,?)",
                id, operatorUid, oldStatus, nextStatus, action, reason,
                Instant.now().getEpochSecond());
    }

    private void notifyOwner(long ownerUid, long staffUid, long itemId, int status, String reason) {
        if (ownerUid <= 0 || ownerUid == staffUid) {
            return;
        }
        String text = status == STATUS_ACTIVE ? "你的失物招领信息已通过审核"
                : "你的失物招领信息未通过审核：" + reason;
        try {
            jdbc.update("INSERT INTO starfree_inbox(type,uid,text,touid,isread,value,created,cid) "
                            + "VALUES('system',?,?,?,0,?,?,?)",
                    staffUid, text, ownerUid, itemId, Instant.now().getEpochSecond(), itemId);
        } catch (DataAccessException ignored) {
            // The item and audit rows are authoritative if the legacy inbox is unavailable.
        }
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
            throw new IllegalStateException("未能创建失物招领信息");
        }
        return key.longValue();
    }

    private Viewer viewer(String token) {
        Map<String, Object> user = tokens.user(token);
        if (user == null) {
            return new Viewer(0, false);
        }
        String group = text(user.get("group"));
        return new Viewer(number(user.get("uid")),
                "administrator".equals(group) || "editor".equals(group));
    }

    private void requireOwnerOrStaff(StaffAccess.Actor actor, long ownerUid) {
        if (actor.getUid() != ownerUid && !actor.isStaff()) {
            throw new IllegalArgumentException("你没有操作权限");
        }
    }

    private long positive(Object value, String message) {
        long id = number(value);
        if (id <= 0) {
            throw new IllegalArgumentException(message);
        }
        return id;
    }

    private int ranged(int value, int minimum, int maximum, String message) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String required(Object raw, int minimum, int maximum,
                            String minimumMessage, String maximumMessage) {
        String value = text(raw).trim();
        if (value.length() < minimum) {
            throw new IllegalArgumentException(minimumMessage);
        }
        if (value.length() > maximum) {
            throw new IllegalArgumentException(maximumMessage);
        }
        return value;
    }

    private String optional(Object raw, int maximum, String message) {
        String value = text(raw).trim();
        if (value.length() > maximum) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private int bounded(int requested) {
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }

    private Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
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
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class Values {
        private final int kind;
        private final int category;
        private final String title;
        private final String description;
        private final String imageUrl;
        private final String location;
        private final long occurredAt;

        private Values(int kind, int category, String title, String description,
                       String imageUrl, String location, long occurredAt) {
            this.kind = kind;
            this.category = category;
            this.title = title;
            this.description = description;
            this.imageUrl = imageUrl;
            this.location = location;
            this.occurredAt = occurredAt;
        }
    }

    private static final class Viewer {
        private final long uid;
        private final boolean staff;

        private Viewer(long uid, boolean staff) {
            this.uid = uid;
            this.staff = staff;
        }

        private boolean canManage(long ownerUid) {
            return uid > 0 && (uid == ownerUid || staff);
        }
    }

    public static final class Page {
        private final List<Map<String, Object>> data;
        private final int total;

        private Page(List<Map<String, Object>> data, int total) {
            this.data = data == null ? Collections.<Map<String, Object>>emptyList() : data;
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
