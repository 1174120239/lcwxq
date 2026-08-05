package cn.lcxqy.starfree.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * token 到用户的兼容解析和公开用户快照读取。
 *
 * <p>先查 MySQL authCode，再查 LegacySessionBridge。Redis 只用于解析 uid，最终用户字段重新从
 * MySQL 读取，避免长期信任过期 session 快照。返回字段白名单不包含 password/authCode。
 */
@Service
public class LegacyTokenService {
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

    /** MySQL 优先、Redis 后备地解析 uid；空 token 或两边都不存在时返回 null。 */
    public Long userId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String normalizedToken = token.trim();
        List<Long> ids = jdbc.query(
                "SELECT uid FROM starfree_users WHERE authCode = ? LIMIT 1",
                new Object[]{normalizedToken},
                (rs, rowNum) -> rs.getLong("uid"));
        return ids.isEmpty() ? sessions.userId(normalizedToken) : ids.get(0);
    }

    /** 解析 token 后读取脱敏用户；不存在返回 null。 */
    public Map<String, Object> user(String token) {
        Long uid = userId(token);
        return uid == null ? null : userById(uid);
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
