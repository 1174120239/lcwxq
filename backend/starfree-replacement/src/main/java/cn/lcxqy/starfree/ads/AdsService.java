package cn.lcxqy.starfree.ads;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.economy.EconomyOperationJournal;
import cn.lcxqy.starfree.economy.EconomyService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdsService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PURCHASE_DAYS = 3650;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LegacyTokenService tokens;
    private final EconomyService economy;
    private final EconomyLockExecutor lock;
    private final EconomyOperationJournal journal;

    @Autowired
    public AdsService(JdbcTemplate jdbc, ObjectMapper mapper, LegacyTokenService tokens,
                      EconomyService economy, EconomyLockExecutor lock,
                      EconomyOperationJournal journal) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tokens = tokens;
        this.economy = economy;
        this.lock = lock;
        this.journal = journal;
    }

    /** Retained for read-only unit tests that do not execute a balance operation. */
    AdsService(JdbcTemplate jdbc, ObjectMapper mapper, LegacyTokenService tokens,
               EconomyService economy) {
        this(jdbc, mapper, tokens, economy, null, null);
    }

    public Map<String, Object> config() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT pushAdsPrice,pushAdsNum,bannerAdsPrice,bannerAdsNum,startAdsPrice,startAdsNum "
                        + "FROM starfree_apiconfig ORDER BY id LIMIT 1");
        Map<String, Object> source = rows.isEmpty()
                ? new LinkedHashMap<String, Object>() : rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        putNumber(result, source, "startAdsNum");
        putNumber(result, source, "startAdsPrice");
        putNumber(result, source, "pushAdsNum");
        putNumber(result, source, "bannerAdsPrice");
        putNumber(result, source, "bannerAdsNum");
        putNumber(result, source, "pushAdsPrice");
        return result;
    }

    public AdsPage page(String searchParams, int limit, int page, String searchKey, String token) {
        Map<String, Object> filters = RequestValues.jsonObject(mapper, searchParams);
        Long uid = tokens.userId(token);
        Map<String, Object> currentUser = uid == null ? null : tokens.userById(uid);
        boolean staff = isStaff(currentUser);

        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        int safePage = Math.max(1, page);
        long now = Instant.now().getEpochSecond();
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        if (uid == null) {
            // Anonymous clients may only see currently active ads, regardless of supplied filters.
            where.append(" AND `status` = 1 AND `close` >= ?");
            args.add(now);
        } else if (filters.containsKey("status")) {
            int status = RequestValues.objectInteger(filters, "status", -1);
            if (status == 2) {
                // Legacy rows can pass their close time without a background job changing status.
                where.append(" AND (`status` = 2 OR `close` < ?)");
                args.add(now);
            } else if (status == 1) {
                where.append(" AND `status` = 1 AND `close` >= ?");
                args.add(now);
            } else {
                where.append(" AND `status` = ?");
                args.add(status);
            }
        }

        if (filters.containsKey("uid")) {
            long requestedUid = RequestValues.objectInteger(filters, "uid", 0);
            if (requestedUid > 0) {
                if (uid == null) {
                    throw new IllegalArgumentException("用户未登录或Token验证失败");
                }
                if (!staff && requestedUid != uid) {
                    throw new IllegalArgumentException("没有查看权限");
                }
                where.append(" AND uid = ?");
                args.add(requestedUid);
            }
        } else if (uid != null && !staff) {
            // "My ads" must never leak rows owned by another user.
            where.append(" AND uid = ?");
            args.add(uid);
        }

        appendIntegerFilter(where, args, filters, "type", "type");
        appendIntegerFilter(where, args, filters, "aid", "aid");
        if (searchKey != null && !searchKey.trim().isEmpty()) {
            where.append(" AND name LIKE ?");
            args.add("%" + searchKey.trim() + "%");
        }

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_ads" + where, Integer.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add((safePage - 1) * safeLimit);
        rowArgs.add(safeLimit);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT aid,name,type,img,`close`,created,price,intro,urltype,url,uid,status "
                        + "FROM starfree_ads" + where
                        + " ORDER BY created DESC,aid DESC LIMIT ?, ?",
                rowArgs.toArray());

        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            data.add(toAd(row));
        }
        return new AdsPage(data, total == null ? 0 : total);
    }

    public Map<String, Object> info(long aid, String token) {
        Map<String, Object> ad = requireAd(aid);
        Long uid = tokens.userId(token);
        Map<String, Object> user = uid == null ? null : tokens.userById(uid);
        if (!canManage(uid, user, ad) && number(ad.get("status")) != 1) {
            throw new IllegalArgumentException("广告不存在或没有查看权限");
        }
        return toAd(ad);
    }

    public Map<String, Object> add(Map<String, String> request) {
        final long uid = requireUser(request);
        final Map<String, Object> user = tokens.userById(uid);
        final Map<String, Object> params = RequestValues.jsonObject(
                mapper, RequestValues.text(request, "params"));
        final int type = validateType(RequestValues.objectInteger(params, "type", -1));
        final int days = validateDays(RequestValues.integer(request, "day", 0));
        validateAdFields(params);
        requireFinancialInfrastructure();

        final String operationKey = journal.requestKey("ad-buy", uid,
                RequestValues.text(request, "requestId"),
                days + ":" + type + ":" + RequestValues.text(request, "params"));
        final Map<String, Object> payload = mapOf(
                "uid", uid, "days", days, "type", type, "name", text(params, "name"));
        final int status = isStaff(user) ? 1 : 0;
        return lock.execute(connection -> addLocked(connection, uid, params, type,
                days, status, operationKey, payload));
    }

    public Map<String, Object> edit(Map<String, String> request) {
        long uid = requireUser(request);
        Map<String, Object> user = tokens.userById(uid);
        Map<String, Object> params = RequestValues.jsonObject(
                mapper, RequestValues.text(request, "params"));
        long aid = RequestValues.objectInteger(params, "aid", 0);
        Map<String, Object> ad = requireAd(aid);
        if (!canManage(uid, user, ad)) {
            throw new IllegalArgumentException("没有编辑权限");
        }
        int type = validateType(RequestValues.objectInteger(
                params, "type", (int) number(ad.get("type"))));
        validateAdFields(params);

        /*
         * User edits return an active ad to review. Staff edits preserve status because the
         * management page is itself the moderation surface.
         */
        int nextStatus = isStaff(user) ? (int) number(ad.get("status")) : 0;
        jdbc.update(
                "UPDATE starfree_ads SET name = ?,type = ?,img = ?,intro = ?,"
                        + "urltype = ?,url = ?,status = ? WHERE aid = ?",
                text(params, "name"), type, text(params, "img"), text(params, "intro"),
                RequestValues.objectInteger(params, "urltype", 0),
                text(params, "url"), nextStatus, aid);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aid", aid);
        result.put("status", nextStatus);
        return result;
    }

    public void delete(Map<String, String> request) {
        long uid = requireUser(request);
        Map<String, Object> user = tokens.userById(uid);
        long aid = RequestValues.integer(request, "id", 0);
        Map<String, Object> ad = requireAd(aid);
        if (!canManage(uid, user, ad)) {
            throw new IllegalArgumentException("没有删除权限");
        }
        // Legacy behavior does not refund deleted advertisements.
        jdbc.update("DELETE FROM starfree_ads WHERE aid = ?", aid);
    }

    public Map<String, Object> audit(Map<String, String> request) {
        long uid = requireUser(request);
        Map<String, Object> user = tokens.userById(uid);
        if (!isStaff(user)) {
            throw new IllegalArgumentException("没有审核权限");
        }
        long aid = RequestValues.integer(request, "id", 0);
        requireAd(aid);
        jdbc.update("UPDATE starfree_ads SET status = 1 WHERE aid = ?", aid);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aid", aid);
        result.put("status", 1);
        return result;
    }

    public Map<String, Object> renewal(Map<String, String> request) {
        final long administratorUid = requireUser(request);
        final Map<String, Object> user = tokens.userById(administratorUid);
        if (!isAdministrator(user)) {
            throw new IllegalArgumentException("你没有操作权限");
        }
        final long aid = RequestValues.integer(request, "id", 0);
        final int days = validateDays(RequestValues.integer(request, "day", 0));
        if (aid <= 0) {
            throw new IllegalArgumentException("广告不存在");
        }
        requireFinancialInfrastructure();

        final String operationKey = journal.requestKey("ad-renewal", administratorUid,
                RequestValues.text(request, "requestId"), aid + ":" + days);
        final Map<String, Object> payload = mapOf(
                "administratorUid", administratorUid, "aid", aid, "days", days);
        return lock.execute(connection -> renewalLocked(connection, administratorUid,
                aid, days, operationKey, payload));
    }

    private Map<String, Object> addLocked(Connection connection, long uid,
                                           Map<String, Object> params, int type, int days,
                                           int status, String operationKey,
                                           Map<String, Object> payload) throws SQLException {
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "ad-buy", uid, uid, 0, payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }

        Map<String, Object> cfg = config(connection);
        int dailyPrice = dailyPrice(cfg, type);
        int maxSlots = slotLimit(cfg, type);
        if (maxSlots > 0 && activeCount(connection, type) >= maxSlots) {
            return fail(connection, operationKey, "该广告位已满，请稍后再试");
        }
        int total = totalPrice(dailyPrice, days);
        Map<String, Object> user = one(connection,
                "SELECT COALESCE(assets,0) AS assets FROM starfree_users WHERE uid=? LIMIT 1",
                uid);
        if (user == null) {
            return fail(connection, operationKey, "用户不存在");
        }
        long oldAssets = number(user.get("assets"));
        if (total > oldAssets) {
            return fail(connection, operationKey, "积分余额不足");
        }

        long now = Instant.now().getEpochSecond();
        long close = now + days * 86400L;
        long paylogId = 0;
        long aid = 0;
        boolean balanceChanged = false;
        try {
            if (update(connection, "UPDATE starfree_users SET assets=? WHERE uid=?",
                    oldAssets - total, uid) != 1) {
                throw new SQLException("Advertisement balance update failed");
            }
            balanceChanged = true;
            paylogId = insertPaylog(connection, uid, "开通广告位", "-" + total,
                    operationKey, "buyAds", now);
            aid = insertKey(connection,
                    "INSERT INTO starfree_ads "
                            + "(name,type,img,`close`,created,price,intro,urltype,url,uid,status) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    text(params, "name"), type, text(params, "img"), close, now, total,
                    text(params, "intro"), RequestValues.objectInteger(params, "urltype", 0),
                    text(params, "url"), uid, status);

            Map<String, Object> result = mapOf(
                    "aid", aid, "status", status, "price", total, "close", close);
            journal.commit(connection, operationKey, result);
            return result;
        } catch (Exception error) {
            try {
                if (aid > 0) {
                    update(connection, "DELETE FROM starfree_ads WHERE aid=?", aid);
                }
                if (paylogId > 0) {
                    update(connection, "DELETE FROM starfree_paylog WHERE pid=?", paylogId);
                }
                if (balanceChanged) {
                    update(connection, "UPDATE starfree_users SET assets=? WHERE uid=?",
                            oldAssets, uid);
                }
                journal.fail(connection, operationKey, error);
            } catch (Exception compensationError) {
                error.addSuppressed(compensationError);
                journal.needsReview(connection, operationKey, error);
            }
            throw error;
        }
    }

    private Map<String, Object> renewalLocked(Connection connection, long administratorUid,
                                               long aid, int days, String operationKey,
                                               Map<String, Object> payload) throws SQLException {
        Map<String, Object> ad = one(connection,
                "SELECT aid,type,`close`,price,uid,status FROM starfree_ads WHERE aid=? LIMIT 1",
                aid);
        if (ad == null) {
            throw new IllegalArgumentException("广告不存在");
        }
        long ownerUid = number(ad.get("uid"));
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "ad-renewal", administratorUid, ownerUid, aid, payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }

        int type = validateType((int) number(ad.get("type")));
        int cost = totalPrice(dailyPrice(config(connection), type), days);
        long now = Instant.now().getEpochSecond();
        long oldClose = number(ad.get("close"));
        long oldPrice = number(ad.get("price"));
        long nextClose = Math.max(oldClose, now) + days * 86400L;
        long nextPrice = oldPrice + cost;
        if (nextPrice > Integer.MAX_VALUE) {
            return fail(connection, operationKey, "广告价格累计值过大");
        }

        long paylogId = 0;
        boolean adChanged = false;
        try {
            if (update(connection,
                    "UPDATE starfree_ads SET `close`=?,price=? WHERE aid=?",
                    nextClose, nextPrice, aid) != 1) {
                throw new SQLException("Advertisement renewal update failed");
            }
            adChanged = true;
            paylogId = insertPaylog(connection, ownerUid,
                    "系统赠送广告位时间" + days + "天", String.valueOf(cost),
                    operationKey, "renewalAds", now);
            Map<String, Object> result = mapOf(
                    "aid", aid, "close", nextClose, "status", number(ad.get("status")),
                    "price", cost, "totalPrice", nextPrice);
            journal.commit(connection, operationKey, result);
            return result;
        } catch (Exception error) {
            try {
                if (paylogId > 0) {
                    update(connection, "DELETE FROM starfree_paylog WHERE pid=?", paylogId);
                }
                if (adChanged) {
                    update(connection, "UPDATE starfree_ads SET `close`=?,price=? WHERE aid=?",
                            oldClose, oldPrice, aid);
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
        if (uid == null) {
            throw new IllegalArgumentException("用户未登录或Token验证失败");
        }
        return uid;
    }

    private Map<String, Object> requireAd(long aid) {
        if (aid <= 0) {
            throw new IllegalArgumentException("广告不存在");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT aid,name,type,img,`close`,created,price,intro,urltype,url,uid,status "
                        + "FROM starfree_ads WHERE aid = ? LIMIT 1", aid);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("广告不存在");
        }
        return rows.get(0);
    }

    private boolean canManage(Long uid, Map<String, Object> user, Map<String, Object> ad) {
        return uid != null && (uid.longValue() == number(ad.get("uid")) || isStaff(user));
    }

    private boolean isStaff(Map<String, Object> user) {
        return user != null && economy.isStaff(String.valueOf(user.get("group")));
    }

    private boolean isAdministrator(Map<String, Object> user) {
        return user != null && "administrator".equals(String.valueOf(user.get("group")));
    }

    private void requireFinancialInfrastructure() {
        if (lock == null || journal == null) {
            throw new IllegalStateException("Economy lock and journal are required");
        }
    }

    private Map<String, Object> config(Connection connection) throws SQLException {
        Map<String, Object> source = one(connection,
                "SELECT pushAdsPrice,pushAdsNum,bannerAdsPrice,bannerAdsNum,"
                        + "startAdsPrice,startAdsNum FROM starfree_apiconfig ORDER BY id LIMIT 1");
        return source == null ? new LinkedHashMap<String, Object>() : source;
    }

    private int activeCount(Connection connection, int type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM starfree_ads "
                        + "WHERE type=? AND status=1 AND `close`>=?")) {
            statement.setInt(1, type);
            statement.setLong(2, Instant.now().getEpochSecond());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private long insertPaylog(Connection connection, long uid, String subject, String amount,
                              String tradeNumber, String payType, long created)
            throws SQLException {
        return insertKey(connection,
                "INSERT INTO starfree_paylog "
                        + "(subject,total_amount,out_trade_no,trade_no,paytype,uid,created,status) "
                        + "VALUES (?,?,?,'',?,?,?,1)",
                subject, amount, tradeNumber, payType, uid, created);
    }

    private Map<String, Object> fail(Connection connection, String operationKey,
                                     String message) throws SQLException {
        IllegalArgumentException error = new IllegalArgumentException(message);
        journal.fail(connection, operationKey, error);
        throw error;
    }

    private void validateAdFields(Map<String, Object> params) {
        if (text(params, "name").isEmpty() || text(params, "img").isEmpty()
                || text(params, "intro").isEmpty() || text(params, "url").isEmpty()) {
            throw new IllegalArgumentException("请完善广告表单");
        }
    }

    private int validateType(int type) {
        if (type < 0 || type > 2) {
            throw new IllegalArgumentException("广告类型不正确");
        }
        return type;
    }

    private int validateDays(int days) {
        if (days <= 0 || days > MAX_PURCHASE_DAYS) {
            throw new IllegalArgumentException("购买天数必须在1到3650天之间");
        }
        return days;
    }

    private int dailyPrice(Map<String, Object> config, int type) {
        int price;
        if (type == 0) {
            price = (int) number(config.get("pushAdsPrice"));
        } else if (type == 1) {
            price = (int) number(config.get("bannerAdsPrice"));
        } else {
            price = (int) number(config.get("startAdsPrice"));
        }
        if (price < 0) {
            throw new IllegalArgumentException("广告价格配置不正确");
        }
        return price;
    }

    private int totalPrice(int dailyPrice, int days) {
        try {
            return Math.multiplyExact(dailyPrice, days);
        } catch (ArithmeticException error) {
            // Reject a bad admin-side price before any MyISAM debit or insert can happen.
            throw new IllegalArgumentException("\u5e7f\u544a\u4ef7\u683c\u914d\u7f6e\u8fc7\u5927", error);
        }
    }

    private int slotLimit(Map<String, Object> config, int type) {
        if (type == 0) {
            return (int) number(config.get("pushAdsNum"));
        }
        if (type == 1) {
            return (int) number(config.get("bannerAdsNum"));
        }
        return (int) number(config.get("startAdsNum"));
    }

    private void appendIntegerFilter(StringBuilder sql, List<Object> args,
                                     Map<String, Object> filters, String key, String column) {
        if (filters.containsKey(key)) {
            sql.append(" AND ").append(column).append(" = ?");
            args.add(RequestValues.objectInteger(filters, key, 0));
        }
    }

    private Map<String, Object> toAd(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private void putNumber(Map<String, Object> target, Map<String, Object> source, String key) {
        target.put(key, number(source.get(key)));
    }

    private String text(Map<String, Object> params, String key) {
        return RequestValues.objectText(params, key);
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
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
                throw new SQLException("Insert did not affect exactly one row");
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Insert did not return a generated key");
                }
                return keys.getLong(1);
            }
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
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    row.put(metadata.getColumnLabel(column), result.getObject(column));
                }
                return row;
            }
        }
    }

    private void bind(PreparedStatement statement, Object... args) throws SQLException {
        for (int index = 0; index < args.length; index++) {
            statement.setObject(index + 1, args[index]);
        }
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    public static final class AdsPage {
        private final List<Map<String, Object>> data;
        private final int total;

        public AdsPage(List<Map<String, Object>> data, int total) {
            this.data = data;
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
