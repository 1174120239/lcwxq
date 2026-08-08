package cn.lcxqy.starfree.anonymous;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import cn.lcxqy.starfree.space.SpaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 匿名动态（原 ng_music 插件功能的本土化实现）。
 *
 * <p>匿名动态仍是 {@code starfree_space} 里的普通动态，只是 uid 指向运营配置的专用匿名账号；
 * 真实发布者只记录在 {@code starfree_anonymous_posts}，该表不对任何公开接口输出。动态发布
 * 校验复用 {@link SpaceService#addAnonymous}，本服务只负责匿名配置、发布入口和归属查询。
 */
@Service
public class AnonymousPostService {
    private static final Logger LOG = LoggerFactory.getLogger(AnonymousPostService.class);

    private final JdbcTemplate jdbc;
    private final LegacyTokenService tokens;
    private final StaffAccess staff;
    private final SpaceService spaces;

    @Autowired
    public AnonymousPostService(JdbcTemplate jdbc, LegacyTokenService tokens,
                                StaffAccess staff, SpaceService spaces) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.staff = staff;
        this.spaces = spaces;
    }

    /** 公开配置：只暴露匿名动态是否开放，绝不暴露匿名账号身份。 */
    public Map<String, Object> publicConfig() {
        Map<String, Object> config = configRow();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", number(config.get("fid")) > 0);
        return result;
    }

    /** 管理端读取完整配置；仅 administrator。 */
    public Map<String, Object> adminConfig(String token) {
        staff.requireAdministrator(token);
        Map<String, Object> config = configRow();
        long fid = number(config.get("fid"));
        Map<String, Object> anonymous = fid > 0 ? tokens.userById(fid) : null;
        config.put("anonymousName", anonymous == null ? "" : String.valueOf(anonymous.get("screenName")));
        config.put("anonymousExists", anonymous != null);
        return config;
    }

    /** 管理端更新配置；仅 administrator。 */
    @Transactional
    public void updateAdminConfig(String token, Map<String, String> form) {
        staff.requireAdministrator(token);
        long fid = RequestValues.integer(form, "fid", 0);
        int review = RequestValues.integer(form, "review", -1);
        if (fid <= 0) {
            throw new IllegalArgumentException("匿名账号不能为空");
        }
        if (tokens.userById(fid) == null) {
            throw new IllegalArgumentException("匿名账号不存在");
        }
        if (review != 0 && review != 1) {
            throw new IllegalArgumentException("审核开关取值不正确");
        }
        long now = Instant.now().getEpochSecond();
        jdbc.update("UPDATE starfree_anonymous_config SET fid = ?, review = ?, modified = ? WHERE id = 1",
                fid, review, now);
    }

    /**
     * 匿名发布动态。请求结构与 addSpace 兼容：token、type、text、pic、topicIds。
     * 成功返回 {@code {sid, status}}。
     */
    public Map<String, Object> post(Map<String, String> request, String clientIp) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        Map<String, Object> config = configRow();
        long anonymousUid = number(config.get("fid"));
        if (anonymousUid <= 0) {
            throw new IllegalArgumentException("匿名动态暂未开放");
        }
        if (tokens.userById(anonymousUid) == null) {
            throw new IllegalArgumentException("匿名账号未配置或不存在");
        }
        boolean pending = spaces.addAnonymous(request, clientIp, anonymousUid,
                number(config.get("review")) == 1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", pending ? "waiting" : "publish");
        return result;
    }

    /**
     * 查询匿名动态的真实发布者；仅动态主人或 staff 可见，避免匿名身份被枚举。
     */
    public long owner(String token, long sid) {
        if (sid <= 0) {
            throw new IllegalArgumentException("无效的ID参数");
        }
        List<Long> rows = jdbc.query(
                "SELECT uid FROM starfree_anonymous_posts WHERE sid = ? LIMIT 1",
                new Object[]{sid}, (rs, rowNum) -> rs.getLong("uid"));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("该动态不是匿名动态");
        }
        long ownerUid = rows.get(0);
        Long requester = tokens.userId(token);
        if (requester == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        Map<String, Object> user = tokens.userById(requester);
        boolean isStaff = user != null && isStaff(String.valueOf(user.get("group")));
        if (requester != ownerUid && !isStaff) {
            throw new IllegalArgumentException("无权查看");
        }
        return ownerUid;
    }

    private Map<String, Object> configRow() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT fid, review, created, modified FROM starfree_anonymous_config WHERE id = 1 LIMIT 1");
        if (rows.isEmpty()) {
            throw new IllegalStateException("匿名动态配置不存在，请先执行数据库迁移");
        }
        return rows.get(0);
    }

    private static boolean isStaff(String group) {
        return "administrator".equals(group) || "editor".equals(group);
    }

    private static long number(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
