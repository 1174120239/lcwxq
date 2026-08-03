package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.LegacyTokenService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Balance-changing shop and VIP operations; shop CRUD remains independently routable. */
@Service
public class ShopEconomyService {
    private final JdbcTemplate jdbc;
    private final LegacyTokenService tokens;
    private final EconomyLockExecutor lock;
    private final EconomyOperationJournal journal;

    public ShopEconomyService(JdbcTemplate jdbc, LegacyTokenService tokens,
                              EconomyLockExecutor lock, EconomyOperationJournal journal) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.lock = lock;
        this.journal = journal;
    }

    public Map<String, Object> buy(Map<String, String> request) {
        final long buyerUid = requireUser(request);
        final long shopId = RequestValues.integer(request, "sid", 0);
        final int usePoints = RequestValues.integer(request, "isIntegral", 0);
        final long contentId = RequestValues.integer(request, "fid", 0);
        if (shopId <= 0 || (usePoints != 0 && usePoints != 1)) {
            throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
        }
        String requestId = RequestValues.text(request, "requestId");
        final String operationKey = journal.requestKey("shop-buy", buyerUid, requestId,
                shopId + ":" + usePoints + ":" + contentId);
        final Map<String, Object> payload = mapOf(
                "buyerUid", buyerUid, "shopId", shopId,
                "usePoints", usePoints, "contentId", contentId);
        return lock.execute(connection -> buyLocked(connection, buyerUid, shopId,
                usePoints == 1, contentId, operationKey, payload));
    }

    public boolean isBought(Map<String, String> request) {
        long uid = requireUser(request);
        long shopId = RequestValues.integer(request, "sid", 0);
        if (shopId <= 0) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_userlog "
                        + "WHERE uid=? AND cid=? AND type='buy'",
                Integer.class, uid, shopId);
        return count != null && count > 0;
    }

    public Map<String, Object> buyVipDays(Map<String, String> request) {
        final long uid = requireUser(request);
        final int days = RequestValues.integer(request, "day", 0);
        if (days <= 0) {
            throw new IllegalArgumentException("\u53c2\u6570\u9519\u8bef\uff01");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT vipPrice,vipDay FROM starfree_apiconfig ORDER BY id LIMIT 1");
        Map<String, Object> config = rows.isEmpty() ? null : rows.get(0);
        int unitPrice = integer(config, "vipPrice");
        int permanentDays = integer(config, "vipDay");
        long price = multiply(unitPrice, days);
        String operationKey = journal.requestKey("vip-days", uid,
                RequestValues.text(request, "requestId"), days + ":" + price);
        Map<String, Object> payload = mapOf(
                "uid", uid, "days", days, "price", price, "source", "days");
        return lock.execute(connection -> buyVipLocked(connection, uid, days,
                price, permanentDays, "\u8d2d\u4e70VIP", operationKey, payload));
    }

    public Map<String, Object> buyVipPackage(Map<String, String> request) {
        final long uid = requireUser(request);
        final long packageId = RequestValues.integer(request, "id", 0);
        if (packageId <= 0) {
            throw new IllegalArgumentException("\u8be5\u5957\u9910\u4e0d\u5b58\u5728");
        }
        List<Map<String, Object>> packages = jdbc.queryForList(
                "SELECT id,name,price,day,giftDay FROM starfree_vips WHERE id=? LIMIT 1",
                packageId);
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("\u8be5\u5957\u9910\u4e0d\u5b58\u5728");
        }
        Map<String, Object> item = packages.get(0);
        long totalDays = (long) integer(item, "day") + integer(item, "giftDay");
        long price = number(item.get("price"));
        if (totalDays <= 0 || totalDays > Integer.MAX_VALUE || price < 0) {
            throw new IllegalArgumentException("\u5957\u9910\u914d\u7f6e\u4e0d\u6b63\u786e");
        }
        Integer permanentDays = jdbc.queryForObject(
                "SELECT vipDay FROM starfree_apiconfig ORDER BY id LIMIT 1", Integer.class);
        String operationKey = journal.requestKey("vip-package", uid,
                RequestValues.text(request, "requestId"), packageId + ":" + price);
        Map<String, Object> payload = mapOf(
                "uid", uid, "packageId", packageId, "days", totalDays,
                "price", price, "source", "package");
        String subject = "\u8d2d\u4e70VIP\u5957\u9910[" + text(item.get("name")) + "]";
        return lock.execute(connection -> buyVipLocked(connection, uid, (int) totalDays,
                price, permanentDays == null ? 0 : permanentDays,
                subject, operationKey, payload));
    }

    public Map<String, Object> vipInfo() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT vipDiscount,vipPrice,scale,vipDay "
                        + "FROM starfree_apiconfig ORDER BY id LIMIT 1");
        Map<String, Object> source = rows.isEmpty()
                ? new LinkedHashMap<String, Object>() : rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vipDiscount", source.get("vipDiscount"));
        result.put("vipPrice", integer(source, "vipPrice"));
        result.put("scale", integer(source, "scale"));
        result.put("vipDay", integer(source, "vipDay"));
        return result;
    }

    private Map<String, Object> buyLocked(Connection connection, long buyerUid, long shopId,
                                          boolean usePoints, long contentId,
                                          String operationKey, Map<String, Object> payload)
            throws SQLException {
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "shop-buy", buyerUid, 0, shopId, payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }

        Map<String, Object> shop = EconomySql.one(connection,
                "SELECT id,title,price,num,type,uid,vipDiscount,status,sellNum,integral "
                        + "FROM starfree_shop WHERE id=? LIMIT 1", shopId);
        if (shop == null || number(shop.get("status")) != 1) {
            return fail(connection, operationKey, "\u8be5\u5546\u54c1\u5df2\u4e0b\u67b6");
        }
        long sellerUid = number(shop.get("uid"));
        if (buyerUid == sellerUid) {
            return fail(connection, operationKey,
                    "\u4f60\u4e0d\u53ef\u4ee5\u4e70\u81ea\u5df1\u7684\u5546\u54c1");
        }
        long stock = number(shop.get("num"));
        if (stock != -1 && stock < 1) {
            return fail(connection, operationKey, "\u8be5\u5546\u54c1\u5df2\u552e\u5b8c");
        }
        int type = (int) number(shop.get("type"));
        if (type != 1 && EconomySql.number(connection,
                "SELECT COUNT(*) FROM starfree_userlog "
                        + "WHERE uid=? AND cid=? AND type='buy'",
                buyerUid, shopId) > 0) {
            return fail(connection, operationKey, "\u4f60\u5df2\u7ecf\u8d2d\u4e70\u8fc7\u4e86");
        }

        Map<String, Object> buyer = loadUser(connection, buyerUid);
        Map<String, Object> seller = loadUser(connection, sellerUid);
        if (buyer == null || seller == null) {
            return fail(connection, operationKey, "\u4e70\u5bb6\u6216\u5356\u5bb6\u4e0d\u5b58\u5728");
        }
        if (type == 1 && text(buyer.get("address")).trim().isEmpty()) {
            return fail(connection, operationKey,
                    "\u8d2d\u4e70\u5b9e\u4f53\u5546\u54c1\u524d\uff0c\u9700\u8981\u5148\u8bbe\u7f6e\u6536\u8d27\u5730\u5740");
        }

        long price = number(shop.get("price"));
        if (isVip(number(buyer.get("vip")))) {
            price = discounted(price, text(shop.get("vipDiscount")));
        }
        long points = usePoints ? Math.max(0, number(shop.get("integral"))) : 0;
        if (points > number(buyer.get("points"))) {
            return fail(connection, operationKey, "\u7528\u6237\u79ef\u5206\u4e0d\u8db3");
        }
        price -= points;
        if (price < 0) {
            return fail(connection, operationKey,
                    "\u8be5\u5546\u54c1\u4ef7\u683c\u53c2\u6570\u5f02\u5e38\uff0c\u65e0\u6cd5\u4ea4\u6613");
        }
        if (price > number(buyer.get("assets"))) {
            return fail(connection, operationKey,
                    "\u5f53\u524d\u8d44\u4ea7\u4e0d\u8db3\uff0c\u8bf7\u5145\u503c");
        }

        Map<String, Object> config = EconomySql.one(connection,
                "SELECT rebateLevel,rebateProportion FROM starfree_apiconfig ORDER BY id LIMIT 1");
        long inviterUid = number(buyer.get("invitationUser"));
        long rebate = 0;
        if (integer(config, "rebateLevel") > 1 && inviterUid > 0) {
            rebate = multiply(price, Math.max(0, integer(config, "rebateProportion"))) / 100L;
        }

        Map<Long, Balance> balances = new LinkedHashMap<>();
        addBalance(balances, buyerUid, buyer);
        addBalance(balances, sellerUid, seller);
        if (rebate > 0 && !balances.containsKey(inviterUid)) {
            Map<String, Object> inviter = loadUser(connection, inviterUid);
            if (inviter != null) {
                addBalance(balances, inviterUid, inviter);
            } else {
                rebate = 0;
            }
        }
        balances.get(buyerUid).assetDelta -= price;
        balances.get(buyerUid).pointDelta -= points;
        balances.get(sellerUid).assetDelta += price;
        balances.get(sellerUid).pointDelta += points;
        if (rebate > 0) {
            balances.get(inviterUid).assetDelta += rebate;
        }

        List<Long> paylogIds = new ArrayList<>();
        long orderId = 0;
        long inboxId = 0;
        boolean shopChanged = false;
        boolean balancesChanged = false;
        long oldSellNum = number(shop.get("sellNum"));
        try {
            applyBalances(connection, balances);
            balancesChanged = true;
            long nextStock = stock == -1 ? -1 : stock - 1;
            if (EconomySql.update(connection,
                    "UPDATE starfree_shop SET num=?,sellNum=? WHERE id=? AND status=1",
                    nextStock, oldSellNum + 1, shopId) != 1) {
                throw new SQLException("Shop stock update failed");
            }
            shopChanged = true;
            orderId = EconomySql.insertKey(connection,
                    "INSERT INTO starfree_userlog (uid,cid,type,num,created,toid) "
                            + "VALUES (?,?,'buy',?,?,?)",
                    buyerUid, shopId, balances.get(buyerUid).nextAssets(), now(), sellerUid);
            paylogIds.add(insertPaylog(connection, buyerUid, "\u8d2d\u4e70\u5546\u54c1",
                    "-" + price, operationKey + ":buyer", "buyshop"));
            String sellerSubject;
            if (points > 0) {
                sellerSubject = "\u51fa\u552e\u5546\u54c1\u6536\u76ca\uff0c\u79ef\u5206\u62b5\u6263" + points;
            } else if (contentId != 0 && type == 4) {
                sellerSubject = "\u5e16\u5b50id" + contentId + "\u7684\u4ed8\u8d39\u5185\u5bb9\u88ab\u8d2d\u4e70";
            } else {
                sellerSubject = "\u51fa\u552e\u5546\u54c1\u6536\u76ca";
            }
            paylogIds.add(insertPaylog(connection, sellerUid, sellerSubject,
                    String.valueOf(price), operationKey + ":seller", "sellshop"));
            if (rebate > 0) {
                paylogIds.add(insertPaylog(connection, inviterUid,
                        "\u88ab\u9080\u8bf7\u8005\u6d88\u8d39\u540e\u8fd4\u5229", String.valueOf(rebate),
                        operationKey + ":rebate", "rebate"));
            }
            String title = text(shop.get("title"));
            String notice = title.isEmpty()
                    ? "\u4f60\u7684\u5546\u54c1\u6709\u65b0\u7684\u8ba2\u5355\u3002"
                    : "\u4f60\u7684\u5546\u54c1\u3010" + title + "\u3011\u6709\u65b0\u7684\u8ba2\u5355\u3002";
            inboxId = EconomySql.insertKey(connection,
                    "INSERT INTO starfree_inbox "
                            + "(type,uid,text,touid,isread,value,created,cid) "
                            + "VALUES ('finance',?,?,?,0,?,?,0)",
                    buyerUid, notice, sellerUid, shopId, now());

            Map<String, Object> result = mapOf(
                    "rows", 1, "orderId", orderId, "shopId", shopId,
                    "assetsPaid", price, "pointsPaid", points);
            journal.commit(connection, operationKey, result);
            return result;
        } catch (Exception error) {
            try {
                if (inboxId > 0) {
                    EconomySql.update(connection, "DELETE FROM starfree_inbox WHERE id=?", inboxId);
                }
                if (orderId > 0) {
                    EconomySql.update(connection, "DELETE FROM starfree_userlog WHERE id=?", orderId);
                }
                for (Long paylogId : paylogIds) {
                    EconomySql.update(connection, "DELETE FROM starfree_paylog WHERE pid=?", paylogId);
                }
                if (shopChanged) {
                    EconomySql.update(connection,
                            "UPDATE starfree_shop SET num=?,sellNum=? WHERE id=?",
                            stock, oldSellNum, shopId);
                }
                if (balancesChanged) {
                    restoreBalances(connection, balances);
                }
                journal.fail(connection, operationKey, error);
            } catch (Exception compensationError) {
                error.addSuppressed(compensationError);
                journal.needsReview(connection, operationKey, error);
            }
            throw error;
        }
    }

    private Map<String, Object> buyVipLocked(Connection connection, long uid, int days,
                                             long price, int permanentDays, String subject,
                                             String operationKey, Map<String, Object> payload)
            throws SQLException {
        EconomyOperationJournal.BeginResult begin = journal.begin(connection, operationKey,
                "vip-buy", uid, uid, 0, payload);
        if (begin.isReplay()) {
            return begin.getResult();
        }
        if (days <= 0 || price < 0 || price > Integer.MAX_VALUE) {
            return fail(connection, operationKey, "\u53c2\u6570\u9519\u8bef\uff01");
        }
        Map<String, Object> user = loadUser(connection, uid);
        if (user == null) {
            return fail(connection, operationKey, "\u7528\u6237\u4e0d\u5b58\u5728");
        }
        long oldVip = number(user.get("vip"));
        if (oldVip == 1) {
            return fail(connection, operationKey,
                    "\u60a8\u5df2\u7ecf\u662f\u6c38\u4e45VIP\uff0c\u65e0\u9700\u8d2d\u4e70");
        }
        long oldAssets = number(user.get("assets"));
        if (price > oldAssets) {
            return fail(connection, operationKey,
                    "\u5f53\u524d\u8d44\u4ea7\u4e0d\u8db3\uff0c\u8bf7\u5145\u503c");
        }
        long current = now();
        long nextVip = Math.max(oldVip, current) + multiply(days, 86400L);
        if (permanentDays > 0 && days >= permanentDays) {
            nextVip = 1;
        } else if (nextVip > Integer.MAX_VALUE) {
            return fail(connection, operationKey, "VIP\u6709\u6548\u671f\u8d85\u51fa\u53ef\u7528\u8303\u56f4");
        }

        long paylogId = 0;
        boolean userChanged = false;
        try {
            if (EconomySql.update(connection,
                    "UPDATE starfree_users SET assets=?,vip=? WHERE uid=?",
                    oldAssets - price, nextVip, uid) != 1) {
                throw new SQLException("VIP balance update failed");
            }
            userChanged = true;
            paylogId = insertPaylog(connection, uid, subject, "-" + price,
                    operationKey, "buyvip");
            Map<String, Object> result = mapOf(
                    "rows", 1, "vip", nextVip, "assets", oldAssets - price);
            journal.commit(connection, operationKey, result);
            return result;
        } catch (Exception error) {
            try {
                if (paylogId > 0) {
                    EconomySql.update(connection, "DELETE FROM starfree_paylog WHERE pid=?", paylogId);
                }
                if (userChanged) {
                    EconomySql.update(connection,
                            "UPDATE starfree_users SET assets=?,vip=? WHERE uid=?",
                            oldAssets, oldVip, uid);
                }
                journal.fail(connection, operationKey, error);
            } catch (Exception compensationError) {
                error.addSuppressed(compensationError);
                journal.needsReview(connection, operationKey, error);
            }
            throw error;
        }
    }

    private Map<String, Object> loadUser(Connection connection, long uid) throws SQLException {
        return EconomySql.one(connection,
                "SELECT uid,COALESCE(assets,0) AS assets,COALESCE(points,0) AS points,"
                        + "COALESCE(vip,0) AS vip,address,COALESCE(invitationUser,0) AS invitationUser "
                        + "FROM starfree_users WHERE uid=? LIMIT 1", uid);
    }

    private void addBalance(Map<Long, Balance> balances, long uid, Map<String, Object> user) {
        balances.put(uid, new Balance(uid, number(user.get("assets")), number(user.get("points"))));
    }

    private void applyBalances(Connection connection, Map<Long, Balance> balances)
            throws SQLException {
        for (Balance balance : balances.values()) {
            long assets = balance.nextAssets();
            long points = balance.nextPoints();
            if (assets < 0 || assets > Integer.MAX_VALUE || points < 0 || points > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("\u4ea4\u6613\u540e\u4f59\u989d\u4e0d\u5408\u6cd5");
            }
            if (EconomySql.update(connection,
                    "UPDATE starfree_users SET assets=?,points=? WHERE uid=?",
                    assets, points, balance.uid) != 1) {
                throw new SQLException("Shop balance update failed for uid " + balance.uid);
            }
        }
    }

    private void restoreBalances(Connection connection, Map<Long, Balance> balances)
            throws SQLException {
        for (Balance balance : balances.values()) {
            EconomySql.update(connection,
                    "UPDATE starfree_users SET assets=?,points=? WHERE uid=?",
                    balance.assets, balance.points, balance.uid);
        }
    }

    private long insertPaylog(Connection connection, long uid, String subject,
                              String amount, String tradeNumber, String payType)
            throws SQLException {
        return EconomySql.insertKey(connection,
                "INSERT INTO starfree_paylog "
                        + "(subject,total_amount,out_trade_no,trade_no,paytype,uid,created,status) "
                        + "VALUES (?,?,?,'',?,?,?,1)",
                subject, amount, tradeNumber, payType, uid, now());
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

    private long discounted(long price, String discountText) {
        try {
            double discount = Double.parseDouble(discountText);
            if (!Double.isFinite(discount) || discount < 0) {
                throw new NumberFormatException("invalid discount");
            }
            double result = price * discount;
            if (result < 0 || result > Integer.MAX_VALUE) {
                throw new NumberFormatException("discounted price out of range");
            }
            return (long) result;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("VIP\u6298\u6263\u914d\u7f6e\u4e0d\u6b63\u786e", error);
        }
    }

    private boolean isVip(long vip) {
        return vip == 1 || vip > now();
    }

    private long multiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("\u91d1\u989d\u6216\u6709\u6548\u671f\u8d85\u51fa\u53ef\u7528\u8303\u56f4", error);
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static final class Balance {
        private final long uid;
        private final long assets;
        private final long points;
        private long assetDelta;
        private long pointDelta;

        private Balance(long uid, long assets, long points) {
            this.uid = uid;
            this.assets = assets;
            this.points = points;
        }

        private long nextAssets() {
            return assets + assetDelta;
        }

        private long nextPoints() {
            return points + pointDelta;
        }
    }
}
