package cn.lcxqy.starfree.invitation;

import cn.lcxqy.starfree.security.LegacyTokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class InvitationService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6,16}$");

    private final JdbcTemplate jdbc;
    private final LegacyTokenService tokens;
    private final SecureRandom random = new SecureRandom();

    public InvitationService(JdbcTemplate jdbc, LegacyTokenService tokens) {
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    public Map<String, Object> publicConfig(String inviteCode) {
        Map<String, Object> result = configResponse(config());
        String code = normalizeOptionalCode(inviteCode);
        result.put("inviteCode", code);
        result.put("validInvite", false);
        result.put("inviter", null);
        if (code.isEmpty()) {
            return result;
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.uid,c.invite_code,u.screenName,u.name,u.avatar "
                        + "FROM lcxqy_invitation_codes c "
                        + "JOIN starfree_users u ON u.uid=c.uid "
                        + "WHERE c.invite_code=? LIMIT 1",
                code);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            Map<String, Object> inviter = new LinkedHashMap<>();
            inviter.put("uid", number(row.get("uid")));
            inviter.put("name", displayName(row));
            inviter.put("avatar", text(row.get("avatar")));
            result.put("validInvite", true);
            result.put("inviter", inviter);
        }
        return result;
    }

    public Map<String, Object> me(String token) {
        Long uid = tokens.userId(token);
        if (uid == null || tokens.userById(uid) == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }

        Map<String, Object> result = configResponse(config());
        String code = ensureCode(uid);
        Map<String, Object> totals = jdbc.queryForMap(
                "SELECT COUNT(*) AS invitationCount,"
                        + "COALESCE(SUM(reward_points),0) AS totalPoints,"
                        + "COALESCE(SUM(reward_experience),0) AS totalExperience "
                        + "FROM lcxqy_invitation_records WHERE inviter_uid=?",
                uid);
        List<Map<String, Object>> invitees = jdbc.queryForList(
                "SELECT r.invitee_uid AS uid,COALESCE(u.screenName,u.name) AS name,u.avatar,"
                        + "r.reward_points AS rewardPoints,"
                        + "r.reward_experience AS rewardExperience,"
                        + "UNIX_TIMESTAMP(r.created_at) AS created "
                        + "FROM lcxqy_invitation_records r "
                        + "LEFT JOIN starfree_users u ON u.uid=r.invitee_uid "
                        + "WHERE r.inviter_uid=? ORDER BY r.id DESC LIMIT 50",
                uid);
        result.put("inviteCode", code);
        result.put("invitationCount", number(totals.get("invitationCount")));
        result.put("totalPoints", number(totals.get("totalPoints")));
        result.put("totalExperience", number(totals.get("totalExperience")));
        result.put("invitees", invitees == null ? Collections.emptyList() : invitees);
        return result;
    }

    private Map<String, Object> config() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT enabled,reward_points,reward_experience,"
                        + "android_download_url,ios_download_url "
                        + "FROM lcxqy_invitation_config WHERE id=1 LIMIT 1");
        if (rows.isEmpty()) {
            throw new IllegalStateException("Invitation configuration is missing");
        }
        return rows.get(0);
    }

    private Map<String, Object> configResponse(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", number(row.get("enabled")) == 1L);
        result.put("rewardPoints", number(row.get("reward_points")));
        result.put("rewardExperience", number(row.get("reward_experience")));
        result.put("androidDownloadUrl", text(row.get("android_download_url")));
        result.put("iosDownloadUrl", text(row.get("ios_download_url")));
        return result;
    }

    private String ensureCode(long uid) {
        List<String> existing = jdbc.query(
                "SELECT invite_code FROM lcxqy_invitation_codes WHERE uid=? LIMIT 1",
                new Object[]{uid}, (rs, rowNum) -> rs.getString(1));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = randomCode();
            int inserted = jdbc.update(
                    "INSERT IGNORE INTO lcxqy_invitation_codes(uid,invite_code,created_at) "
                            + "VALUES(?,?,NOW())",
                    uid, code);
            if (inserted == 1) {
                return code;
            }
            existing = jdbc.query(
                    "SELECT invite_code FROM lcxqy_invitation_codes WHERE uid=? LIMIT 1",
                    new Object[]{uid}, (rs, rowNum) -> rs.getString(1));
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
        }
        throw new IllegalStateException("Could not allocate a unique invitation code");
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder("LY");
        while (code.length() < 10) {
            code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    private String normalizeOptionalCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.isEmpty() && !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("邀请码格式不正确");
        }
        return code;
    }

    private String displayName(Map<String, Object> row) {
        String screenName = text(row.get("screenName"));
        return screenName.isEmpty() ? text(row.get("name")) : screenName;
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
