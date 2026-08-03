package cn.lcxqy.starfree.ads;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.economy.EconomyOperationJournal;
import cn.lcxqy.starfree.security.LegacyTokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdsRewardService {
    private static final String REWARD_TYPE = "adsGift";

    private final JdbcTemplate jdbc;
    private final LegacyTokenService tokens;
    private final EconomyLockExecutor lock;
    private final EconomyOperationJournal journal;
    private final LegacyAdsRewardGuard guard;

    public AdsRewardService(JdbcTemplate jdbc, LegacyTokenService tokens,
                            EconomyLockExecutor lock, EconomyOperationJournal journal,
                            LegacyAdsRewardGuard guard) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.lock = lock;
        this.journal = journal;
        this.guard = guard;
    }

    public Map<String, Object> start(Map<String, String> request) {
        long uid = requireUser(request);
        RewardConfig config = config();
        guard.checkBurst(uid, config.banRobots, config.silenceSeconds);
        LegacyAdsRewardGuard.Reservation reservation = guard.reserveVideoStart(uid);
        try {
            return lock.execute(connection -> startLocked(
                    connection, uid, required(request, "appkey", 255)));
        } catch (RuntimeException error) {
            reservation.cancel();
            throw error;
        }
    }

    public Map<String, Object> clientNotify(Map<String, String> request) {
        long uid = requireUser(request);
        long logId = positiveLong(RequestValues.text(request, "logid"),
                "\u8bf7\u5148\u53d1\u8d77\u6fc0\u52b1\u89c6\u9891");
        RewardConfig config = config();
        if (config.videoType != 0) {
            throw new IllegalArgumentException(
                    "\u672a\u5f00\u542f\u8be5\u56de\u8c03\u6e20\u9053\uff01");
        }
        guard.checkBurst(uid, config.banRobots, config.silenceSeconds);
        return lock.execute(connection -> clientNotifyLocked(connection, uid, logId));
    }

    public boolean serverNotify(Map<String, String> request) {
        String transactionId = required(request, "trans_id", 200);
        long uid = positiveLong(RequestValues.text(request, "user_id"),
                "Invalid advertising user id");
        String sign = RequestValues.text(request, "sign");
        return lock.execute(connection -> serverNotifyLocked(
                connection, uid, transactionId, sign, request));
    }

    private Map<String, Object> startLocked(Connection connection, long uid, String appKey)
            throws SQLException {
        RewardConfig config = config(connection);
        if (one(connection, "SELECT uid FROM starfree_users WHERE uid=? LIMIT 1", uid) == null) {
            throw new IllegalArgumentException(
                    "\u7528\u6237\u672a\u767b\u5f55\u6216Token\u9a8c\u8bc1\u5931\u8d25");
        }
        String adpid = resolveAdpid(connection, appKey);
        if (dailyCount(connection, uid) >= config.dailyLimit) {
            throw new IllegalArgumentException(
                    "\u4eca\u65e5\u5956\u52b1\u83b7\u53d6\u6b21\u6570\u5df2\u7528\u5b8c");
        }
        long logId = insertKey(connection,
                "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) "
                        + "VALUES (?,0,?,0,?,0)",
                uid, REWARD_TYPE, Instant.now().getEpochSecond());
        return mapOf("adpid", adpid, "logid", logId);
    }

    private Map<String, Object> clientNotifyLocked(Connection connection, long uid, long logId)
            throws SQLException {
        RewardConfig config = config(connection);
        if (config.videoType != 0) {
            throw new IllegalArgumentException(
                    "\u672a\u5f00\u542f\u8be5\u56de\u8c03\u6e20\u9053\uff01");
        }
        Map<String, Object> pending = one(connection,
                "SELECT id,uid,cid,type,created FROM starfree_userlog WHERE id=? LIMIT 1",
                logId);
        if (pending == null || number(pending.get("uid")) != uid
                || !REWARD_TYPE.equals(String.valueOf(pending.get("type")))) {
            throw new IllegalArgumentException(
                    "\u8bf7\u5148\u53d1\u8d77\u6fc0\u52b1\u89c6\u9891");
        }
        if (number(pending.get("cid")) == 1) {
            // A committed replacement or legacy callback is already authoritative.
            return clientResult(logId, config.award);
        }
        if (number(pending.get("cid")) != 0 || !isToday(connection, logId)) {
            throw new IllegalArgumentException(
                    "\u4e0d\u8981\u91cd\u590d\u8bf7\u6c42\u56de\u8c03");
        }

        Map<String, Object> user = one(connection,
                "SELECT COALESCE(assets,0) AS assets FROM starfree_users WHERE uid=? LIMIT 1",
                uid);
        if (user == null) {
            throw new IllegalArgumentException("\u7528\u6237\u4e0d\u5b58\u5728");
        }
        String operationKey = journal.fixedKey("ads-gift-client", logId);
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "ads-gift-client", uid, uid, logId,
                mapOf("uid", uid, "logid", logId));
        if (begin.isReplay()) {
            return begin.getResult();
        }

        long oldAssets = number(user.get("assets"));
        long paylogId = 0;
        boolean logChanged = false;
        boolean balanceChanged = false;
        try {
            long nextAssets = addExact(oldAssets, config.award);
            if (update(connection,
                    "UPDATE starfree_userlog SET cid=1 WHERE id=? AND uid=? AND type=? AND cid=0",
                    logId, uid, REWARD_TYPE) != 1) {
                throw new SQLException("Advertising reward log state changed concurrently");
            }
            logChanged = true;
            if (config.award > 0) {
                if (update(connection, "UPDATE starfree_users SET assets=? WHERE uid=?",
                        nextAssets, uid) != 1) {
                    throw new SQLException("Advertising reward balance update failed");
                }
                balanceChanged = true;
            }
            paylogId = insertPaylog(connection, uid, config.award, operationKey);
            Map<String, Object> result = clientResult(logId, config.award);
            journal.commit(connection, operationKey, result);
            return result;
        } catch (Exception error) {
            compensateClient(connection, operationKey, error, uid, logId, oldAssets,
                    paylogId, logChanged, balanceChanged);
            rethrow(error);
            return null;
        }
    }

    private boolean serverNotifyLocked(Connection connection, long uid, String transactionId,
                                       String sign, Map<String, String> request)
            throws SQLException {
        RewardConfig config = config(connection);
        // An empty secret makes SHA256(":" + trans_id) publicly forgeable. Fail closed.
        if (config.videoType != 1 || config.securityKey.trim().isEmpty()
                || !validSignature(config.securityKey, transactionId, sign)) {
            return false;
        }
        Map<String, Object> user = one(connection,
                "SELECT COALESCE(assets,0) AS assets FROM starfree_users WHERE uid=? LIMIT 1",
                uid);
        if (user == null) {
            return false;
        }

        String operationKey = journal.requestKey(
                "ads-gift-server", 0L, transactionId, transactionId);
        Map<String, Object> payload = mapOf(
                "uid", uid,
                "transId", transactionId,
                "adpid", auditText(request.get("adpid")),
                "provider", auditText(request.get("provider")),
                "extra", auditText(request.get("extra")));
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "ads-gift-server", 0L, uid, 0L, payload);
        if (begin.isReplay()) {
            return Boolean.TRUE.equals(begin.getResult().get("isValid"));
        }

        Map<String, Object> pending = one(connection,
                "SELECT id FROM starfree_userlog WHERE uid=? AND type=? AND cid=0 "
                        + "AND created>=UNIX_TIMESTAMP(CURDATE()) "
                        + "AND created<UNIX_TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL 1 DAY)) "
                        + "ORDER BY id DESC LIMIT 1",
                uid, REWARD_TYPE);
        int count = dailyCount(connection, uid);
        if (config.dailyLimit <= 0
                || (pending == null && count >= config.dailyLimit)
                || (pending != null && count > config.dailyLimit)) {
            journal.commit(connection, operationKey,
                    mapOf("isValid", false, "reason", "daily-limit"));
            return false;
        }

        long oldAssets = number(user.get("assets"));
        long logId = pending == null ? 0L : number(pending.get("id"));
        long paylogId = 0L;
        boolean createdLog = false;
        boolean consumedPending = false;
        boolean balanceChanged = false;
        try {
            long nextAssets = addExact(oldAssets, config.award);
            if (pending == null) {
                logId = insertKey(connection,
                        "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) "
                                + "VALUES (?,1,?,0,?,0)",
                        uid, REWARD_TYPE, Instant.now().getEpochSecond());
                createdLog = true;
            } else {
                if (update(connection,
                        "UPDATE starfree_userlog SET cid=1 WHERE id=? AND uid=? "
                                + "AND type=? AND cid=0",
                        logId, uid, REWARD_TYPE) != 1) {
                    throw new SQLException("Advertising pending log state changed concurrently");
                }
                consumedPending = true;
            }
            if (config.award > 0) {
                if (update(connection, "UPDATE starfree_users SET assets=? WHERE uid=?",
                        nextAssets, uid) != 1) {
                    throw new SQLException("Advertising reward balance update failed");
                }
                balanceChanged = true;
            }
            paylogId = insertPaylog(connection, uid, config.award, operationKey);
            journal.commit(connection, operationKey,
                    mapOf("isValid", true, "uid", uid, "logid", logId,
                            "award", config.award));
            return true;
        } catch (Exception error) {
            compensateServer(connection, operationKey, error, uid, logId, oldAssets,
                    paylogId, createdLog, consumedPending, balanceChanged);
            rethrow(error);
            return false;
        }
    }

    private RewardConfig config() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT adsVideoType,adsSecuritykey,adsGiftNum,adsGiftAward,"
                        + "banRobots,silenceTime FROM starfree_apiconfig ORDER BY id LIMIT 1");
        if (rows.isEmpty()) {
            throw new IllegalStateException("Advertising reward configuration is missing");
        }
        return RewardConfig.from(rows.get(0));
    }

    private RewardConfig config(Connection connection) throws SQLException {
        Map<String, Object> row = one(connection,
                "SELECT adsVideoType,adsSecuritykey,adsGiftNum,adsGiftAward,"
                        + "banRobots,silenceTime FROM starfree_apiconfig ORDER BY id LIMIT 1");
        if (row == null) {
            throw new SQLException("Advertising reward configuration is missing");
        }
        return RewardConfig.from(row);
    }

    private String resolveAdpid(Connection connection, String appKey) throws SQLException {
        Map<String, Object> table = one(connection,
                "SELECT COUNT(*) AS total FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='starfree_app'");
        if (table == null || number(table.get("total")) == 0) {
            throw new IllegalArgumentException(
                    "\u5e94\u7528\u4e0d\u5b58\u5728\u6216\u5bc6\u94a5\u9519\u8bef");
        }
        Map<String, Object> app = one(connection,
                "SELECT adpid FROM starfree_app WHERE `key`=? LIMIT 1", appKey);
        String adpid = app == null || app.get("adpid") == null
                ? "" : String.valueOf(app.get("adpid")).trim();
        if (adpid.isEmpty()) {
            throw new IllegalArgumentException(
                    "\u5e94\u7528\u4e0d\u5b58\u5728\u6216\u5bc6\u94a5\u9519\u8bef");
        }
        return adpid;
    }

    private int dailyCount(Connection connection, long uid) throws SQLException {
        Map<String, Object> row = one(connection,
                "SELECT COUNT(*) AS total FROM starfree_userlog WHERE uid=? AND type=? "
                        + "AND created>=UNIX_TIMESTAMP(CURDATE()) "
                        + "AND created<UNIX_TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL 1 DAY))",
                uid, REWARD_TYPE);
        return row == null ? 0 : (int) number(row.get("total"));
    }

    private boolean isToday(Connection connection, long logId) throws SQLException {
        Map<String, Object> row = one(connection,
                "SELECT COUNT(*) AS total FROM starfree_userlog WHERE id=? "
                        + "AND created>=UNIX_TIMESTAMP(CURDATE()) "
                        + "AND created<UNIX_TIMESTAMP(DATE_ADD(CURDATE(),INTERVAL 1 DAY))",
                logId);
        return row != null && number(row.get("total")) == 1;
    }

    private long insertPaylog(Connection connection, long uid, int award, String operationKey)
            throws SQLException {
        return insertKey(connection,
                "INSERT INTO starfree_paylog "
                        + "(subject,total_amount,out_trade_no,paytype,uid,created,status) "
                        + "VALUES (?,?,?,?,?,?,1)",
                "\u5e7f\u544a\u5956\u52b1", String.valueOf(award), operationKey,
                REWARD_TYPE, uid, Instant.now().getEpochSecond());
    }

    private void compensateClient(Connection connection, String operationKey, Exception error,
                                  long uid, long logId, long oldAssets, long paylogId,
                                  boolean logChanged, boolean balanceChanged) throws SQLException {
        Exception compensation = null;
        compensation = compensateDelete(connection, compensation, paylogId,
                "DELETE FROM starfree_paylog WHERE pid=?");
        compensation = compensateUpdate(connection, compensation, balanceChanged,
                "UPDATE starfree_users SET assets=? WHERE uid=?", oldAssets, uid);
        compensation = compensateUpdate(connection, compensation, logChanged,
                "UPDATE starfree_userlog SET cid=0 WHERE id=?", logId);
        finishCompensation(connection, operationKey, error, compensation);
    }

    private void compensateServer(Connection connection, String operationKey, Exception error,
                                  long uid, long logId, long oldAssets, long paylogId,
                                  boolean createdLog, boolean consumedPending,
                                  boolean balanceChanged) throws SQLException {
        Exception compensation = null;
        compensation = compensateDelete(connection, compensation, paylogId,
                "DELETE FROM starfree_paylog WHERE pid=?");
        compensation = compensateUpdate(connection, compensation, balanceChanged,
                "UPDATE starfree_users SET assets=? WHERE uid=?", oldAssets, uid);
        compensation = compensateUpdate(connection, compensation, consumedPending,
                "UPDATE starfree_userlog SET cid=0 WHERE id=?", logId);
        compensation = compensateUpdate(connection, compensation, createdLog,
                "DELETE FROM starfree_userlog WHERE id=?", logId);
        finishCompensation(connection, operationKey, error, compensation);
    }

    private Exception compensateDelete(Connection connection, Exception prior, long id,
                                       String sql) {
        return compensateUpdate(connection, prior, id > 0, sql, id);
    }

    private Exception compensateUpdate(Connection connection, Exception prior, boolean required,
                                       String sql, Object... args) {
        if (!required) {
            return prior;
        }
        try {
            if (update(connection, sql, args) != 1) {
                throw new SQLException("Advertising reward compensation affected an unexpected row count");
            }
            return prior;
        } catch (Exception failure) {
            if (prior != null) {
                prior.addSuppressed(failure);
                return prior;
            }
            return failure;
        }
    }

    private void finishCompensation(Connection connection, String operationKey, Exception error,
                                    Exception compensation) throws SQLException {
        if (compensation == null) {
            journal.fail(connection, operationKey, error);
            return;
        }
        error.addSuppressed(compensation);
        journal.needsReview(connection, operationKey, error);
    }

    private void rethrow(Exception error) throws SQLException {
        if (error instanceof SQLException) {
            throw (SQLException) error;
        }
        if (error instanceof RuntimeException) {
            throw (RuntimeException) error;
        }
        throw new SQLException(error);
    }

    private Map<String, Object> clientResult(long logId, int award) {
        return mapOf("logid", String.valueOf(logId), "award", award);
    }

    private long requireUser(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null) {
            throw new IllegalArgumentException(
                    "\u7528\u6237\u672a\u767b\u5f55\u6216Token\u9a8c\u8bc1\u5931\u8d25");
        }
        return uid;
    }

    private String required(Map<String, String> request, String key, int maxLength) {
        String value = RequestValues.text(request, key);
        if (value.isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException("Invalid advertising callback parameter: " + key);
        }
        return value;
    }

    private long positiveLong(String value, String message) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Replaced with the stable API error below.
        }
        throw new IllegalArgumentException(message);
    }

    static boolean validSignature(String secret, String transactionId, String sign) {
        if (secret == null || secret.trim().isEmpty() || transactionId == null
                || transactionId.isEmpty() || sign == null || !sign.matches("(?i)[0-9a-f]{64}")) {
            return false;
        }
        byte[] expected = sha256Hex(secret + ":" + transactionId)
                .getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = sign.toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, supplied);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String auditText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private long addExact(long left, int right) {
        try {
            long result = Math.addExact(left, (long) right);
            if (result > Integer.MAX_VALUE) {
                throw new ArithmeticException("Legacy asset column overflow");
            }
            return result;
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Advertising reward balance is out of range");
        }
    }

    private Map<String, Object> one(Connection connection, String sql, Object... args)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                ResultSetMetaData metadata = result.getMetaData();
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    row.put(metadata.getColumnLabel(index), result.getObject(index));
                }
                return row;
            }
        }
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
                throw new SQLException("Advertising reward insert did not affect one row");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Advertising reward insert returned no key");
                }
                return keys.getLong(1);
            }
        }
    }

    private void bind(PreparedStatement statement, Object... args) throws SQLException {
        for (int index = 0; index < args.length; index++) {
            statement.setObject(index + 1, args[index]);
        }
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

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static final class RewardConfig {
        private final int videoType;
        private final String securityKey;
        private final int dailyLimit;
        private final int award;
        private final boolean banRobots;
        private final int silenceSeconds;

        private RewardConfig(int videoType, String securityKey, int dailyLimit, int award,
                             boolean banRobots, int silenceSeconds) {
            this.videoType = videoType;
            this.securityKey = securityKey;
            this.dailyLimit = Math.max(0, dailyLimit);
            this.award = Math.max(0, award);
            this.banRobots = banRobots;
            this.silenceSeconds = silenceSeconds > 0 ? silenceSeconds : 600;
        }

        private static RewardConfig from(Map<String, Object> row) {
            return new RewardConfig(
                    integer(row.get("adsVideoType")),
                    row.get("adsSecuritykey") == null
                            ? "" : String.valueOf(row.get("adsSecuritykey")),
                    integer(row.get("adsGiftNum")),
                    integer(row.get("adsGiftAward")),
                    integer(row.get("banRobots")) == 1,
                    integer(row.get("silenceTime")));
        }

        private static int integer(Object value) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return value == null ? 0 : Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }
}
