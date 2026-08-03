package cn.lcxqy.starfree.economy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EconomyService {
    private static final int DAILY_EXPERIENCE_LIMIT = 3;

    private final JdbcTemplate jdbc;
    private final EconomyLockExecutor lock;
    private final EconomyOperationJournal journal;

    @Autowired
    public EconomyService(JdbcTemplate jdbc, EconomyLockExecutor lock,
                          EconomyOperationJournal journal) {
        this.jdbc = jdbc;
        this.lock = lock;
        this.journal = journal;
    }

    // Focused rule tests do not execute balance-changing methods.
    EconomyService(JdbcTemplate jdbc) {
        this(jdbc, null, null);
    }

    public EconomyConfig config() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT clock,clockExp,clockPoints,postExp,reviewExp,deleteExp,allowDelete,"
                        + "contentAuditlevel,auditlevel,postMax,forbidden,banRobots,"
                        + "silenceTime,disableCode FROM starfree_apiconfig ORDER BY id LIMIT 1");
        return EconomyConfig.from(rows.isEmpty()
                ? Collections.<String, Object>emptyMap() : rows.get(0));
    }

    public boolean isStaff(String group) {
        return "administrator".equals(group) || "editor".equals(group);
    }

    public void requirePostWithinLimit(long uid, String group) {
        if (isStaff(group)) {
            return;
        }
        int postMax = config().getPostMax();
        if (postMax <= 0) {
            return;
        }
        long since = Instant.now().getEpochSecond() - 86400;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_contents "
                        + "WHERE authorId=? AND created>=? AND type='post'",
                Integer.class, uid, since);
        if (count != null && count >= postMax) {
            throw new IllegalArgumentException(
                    "\u4f60\u5df2\u8d85\u8fc7\u6700\u5927\u53d1\u5e03\u6570\u91cf\u9650\u5236\uff0c\u8bf724\u5c0f\u65f6\u540e\u518d\u64cd\u4f5c");
        }
    }

    public int postsInLastDay(long uid) {
        long since = Instant.now().getEpochSecond() - 86400;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_contents "
                        + "WHERE authorId=? AND created>=? AND type IN ('post','video')",
                Integer.class, uid, since);
        return count == null ? 0 : count;
    }

    public String contentStatus(String group, String title, String text) {
        return contentStatus(config(), group, title, text);
    }

    public String contentStatus(EconomyConfig config, String group, String title, String text) {
        if (containsForbidden(config.getForbidden(), title)) {
            throw new IllegalArgumentException("\u6807\u9898\u5b58\u5728\u8fdd\u7981\u8bcd");
        }
        if (isStaff(group) || config.getContentAuditLevel() == 0) {
            return "publish";
        }
        if (config.getContentAuditLevel() == 1) {
            return containsForbidden(config.getForbidden(), text) ? "waiting" : "publish";
        }
        return "waiting";
    }

    public String commentStatus(long uid, String text) {
        EconomyConfig config = config();
        int level = config.getCommentAuditLevel();
        if (level == 0) {
            return "approved";
        }
        if (level == 1) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM starfree_comments "
                            + "WHERE authorId=? AND status='approved'",
                    Integer.class, uid);
            return count != null && count > 0 ? "approved" : "waiting";
        }
        boolean forbidden = containsForbidden(config.getForbidden(), text);
        if (level == 2) {
            return forbidden ? "waiting" : "approved";
        }
        if (level == 3) {
            if (forbidden) {
                throw new IllegalArgumentException(
                        "\u5b58\u5728\u8fdd\u89c4\u5185\u5bb9\uff0c\u8bc4\u8bba\u53d1\u5e03\u5931\u8d25");
            }
            return "approved";
        }
        return "waiting";
    }

    @Transactional
    public boolean grantPostExperience(long uid) {
        return grantDailyExperience(uid, "postExp", config().getPostExp());
    }

    @Transactional
    public boolean grantCommentExperience(long uid) {
        return grantDailyExperience(uid, "reviewExp", config().getReviewExp());
    }

    @Transactional
    public boolean grantDailyExperience(long uid, String type, int amount) {
        if (amount <= 0) {
            return false;
        }
        int day = currentDay();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_userlog WHERE uid=? AND cid=? AND type=?",
                Integer.class, uid, day, type);
        if (count != null && count >= DAILY_EXPERIENCE_LIMIT) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        jdbc.update("INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) "
                        + "VALUES (?,?,?,?,?,?)",
                uid, day, type, amount, now, 0);
        jdbc.update("UPDATE starfree_users SET experience=COALESCE(experience,0)+? WHERE uid=?",
                amount, uid);
        return true;
    }

    public void deductDeleteExperience(long uid) {
        int amount = config().getDeleteExp();
        if (amount > 0) {
            jdbc.update("UPDATE starfree_users "
                    + "SET experience=COALESCE(experience,0)-? WHERE uid=?", amount, uid);
        }
    }

    /** Legacy Java daily clock: assets, experience, and task points are distinct awards. */
    public Map<String, Object> clock(long uid) {
        return clock(uid, "");
    }

    public Map<String, Object> clock(long uid, String requestId) {
        requireFinancialInfrastructure();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        String operationKey = journal.fixedKey("java-clock-" + today, uid);
        return lock.execute(connection -> clockLocked(connection, uid, operationKey));
    }

    public Map<String, Object> reward(long uid, long cid, int amount) {
        return reward(uid, cid, amount, "");
    }

    public Map<String, Object> reward(long uid, long cid, int amount, String requestId) {
        if (cid <= 0 || amount <= 0) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        requireFinancialInfrastructure();
        String operationKey = journal.requestKey(
                "reward", uid, requestId, cid + ":" + amount);
        return lock.execute(connection -> rewardLocked(
                connection, uid, cid, amount, operationKey));
    }

    public void sendDeletionNotice(long operatorId, long authorId, String text) {
        sendInbox("system", operatorId, authorId, text, 0, 0);
    }

    public void sendInbox(String type, long fromUid, long toUid, String text,
                          long value, long cid) {
        long now = Instant.now().getEpochSecond();
        jdbc.update("INSERT INTO starfree_inbox "
                        + "(type,uid,text,touid,isread,value,created,cid) VALUES (?,?,?,?,?,?,?,?)",
                type, fromUid, text, toUid, 0, value, now, cid);
    }

    private Map<String, Object> clockLocked(Connection connection, long uid,
                                            String operationKey) throws SQLException {
        Map<String, Object> payload = mapOf("uid", uid, "day", currentDay());
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "java-clock", uid, uid, currentDay(), payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }

        long now = Instant.now().getEpochSecond();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long start = today.atStartOfDay(zone).toEpochSecond();
        long end = today.plusDays(1).atStartOfDay(zone).toEpochSecond();
        if (EconomySql.number(connection,
                "SELECT COUNT(*) FROM starfree_userlog "
                        + "WHERE uid=? AND type='clock' AND created>=? AND created<?",
                uid, start, end) > 0) {
            return failOperation(connection, operationKey,
                    "\u4f60\u5df2\u7ecf\u7b7e\u5230\u8fc7\u4e86\u54e6");
        }

        Map<String, Object> configRow = EconomySql.one(connection,
                "SELECT clock,clockExp,clockPoints FROM starfree_apiconfig ORDER BY id LIMIT 1");
        int maximum = integer(configRow, "clock");
        int addExp = integer(configRow, "clockExp");
        int addPoints = integer(configRow, "clockPoints");
        int award = maximum > 0 ? ThreadLocalRandom.current().nextInt(maximum) + 1 : 0;
        if (addExp < 0 || addPoints < 0) {
            return failOperation(connection, operationKey,
                    "\u7b7e\u5230\u5956\u52b1\u914d\u7f6e\u4e0d\u6b63\u786e");
        }

        Map<String, Object> user = EconomySql.one(connection,
                "SELECT COALESCE(assets,0) AS assets,"
                        + "COALESCE(experience,0) AS experience,COALESCE(points,0) AS points "
                        + "FROM starfree_users WHERE uid=? LIMIT 1", uid);
        if (user == null) {
            return failOperation(connection, operationKey, "\u7528\u6237\u4e0d\u5b58\u5728");
        }

        long oldAssets = number(user.get("assets"));
        long oldExperience = number(user.get("experience"));
        long oldPoints = number(user.get("points"));
        long logId = 0;
        long paylogId = 0;
        boolean balanceChanged = false;
        try {
            if (EconomySql.update(connection,
                    "UPDATE starfree_users SET assets=?,experience=?,points=? WHERE uid=?",
                    oldAssets + award, oldExperience + addExp, oldPoints + addPoints, uid) != 1) {
                throw new SQLException("Clock balance update failed");
            }
            balanceChanged = true;
            logId = EconomySql.insertKey(connection,
                    "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) "
                            + "VALUES (?,?,'clock',?,?,?)",
                    uid, currentDay(), award, now, uid);
            paylogId = insertPaylog(connection, uid, "\u7b7e\u5230\u5956\u52b1",
                    String.valueOf(award), operationKey, "clock", now);

            Map<String, Object> result = mapOf(
                    "award", award, "addExp", addExp, "clockPoints", addPoints);
            journal.commit(connection, operationKey, result);
            return result;
        } catch (Exception error) {
            try {
                if (paylogId > 0) {
                    EconomySql.update(connection, "DELETE FROM starfree_paylog WHERE pid=?", paylogId);
                }
                if (logId > 0) {
                    EconomySql.update(connection, "DELETE FROM starfree_userlog WHERE id=?", logId);
                }
                if (balanceChanged) {
                    EconomySql.update(connection,
                            "UPDATE starfree_users SET assets=?,experience=?,points=? WHERE uid=?",
                            oldAssets, oldExperience, oldPoints, uid);
                }
                journal.fail(connection, operationKey, error);
            } catch (Exception compensationError) {
                error.addSuppressed(compensationError);
                journal.needsReview(connection, operationKey, error);
            }
            throw error;
        }
    }

    private Map<String, Object> rewardLocked(Connection connection, long uid, long cid,
                                             int amount, String operationKey) throws SQLException {
        Map<String, Object> payload = mapOf("uid", uid, "cid", cid, "amount", amount);
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "reward", uid, 0, cid, payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }

        Map<String, Object> content = EconomySql.one(connection,
                "SELECT cid,title,authorId FROM starfree_contents WHERE cid=? LIMIT 1", cid);
        if (content == null) {
            return failOperation(connection, operationKey, "\u5185\u5bb9\u4e0d\u5b58\u5728");
        }
        long authorId = number(content.get("authorId"));
        if (uid == authorId) {
            return failOperation(connection, operationKey,
                    "\u4f60\u4e0d\u53ef\u4ee5\u6253\u8d4f\u81ea\u5df1\u7684\u4f5c\u54c1\uff01");
        }
        Map<String, Object> sender = EconomySql.one(connection,
                "SELECT COALESCE(assets,0) AS assets FROM starfree_users WHERE uid=? LIMIT 1", uid);
        Map<String, Object> author = EconomySql.one(connection,
                "SELECT COALESCE(assets,0) AS assets FROM starfree_users WHERE uid=? LIMIT 1",
                authorId);
        if (sender == null || number(sender.get("assets")) < amount) {
            return failOperation(connection, operationKey, "\u79ef\u5206\u4e0d\u8db3\uff01");
        }
        if (author == null) {
            return failOperation(connection, operationKey, "\u4f5c\u54c1\u4f5c\u8005\u4e0d\u5b58\u5728");
        }

        long senderAssets = number(sender.get("assets"));
        long authorAssets = number(author.get("assets"));
        long debitPaylog = 0;
        long creditPaylog = 0;
        long inboxId = 0;
        long userlogId = 0;
        boolean senderChanged = false;
        boolean authorChanged = false;
        try {
            senderChanged = EconomySql.update(connection,
                    "UPDATE starfree_users SET assets=? WHERE uid=?",
                    senderAssets - amount, uid) == 1;
            if (!senderChanged) {
                throw new SQLException("Reward sender debit failed");
            }
            authorChanged = EconomySql.update(connection,
                    "UPDATE starfree_users SET assets=? WHERE uid=?",
                    authorAssets + amount, authorId) == 1;
            if (!authorChanged) {
                throw new SQLException("Reward author credit failed");
            }
            long now = Instant.now().getEpochSecond();
            debitPaylog = insertPaylog(connection, uid, "\u6253\u8d4f\u4f5c\u54c1",
                    "-" + amount, operationKey + ":debit", "toReward", now);
            creditPaylog = insertPaylog(connection, authorId,
                    "\u6765\u81ea\u7528\u6237ID" + uid + "\u6253\u8d4f",
                    String.valueOf(amount), operationKey + ":credit", "reward", now);
            inboxId = EconomySql.insertKey(connection,
                    "INSERT INTO starfree_inbox "
                            + "(type,uid,text,touid,isread,value,created,cid) "
                            + "VALUES ('finance',?,?,?,0,?,?,0)",
                    uid, "\u6253\u8d4f\u4e86\u4f60\u7684\u6587\u7ae0\u3010"
                            + String.valueOf(content.get("title")) + "\u3011",
                    authorId, cid, now);
            userlogId = EconomySql.insertKey(connection,
                    "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) "
                            + "VALUES (?,?,'reward',?,?,?)",
                    uid, cid, amount, now, authorId);

            Map<String, Object> result = mapOf(
                    "cid", cid, "type", "reward", "num", amount, "toid", authorId);
            journal.commit(connection, operationKey, result);
            return result;
        } catch (Exception error) {
            try {
                if (userlogId > 0) {
                    EconomySql.update(connection, "DELETE FROM starfree_userlog WHERE id=?", userlogId);
                }
                if (inboxId > 0) {
                    EconomySql.update(connection, "DELETE FROM starfree_inbox WHERE id=?", inboxId);
                }
                if (creditPaylog > 0) {
                    EconomySql.update(connection, "DELETE FROM starfree_paylog WHERE pid=?", creditPaylog);
                }
                if (debitPaylog > 0) {
                    EconomySql.update(connection, "DELETE FROM starfree_paylog WHERE pid=?", debitPaylog);
                }
                if (authorChanged) {
                    EconomySql.update(connection,
                            "UPDATE starfree_users SET assets=? WHERE uid=?", authorAssets, authorId);
                }
                if (senderChanged) {
                    EconomySql.update(connection,
                            "UPDATE starfree_users SET assets=? WHERE uid=?", senderAssets, uid);
                }
                journal.fail(connection, operationKey, error);
            } catch (Exception compensationError) {
                error.addSuppressed(compensationError);
                journal.needsReview(connection, operationKey, error);
            }
            throw error;
        }
    }

    private long insertPaylog(Connection connection, long uid, String subject, String amount,
                              String tradeNumber, String payType, long created) throws SQLException {
        return EconomySql.insertKey(connection,
                "INSERT INTO starfree_paylog "
                        + "(subject,total_amount,out_trade_no,trade_no,paytype,uid,created,status) "
                        + "VALUES (?,?,?,'',?,?,?,1)",
                subject, amount, tradeNumber, payType, uid, created);
    }

    private Map<String, Object> failOperation(Connection connection, String operationKey,
                                              String message) throws SQLException {
        IllegalArgumentException error = new IllegalArgumentException(message);
        journal.fail(connection, operationKey, error);
        throw error;
    }

    private boolean containsForbidden(String forbidden, String text) {
        if (forbidden == null || forbidden.trim().isEmpty()
                || text == null || text.isEmpty()) {
            return false;
        }
        String[] words = forbidden.split("[,|\\r\\n]+");
        for (String word : words) {
            String value = word.trim();
            if (!value.isEmpty() && text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private int currentDay() {
        return Integer.parseInt(DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now()));
    }

    private int integer(Map<String, Object> row, String key) {
        return row == null ? 0 : (int) number(row.get(key));
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

    private void requireFinancialInfrastructure() {
        if (lock == null || journal == null) {
            throw new IllegalStateException("Economy lock and journal are required");
        }
    }
}
