package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.security.LegacyTokenService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserProfileService {
    private final JdbcTemplate jdbc;
    private final LegacyTokenService tokens;

    public UserProfileService(JdbcTemplate jdbc, LegacyTokenService tokens) {
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    public boolean containsProfileFields(Map<String, Object> body) {
        return body != null && (body.containsKey("gender") || body.containsKey("birthday")
                || body.containsKey("showGender") || body.containsKey("showBirthday"));
    }

    public int save(String token, Map<String, Object> body) {
        Long uid = tokens.userId(token);
        long requestedUid = positiveLong(body == null ? null : body.get("uid"));
        if (uid == null || uid <= 0 || requestedUid != uid) {
            throw new IllegalArgumentException("无权修改其他用户资料");
        }
        Map<String, Object> current = raw(uid);
        String gender = body.containsKey("gender") ? gender(body.get("gender"))
                : text(current.get("gender"));
        LocalDate birthday = body.containsKey("birthday") ? birthday(body.get("birthday"))
                : localDate(current.get("birthday"));
        int showGender = body.containsKey("showGender") ? binary(body.get("showGender"))
                : number(current.get("showGender"));
        int showBirthday = body.containsKey("showBirthday") ? binary(body.get("showBirthday"))
                : number(current.get("showBirthday"));
        long now = Instant.now().getEpochSecond();
        return jdbc.update("INSERT INTO starfree_user_profiles"
                        + "(uid,gender,birthday,show_gender,show_birthday,created,modified) "
                        + "VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE gender=VALUES(gender),"
                        + "birthday=VALUES(birthday),show_gender=VALUES(show_gender),"
                        + "show_birthday=VALUES(show_birthday),modified=VALUES(modified)",
                uid, gender, birthday == null ? null : Date.valueOf(birthday), showGender,
                showBirthday, now, now);
    }

    public void validate(Map<String, Object> body) {
        if (body == null) {
            return;
        }
        if (body.containsKey("gender")) {
            gender(body.get("gender"));
        }
        if (body.containsKey("birthday")) {
            birthday(body.get("birthday"));
        }
        if (body.containsKey("showGender")) {
            binary(body.get("showGender"));
        }
        if (body.containsKey("showBirthday")) {
            binary(body.get("showBirthday"));
        }
    }

    public void attach(Map<String, Object> user, boolean owner) {
        if (user == null) {
            return;
        }
        long uid = positiveLong(user.get("uid"));
        Map<String, Object> profile = raw(uid);
        int showGender = number(profile.get("showGender"));
        int showBirthday = number(profile.get("showBirthday"));
        user.put("showGender", showGender);
        user.put("showBirthday", showBirthday);
        user.put("gender", owner || showGender == 1 ? text(profile.get("gender")) : "");
        LocalDate birthday = localDate(profile.get("birthday"));
        user.put("birthday", owner || showBirthday == 1
                ? (birthday == null ? "" : birthday.toString()) : "");
    }

    private Map<String, Object> raw(long uid) {
        if (uid <= 0) {
            return defaults();
        }
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT gender,birthday,show_gender AS showGender,"
                            + "show_birthday AS showBirthday FROM starfree_user_profiles WHERE uid=? LIMIT 1",
                    uid);
            return rows.isEmpty() ? defaults() : rows.get(0);
        } catch (DataAccessException error) {
            // Additive migration may be deployed after the application. Existing profile reads
            // must remain available until that migration is explicitly run.
            return defaults();
        }
    }

    private Map<String, Object> defaults() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gender", "");
        result.put("birthday", null);
        result.put("showGender", 0);
        result.put("showBirthday", 0);
        return result;
    }

    private String gender(Object value) {
        String normalized = text(value).trim();
        if (!(normalized.isEmpty() || "男".equals(normalized) || "女".equals(normalized)
                || "保密".equals(normalized))) {
            throw new IllegalArgumentException("性别选项不正确");
        }
        return normalized;
    }

    private LocalDate birthday(Object value) {
        String normalized = text(value).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            LocalDate result = LocalDate.parse(normalized);
            if (result.isAfter(LocalDate.now()) || result.isBefore(LocalDate.of(1900, 1, 1))) {
                throw new IllegalArgumentException("生日日期不正确");
            }
            return result;
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("生日日期不正确");
        }
    }

    private LocalDate localDate(Object value) {
        if (value instanceof Date) {
            return ((Date) value).toLocalDate();
        }
        String normalized = text(value).trim();
        return normalized.isEmpty() ? null : LocalDate.parse(normalized.substring(0, 10));
    }

    private int binary(Object value) {
        int result = number(value);
        if (result != 0 && result != 1) {
            throw new IllegalArgumentException("公开设置不正确");
        }
        return result;
    }

    private int number(Object value) {
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long positiveLong(Object value) {
        try {
            long result = value == null ? 0 : Long.parseLong(String.valueOf(value));
            return Math.max(0, result);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
