package cn.lcxqy.starfree.log;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.content.ContentService;
import cn.lcxqy.starfree.economy.EconomyService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserlogService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LegacyTokenService tokens;
    private final ContentService contents;
    private final EconomyService economy;

    public UserlogService(JdbcTemplate jdbc, ObjectMapper mapper, LegacyTokenService tokens,
                          ContentService contents, EconomyService economy) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tokens = tokens;
        this.contents = contents;
        this.economy = economy;
    }

    public MarkPage markList(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        int limit = Math.max(1, Math.min(RequestValues.integer(request, "limit", 15), 50));
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        Integer totalValue = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_userlog WHERE uid = ? AND type = 'mark'", Integer.class, uid);
        List<Map<String, Object>> logs = jdbc.queryForList(
                "SELECT id,cid FROM starfree_userlog WHERE uid = ? AND type = 'mark' "
                        + "ORDER BY created DESC LIMIT ?, ?",
                uid, (page - 1) * limit, limit);
        List<Map<String, Object>> data = new java.util.ArrayList<>();
        for (Map<String, Object> log : logs) {
            Map<String, Object> content = contents.info(((Number) log.get("cid")).longValue(), false);
            if (content != null && "publish".equals(content.get("status")) && "post".equals(content.get("type"))) {
                content.put("logid", log.get("id"));
                data.add(content);
            }
        }
        return new MarkPage(data, totalValue == null ? 0 : totalValue);
    }

    public Map<String, Object> isMark(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        long cid = RequestValues.integer(request, "cid", 0);
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM starfree_userlog WHERE uid = ? AND cid = ? AND type = 'mark' ORDER BY id DESC LIMIT 1", uid, cid);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isMark", rows.isEmpty() ? 0 : 1);
        result.put("logid", rows.isEmpty() ? -1 : rows.get(0).get("id"));
        return result;
    }

    // Do not add a Spring transaction here. The balance-changing branches use an
    // independent connection, global advisory lock, and durable operation journal.
    // Holding an outer connection while waiting for that lock can exhaust the pool.
    public Map<String, Object> add(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        Map<String, Object> params = RequestValues.jsonObject(mapper, RequestValues.text(request, "params"));
        long cid = RequestValues.objectInteger(params, "cid", 0);
        String type = RequestValues.objectText(params, "type");
        int num = RequestValues.objectInteger(params, "num", 0);
        if (!"mark".equals(type) && !"reward".equals(type)
                && !"likes".equals(type) && !"clock".equals(type)) {
            throw new IllegalArgumentException("错误的字段类型");
        }
        if (!"clock".equals(type) && cid <= 0) {
            throw new IllegalArgumentException("互动参数不完整");
        }
        if ("clock".equals(type)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clockData", economy.clock(uid, RequestValues.text(request, "requestId")));
            return result;
        }
        if ("reward".equals(type)) {
            String requestId = RequestValues.text(request, "requestId");
            if (requestId.isEmpty()) {
                requestId = RequestValues.objectText(params, "requestId");
            }
            return economy.reward(uid, cid, num, requestId);
        }
        if ("mark".equals(type) || "likes".equals(type)) {
            List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM starfree_userlog WHERE uid = ? AND cid = ? AND type = ? LIMIT 1", uid, cid, type);
            if (!existing.isEmpty()) {
                if ("mark".equals(type)) {
                    throw new IllegalArgumentException("已在你的收藏中！");
                }
                throw new IllegalArgumentException("距离上次操作不到24小时！");
            }
        }
        jdbc.update("INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) VALUES (?,?,?,?,?,?)",
                uid, cid, type, num, Instant.now().getEpochSecond(), RequestValues.objectInteger(params, "toid", 0));
        if ("likes".equals(type)) {
            jdbc.update("UPDATE starfree_contents SET likes = COALESCE(likes, 0) + 1 WHERE cid = ?", cid);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cid", cid);
        result.put("type", type);
        return result;
    }

    @Transactional
    public void remove(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        long id = RequestValues.integer(request, "key", 0);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT uid,cid,type FROM starfree_userlog WHERE id = ? LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("互动记录不存在");
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> user = tokens.userById(uid);
        String group = user == null ? "" : String.valueOf(user.get("group"));
        boolean administrator = "administrator".equals(group);
        long owner = ((Number) row.get("uid")).longValue();
        if (!administrator && owner != uid) {
            throw new IllegalArgumentException("你无权进行此操作");
        }
        if (!administrator && !"mark".equals(row.get("type"))) {
            throw new IllegalArgumentException("该类型数据不允许删除");
        }
        jdbc.update("DELETE FROM starfree_userlog WHERE id = ?", id);
        if ("likes".equals(row.get("type"))) {
            jdbc.update("UPDATE starfree_contents SET likes = GREATEST(COALESCE(likes, 0) - 1, 0) WHERE cid = ?", row.get("cid"));
        }
    }

    public static final class MarkPage {
        private final List<Map<String, Object>> data;
        private final int total;

        public MarkPage(List<Map<String, Object>> data, int total) {
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
