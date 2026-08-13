package cn.lcxqy.starfree.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * token 到用户的兼容解析和公开用户快照读取。
 *
 * <p>生产启用 LegacySessionBridge 后，Redis TTL 是登录态的唯一有效期来源；不存在的 Redis
 * session 不能再凭 MySQL authCode 复活。未启用桥接的本地环境才回退 MySQL。用户字段始终从
 * MySQL 重读，避免信任过期 session 快照。返回字段白名单不包含 password/authCode。
 */
@Service
public class LegacyTokenService {
    private static final Set<String> PUBLIC_PROFILE_FIELDS = new LinkedHashSet<>();

    static {
        String[] fields = {"uid", "name", "url", "screenName", "created", "activated",
                "introduce", "customize", "vip", "experience", "avatar", "bantime",
                "posttime", "userBg", "campusId", "campus", "gradeId", "grade"};
        for (String field : fields) {
            PUBLIC_PROFILE_FIELDS.add(field);
        }
    }

    private final JdbcTemplate jdbc;
    private final LegacySessionBridge sessions;

    @Autowired
    public LegacyTokenService(JdbcTemplate jdbc, LegacySessionBridge sessions) {
        this.jdbc = jdbc;
        this.sessions = sessions == null ? LegacySessionBridge.NOOP : sessions;
    }

    public LegacyTokenService(JdbcTemplate jdbc) {
        this(jdbc, LegacySessionBridge.NOOP);
    }

    /** Redis enabled时严格服从TTL；仅桥接关闭的本地环境读取MySQL authCode。 */
    public Long userId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String normalizedToken = token.trim();
        if (!SessionTokenGenerator.isCurrentFormat(normalizedToken)) {
            return null;
        }
        if (sessions.available()) {
            return sessions.userId(normalizedToken);
        }
        List<Long> ids = jdbc.query(
                "SELECT uid FROM starfree_users WHERE authCode = ? LIMIT 1",
                new Object[]{normalizedToken},
                (rs, rowNum) -> rs.getLong("uid"));
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 解析 token 后读取脱敏用户；不存在返回 null。 */
    public Map<String, Object> user(String token) {
        Long uid = userId(token);
        return uid == null ? null : userById(uid);
    }

    /** Public profile projection for anonymous and cross-account reads. */
    public Map<String, Object> publicUserById(long uid) {
        Map<String, Object> user = userById(uid);
        if (user == null) {
            return null;
        }
        Map<String, Object> projection = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : user.entrySet()) {
            if (PUBLIC_PROFILE_FIELDS.contains(entry.getKey())) {
                projection.put(entry.getKey(), entry.getValue());
            }
        }
        return projection;
    }

    /**
     * 按 uid 读取前端需要的用户字段。assets、points、experience 虽会返回用于本人页面显示，
     * 但任何写接口都不能把客户端传回的这些数值当作可信输入。
     */
    public Map<String, Object> userById(long uid) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT u.uid,u.name,u.mail,u.url,u.screenName,u.created,u.activated,u.logged,"
                        + "u.`group`,u.introduce,u.assets,u.address,u.pay,u.customize,u.vip,"
                        + "u.experience,u.avatar,u.clientId,u.bantime,u.posttime,u.ip,u.local,"
                        + "u.phone,u.userBg,u.invitationCode,u.invitationUser,u.points,"
                        + "u.campus_option_id AS campusId,campus.name AS campus,"
                        + "u.grade_option_id AS gradeId,grade.name AS grade "
                        + "FROM starfree_users u "
                        + "LEFT JOIN starfree_identity_options campus ON campus.id=u.campus_option_id "
                        + "LEFT JOIN starfree_identity_options grade ON grade.id=u.grade_option_id "
                        + "WHERE u.uid = ? LIMIT 1",
                uid);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> user = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            user.put(entry.getKey(), entry.getValue());
        }
        return user;
    }
}
