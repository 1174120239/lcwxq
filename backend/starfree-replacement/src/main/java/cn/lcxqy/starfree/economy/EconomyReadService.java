package cn.lcxqy.starfree.economy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EconomyReadService {
    private final JdbcTemplate jdbc;

    public EconomyReadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EconomyAccountService.Page rewards(long cid, int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_userlog WHERE cid=? AND type='reward'",
                Integer.class, cid);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT l.id,l.uid,l.cid,l.type,l.num,l.created,l.toid,"
                        + "u.name AS user_name,u.screenName AS user_screenName,"
                        + "u.avatar AS user_avatar,u.customize AS user_customize,"
                        + "u.experience AS user_experience,u.vip AS user_vip "
                        + "FROM starfree_userlog l LEFT JOIN starfree_users u ON u.uid=l.uid "
                        + "WHERE l.cid=? AND l.type='reward' "
                        + "ORDER BY l.created DESC,l.id DESC LIMIT ?,?",
                cid, (safePage - 1) * safeLimit, safeLimit);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            copy(item, row, "id", "uid", "cid", "type", "num", "created", "toid");
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("uid", number(row.get("uid")));
            String name = text(row.get("user_screenName"));
            if (name.isEmpty()) {
                name = text(row.get("user_name"));
            }
            user.put("name", name.isEmpty() ? "\u7528\u6237\u5df2\u6ce8\u9500" : name);
            user.put("screenName", text(row.get("user_screenName")));
            user.put("avatar", text(row.get("user_avatar")));
            user.put("customize", text(row.get("user_customize")));
            user.put("experience", number(row.get("user_experience")));
            long vip = number(row.get("user_vip"));
            int isVip = vip == 1 || vip > Instant.now().getEpochSecond() ? 1 : 0;
            user.put("vip", vip);
            user.put("isvip", isVip);
            item.put("userJson", user);
            item.put("vip", vip);
            item.put("isvip", isVip);
            data.add(item);
        }
        return new EconomyAccountService.Page(data, total == null ? 0 : total);
    }

    private void copy(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.get(key) != null) {
                target.put(key, source.get(key));
            }
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
