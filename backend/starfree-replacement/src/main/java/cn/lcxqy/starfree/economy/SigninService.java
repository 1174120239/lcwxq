package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.LegacyTokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rebuild of the PHP seven-day sign-in feature used by pages/user/userexp.vue. */
@Service
public class SigninService {
    private final JdbcTemplate jdbc;
    private final LegacyTokenService tokens;
    private final EconomyLockExecutor lock;
    private final EconomyOperationJournal journal;

    public SigninService(JdbcTemplate jdbc, LegacyTokenService tokens,
                         EconomyLockExecutor lock, EconomyOperationJournal journal) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.lock = lock;
        this.journal = journal;
    }

    public Map<String, Object> config() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT Signinasset1,Signinasset2,Signinasset3,Signinasset4,"
                        + "Signinasset5,Signinasset6,Signinasset7,Signinexp1,Signinexp2,"
                        + "Signinexp3,Signinexp4,Signinexp5,Signinexp6,Signinexp7 "
                        + "FROM starfree_admin_functions ORDER BY id LIMIT 1");
        Map<String, Object> row = rows.isEmpty()
                ? new LinkedHashMap<String, Object>() : rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        for (int day = 1; day <= 7; day++) {
            result.put("assets_" + day + "day", integer(row, "Signinasset" + day));
        }
        for (int day = 1; day <= 7; day++) {
            result.put("experience_" + day + "day", integer(row, "Signinexp" + day));
        }
        return result;
    }

    public Map<String, Object> streak(Map<String, String> request) {
        long uid = requireUser(request);
        Integer value = jdbc.query(
                "SELECT continuous FROM starfree_admin_Signinlog "
                        + "WHERE uid=? ORDER BY time DESC,id DESC LIMIT 1",
                new Object[]{uid}, result -> result.next() ? result.getInt(1) : 0);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("leiji", value == null ? 0 : value);
        return response;
    }

    public Map<String, Object> signin(Map<String, String> request) {
        final long uid = requireUser(request);
        final LocalDate today = LocalDate.now();
        final String operationKey = journal.fixedKey("seven-day-signin-" + today, uid);
        return lock.execute(connection -> signinLocked(connection, uid, today, operationKey));
    }

    private Map<String, Object> signinLocked(Connection connection, long uid,
                                             LocalDate today, String operationKey)
            throws SQLException {
        Map<String, Object> payload = mapOf("uid", uid, "date", today.toString());
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "seven-day-signin", uid, uid, Integer.parseInt(today.toString().replace("-", "")),
                payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }

        Map<String, Object> last = EconomySql.one(connection,
                "SELECT continuous,DATE(time) AS signDate FROM starfree_admin_Signinlog "
                        + "WHERE uid=? ORDER BY time DESC,id DESC LIMIT 1", uid);
        LocalDate lastDate = sqlDate(last == null ? null : last.get("signDate"));
        if (today.equals(lastDate)) {
            return fail(connection, operationKey,
                    "\u4eca\u5929\u5df2\u7ecf\u7b7e\u5230\u8fc7\u4e86");
        }
        int continuous = today.minusDays(1).equals(lastDate)
                ? (int) number(last.get("continuous")) + 1 : 1;
        if (continuous > 7) {
            continuous = 1;
        }

        Map<String, Object> config = EconomySql.one(connection,
                "SELECT Signinasset1,Signinasset2,Signinasset3,Signinasset4,"
                        + "Signinasset5,Signinasset6,Signinasset7,Signinexp1,Signinexp2,"
                        + "Signinexp3,Signinexp4,Signinexp5,Signinexp6,Signinexp7 "
                        + "FROM starfree_admin_functions ORDER BY id LIMIT 1");
        int assetsAward = integer(config, "Signinasset" + continuous);
        int experienceAward = integer(config, "Signinexp" + continuous);
        if (assetsAward < 0 || experienceAward < 0) {
            return fail(connection, operationKey,
                    "\u7b7e\u5230\u5956\u52b1\u914d\u7f6e\u4e0d\u6b63\u786e");
        }

        Map<String, Object> user = EconomySql.one(connection,
                "SELECT COALESCE(assets,0) AS assets,COALESCE(experience,0) AS experience "
                        + "FROM starfree_users WHERE uid=? LIMIT 1", uid);
        if (user == null) {
            return fail(connection, operationKey, "\u7528\u6237\u4e0d\u5b58\u5728");
        }
        long oldAssets = number(user.get("assets"));
        long oldExperience = number(user.get("experience"));
        long signinId = 0;
        long paylogId = 0;
        boolean balanceChanged = false;
        try {
            if (EconomySql.update(connection,
                    "UPDATE starfree_users SET assets=?,experience=? WHERE uid=?",
                    oldAssets + assetsAward, oldExperience + experienceAward, uid) != 1) {
                throw new SQLException("Seven-day sign-in balance update failed");
            }
            balanceChanged = true;
            signinId = EconomySql.insertKey(connection,
                    "INSERT INTO starfree_admin_Signinlog "
                            + "(uid,time,continuous,assets,exp) VALUES (?,NOW(),?,?,?)",
                    uid, continuous, assetsAward, experienceAward);
            paylogId = EconomySql.insertKey(connection,
                    "INSERT INTO starfree_paylog "
                            + "(subject,total_amount,out_trade_no,trade_no,paytype,uid,created,status) "
                            + "VALUES (?,?,?,'','Signin',?,?,1)",
                    "\u8fde\u7eed\u7b7e\u5230" + continuous + "\u5929",
                    String.valueOf(assetsAward), operationKey, uid,
                    Instant.now().getEpochSecond());

            Map<String, Object> result = mapOf(
                    "continuous", continuous, "assets", assetsAward,
                    "experience", experienceAward);
            journal.commit(connection, operationKey, result);
            return result;
        } catch (Exception error) {
            try {
                if (paylogId > 0) {
                    EconomySql.update(connection, "DELETE FROM starfree_paylog WHERE pid=?", paylogId);
                }
                if (signinId > 0) {
                    EconomySql.update(connection,
                            "DELETE FROM starfree_admin_Signinlog WHERE id=?", signinId);
                }
                if (balanceChanged) {
                    EconomySql.update(connection,
                            "UPDATE starfree_users SET assets=?,experience=? WHERE uid=?",
                            oldAssets, oldExperience, uid);
                }
                journal.fail(connection, operationKey, error);
            } catch (Exception compensationError) {
                error.addSuppressed(compensationError);
                journal.needsReview(connection, operationKey, error);
            }
            throw error;
        }
    }

    private long requireUser(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null || tokens.userById(uid) == null) {
            throw new IllegalArgumentException(
                    "\u7528\u6237\u672a\u767b\u5f55\u6216Token\u9a8c\u8bc1\u5931\u8d25");
        }
        return uid;
    }

    private Map<String, Object> fail(Connection connection, String operationKey,
                                     String message) throws SQLException {
        IllegalArgumentException error = new IllegalArgumentException(message);
        journal.fail(connection, operationKey, error);
        throw error;
    }

    private LocalDate sqlDate(Object value) {
        if (value instanceof Date) {
            return ((Date) value).toLocalDate();
        }
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int integer(Map<String, Object> row, String key) {
        if (row == null) {
            return 0;
        }
        Object value = row.get(key);
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (key.equalsIgnoreCase(entry.getKey())) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return (int) number(value);
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
}
