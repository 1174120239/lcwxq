package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.StaffAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CampusIdentityService {
    private final JdbcTemplate jdbc;
    private final StaffAccess access;

    public CampusIdentityService(JdbcTemplate jdbc, StaffAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public Map<String, Object> registrationOptions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("campuses", options("campus", true));
        result.put("grades", options("grade", true));
        return result;
    }

    public Map<String, Object> manageOptions(String token) {
        access.requireStaff(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("campuses", options("campus", false));
        result.put("grades", options("grade", false));
        return result;
    }

    public Map<String, Object> save(String token, Map<String, Object> body) {
        access.requireStaff(token);
        long id = number(body.get("id"));
        String type = RequestValues.objectText(body, "type");
        String name = RequestValues.objectText(body, "name");
        int sort = RequestValues.objectInteger(body, "sortOrder", 0);
        int enabled = RequestValues.objectInteger(body, "enabled", 1);
        if (!("campus".equals(type) || "grade".equals(type))) {
            throw new IllegalArgumentException("选项类型不正确");
        }
        if (name.isEmpty() || name.length() > 40 || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("选项名称长度必须为1到40个字符");
        }
        if ("grade".equals(type) && !name.matches("\\d{4}级")) {
            throw new IllegalArgumentException("年级名称请使用“2024级”格式");
        }
        if (enabled != 0 && enabled != 1) {
            throw new IllegalArgumentException("启用状态不正确");
        }
        long now = Instant.now().getEpochSecond();
        if (id > 0) {
            int changed = jdbc.update("UPDATE starfree_identity_options SET name=?,sort_order=?,"
                            + "enabled=?,modified=? WHERE id=? AND type=?",
                    name, sort, enabled, now, id, type);
            if (changed != 1) throw new IllegalArgumentException("选项不存在");
        } else {
            jdbc.update("INSERT INTO starfree_identity_options"
                            + "(type,name,sort_order,enabled,created,modified) VALUES(?,?,?,?,?,?)",
                    type, name, sort, enabled, now, now);
            Long created = jdbc.queryForObject("SELECT id FROM starfree_identity_options "
                    + "WHERE type=? AND name=? LIMIT 1", Long.class, type, name);
            id = created == null ? 0 : created;
        }
        return option(id);
    }

    private List<Map<String, Object>> options(String type, boolean enabledOnly) {
        String usage = enabledOnly ? "" : ","
                + "(SELECT COUNT(*) FROM starfree_users u WHERE "
                + ("campus".equals(type) ? "u.campus_option_id=o.id" : "u.grade_option_id=o.id")
                + ") AS userCount";
        String sql = "SELECT id,type,name,sort_order,enabled,created,modified" + usage
                + " FROM starfree_identity_options o WHERE type=?"
                + (enabledOnly ? " AND enabled=1" : "")
                + " ORDER BY sort_order DESC,id DESC";
        return normalize(jdbc.queryForList(sql, type));
    }

    private Map<String, Object> option(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,type,name,sort_order,enabled,created,modified FROM starfree_identity_options WHERE id=?",
                id);
        if (rows.isEmpty()) throw new IllegalArgumentException("选项不存在");
        return normalize(rows).get(0);
    }

    private List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", number(value(row, "id")));
            item.put("type", text(value(row, "type")));
            item.put("name", text(value(row, "name")));
            item.put("sortOrder", number(value(row, "sort_order")));
            item.put("enabled", number(value(row, "enabled")));
            if (value(row, "userCount") != null) item.put("userCount", number(value(row, "userCount")));
            result.add(item);
        }
        return result;
    }

    private Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }

    private long number(Object value) {
        try { return value == null ? 0 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
