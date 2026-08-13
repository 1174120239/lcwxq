package cn.lcxqy.starfree.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
class AccountMaintenanceRepository {
    private static final String LEGACY_TEXT_PARAMETER =
            "CONVERT(? USING utf8) COLLATE utf8_general_ci";
    private static final Set<String> EDITABLE_COLUMNS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "password", "mail", "phone", "screenName", "introduce", "userBg",
                    "avatar", "url", "address", "pay", "clientId", "authCode")));

    private final JdbcTemplate jdbc;

    AccountMaintenanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Map<String, Object> publicRegistrationConfig() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT isEmail,isInvite,isPhone FROM starfree_apiconfig ORDER BY id LIMIT 1");
        if (rows.isEmpty()) {
            throw new IllegalStateException("Registration configuration is missing");
        }
        return rows.get(0);
    }

    AccountRecord accountByUid(Connection connection, long uid) throws SQLException {
        return account(connection,
                "SELECT uid,name,password,mail,phone,authCode FROM starfree_users "
                        + "WHERE uid=? LIMIT 1", uid);
    }

    AccountRecord accountByName(Connection connection, String name) throws SQLException {
        return account(connection,
                "SELECT uid,name,password,mail,phone,authCode FROM starfree_users "
                        + "WHERE name=? LIMIT 1", name);
    }

    AccountRecord accountByMail(Connection connection, String mail) throws SQLException {
        return account(connection,
                "SELECT uid,name,password,mail,phone,authCode FROM starfree_users "
                        + "WHERE mail=? LIMIT 1", mail);
    }

    boolean valueExists(Connection connection, String column, String value, long excludedUid)
            throws SQLException {
        if (!("mail".equals(column) || "phone".equals(column)
                || "screenName".equals(column))) {
            throw new IllegalArgumentException("Unsupported uniqueness column");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM starfree_users WHERE " + column + "="
                        + LEGACY_TEXT_PARAMETER + " AND uid<>? LIMIT 1")) {
            statement.setString(1, value);
            statement.setLong(2, excludedUid);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    int update(Connection connection, long uid, Map<String, Object> changes) throws SQLException {
        if (changes == null || changes.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("UPDATE starfree_users SET ");
        int index = 0;
        for (String column : changes.keySet()) {
            if (!EDITABLE_COLUMNS.contains(column)) {
                throw new IllegalArgumentException("Unsupported account field: " + column);
            }
            if (index++ > 0) {
                sql.append(',');
            }
            sql.append('`').append(column).append("`=?");
        }
        sql.append(" WHERE uid=?");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int parameter = 1;
            for (Object value : changes.values()) {
                statement.setObject(parameter++, value);
            }
            statement.setLong(parameter, uid);
            return statement.executeUpdate();
        }
    }

    private AccountRecord account(Connection connection, String sql, Object value)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new AccountRecord(
                        result.getLong("uid"),
                        result.getString("name"),
                        result.getString("password"),
                        result.getString("mail"),
                        result.getString("phone"),
                        result.getString("authCode"));
            }
        }
    }

    static final class AccountRecord {
        private final long uid;
        private final String name;
        private final String password;
        private final String mail;
        private final String phone;
        private final String authCode;

        AccountRecord(long uid, String name, String password, String mail, String phone,
                      String authCode) {
            this.uid = uid;
            this.name = name == null ? "" : name;
            this.password = password == null ? "" : password;
            this.mail = mail == null ? "" : mail;
            this.phone = phone == null ? "" : phone;
            this.authCode = authCode == null ? "" : authCode;
        }

        long getUid() {
            return uid;
        }

        String getName() {
            return name;
        }

        String getPassword() {
            return password;
        }

        String getMail() {
            return mail;
        }

        String getPhone() {
            return phone;
        }

        String getAuthCode() {
            return authCode;
        }
    }
}
