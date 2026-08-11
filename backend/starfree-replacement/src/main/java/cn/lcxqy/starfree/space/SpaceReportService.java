package cn.lcxqy.starfree.space;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SpaceReportService {
    private static final Set<String> REASONS = Collections.unmodifiableSet(new HashSet<String>(
            Arrays.asList("广告营销", "人身攻击", "色情低俗", "违法违规", "其他")));

    private final JdbcTemplate jdbc;
    private final StaffAccess access;
    private final LegacyTokenService tokens;
    private final SpaceService spaces;

    public SpaceReportService(JdbcTemplate jdbc, StaffAccess access, LegacyTokenService tokens,
                              SpaceService spaces) {
        this.jdbc = jdbc;
        this.access = access;
        this.tokens = tokens;
        this.spaces = spaces;
    }

    public int add(Map<String, String> request) {
        StaffAccess.Actor actor = access.requireUser(RequestValues.text(request, "token"));
        long spaceId = positive(longValue(request.get("id")), "动态不存在");
        Map<String, Object> space = requireSpace(spaceId);
        if (number(get(space, "status")) != 1 || number(get(space, "onlyMe")) != 0
                || number(get(space, "type")) == 3) {
            throw new IllegalArgumentException("该动态当前不可举报");
        }
        if (number(get(space, "uid")) == actor.getUid()) {
            throw new IllegalArgumentException("不能举报自己的动态");
        }
        String reason = RequestValues.text(request, "reason");
        if (!REASONS.contains(reason)) {
            throw new IllegalArgumentException("请选择举报原因");
        }
        String detail = RequestValues.text(request, "detail");
        if (detail.length() > 500) {
            throw new IllegalArgumentException("举报说明不能超过500个字");
        }
        Integer duplicate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_space_reports WHERE space_id=? AND reporter_uid=?",
                Integer.class, spaceId, actor.getUid());
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("你已经举报过这条动态");
        }
        long now = Instant.now().getEpochSecond();
        try {
            return jdbc.update("INSERT INTO starfree_space_reports"
                            + "(space_id,reporter_uid,reason,detail,status,reviewer_uid,review_note,created,modified) "
                            + "VALUES(?,?,?,?,0,0,'',?,?)",
                    spaceId, actor.getUid(), reason, detail, now, now);
        } catch (DuplicateKeyException error) {
            throw new IllegalArgumentException("你已经举报过这条动态");
        }
    }

    public Page list(Map<String, String> request) {
        access.requireStaff(RequestValues.text(request, "token"));
        int limit = Math.max(1, Math.min(RequestValues.integer(request, "limit", 20), 60));
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        int status = RequestValues.integer(request, "status", 0);
        if (status < 0 || status > 2) {
            throw new IllegalArgumentException("举报状态不正确");
        }
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_space_reports WHERE status=?",
                Integer.class, status);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.id,r.space_id,r.reporter_uid,r.reason,r.detail,r.status,r.reviewer_uid,"
                        + "r.review_note,r.created,r.modified,s.id AS target_id,s.uid AS target_uid,"
                        + "s.text AS target_text,s.type AS target_type,s.status AS target_status,"
                        + "s.onlyMe AS target_only_me FROM starfree_space_reports r "
                        + "LEFT JOIN starfree_space s ON s.id=r.space_id WHERE r.status=? "
                        + "ORDER BY r.created ASC,r.id ASC LIMIT ?,?",
                status, (page - 1) * limit, limit);
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            data.add(normalize(row));
        }
        return new Page(data, total == null ? 0 : total);
    }

    public int review(Map<String, String> request) {
        StaffAccess.Actor actor = access.requireStaff(RequestValues.text(request, "token"));
        long reportId = positive(longValue(request.get("id")), "举报记录不存在");
        String action = RequestValues.text(request, "action");
        if (!"delete".equals(action) && !"dismiss".equals(action)) {
            throw new IllegalArgumentException("审核操作不正确");
        }
        String note = RequestValues.text(request, "note");
        if (note.length() > 500) {
            throw new IllegalArgumentException("审核说明不能超过500个字");
        }
        Map<String, Object> report = requirePendingReport(reportId);
        long spaceId = number(get(report, "space_id"));
        long now = Instant.now().getEpochSecond();
        if ("dismiss".equals(action)) {
            int changed = jdbc.update("UPDATE starfree_space_reports SET status=2,reviewer_uid=?,"
                            + "review_note=?,modified=? WHERE id=? AND status=0",
                    actor.getUid(), note, now, reportId);
            if (changed != 1) {
                throw new IllegalArgumentException("举报已经处理");
            }
            return changed;
        }

        if (spaceExists(spaceId)) {
            Map<String, String> deleteRequest = new LinkedHashMap<String, String>();
            deleteRequest.put("token", RequestValues.text(request, "token"));
            deleteRequest.put("id", String.valueOf(spaceId));
            try {
                spaces.delete(deleteRequest);
            } catch (IllegalArgumentException error) {
                if (spaceExists(spaceId)) {
                    throw error;
                }
            }
        }
        int changed = jdbc.update("UPDATE starfree_space_reports SET status=1,reviewer_uid=?,"
                        + "review_note=?,modified=? WHERE space_id=? AND status=0",
                actor.getUid(), note, now, spaceId);
        if (changed < 1) {
            throw new IllegalArgumentException("举报已经处理");
        }
        return changed;
    }

    private Map<String, Object> normalize(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", number(get(row, "id")));
        result.put("spaceId", number(get(row, "space_id")));
        result.put("reporterUid", number(get(row, "reporter_uid")));
        result.put("reason", text(get(row, "reason")));
        result.put("detail", text(get(row, "detail")));
        result.put("status", number(get(row, "status")));
        result.put("reviewerUid", number(get(row, "reviewer_uid")));
        result.put("reviewNote", text(get(row, "review_note")));
        result.put("created", number(get(row, "created")));
        result.put("modified", number(get(row, "modified")));
        result.put("reporterJson", publicUser(number(get(row, "reporter_uid"))));
        Object targetId = get(row, "target_id");
        if (targetId == null) {
            result.put("spaceState", "deleted");
            result.put("spaceInfo", null);
        } else {
            Map<String, Object> space = new LinkedHashMap<String, Object>();
            space.put("id", number(targetId));
            space.put("uid", number(get(row, "target_uid")));
            space.put("text", text(get(row, "target_text")));
            space.put("type", number(get(row, "target_type")));
            space.put("status", number(get(row, "target_status")));
            space.put("onlyMe", number(get(row, "target_only_me")));
            space.put("userJson", publicUser(number(get(row, "target_uid"))));
            result.put("spaceState", "visible");
            result.put("spaceInfo", space);
        }
        return result;
    }

    private Map<String, Object> publicUser(long uid) {
        Map<String, Object> source = tokens.userById(uid);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("uid", uid);
        if (source == null) {
            result.put("name", "已注销用户");
            result.put("avatar", "");
            return result;
        }
        String screenName = text(source.get("screenName"));
        result.put("name", screenName.isEmpty() ? text(source.get("name")) : screenName);
        String avatar = text(source.get("avatar"));
        String mail = text(source.get("mail")).toLowerCase();
        if (avatar.isEmpty() && mail.endsWith("@qq.com") && mail.length() > 7) {
            avatar = "https://q1.qlogo.cn/g?b=qq&nk="
                    + mail.substring(0, mail.length() - 7) + "&s=640";
        }
        result.put("avatar", avatar);
        return result;
    }

    private Map<String, Object> requireSpace(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,uid,text,type,status,onlyMe FROM starfree_space WHERE id=? LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("动态不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requirePendingReport(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,space_id,reporter_uid,status FROM starfree_space_reports "
                        + "WHERE id=? AND status=0 LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("举报记录不存在或已经处理");
        }
        return rows.get(0);
    }

    private boolean spaceExists(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_space WHERE id=?",
                Integer.class, id);
        return count != null && count > 0;
    }

    private long positive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private long longValue(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Object get(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
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
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static final class Page {
        private final List<Map<String, Object>> data;
        private final int total;

        private Page(List<Map<String, Object>> data, int total) {
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
