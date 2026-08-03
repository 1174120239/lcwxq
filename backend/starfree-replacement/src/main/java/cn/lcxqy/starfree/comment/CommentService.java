package cn.lcxqy.starfree.comment;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.economy.EconomyConfig;
import cn.lcxqy.starfree.economy.EconomyService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LegacyTokenService tokens;
    private final EconomyService economy;

    public CommentService(JdbcTemplate jdbc, ObjectMapper mapper, LegacyTokenService tokens,
                          EconomyService economy) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tokens = tokens;
        this.economy = economy;
    }

    public List<Map<String, Object>> list(String searchParams, int limit, int page) {
        return page(searchParams, limit, page, "", "created", "").getData();
    }

    public CommentPage page(String searchParams, int limit, int page, String searchKey, String order,
                            String token) {
        Map<String, Object> filters = RequestValues.jsonObject(mapper, searchParams);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        int safePage = Math.max(1, page);
        List<Object> args = new ArrayList<>();
        StringBuilder from = new StringBuilder(
                " FROM starfree_comments co LEFT JOIN starfree_users u ON u.uid = co.authorId "
                        + "LEFT JOIN starfree_contents c ON c.cid = co.cid WHERE 1=1");
        String type = RequestValues.objectText(filters, "type");
        if (!type.isEmpty()) {
            from.append(" AND co.type = ?");
            args.add(type);
        }
        String status = RequestValues.objectText(filters, "status");
        if (isStaff(token) && !status.isEmpty()) {
            from.append(" AND co.status = ?");
            args.add(status);
        } else {
            from.append(" AND co.status = 'approved'");
        }
        appendIntegerFilter(from, args, filters, "cid", "co.cid");
        appendIntegerFilter(from, args, filters, "coid", "co.coid");
        appendIntegerFilter(from, args, filters, "authorId", "co.authorId");
        appendIntegerFilter(from, args, filters, "ownerId", "co.ownerId");
        appendIntegerFilter(from, args, filters, "parent", "co.parent");
        String keyword = searchKey == null ? "" : searchKey.trim();
        if (!keyword.isEmpty()) {
            from.append(" AND (co.text LIKE ? OR co.author LIKE ? OR c.title LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }

        Integer totalValue = jdbc.queryForObject("SELECT COUNT(*)" + from, Integer.class, args.toArray());
        String sql = "SELECT co.*, u.uid AS user_uid, u.name AS user_name, u.screenName AS user_screenName, "
                + "u.mail AS user_mail, u.avatar AS user_avatar, u.`group` AS user_group, "
                + "u.customize AS user_customize, u.vip AS user_vip, u.experience AS user_experience, "
                + "u.ip AS user_ip, u.local AS user_local, c.title AS content_title, c.slug AS content_slug, "
                + "c.type AS content_type" + from + " ORDER BY co." + safeOrder(order) + " DESC LIMIT ?, ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((safePage - 1) * safeLimit);
        pageArgs.add(safeLimit);
        Long viewerId = token == null || token.isEmpty() ? null : tokens.userId(token);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, pageArgs.toArray());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(toComment(row, viewerId));
        }
        return new CommentPage(result, totalValue == null ? 0 : totalValue);
    }

    private void appendIntegerFilter(StringBuilder sql, List<Object> args, Map<String, Object> filters,
                                     String key, String column) {
        if (filters.containsKey(key)) {
            sql.append(" AND ").append(column).append(" = ?");
            args.add(RequestValues.objectInteger(filters, key, 0));
        }
    }

    private String safeOrder(String order) {
        if ("likes".equals(order) || "coid".equals(order)) {
            return order;
        }
        return "created";
    }

    private boolean isStaff(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        Long uid = tokens.userId(token);
        Map<String, Object> user = uid == null ? null : tokens.userById(uid);
        String group = user == null ? "" : String.valueOf(user.get("group"));
        return economy.isStaff(group);
    }
    @Transactional
    public Map<String, Object> add(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        Map<String, Object> params = RequestValues.jsonObject(mapper, RequestValues.text(request, "params"));
        long cid = RequestValues.objectInteger(params, "cid", 0);
        int parent = RequestValues.objectInteger(params, "parent", 0);
        String text = RequestValues.text(request, "text");
        String pic = RequestValues.text(request, "pic");
        if (text.isEmpty() && !pic.isEmpty()) {
            text = "[图片]";
        }
        if (cid <= 0 || text.isEmpty()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        List<Map<String, Object>> contentRows = jdbc.queryForList(
                "SELECT authorId FROM starfree_contents WHERE cid = ? LIMIT 1", cid);
        if (contentRows.isEmpty()) {
            throw new IllegalArgumentException("内容不存在");
        }
        long ownerId = ((Number) contentRows.get(0).get("authorId")).longValue();
        Map<String, Object> user = tokens.userById(uid);
        String author = user.get("screenName") == null || String.valueOf(user.get("screenName")).isEmpty()
                ? String.valueOf(user.get("name")) : String.valueOf(user.get("screenName"));
        String status = economy.commentStatus(uid, text);
        long now = Instant.now().getEpochSecond();
        jdbc.update("INSERT INTO starfree_comments (cid,created,author,authorId,ownerId,mail,url,ip,agent,text,type,status,parent,likes,pic) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                cid, now, author, uid, ownerId, "", "", "", "", text, "comment", status, parent, 0, pic);
        jdbc.update("UPDATE starfree_contents SET commentsNum = COALESCE(commentsNum, 0) + 1, replyTime = ? WHERE cid = ?",
                now, cid);
        if ("approved".equals(status)) {
            economy.grantCommentExperience(uid);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cid", cid);
        data.put("parent", parent);
        data.put("status", status);
        return data;
    }

    @Transactional
    public void delete(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        long coid = RequestValues.integer(request, "key", RequestValues.integer(request, "coid", 0));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT cid,authorId,text FROM starfree_comments WHERE coid = ?", coid);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("评论不存在");
        }
        Map<String, Object> comment = rows.get(0);
        Map<String, Object> user = tokens.userById(uid);
        String group = user == null ? "" : String.valueOf(user.get("group"));
        long authorId = ((Number) comment.get("authorId")).longValue();
        if (!economy.isStaff(group)) {
            EconomyConfig config = economy.config();
            if (!config.isUserDeleteAllowed()) {
                throw new IllegalArgumentException("系统禁止删除评论");
            }
            if (authorId != uid) {
                throw new IllegalArgumentException("你无权进行此操作");
            }
        } else if (authorId != uid) {
            economy.sendDeletionNotice(uid, authorId, "你的评论【" + comment.get("text") + "】已被删除");
        }
        economy.deductDeleteExperience(authorId);
        jdbc.update("DELETE FROM starfree_comments WHERE coid = ?", coid);
        jdbc.update("UPDATE starfree_contents SET commentsNum = "
                        + "(SELECT COUNT(*) FROM starfree_comments WHERE cid = ?) WHERE cid = ?",
                comment.get("cid"), comment.get("cid"));
    }

    @Transactional
    public Map<String, Object> audit(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        Map<String, Object> operator = tokens.userById(uid);
        String group = operator == null ? "" : String.valueOf(operator.get("group"));
        if (!economy.isStaff(group)) {
            throw new IllegalArgumentException("你没有操作权限");
        }
        long coid = RequestValues.integer(request, "key", 0);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT coid,cid,authorId,ownerId,text,status,parent FROM starfree_comments WHERE coid = ? LIMIT 1",
                coid);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("评论不存在");
        }
        Map<String, Object> comment = rows.get(0);
        if ("approved".equals(comment.get("status"))) {
            throw new IllegalArgumentException("该评论已被通过");
        }
        long cid = ((Number) comment.get("cid")).longValue();
        long authorId = ((Number) comment.get("authorId")).longValue();
        long ownerId = number(comment.get("ownerId"));
        long parent = number(comment.get("parent"));
        int type = RequestValues.integer(request, "type", 0);
        String status;
        if (type == 0) {
            jdbc.update("UPDATE starfree_comments SET status = 'approved' WHERE coid = ?", coid);
            status = "approved";
            if (authorId > 0) {
                economy.grantCommentExperience(authorId);
            }
            if (parent > 0) {
                List<Map<String, Object>> parentRows = jdbc.queryForList(
                        "SELECT authorId,cid FROM starfree_comments WHERE coid = ? LIMIT 1", parent);
                if (!parentRows.isEmpty()) {
                    long targetId = number(parentRows.get(0).get("authorId"));
                    if (targetId > 0 && targetId != authorId) {
                        economy.sendInbox("comment", authorId, targetId, String.valueOf(comment.get("text")),
                                cid, coid);
                    }
                }
            } else if (ownerId > 0 && ownerId != authorId) {
                economy.sendInbox("comment", authorId, ownerId, String.valueOf(comment.get("text")), cid, coid);
            }
        } else {
            jdbc.update("DELETE FROM starfree_comments WHERE coid = ?", coid);
            jdbc.update("UPDATE starfree_contents SET commentsNum = "
                            + "(SELECT COUNT(*) FROM starfree_comments WHERE cid = ?) WHERE cid = ?",
                    cid, cid);
            status = "deleted";
            if (authorId > 0) {
                economy.sendDeletionNotice(uid, authorId,
                        "你的评论【" + comment.get("text") + "】未审核通过，已被删除！");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coid", coid);
        result.put("status", status);
        return result;
    }

    private Map<String, Object> toComment(Map<String, Object> row, Long viewerId) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("user_") && !key.startsWith("content_") && entry.getValue() != null) {
                result.put(key, entry.getValue());
            }
        }
        long cid = number(row.get("cid"));
        long coid = number(row.get("coid"));
        result.put("subNum", commentCount(cid, coid));
        result.put("parentComments", parentComment(number(row.get("parent"))));
        enrichAuthor(result, row);
        enrichContent(result, row, cid);
        result.put("isLike", isLiked(viewerId, coid) ? 1 : 0);
        return result;
    }

    private void enrichAuthor(Map<String, Object> result, Map<String, Object> row) {
        if (number(row.get("authorId")) == 0) {
            result.put("customize", "");
            result.put("avatar", avatar(null, row.get("mail")));
            return;
        }
        if (row.get("user_uid") == null) {
            return;
        }
        String name = value(row.get("user_screenName"));
        if (name.isEmpty()) {
            name = value(row.get("user_name"));
        }
        result.put("author", name);
        result.put("avatar", avatar(row.get("user_avatar"), row.get("user_mail")));
        putNonNull(result, "mail", row.get("user_mail"));
        putNonNull(result, "customize", row.get("user_customize"));
        result.put("experience", number(row.get("user_experience")));
        result.put("ip", value(row.get("user_ip")));
        result.put("local", value(row.get("user_local")));
        long vip = number(row.get("user_vip"));
        result.put("isvip", vipStatus(vip));
        result.put("vip", vip);
    }

    private void enrichContent(Map<String, Object> result, Map<String, Object> row, long cid) {
        if (row.get("content_title") == null) {
            result.put("contenTitle", "文章已删除");
            return;
        }
        result.put("contenTitle", row.get("content_title"));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("cid", cid);
        putNonNull(content, "slug", row.get("content_slug"));
        content.put("title", row.get("content_title"));
        putNonNull(content, "type", row.get("content_type"));
        List<Map<String, Object>> metas = jdbc.queryForList(
                "SELECT m.mid,m.name,m.slug,m.type,m.description,m.count,m.`order`,m.parent,m.imgurl,m.isrecommend "
                        + "FROM starfree_relationships r JOIN starfree_metas m ON m.mid = r.mid "
                        + "WHERE r.cid = ? ORDER BY m.`order`,m.mid LIMIT 1", cid);
        if (!metas.isEmpty()) {
            List<Map<String, Object>> category = new ArrayList<>();
            category.add(withLegacyOrder(metas.get(0)));
            content.put("category", category);
        }
        result.put("contentsInfo", content);
    }

    private int commentCount(long cid, long parent) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_comments WHERE cid = ? AND parent = ?", Integer.class, cid, parent);
        return count == null ? 0 : count;
    }

    private Map<String, Object> parentComment(long parent) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (parent <= 0) {
            return result;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT author,text,pic,created,status FROM starfree_comments WHERE coid = ? LIMIT 1", parent);
        if (rows.isEmpty() || !"approved".equals(rows.get(0).get("status"))) {
            result.put("text", "该评论已被删除");
            return result;
        }
        Map<String, Object> row = rows.get(0);
        putNonNull(result, "author", row.get("author"));
        putNonNull(result, "text", row.get("text"));
        putNonNull(result, "pic", row.get("pic"));
        if (row.get("created") != null) {
            result.put("created", String.valueOf(row.get("created")));
        }
        return result;
    }

    private boolean isLiked(Long uid, long coid) {
        if (uid == null) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_userlog WHERE uid = ? AND cid = ? AND type = 'commentLike'",
                Integer.class, uid, coid);
        return count != null && count > 0;
    }

    private Map<String, Object> withLegacyOrder(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getValue() != null) {
                result.put("order".equals(entry.getKey()) ? "orderKey" : entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private String avatar(Object avatar, Object mail) {
        String configured = value(avatar);
        if (!configured.isEmpty()) {
            return configured;
        }
        String email = value(mail).trim().toLowerCase();
        if (email.endsWith("@qq.com")) {
            return "https://q1.qlogo.cn/g?b=qq&nk=" + email.substring(0, email.length() - 7) + "&s=640";
        }
        return defaultAvatarPrefix() + (email.isEmpty() ? "null" : md5(email));
    }

    private String defaultAvatarPrefix() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT webinfoAvatar FROM starfree_apiconfig ORDER BY id LIMIT 1");
        if (rows.isEmpty() || rows.get(0).get("webinfoAvatar") == null) {
            return "https://cravatar.cn/wp-content/themes/cravatar/assets/img/img1.png#";
        }
        return String.valueOf(rows.get(0).get("webinfoAvatar"));
    }

    private int vipStatus(long vip) {
        if (vip == 1) {
            return 2;
        }
        return vip > Instant.now().getEpochSecond() ? 1 : 0;
    }

    private String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 is unavailable", ex);
        }
    }

    private long number(Object input) {
        return input instanceof Number ? ((Number) input).longValue() : 0;
    }

    private String value(Object input) {
        return input == null ? "" : String.valueOf(input);
    }

    private void putNonNull(Map<String, Object> target, String key, Object input) {
        if (input != null) {
            target.put(key, input);
        }
    }

    public static final class CommentPage {
        private final List<Map<String, Object>> data;
        private final int total;

        CommentPage(List<Map<String, Object>> data, int total) {
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