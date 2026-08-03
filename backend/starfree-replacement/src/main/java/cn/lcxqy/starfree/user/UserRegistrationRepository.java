package cn.lcxqy.starfree.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
class UserRegistrationRepository {
    private static final String CONFIG_SQL =
            "SELECT isEmail,isInvite,isPhone,forbidden,rebateLevel,rebateNum,"
                    + "banRobots,silenceTime FROM starfree_apiconfig ORDER BY id LIMIT 1";

    private final JdbcTemplate jdbc;

    UserRegistrationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    RegistrationConfig config() {
        List<Map<String, Object>> rows = jdbc.queryForList(CONFIG_SQL);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Registration configuration is missing");
        }
        return new RegistrationConfig(rows.get(0));
    }

    RegistrationConfig config(Connection connection) throws SQLException {
        Map<String, Object> row = one(connection, CONFIG_SQL);
        if (row == null) {
            throw new SQLException("Registration configuration is missing");
        }
        return new RegistrationConfig(row);
    }

    boolean nameExists(Connection connection, String name) throws SQLException {
        return number(connection,
                "SELECT COUNT(*) FROM starfree_users WHERE name=?", name) > 0;
    }

    boolean mailExists(Connection connection, String mail) throws SQLException {
        return number(connection,
                "SELECT COUNT(*) FROM starfree_users WHERE mail=?", mail) > 0;
    }

    List<Map<String, Object>> availableInvitations(Connection connection, String code)
            throws SQLException {
        return list(connection,
                "SELECT id,uid,status FROM starfree_invitation "
                        + "WHERE code=? AND status=0 ORDER BY id LIMIT 2", code);
    }

    Map<String, Object> user(Connection connection, long uid) throws SQLException {
        return one(connection,
                "SELECT uid,COALESCE(assets,0) AS assets FROM starfree_users WHERE uid=? LIMIT 1",
                uid);
    }

    int consumeInvitation(Connection connection, long invitationId) throws SQLException {
        return update(connection,
                "UPDATE starfree_invitation SET status=1 WHERE id=? AND status=0", invitationId);
    }

    int releaseInvitation(Connection connection, long invitationId) throws SQLException {
        return update(connection,
                "UPDATE starfree_invitation SET status=0 WHERE id=? AND status=1", invitationId);
    }

    long insertUser(Connection connection, RegistrationUser user) throws SQLException {
        return insertKey(connection,
                "INSERT INTO starfree_users "
                        + "(name,password,mail,screenName,created,activated,logged,`group`,"
                        + "assets,vip,experience,bantime,posttime,ip,local,phone,"
                        + "invitationCode,invitationUser,points) "
                        + "VALUES (?,?,?,?,?,0,0,'contributor',0,0,0,0,0,?,'',?,'',?,0)",
                user.name, user.passwordHash, emptyToNull(user.mail), user.name,
                user.created, user.remoteAddress, user.phone, user.inviterUid);
    }

    int setAssets(Connection connection, long uid, long assets) throws SQLException {
        return update(connection, "UPDATE starfree_users SET assets=? WHERE uid=?", assets, uid);
    }

    long insertRebatePaylog(Connection connection, long uid, int amount,
                            String operationKey, long created) throws SQLException {
        return insertKey(connection,
                "INSERT INTO starfree_paylog "
                        + "(subject,total_amount,out_trade_no,paytype,uid,created,status) "
                        + "VALUES (?,?,?,?,?,?,1)",
                "\u9080\u8bf7\u6ce8\u518c\u56fa\u5b9a\u5956\u52b1", String.valueOf(amount),
                operationKey + ":rebate", "rebate", uid, created);
    }

    int deletePaylog(Connection connection, long paylogId) throws SQLException {
        return update(connection, "DELETE FROM starfree_paylog WHERE pid=?", paylogId);
    }

    int deleteUser(Connection connection, long uid) throws SQLException {
        return update(connection, "DELETE FROM starfree_users WHERE uid=?", uid);
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private int update(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            return statement.executeUpdate();
        }
    }

    private long insertKey(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, args);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Registration insert did not affect one row");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Registration insert did not return a key");
                }
                return keys.getLong(1);
            }
        }
    }

    private long number(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private Map<String, Object> one(Connection connection, String sql, Object... args)
            throws SQLException {
        List<Map<String, Object>> rows = list(connection, sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> list(Connection connection, String sql, Object... args)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData metadata = result.getMetaData();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        row.put(metadata.getColumnLabel(index), result.getObject(index));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private void bind(PreparedStatement statement, Object... args) throws SQLException {
        for (int index = 0; index < args.length; index++) {
            statement.setObject(index + 1, args[index]);
        }
    }

    static final class RegistrationUser {
        private final String name;
        private final String passwordHash;
        private final String mail;
        private final String phone;
        private final String remoteAddress;
        private final long inviterUid;
        private final long created;

        RegistrationUser(String name, String passwordHash, String mail, String phone,
                         String remoteAddress, long inviterUid, long created) {
            this.name = name;
            this.passwordHash = passwordHash;
            this.mail = mail;
            this.phone = phone;
            this.remoteAddress = remoteAddress;
            this.inviterUid = inviterUid;
            this.created = created;
        }

        long getInviterUid() {
            return inviterUid;
        }
    }
}
