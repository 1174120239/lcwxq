package cn.lcxqy.starfree.content;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.economy.EconomyConfig;
import cn.lcxqy.starfree.economy.EconomyService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContentService {
    private static final Logger LOG = LoggerFactory.getLogger(ContentService.class);
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\((https?://[^)\\s]+)(?:\\s+[^)]*)?\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_IMAGE = Pattern.compile("<img[^>]+src=[\\\"']([^\\\"']+)[\\\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SENSITIVE_CODE = Pattern.compile(
            "<\\s*(script|form|iframe|frame)\\b", Pattern.CASE_INSENSITIVE);
    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder().build();
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LegacyTokenService tokens;
    private final EconomyService economy;
    private final LegacyContentAbuseGuard abuse;
    private final LegacyContentCacheInvalidator cache;

    @Autowired
    public ContentService(JdbcTemplate jdbc, ObjectMapper mapper, LegacyTokenService tokens,
                          EconomyService economy, LegacyContentAbuseGuard abuse,
                          LegacyContentCacheInvalidator cache) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tokens = tokens;
        this.economy = economy;
        this.abuse = abuse;
        this.cache = cache;
    }

    public ContentService(JdbcTemplate jdbc, ObjectMapper mapper, LegacyTokenService tokens,
                          EconomyService economy) {
        this(jdbc, mapper, tokens, economy, LegacyContentAbuseGuard.disabled(),
                LegacyContentCacheInvalidator.disabled());
    }

    public List<Map<String, Object>> list(String searchParams, int limit, int page, String order) {
        return page(searchParams, limit, page, order, "", 0, "").getData();
    }

    public ContentPage page(String searchParams, int limit, int page, String order, String searchKey,
                            int random, String token) {
        Map<String, Object> filters = RequestValues.jsonObject(mapper, searchParams);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        int safePage = Math.max(1, page);
        List<Object> args = new ArrayList<>();
        StringBuilder from = new StringBuilder(
                " FROM starfree_contents c LEFT JOIN starfree_users u ON u.uid = c.authorId WHERE 1=1");

        String type = RequestValues.objectText(filters, "type");
        if (!type.isEmpty()) {
            from.append(" AND c.type = ?");
            args.add(type);
        }
        String status = RequestValues.objectText(filters, "status");
        if (isStaff(token) && !status.isEmpty()) {
            from.append(" AND c.status = ?");
            args.add(status);
        } else {
            from.append(" AND c.status = 'publish'");
        }
        appendIntegerFilter(from, args, filters, "istop", "c.istop");
        appendIntegerFilter(from, args, filters, "isrecommend", "c.isrecommend");
        appendIntegerFilter(from, args, filters, "isswiper", "c.isswiper");
        if (filters.containsKey("authorId")) {
            from.append(" AND c.authorId = ?");
            args.add(RequestValues.objectInteger(filters, "authorId", 0));
        }
        String keyword = RequestValues.objectText(filters, "keyword");
        if (keyword.isEmpty()) {
            keyword = searchKey == null ? "" : searchKey.trim();
        }
        if (!keyword.isEmpty()) {
            from.append(" AND (c.title LIKE ? OR c.text LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (filters.containsKey("mid")) {
            from.append(" AND EXISTS (SELECT 1 FROM starfree_relationships r WHERE r.cid = c.cid AND r.mid = ?)");
            args.add(RequestValues.objectInteger(filters, "mid", 0));
        }

        Integer totalValue = jdbc.queryForObject("SELECT COUNT(*)" + from, Integer.class, args.toArray());
        StringBuilder sql = new StringBuilder(
                "SELECT c.*, u.uid AS author_uid, u.name AS author_name, u.screenName AS author_screenName, "
                        + "u.mail AS author_mail, u.avatar AS author_avatar, "
                        + "u.customize AS author_customize, u.vip AS author_vip, u.experience AS author_experience").append(from);
        if (random == 1) {
            sql.append(" ORDER BY RAND()");
        } else {
            sql.append(" ORDER BY c.").append(safeOrder(order)).append(" DESC");
        }
        sql.append(" LIMIT ?, ?");
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((safePage - 1) * safeLimit);
        pageArgs.add(safeLimit);

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), pageArgs.toArray());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(toContent(row));
        }
        return new ContentPage(result, totalValue == null ? 0 : totalValue);
    }
    public Map<String, Object> info(long cid, boolean countView) {
        if (countView) {
            jdbc.update("UPDATE starfree_contents SET views = COALESCE(views, 0) + 1 WHERE cid = ?", cid);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.*, u.uid AS author_uid, u.name AS author_name, u.screenName AS author_screenName, "
                        + "u.avatar AS author_avatar, u.experience AS author_experience "
                        + "FROM starfree_contents c LEFT JOIN starfree_users u ON u.uid = c.authorId WHERE c.cid = ? LIMIT 1",
                cid);
        return rows.isEmpty() ? null : toContent(rows.get(0));
    }

    /** Builds the full legacy contentsInfo payload instead of the contentsList preview. */
    public Map<String, Object> detail(long cid, int isMd, String token) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.* FROM starfree_contents c WHERE c.cid = ? LIMIT 1", cid);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        if (!"publish".equals(value(row.get("status"))) && !isStaff(token)) {
            throw new IllegalArgumentException("\u6587\u7ae0\u6682\u672a\u516c\u5f00\u8bbf\u95ee");
        }
        return toContentDetail(row, isMd == 1);
    }

    public void incrementViews(long cid) {
        jdbc.update("UPDATE starfree_contents SET views = COALESCE(views, 0) + 1 WHERE cid = ?", cid);
    }

    public Long requireUser(String token) {
        return tokens.userId(token);
    }

    @Transactional
    public Map<String, Object> add(Map<String, String> request) {
        return add(request, "");
    }

    @Transactional
    public Map<String, Object> add(Map<String, String> request, String clientIp) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        Map<String, Object> params = RequestValues.jsonObject(mapper, RequestValues.text(request, "params"));
        String title = RequestValues.objectText(params, "title");
        String text = RequestValues.text(request, "text");
        if (text.isEmpty()) {
            text = RequestValues.objectText(params, "text");
        }
        if (title.isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("文章内容不能为空");
        }
        if (text.length() > 60000) {
            throw new IllegalArgumentException("超出最大文章内容长度");
        }
        if (RequestValues.integer(request, "isDraft", 0) == 1) {
            throw new IllegalArgumentException("草稿发布暂由旧后端处理");
        }
        if (RequestValues.integer(request, "isPaid", 0) == 1) {
            throw new IllegalArgumentException("付费内容暂由旧后端处理");
        }
        if (RequestValues.integer(request, "isSpace", 0) == 1) {
            throw new IllegalArgumentException("文章关联动态暂由旧后端处理");
        }
        if (RequestValues.objectInteger(params, "sid", -1) >= 0) {
            throw new IllegalArgumentException("商品挂载暂由旧后端处理");
        }
        String type = RequestValues.objectText(params, "type");
        if (type.isEmpty()) {
            type = "post";
        }
        if (!"post".equals(type) && !"video".equals(type)) {
            throw new IllegalArgumentException("内容类型不正确");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("标题长度不能超过200个字符");
        }
        int isMd = RequestValues.integer(request, "isMd", 1);
        if (isMd == 1) {
            if (!text.contains("<!--markdown-->")) {
                text = "<!--markdown-->" + text;
            }
            text = text.replace("||rn||", "\n");
        }

        Map<String, Object> user = tokens.userById(uid);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String group = String.valueOf(user.get("group"));
        EconomyConfig config = economy.config();
        abuse.checkBurst(uid, config.isRobotProtectionEnabled(), config.getSilenceTime());
        if (config.isCodeDisabled() && SENSITIVE_CODE.matcher(text).find()) {
            throw new IllegalArgumentException("你的内容包含敏感代码，请修改后重试！");
        }
        String status = economy.contentStatus(config, group, title, text);
        boolean staff = economy.isStaff(group);
        LegacyContentAbuseGuard.Reservation reservation = abuse.reservePost(
                uid, staff, config.getPostMax(), staff ? 0 : economy.postsInLastDay(uid));

        long now = Instant.now().getEpochSecond();
        final String contentText = text;
        final String contentType = type;
        final String contentStatus = status;
        long cid = 0;
        try {
            String sql = "INSERT INTO starfree_contents (title,slug,created,modified,text,`order`,authorId,template,type,status,password,commentsNum,allowComment,allowPing,allowFeed,parent,views,likes,isrecommend,istop,isswiper,replyTime) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                statement.setString(i++, title);
                statement.setNull(i++, Types.VARCHAR);
                statement.setLong(i++, now);
                statement.setLong(i++, now);
                statement.setString(i++, contentText);
                statement.setInt(i++, 0);
                statement.setLong(i++, uid);
                statement.setNull(i++, Types.VARCHAR);
                statement.setString(i++, contentType);
                statement.setString(i++, contentStatus);
                statement.setNull(i++, Types.VARCHAR);
                statement.setInt(i++, 0);
                statement.setString(i++, "1");
                statement.setString(i++, "1");
                statement.setString(i++, "1");
                statement.setInt(i++, 0);
                statement.setInt(i++, 0);
                statement.setInt(i++, 0);
                statement.setInt(i++, 0);
                statement.setInt(i++, 0);
                statement.setInt(i++, 0);
                statement.setLong(i, now);
                return statement;
            }, keyHolder);
            if (keyHolder.getKey() == null) {
                throw new IllegalStateException("Article insert did not return a cid");
            }
            cid = keyHolder.getKey().longValue();
            if (jdbc.update("UPDATE starfree_contents SET slug = ? WHERE cid = ?", String.valueOf(cid), cid) != 1) {
                throw new IllegalStateException("Article slug update failed");
            }
            insertAddRelationships(cid, RequestValues.objectText(params, "category"));
            insertAddRelationships(cid, RequestValues.objectText(params, "tag"));
        } catch (RuntimeException failure) {
            if (cid > 0) {
                cleanupFailedAdd(cid);
            }
            reservation.cancel();
            throw failure;
        }

        // The article row is authoritative. Secondary activity/experience failures must not
        // make the client retry and create a duplicate MyISAM article.
        try {
            jdbc.update("UPDATE starfree_users SET ip = ?, posttime = ? WHERE uid = ?",
                    clientIp == null ? "" : clientIp, now, uid);
        } catch (RuntimeException secondaryFailure) {
            LOG.error("Article {} was published but user activity update failed for uid {}", cid, uid,
                    secondaryFailure);
        }
        if ("publish".equals(status)) {
            try {
                economy.grantPostExperience(uid);
            } catch (RuntimeException secondaryFailure) {
                LOG.error("Article {} was published but post experience failed for uid {}", cid, uid,
                        secondaryFailure);
            }
        }
        cache.afterContentWrite(cid);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cid", cid);
        result.put("status", status);
        return result;
    }

    @Transactional
    public Map<String, Object> update(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        Map<String, Object> params = RequestValues.jsonObject(mapper, RequestValues.text(request, "params"));
        long cid = RequestValues.objectInteger(params, "cid", RequestValues.integer(request, "cid", 0));
        Map<String, Object> content = info(cid, false);
        if (content == null) {
            throw new IllegalArgumentException("内容不存在");
        }
        if (!canManage(uid, content)) {
            throw new IllegalArgumentException("没有修改权限");
        }
        String title = RequestValues.objectText(params, "title");
        String text = RequestValues.text(request, "text");
        if (text.isEmpty()) {
            text = RequestValues.objectText(params, "text");
        }
        Map<String, Object> user = tokens.userById(uid);
        String group = user == null ? "" : String.valueOf(user.get("group"));
        String status = economy.contentStatus(group, title, text);
        jdbc.update("UPDATE starfree_contents SET title = ?, text = ?, status = ?, modified = ? WHERE cid = ?",
                title, text, status, Instant.now().getEpochSecond(), cid);
        replaceRelationships(cid, params);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cid", cid);
        result.put("status", status);
        return result;
    }

    public boolean isOrdinaryUpdate(Map<String, String> request) {
        Map<String, Object> params = RequestValues.jsonObject(mapper, RequestValues.text(request, "params"));
        long cid = RequestValues.objectInteger(params, "cid", 0);
        if (cid <= 0) {
            return true;
        }
        List<String> types = jdbc.queryForList(
                "SELECT type FROM starfree_contents WHERE cid = ? LIMIT 1", String.class, cid);
        return types.isEmpty() || "post".equals(types.get(0)) || "video".equals(types.get(0));
    }

    @Transactional
    public Map<String, Object> updateOrdinary(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        Map<String, Object> params = RequestValues.jsonObject(mapper, RequestValues.text(request, "params"));
        long cid = RequestValues.objectInteger(params, "cid", RequestValues.integer(request, "cid", 0));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT cid,title,text,status,modified,type,authorId FROM starfree_contents WHERE cid = ? LIMIT 1",
                cid);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("内容不存在");
        }
        Map<String, Object> content = rows.get(0);
        if (!canManage(uid, content)) {
            throw new IllegalArgumentException("没有修改权限");
        }
        String existingType = value(content.get("type"));
        if (!"post".equals(existingType) && !"video".equals(existingType)) {
            throw new IllegalArgumentException("该内容类型暂由旧后端处理");
        }

        String title = RequestValues.objectText(params, "title");
        String text = RequestValues.text(request, "text");
        if (text.isEmpty()) {
            text = RequestValues.objectText(params, "text");
        }
        if (title.isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("标题长度不能超过200个字符");
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("文章内容不能为空");
        }
        if (text.length() > 60000) {
            throw new IllegalArgumentException("超出最大文章内容长度");
        }

        Map<String, Object> user = tokens.userById(uid);
        String group = user == null ? "" : String.valueOf(user.get("group"));
        EconomyConfig config = economy.config();
        if (config.isCodeDisabled() && SENSITIVE_CODE.matcher(text).find()) {
            throw new IllegalArgumentException("你的内容包含敏感代码，请修改后重试！");
        }

        boolean markdown = request.containsKey("isMd")
                ? RequestValues.integer(request, "isMd", 0) == 1
                : value(content.get("text")).contains("<!--markdown-->");
        text = text.replace("||rn||", "\n");
        if (markdown && !text.contains("<!--markdown-->")) {
            text = "<!--markdown-->" + text;
        }
        String status = economy.contentStatus(config, group, title, text);
        Set<Integer> newMids = relationshipIds(params);
        validateRelationshipIds(newMids);
        List<Integer> oldMids = jdbc.queryForList(
                "SELECT mid FROM starfree_relationships WHERE cid = ? ORDER BY mid", Integer.class, cid);
        long modified = Instant.now().getEpochSecond();
        try {
            int changed = jdbc.update(
                    "UPDATE starfree_contents SET title = ?, text = ?, status = ?, modified = ? WHERE cid = ?",
                    title, text, status, modified, cid);
            if (changed != 1) {
                throw new IllegalStateException("Article update did not affect exactly one row");
            }
            replaceRelationshipsCompensated(cid, newMids, oldMids);
        } catch (RuntimeException failure) {
            compensateFailedUpdate(content, oldMids, newMids, failure);
            throw failure;
        }
        cache.afterContentWrite(cid);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cid", cid);
        result.put("status", status);
        return result;
    }

    @Transactional
    public void delete(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        long cid = RequestValues.integer(request, "key", RequestValues.integer(request, "cid", 0));
        Map<String, Object> content = info(cid, false);
        if (content == null) {
            throw new IllegalArgumentException("内容不存在");
        }
        Map<String, Object> user = tokens.userById(uid);
        String group = user == null ? "" : String.valueOf(user.get("group"));
        long authorId = ((Number) content.get("authorId")).longValue();
        if (!economy.isStaff(group)) {
            EconomyConfig config = economy.config();
            if (!config.isUserDeleteAllowed()) {
                throw new IllegalArgumentException("系统禁止删除文章");
            }
            if (uid != authorId) {
                throw new IllegalArgumentException("你无权进行此操作");
            }
        }
        if (!canManage(uid, content)) {
            throw new IllegalArgumentException("没有删除权限");
        }
        List<Integer> oldMids = jdbc.queryForList(
                "SELECT mid FROM starfree_relationships WHERE cid = ? ORDER BY mid", Integer.class, cid);
        economy.sendDeletionNotice(uid, authorId, "你的文章【" + content.get("title") + "】已被删除");
        jdbc.update("DELETE FROM starfree_comments WHERE cid = ?", cid);
        jdbc.update("DELETE FROM starfree_fields WHERE cid = ?", cid);
        jdbc.update("DELETE FROM starfree_relationships WHERE cid = ?", cid);
        int deleted = jdbc.update("DELETE FROM starfree_contents WHERE cid = ?", cid);
        if (deleted != 1) {
            throw new IllegalStateException("Content delete did not affect exactly one row");
        }
        // The article row is authoritative. Evict stale old-API projections before secondary
        // counters or experience updates, so a later projection failure cannot resurrect it.
        cache.afterContentWrite(cid);
        try {
            refreshMetaCounts(oldMids, Collections.<Integer>emptySet());
        } catch (RuntimeException countFailure) {
            LOG.error("Content {} was deleted but category counts could not be refreshed", cid,
                    countFailure);
        }
        economy.deductDeleteExperience(authorId);
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
        long cid = RequestValues.integer(request, "key", 0);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT cid,title,status,authorId FROM starfree_contents WHERE cid = ? LIMIT 1", cid);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("内容不存在");
        }
        Map<String, Object> content = rows.get(0);
        if ("publish".equals(content.get("status"))) {
            throw new IllegalArgumentException("该文章已审核通过");
        }
        long authorId = ((Number) content.get("authorId")).longValue();
        if (tokens.userById(authorId) == null) {
            throw new IllegalArgumentException("文章作者已注销");
        }
        int type = RequestValues.integer(request, "type", 0);
        String status;
        String notice;
        if (type == 0) {
            status = "publish";
            notice = "你的文章【" + content.get("title") + "】已审核通过";
        } else {
            String reason = RequestValues.text(request, "reason");
            if (reason.isEmpty()) {
                throw new IllegalArgumentException("请输入拒绝理由");
            }
            status = "reject";
            notice = "你的文章【" + content.get("title") + "】未审核通过。理由如下：" + reason;
        }
        int changed = jdbc.update("UPDATE starfree_contents SET status = ?, modified = ? WHERE cid = ?",
                status, Instant.now().getEpochSecond(), cid);
        if (changed != 1) {
            throw new IllegalStateException("Content audit did not affect exactly one row");
        }
        cache.afterContentWrite(cid);
        economy.sendDeletionNotice(uid, authorId, notice);
        if (type == 0) {
            economy.grantPostExperience(authorId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cid", cid);
        result.put("status", status);
        return result;
    }

    private boolean canManage(long uid, Map<String, Object> content) {
        Object authorId = content.get("authorId");
        if (authorId != null && uid == ((Number) authorId).longValue()) {
            return true;
        }
        Map<String, Object> user = tokens.userById(uid);
        String group = user == null ? "" : String.valueOf(user.get("group"));
        return "administrator".equals(group) || "editor".equals(group);
    }

    private void replaceRelationships(long cid, Map<String, Object> params) {
        jdbc.update("DELETE FROM starfree_relationships WHERE cid = ?", cid);
        insertRelationshipValues(cid, RequestValues.objectText(params, "category"));
        insertRelationshipValues(cid, RequestValues.objectText(params, "tag"));
    }

    private Set<Integer> relationshipIds(Map<String, Object> params) {
        Set<Integer> result = new LinkedHashSet<>();
        addRelationshipIds(result, RequestValues.objectText(params, "category"));
        addRelationshipIds(result, RequestValues.objectText(params, "tag"));
        return result;
    }

    private void addRelationshipIds(Set<Integer> target, String values) {
        if (values == null || values.trim().isEmpty()) {
            return;
        }
        for (String part : values.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                int mid = Integer.parseInt(value);
                if (mid > 0) {
                    target.add(mid);
                }
            } catch (NumberFormatException invalidMeta) {
                throw new IllegalArgumentException("分类或标签参数不正确");
            }
        }
    }

    private void validateRelationshipIds(Set<Integer> mids) {
        for (Integer mid : mids) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM starfree_metas WHERE mid = ?", Integer.class, mid);
            if (count == null || count != 1) {
                // MyISAM does not enforce a foreign key here, so validate before changing
                // either the article or its relationship rows.
                throw new IllegalArgumentException("分类或标签不存在");
            }
        }
    }

    private void replaceRelationshipsCompensated(long cid, Set<Integer> newMids,
                                                  List<Integer> oldMids) {
        jdbc.update("DELETE FROM starfree_relationships WHERE cid = ?", cid);
        for (Integer mid : newMids) {
            jdbc.update("INSERT INTO starfree_relationships (cid, mid) VALUES (?, ?)", cid, mid);
        }
        refreshMetaCounts(oldMids, newMids);
    }

    private void compensateFailedUpdate(Map<String, Object> oldContent, List<Integer> oldMids,
                                        Set<Integer> attemptedMids, RuntimeException failure) {
        long cid = ((Number) oldContent.get("cid")).longValue();
        try {
            jdbc.update("UPDATE starfree_contents SET title = ?, text = ?, status = ?, modified = ?, type = ? WHERE cid = ?",
                    oldContent.get("title"), oldContent.get("text"), oldContent.get("status"),
                    oldContent.get("modified"), oldContent.get("type"), cid);
            jdbc.update("DELETE FROM starfree_relationships WHERE cid = ?", cid);
            for (Integer mid : oldMids) {
                jdbc.update("INSERT INTO starfree_relationships (cid, mid) VALUES (?, ?)", cid, mid);
            }
            refreshMetaCounts(oldMids, attemptedMids);
        } catch (RuntimeException compensationFailure) {
            failure.addSuppressed(compensationFailure);
            LOG.error("Could not fully compensate failed article update {}", cid, compensationFailure);
        }
    }

    private void refreshMetaCounts(Iterable<Integer> first, Iterable<Integer> second) {
        Set<Integer> mids = new LinkedHashSet<>();
        for (Integer mid : first) {
            if (mid != null && mid > 0) {
                mids.add(mid);
            }
        }
        for (Integer mid : second) {
            if (mid != null && mid > 0) {
                mids.add(mid);
            }
        }
        for (Integer mid : mids) {
            jdbc.update("UPDATE starfree_metas SET count = (SELECT COUNT(*) FROM starfree_relationships WHERE mid = ?) WHERE mid = ?",
                    mid, mid);
        }
    }

    private void insertAddRelationships(long cid, String values) {
        if (values == null || values.trim().isEmpty()) {
            return;
        }
        for (String part : values.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                int mid = Integer.parseInt(value);
                if (mid > 0) {
                    jdbc.update("INSERT IGNORE INTO starfree_relationships (cid, mid) VALUES (?, ?)", cid, mid);
                }
            } catch (NumberFormatException invalidMeta) {
                throw new IllegalArgumentException("分类或标签参数不正确");
            }
        }
    }

    private void cleanupFailedAdd(long cid) {
        try {
            jdbc.update("DELETE FROM starfree_relationships WHERE cid = ?", cid);
        } catch (RuntimeException cleanupFailure) {
            LOG.error("Could not remove relationships for failed article {}", cid, cleanupFailure);
        }
        try {
            jdbc.update("DELETE FROM starfree_contents WHERE cid = ?", cid);
        } catch (RuntimeException cleanupFailure) {
            LOG.error("Could not remove failed article {}", cid, cleanupFailure);
        }
    }

    private void insertRelationshipValues(long cid, String values) {
        if (values == null || values.trim().isEmpty()) {
            return;
        }
        String[] parts = values.split(",");
        for (String part : parts) {
            try {
                int mid = Integer.parseInt(part.trim());
                jdbc.update("INSERT IGNORE INTO starfree_relationships (cid, mid) VALUES (?, ?)", cid, mid);
                jdbc.update("UPDATE starfree_metas SET count = (SELECT COUNT(*) FROM starfree_relationships WHERE mid = ?) WHERE mid = ?", mid, mid);
            } catch (NumberFormatException ignored) {
                // The old frontend sends comma-separated numeric meta IDs.
            }
        }
    }

    private void appendIntegerFilter(StringBuilder sql, List<Object> args, Map<String, Object> filters, String key, String column) {
        if (filters.containsKey(key)) {
            sql.append(" AND ").append(column).append(" = ?");
            args.add(RequestValues.objectInteger(filters, key, 0));
        }
    }

    private String safeOrder(String order) {
        if ("modified".equals(order) || "commentsNum".equals(order) || "views".equals(order)
                || "likes".equals(order) || "order".equals(order)) {
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

    private Map<String, Object> toContent(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            Object itemValue = entry.getValue();
            if (key.startsWith("author_") || "password".equals(key) || "template".equals(key) || itemValue == null) {
                continue;
            }
            result.put("order".equals(key) ? "orderKey" : key, itemValue);
        }
        long cid = ((Number) row.get("cid")).longValue();
        String originalText = value(row.get("text"));
        result.put("markdown", originalText.contains("<!--markdown-->") ? 1 : 0);
        result.put("text", previewText(originalText));
        result.put("images", images(originalText));
        result.put("videos", new ArrayList<>());
        result.put("fields", fields(cid));
        List<Map<String, Object>> category = new ArrayList<>();
        List<Map<String, Object>> tag = new ArrayList<>();
        for (Map<String, Object> meta : metas(cid)) {
            if ("category".equals(meta.get("type"))) {
                category.add(meta);
            } else if ("tag".equals(meta.get("type"))) {
                tag.add(meta);
            }
        }
        result.put("category", category);
        result.put("tag", tag);
        result.put("shop", shop(cid));
        if (number(row.get("authorId")) > 0) {
            result.put("authorInfo", authorInfo(row));
        }
        return result;
    }

    private Map<String, Object> toContentDetail(Map<String, Object> row, boolean renderMarkdown) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            Object itemValue = entry.getValue();
            if ("password".equals(key) || itemValue == null) {
                continue;
            }
            result.put("order".equals(key) ? "orderKey" : key, itemValue);
        }

        long cid = ((Number) row.get("cid")).longValue();
        String originalText = value(row.get("text"));
        String detailText = originalText.replace("<!--markdown-->", "");
        if (renderMarkdown) {
            Node document = MARKDOWN_PARSER.parse(detailText);
            detailText = MARKDOWN_RENDERER.render(document);
        }
        result.put("markdown", originalText.contains("<!--markdown-->") ? 1 : 0);
        result.put("text", detailText);
        result.put("images", images(originalText));
        result.put("fields", fields(cid));
        List<Map<String, Object>> category = new ArrayList<>();
        List<Map<String, Object>> tag = new ArrayList<>();
        for (Map<String, Object> meta : metas(cid)) {
            if ("category".equals(meta.get("type"))) {
                category.add(meta);
            } else if ("tag".equals(meta.get("type"))) {
                tag.add(meta);
            }
        }
        result.put("category", category);
        result.put("tag", tag);
        return result;
    }

    private List<Map<String, Object>> fields(long cid) {
        return jdbc.queryForList("SELECT cid,name,type,str_value AS strValue,int_value AS intValue,float_value AS floatValue FROM starfree_fields WHERE cid = ?", cid);
    }

    private List<Map<String, Object>> metas(long cid) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT m.mid,m.name,m.slug,m.type,m.description,m.count,m.`order`,m.parent,m.imgurl,m.isrecommend FROM starfree_metas m JOIN starfree_relationships r ON r.mid = m.mid WHERE r.cid = ? ORDER BY m.`order`,m.mid", cid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(withLegacyOrder(row));
        }
        return result;
    }

    private List<Map<String, Object>> shop(long cid) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM starfree_shop WHERE cid = ? AND status = 1 ORDER BY sort DESC, id DESC", cid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (!"value".equals(entry.getKey()) && entry.getValue() != null) {
                    item.put(entry.getKey(), entry.getValue());
                }
            }
            result.add(item);
        }
        return result;
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

    private Map<String, Object> authorInfo(Map<String, Object> row) {
        Map<String, Object> author = new LinkedHashMap<>();
        if (row.get("author_uid") == null) {
            author.put("name", "用户已注销");
            author.put("avatar", defaultAvatarPrefix() + "null");
            return author;
        }
        String name = value(row.get("author_screenName"));
        if (name.isEmpty()) {
            name = value(row.get("author_name"));
        }
        author.put("name", name);
        author.put("avatar", avatar(row.get("author_avatar"), row.get("author_mail")));
        putNonNull(author, "customize", row.get("author_customize"));
        author.put("experience", number(row.get("author_experience")));
        author.put("isvip", vipStatus(row.get("author_vip")));
        return author;
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

    private int vipStatus(Object input) {
        long vip = number(input);
        if (vip == 1) {
            return 2;
        }
        return vip > Instant.now().getEpochSecond() ? 1 : 0;
    }

    private List<String> images(String text) {
        List<String> result = new ArrayList<>();
        Matcher html = HTML_IMAGE.matcher(text);
        while (html.find()) {
            result.add(html.group(1));
        }
        Matcher markdown = MARKDOWN_IMAGE.matcher(text);
        while (markdown.find()) {
            result.add(markdown.group(1));
        }
        return result;
    }

    private String previewText(String text) {
        String result = text.replace("<!--markdown-->", "");
        result = HTML_IMAGE.matcher(result).replaceAll("");
        result = MARKDOWN_IMAGE.matcher(result).replaceAll("");
        result = HTML_TAG.matcher(result).replaceAll("");
        return result.length() > 400 ? result.substring(0, 400) : result;
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

    public static final class ContentPage {
        private final List<Map<String, Object>> data;
        private final int total;

        ContentPage(List<Map<String, Object>> data, int total) {
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
