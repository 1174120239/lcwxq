package cn.lcxqy.starfree.lostfound;

import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LostFoundConfigService {
    private static final int[] LEVEL_EXPERIENCE = {
            0, 10, 50, 200, 500, 1000, 2000, 5000, 10000, 20000
    };

    private final JdbcTemplate jdbc;
    private final StaffAccess access;
    private final LegacyTokenService tokens;

    public LostFoundConfigService(JdbcTemplate jdbc, StaffAccess access,
                                  LegacyTokenService tokens) {
        this.jdbc = jdbc;
        this.access = access;
        this.tokens = tokens;
    }

    public Map<String, Object> publicConfig(String token) {
        Config config = config();
        Map<String, Object> result = configMap(config);
        Map<String, Object> user = tokens.user(token);
        long experience = user == null ? 0 : number(user.get("experience"));
        int currentLevel = level(experience);
        boolean staff = user != null && isStaff(text(user.get("group")));
        result.put("currentLevel", currentLevel);
        result.put("eligible", config.enabled && (staff || currentLevel >= config.minimumLevel));
        return result;
    }

    public Map<String, Object> manage(String token) {
        access.requireStaff(token);
        return configMap(config());
    }

    public Map<String, Object> save(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = access.requireAdministrator(token);
        int enabled = flag(body.get("enabled"));
        int minimumLevel = ranged(body.get("minimumLevel"), 0, 9, "最低等级必须在Lv0到Lv9之间");
        int auditRequired = flag(body.get("auditRequired"));
        int contactEnabled = flag(body.get("contactEnabled"));
        int dailyContactLimit = ranged(body.get("dailyContactLimit"), 1, 50,
                "每日联系方式发送上限必须在1到50之间");
        int itemExpiryDays = ranged(body.get("itemExpiryDays"), 1, 365,
                "信息有效期必须在1到365天之间");
        jdbc.update("UPDATE starfree_lost_found_config SET enabled=?,minimum_level=?,"
                        + "audit_required=?,contact_enabled=?,daily_contact_limit=?,item_expiry_days=?,"
                        + "modified_by=?,modified=? WHERE id=1",
                enabled, minimumLevel, auditRequired, contactEnabled, dailyContactLimit,
                itemExpiryDays, actor.getUid(), Instant.now().getEpochSecond());
        return manage(token);
    }

    public StaffAccess.Actor requireParticipant(String token) {
        StaffAccess.Actor actor = access.requireUser(token);
        Config config = config();
        if (!config.enabled && !actor.isStaff()) {
            throw new IllegalArgumentException("校园互助暂未开放");
        }
        int currentLevel = level(number(actor.getUser().get("experience")));
        if (!actor.isStaff() && currentLevel < config.minimumLevel) {
            throw new IllegalArgumentException("达到Lv" + config.minimumLevel + "后才可参与校园互助");
        }
        long bannedUntil = number(actor.getUser().get("bantime"));
        long now = Instant.now().getEpochSecond();
        if (bannedUntil == 1 || bannedUntil > now) {
            throw new IllegalArgumentException("账号当前不可参与校园互助");
        }
        return actor;
    }

    public Config config() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT enabled,minimum_level,audit_required,contact_enabled,"
                        + "daily_contact_limit,item_expiry_days FROM starfree_lost_found_config "
                        + "WHERE id=1 LIMIT 1");
        if (rows.isEmpty()) {
            return new Config(true, 2, true, true, 5, 30);
        }
        Map<String, Object> row = rows.get(0);
        return new Config(number(value(row, "enabled")) == 1,
                (int) number(value(row, "minimum_level")),
                number(value(row, "audit_required")) == 1,
                number(value(row, "contact_enabled")) == 1,
                (int) number(value(row, "daily_contact_limit")),
                (int) number(value(row, "item_expiry_days")));
    }

    public int level(long experience) {
        for (int level = LEVEL_EXPERIENCE.length - 1; level >= 0; level--) {
            if (experience >= LEVEL_EXPERIENCE[level]) {
                return level;
            }
        }
        return 0;
    }

    private Map<String, Object> configMap(Config config) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("enabled", config.enabled ? 1 : 0);
        result.put("minimumLevel", config.minimumLevel);
        result.put("minimumExperience", LEVEL_EXPERIENCE[config.minimumLevel]);
        result.put("auditRequired", config.auditRequired ? 1 : 0);
        result.put("contactEnabled", config.contactEnabled ? 1 : 0);
        result.put("dailyContactLimit", config.dailyContactLimit);
        result.put("itemExpiryDays", config.itemExpiryDays);
        return result;
    }

    private int flag(Object value) {
        return number(value) == 1 ? 1 : 0;
    }

    private int ranged(Object value, int minimum, int maximum, String message) {
        int number = (int) number(value);
        if (number < minimum || number > maximum) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    private boolean isStaff(String group) {
        return "administrator".equals(group) || "editor".equals(group);
    }

    private Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static final class Config {
        private final boolean enabled;
        private final int minimumLevel;
        private final boolean auditRequired;
        private final boolean contactEnabled;
        private final int dailyContactLimit;
        private final int itemExpiryDays;

        Config(boolean enabled, int minimumLevel, boolean auditRequired,
               boolean contactEnabled, int dailyContactLimit, int itemExpiryDays) {
            this.enabled = enabled;
            this.minimumLevel = Math.max(0, Math.min(minimumLevel, 9));
            this.auditRequired = auditRequired;
            this.contactEnabled = contactEnabled;
            this.dailyContactLimit = Math.max(1, dailyContactLimit);
            this.itemExpiryDays = Math.max(1, itemExpiryDays);
        }

        public boolean isEnabled() { return enabled; }
        public int getMinimumLevel() { return minimumLevel; }
        public boolean isAuditRequired() { return auditRequired; }
        public boolean isContactEnabled() { return contactEnabled; }
        public int getDailyContactLimit() { return dailyContactLimit; }
        public int getItemExpiryDays() { return itemExpiryDays; }
    }
}
