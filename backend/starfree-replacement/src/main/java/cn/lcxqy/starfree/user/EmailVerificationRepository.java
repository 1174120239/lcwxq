package cn.lcxqy.starfree.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Reads the existing registration policy, mail template and account bindings. */
@Repository
class EmailVerificationRepository {
    private final JdbcTemplate jdbc;

    EmailVerificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    VerificationConfig config() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT isEmail,webinfoTitle,webinfoUrl "
                        + "FROM starfree_apiconfig ORDER BY id LIMIT 1");
        if (rows.isEmpty()) {
            throw new IllegalStateException("Email verification configuration is missing");
        }
        Map<String, Object> row = rows.get(0);
        return new VerificationConfig(number(row.get("isEmail")) > 0,
                text(row.get("webinfoTitle")), text(row.get("webinfoUrl")), template());
    }

    boolean mailExists(String mail) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_users WHERE mail=?", Integer.class, mail);
        return count != null && count > 0;
    }

    RecoveryAccount accountByName(String name) {
        return account("SELECT name,mail FROM starfree_users WHERE name=? LIMIT 1", name);
    }

    RecoveryAccount accountByMail(String mail) {
        return account("SELECT name,mail FROM starfree_users WHERE mail=? LIMIT 1", mail);
    }

    private RecoveryAccount account(String sql, String value) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, value);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new RecoveryAccount(text(row.get("name")), text(row.get("mail")));
    }

    private String template() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT verifyTemplate FROM starfree_emailtemplate ORDER BY id LIMIT 1");
        return rows.isEmpty() ? "" : text(rows.get(0).get("verifyTemplate"));
    }

    private int number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static final class VerificationConfig {
        private final boolean enabled;
        private final String siteTitle;
        private final String siteUrl;
        private final String template;

        VerificationConfig(boolean enabled, String siteTitle, String siteUrl, String template) {
            this.enabled = enabled;
            this.siteTitle = siteTitle;
            this.siteUrl = siteUrl;
            this.template = template;
        }

        boolean isEnabled() {
            return enabled;
        }

        String getSiteTitle() {
            return siteTitle;
        }

        String getSiteUrl() {
            return siteUrl;
        }

        String getTemplate() {
            return template;
        }
    }

    static final class RecoveryAccount {
        private final String name;
        private final String mail;

        RecoveryAccount(String name, String mail) {
            this.name = name;
            this.mail = mail;
        }

        String getName() {
            return name;
        }

        String getMail() {
            return mail;
        }
    }
}
