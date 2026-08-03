package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EconomyAccountService {
    private static final Logger LOG = LoggerFactory.getLogger(EconomyAccountService.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LegacyTokenService tokens;
    private final EconomyLockExecutor lock;
    private final EconomyOperationJournal journal;

    public EconomyAccountService(JdbcTemplate jdbc, ObjectMapper mapper,
                                 LegacyTokenService tokens, EconomyLockExecutor lock,
                                 EconomyOperationJournal journal) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tokens = tokens;
        this.lock = lock;
        this.journal = journal;
    }

    /** Editor/admin manual credit or debit; rechargeType=0 is assets, 1 is points. */
    public Map<String, Object> adjust(Map<String, String> request) {
        User actor = requireStaff(request, false);
        final long targetUid = RequestValues.integer(request, "key", 0);
        final int amount = RequestValues.integer(request, "num", 0);
        final int direction = RequestValues.integer(request, "type", -1);
        final int balanceType = RequestValues.integer(request, "rechargeType", 0);
        if (targetUid <= 0 || amount <= 0 || (direction != 0 && direction != 1)
                || (balanceType != 0 && balanceType != 1)) {
            throw new IllegalArgumentException("\u91d1\u989d\u6216\u53c2\u6570\u4e0d\u6b63\u786e");
        }

        String fingerprint = targetUid + ":" + amount + ":" + direction + ":" + balanceType;
        final String operationKey = journal.requestKey("manual-adjust", actor.uid,
                RequestValues.text(request, "requestId"), fingerprint);
        final Map<String, Object> payload = mapOf(
                "targetUid", targetUid, "amount", amount,
                "direction", direction, "balanceType", balanceType);

        return lock.execute(connection -> adjustLocked(connection, actor.uid, targetUid,
                amount, direction, balanceType, operationKey, payload));
    }

    public Map<String, Object> requestWithdrawal(Map<String, String> request) {
        final User actor = requireUser(request);
        final int amount = RequestValues.integer(request, "num", 0);
        if (amount <= 0) {
            throw new IllegalArgumentException("\u53c2\u6570\u9519\u8bef");
        }
        final String operationKey = journal.requestKey("withdraw-request", actor.uid,
                RequestValues.text(request, "requestId"), String.valueOf(amount));
        final Map<String, Object> payload = mapOf("uid", actor.uid, "amount", amount);
        return lock.execute(connection -> requestWithdrawalLocked(
                connection, actor.uid, amount, operationKey, payload));
    }

    public Page withdrawalList(Map<String, String> request) {
        User actor = requireUser(request);
        Map<String, Object> filters = RequestValues.jsonObject(
                mapper, RequestValues.text(request, "searchParams"));
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        int limit = bounded(RequestValues.integer(request, "limit", 15));

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE l.type='withdraw'");
        if (!actor.administrator) {
            where.append(" AND l.uid=?");
            args.add(actor.uid);
        } else if (filters.containsKey("uid")) {
            int uid = RequestValues.objectInteger(filters, "uid", 0);
            if (uid > 0) {
                where.append(" AND l.uid=?");
                args.add(uid);
            }
        }
        if (filters.containsKey("cid")) {
            where.append(" AND l.cid=?");
            args.add(RequestValues.objectInteger(filters, "cid", -1));
        }

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_userlog l" + where,
                Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((page - 1) * limit);
        pageArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT l.id,l.uid,l.cid,l.type,l.num,l.created,l.toid,u.pay "
                        + "FROM starfree_userlog l LEFT JOIN starfree_users u ON u.uid=l.uid"
                        + where + " ORDER BY l.created DESC,l.id DESC LIMIT ?,?",
                pageArgs.toArray());
        return new Page(rows, total == null ? 0 : total);
    }

    public Map<String, Object> reviewWithdrawal(Map<String, String> request) {
        final User actor = requireStaff(request, true);
        final long logId = RequestValues.integer(request, "key", 0);
        final int action = RequestValues.integer(request, "type", -1);
        if (logId <= 0 || (action != 0 && action != 1)) {
            throw new IllegalArgumentException("\u53c2\u6570\u9519\u8bef");
        }
        final String operationKey = journal.fixedKey("withdraw-review-" + action, logId);
        final Map<String, Object> payload = mapOf(
                "logId", logId, "action", action, "reviewerUid", actor.uid);
        return lock.execute(connection -> reviewWithdrawalLocked(
                connection, actor.uid, logId, action, operationKey, payload));
    }

    public Map<String, Object> payOrderList(Map<String, String> request) {
        User actor = requireUser(request);
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_paylog WHERE uid=?", Integer.class, actor.uid);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT pid,subject,total_amount AS totalAmount,out_trade_no AS outTradeNo,"
                        + "trade_no AS tradeNo,paytype,uid,created,status "
                        + "FROM starfree_paylog WHERE uid=? ORDER BY created DESC,pid DESC LIMIT 30",
                actor.uid);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 1);
        response.put("msg", "");
        response.put("paydata", rows);
        response.put("count", rows.size());
        response.put("total", total == null ? 0 : total);
        return response;
    }

    public Page financeList(Map<String, String> request) {
        requireStaff(request, true);
        Map<String, Object> filters = RequestValues.jsonObject(
                mapper, RequestValues.text(request, "searchParams"));
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        int limit = bounded(RequestValues.integer(request, "limit", 15));

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        appendInteger(where, args, filters, "uid", "uid");
        appendInteger(where, args, filters, "status", "status");
        appendText(where, args, filters, "paytype", "paytype");

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_paylog" + where,
                Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((page - 1) * limit);
        pageArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT pid,subject,total_amount AS totalAmount,out_trade_no AS outTradeNo,"
                        + "trade_no AS tradeNo,paytype,uid,created,status "
                        + "FROM starfree_paylog" + where
                        + " ORDER BY created DESC,pid DESC LIMIT ?,?",
                pageArgs.toArray());
        return new Page(rows, total == null ? 0 : total);
    }

    public Map<String, Object> financeTotal(Map<String, String> request) {
        requireStaff(request, true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recharge", sum(
                "status=1 AND CAST(total_amount AS SIGNED)>0 AND ("
                        + "paytype IN ('scancodePay','WxPay','tokenPay') OR paytype LIKE 'ePay_%' "
                        + "OR (paytype='system' AND subject LIKE '\u7cfb\u7edf\u5145\u503c%') "
                        + "OR subject IN ('\u626b\u7801\u652f\u4ed8','\u5fae\u4fe1APP\u652f\u4ed8','\u5361\u5bc6\u5145\u503c','\u7cfb\u7edf\u5145\u503c'))"));
        result.put("trade", -sum(
                "status=1 AND paytype IN ('buyshop','buyvip','toReward','buyAds','ads','adsRenewal')"));
        result.put("withdraw", -sum(
                "status=1 AND (paytype='withdraw' OR (paytype='system' "
                        + "AND CAST(total_amount AS SIGNED)<0))"));
        result.put("income", sum(
                "status=1 AND paytype IN ('clock','Signin','sellshop','reward','adsGift','rebate')"));
        return result;
    }

    private Map<String, Object> adjustLocked(Connection connection, long actorUid,
                                              long targetUid, int amount, int direction,
                                              int balanceType, String operationKey,
                                              Map<String, Object> payload) throws SQLException {
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "manual-adjust", actorUid, targetUid, targetUid, payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }

        String column = balanceType == 0 ? "assets" : "points";
        Map<String, Object> user = EconomySql.one(connection,
                "SELECT uid,COALESCE(" + column + ",0) AS balance FROM starfree_users "
                        + "WHERE uid=? LIMIT 1", targetUid);
        if (user == null) {
            return failBusiness(connection, operationKey,
                    "\u7528\u6237\u4e0d\u5b58\u5728");
        }
        long oldBalance = number(user.get("balance"));
        long delta = direction == 0 ? amount : -((long) amount);
        long nextBalance = oldBalance + delta;
        if (nextBalance < 0 || nextBalance > Integer.MAX_VALUE) {
            return failBusiness(connection, operationKey,
                    "\u8c03\u6574\u540e\u4f59\u989d\u4e0d\u5408\u6cd5");
        }

        boolean balanceChanged = false;
        boolean paylogWritten = false;
        try {
            if (EconomySql.update(connection,
                    "UPDATE starfree_users SET " + column + "=? WHERE uid=?",
                    nextBalance, targetUid) != 1) {
                throw new SQLException("Manual adjustment did not update one user");
            }
            balanceChanged = true;
            insertPaylog(connection, targetUid, adjustmentSubject(balanceType, direction),
                    signed(delta), operationKey, "system", now());
            paylogWritten = true;

            Map<String, Object> result = mapOf("rows", 1, "balance", nextBalance,
                    "balanceType", balanceType);
            journal.commit(connection, operationKey, result);
            return result;
        } catch (Exception error) {
            compensateBalanceAndPaylog(connection, operationKey, targetUid, column,
                    oldBalance, balanceChanged, paylogWritten, error);
            throw error;
        }
    }

    private Map<String, Object> requestWithdrawalLocked(Connection connection, long uid,
                                                        int amount, String operationKey,
                                                        Map<String, Object> payload)
            throws SQLException {
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "withdraw-request", uid, uid, 0, payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }
        Map<String, Object> user = EconomySql.one(connection,
                "SELECT COALESCE(assets,0) AS assets,pay FROM starfree_users WHERE uid=? LIMIT 1",
                uid);
        if (user == null) {
            return failBusiness(connection, operationKey, "\u7528\u6237\u4e0d\u5b58\u5728");
        }
        String pay = user.get("pay") == null ? "" : String.valueOf(user.get("pay")).trim();
        if (pay.isEmpty()) {
            return failBusiness(connection, operationKey,
                    "\u8bf7\u5148\u8bbe\u7f6e\u6536\u6b3e\u4fe1\u606f");
        }
        if (number(user.get("assets")) < amount) {
            return failBusiness(connection, operationKey, "\u4f60\u7684\u4f59\u989d\u4e0d\u8db3");
        }
        if (EconomySql.number(connection,
                "SELECT COUNT(*) FROM starfree_userlog "
                        + "WHERE uid=? AND type='withdraw' AND cid=-1", uid) > 0) {
            return failBusiness(connection, operationKey,
                    "\u60a8\u6709\u6b63\u5728\u5ba1\u6838\u7684\u7533\u8bf7");
        }

        try {
            long logId = EconomySql.insertKey(connection,
                    "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) "
                            + "VALUES (?,-1,'withdraw',?,?,?)",
                    uid, amount, now(), uid);
            Map<String, Object> result = mapOf("rows", 1, "id", logId, "status", -1);
            try {
                journal.commit(connection, operationKey, result);
            } catch (SQLException commitError) {
                journal.needsReview(connection, operationKey, commitError);
                throw commitError;
            }
            return result;
        } catch (Exception error) {
            // A generated log id is only available after insertion. A journal commit
            // failure is left for review rather than deleting a possibly accepted request.
            if (!(error instanceof SQLException
                    && error.getMessage() != null
                    && error.getMessage().contains("journal commit"))) {
                journal.fail(connection, operationKey, error);
            }
            throw error;
        }
    }

    private Map<String, Object> reviewWithdrawalLocked(Connection connection, long reviewerUid,
                                                       long logId, int action,
                                                       String operationKey,
                                                       Map<String, Object> payload)
            throws SQLException {
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "withdraw-review", reviewerUid, 0, logId, payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }
        Map<String, Object> withdrawal = EconomySql.one(connection,
                "SELECT id,uid,cid,num FROM starfree_userlog "
                        + "WHERE id=? AND type='withdraw' LIMIT 1", logId);
        if (withdrawal == null || number(withdrawal.get("cid")) != -1) {
            return failBusiness(connection, operationKey,
                    "\u8be5\u63d0\u73b0\u7533\u8bf7\u5df2\u5904\u7406\u6216\u4e0d\u5b58\u5728");
        }
        long uid = number(withdrawal.get("uid"));
        int amount = (int) number(withdrawal.get("num"));
        if (amount <= 0) {
            return failBusiness(connection, operationKey, "\u63d0\u73b0\u91d1\u989d\u5f02\u5e38");
        }

        if (action == 0) {
            try {
                if (EconomySql.update(connection,
                        "UPDATE starfree_userlog SET cid=-2 WHERE id=? AND cid=-1", logId) != 1) {
                    throw new SQLException("Withdrawal rejection did not update one row");
                }
                Map<String, Object> result = mapOf("rows", 1, "id", logId, "status", -2);
                journal.commit(connection, operationKey, result);
                return result;
            } catch (Exception error) {
                EconomySql.update(connection,
                        "UPDATE starfree_userlog SET cid=-1 WHERE id=? AND cid=-2", logId);
                journal.fail(connection, operationKey, error);
                throw error;
            }
        }

        Map<String, Object> user = EconomySql.one(connection,
                "SELECT COALESCE(assets,0) AS assets FROM starfree_users WHERE uid=? LIMIT 1", uid);
        if (user == null || number(user.get("assets")) < amount) {
            return failBusiness(connection, operationKey,
                    "\u8be5\u7528\u6237\u8d44\u4ea7\u5df2\u4e0d\u8db3\u4ee5\u7528\u4e8e\u63d0\u73b0\uff01");
        }
        long oldAssets = number(user.get("assets"));
        boolean balanceChanged = false;
        boolean statusChanged = false;
        boolean paylogWritten = false;
        try {
            if (EconomySql.update(connection,
                    "UPDATE starfree_users SET assets=? WHERE uid=?", oldAssets - amount, uid) != 1) {
                throw new SQLException("Withdrawal debit did not update one user");
            }
            balanceChanged = true;
            if (EconomySql.update(connection,
                    "UPDATE starfree_userlog SET cid=0 WHERE id=? AND cid=-1", logId) != 1) {
                throw new SQLException("Withdrawal approval did not update one request");
            }
            statusChanged = true;
            insertPaylog(connection, uid, "\u7533\u8bf7\u63d0\u73b0", "-" + amount,
                    operationKey, "withdraw", now());
            paylogWritten = true;

            Map<String, Object> result = mapOf("rows", 1, "id", logId,
                    "status", 0, "balance", oldAssets - amount);
            journal.commit(connection, operationKey, result);
            try {
                EconomySql.update(connection,
                        "INSERT INTO starfree_inbox "
                                + "(type,uid,text,touid,isread,value,created,cid) "
                                + "VALUES ('finance',?,? ,?,0,0,?,0)",
                        reviewerUid, "\u4f60\u7684\u63d0\u73b0\u5ba1\u6838\u5df2\u7ecf\u5ba1\u6838\u901a\u8fc7",
                        uid, now());
            } catch (SQLException noticeError) {
                // The committed journal and paylog are authoritative; a notification
                // failure must not make the client retry an already approved withdrawal.
                LOG.error("Could not write withdrawal approval notice for operation {}",
                        operationKey, noticeError);
            }
            return result;
        } catch (Exception error) {
            try {
                if (paylogWritten) {
                    EconomySql.update(connection,
                            "DELETE FROM starfree_paylog WHERE out_trade_no=? AND paytype='withdraw'",
                            operationKey);
                }
                if (statusChanged) {
                    EconomySql.update(connection,
                            "UPDATE starfree_userlog SET cid=-1 WHERE id=? AND cid=0", logId);
                }
                if (balanceChanged) {
                    EconomySql.update(connection,
                            "UPDATE starfree_users SET assets=? WHERE uid=?", oldAssets, uid);
                }
                journal.fail(connection, operationKey, error);
            } catch (Exception compensationError) {
                error.addSuppressed(compensationError);
                journal.needsReview(connection, operationKey, error);
            }
            throw error;
        }
    }

    private void compensateBalanceAndPaylog(Connection connection, String operationKey,
                                            long uid, String column, long oldBalance,
                                            boolean balanceChanged, boolean paylogWritten,
                                            Exception original) throws SQLException {
        try {
            if (paylogWritten) {
                EconomySql.update(connection,
                        "DELETE FROM starfree_paylog WHERE out_trade_no=?", operationKey);
            }
            if (balanceChanged) {
                EconomySql.update(connection,
                        "UPDATE starfree_users SET " + column + "=? WHERE uid=?", oldBalance, uid);
            }
            journal.fail(connection, operationKey, original);
        } catch (Exception compensationError) {
            original.addSuppressed(compensationError);
            journal.needsReview(connection, operationKey, original);
        }
    }

    private Map<String, Object> failBusiness(Connection connection, String operationKey,
                                             String message) throws SQLException {
        IllegalArgumentException error = new IllegalArgumentException(message);
        journal.fail(connection, operationKey, error);
        throw error;
    }

    private User requireUser(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        Map<String, Object> row = uid == null ? null : tokens.userById(uid);
        if (uid == null || row == null) {
            throw new IllegalArgumentException(
                    "\u7528\u6237\u672a\u767b\u5f55\u6216Token\u9a8c\u8bc1\u5931\u8d25");
        }
        String group = String.valueOf(row.get("group"));
        return new User(uid, "administrator".equals(group), "editor".equals(group));
    }

    private User requireStaff(Map<String, String> request, boolean administratorOnly) {
        User user = requireUser(request);
        boolean allowed = user.administrator || (!administratorOnly && user.editor);
        if (!allowed) {
            throw new IllegalArgumentException("\u4f60\u6ca1\u6709\u64cd\u4f5c\u6743\u9650");
        }
        return user;
    }

    private void insertPaylog(Connection connection, long uid, String subject, String amount,
                              String tradeNumber, String payType, long created) throws SQLException {
        EconomySql.update(connection,
                "INSERT INTO starfree_paylog "
                        + "(subject,total_amount,out_trade_no,trade_no,paytype,uid,created,status) "
                        + "VALUES (?,?,?,'',?,?,?,1)",
                subject, amount, tradeNumber, payType, uid, created);
    }

    private String adjustmentSubject(int balanceType, int direction) {
        if (balanceType == 0) {
            return direction == 0 ? "\u7cfb\u7edf\u5145\u503c\u8d44\u4ea7"
                    : "\u7cfb\u7edf\u6263\u9664\u8d44\u4ea7";
        }
        return direction == 0 ? "\u7cfb\u7edf\u5145\u503c\u79ef\u5206"
                : "\u7cfb\u7edf\u6263\u9664\u79ef\u5206";
    }

    private String signed(long delta) {
        return delta > 0 ? String.valueOf(delta) : "-" + Math.abs(delta);
    }

    private long sum(String predicate) {
        Long value = jdbc.queryForObject(
                "SELECT COALESCE(SUM(CAST(total_amount AS SIGNED)),0) "
                        + "FROM starfree_paylog WHERE " + predicate,
                Long.class);
        return value == null ? 0 : value;
    }

    private void appendInteger(StringBuilder where, List<Object> args,
                               Map<String, Object> filters, String key, String column) {
        if (filters.containsKey(key)) {
            where.append(" AND ").append(column).append("=?");
            args.add(RequestValues.objectInteger(filters, key, 0));
        }
    }

    private void appendText(StringBuilder where, List<Object> args,
                            Map<String, Object> filters, String key, String column) {
        String value = RequestValues.objectText(filters, key);
        if (!value.isEmpty()) {
            where.append(" AND ").append(column).append("=?");
            args.add(value);
        }
    }

    private int bounded(int requested) {
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }

    private long now() {
        return Instant.now().getEpochSecond();
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

    private static final class User {
        private final long uid;
        private final boolean administrator;
        private final boolean editor;

        private User(long uid, boolean administrator, boolean editor) {
            this.uid = uid;
            this.administrator = administrator;
            this.editor = editor;
        }
    }

    public static final class Page {
        private final List<Map<String, Object>> data;
        private final int total;

        public Page(List<Map<String, Object>> data, int total) {
            this.data = data == null
                    ? Collections.<Map<String, Object>>emptyList() : data;
            this.total = total;
        }

        public List<Map<String, Object>> getData() {
            return data;
        }

        public int getTotal() {
            return total;
        }
    }
}
