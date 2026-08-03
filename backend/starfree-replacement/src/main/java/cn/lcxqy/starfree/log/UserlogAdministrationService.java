package cn.lcxqy.starfree.log;

import cn.lcxqy.starfree.cache.LegacyProjectionCacheInvalidator;
import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.security.LegacySessionBridge;
import cn.lcxqy.starfree.security.StaffAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Buyer/seller order projections and administrator bulk-maintenance operations.
 *
 * <p>Order ownership is always derived from the authenticated token. Cleanup selectors can remove
 * economic history, so every selector runs while the shared economy advisory lock is held. Tables
 * are MyISAM: a successful delete is durable immediately and cannot be rolled back by Spring.
 */
@Service
public class UserlogAdministrationService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_STALE_USERS_PER_RUN = 500;

    private final JdbcTemplate jdbc;
    private final StaffAccess access;
    private final EconomyLockExecutor economyLock;
    private final LegacySessionBridge sessions;
    private final LegacyProjectionCacheInvalidator caches;

    public UserlogAdministrationService(
            JdbcTemplate jdbc,
            StaffAccess access,
            EconomyLockExecutor economyLock,
            LegacySessionBridge sessions,
            LegacyProjectionCacheInvalidator caches) {
        this.jdbc = jdbc;
        this.access = access;
        this.economyLock = economyLock;
        this.sessions = sessions;
        this.caches = caches;
    }

    /** Returns at most the buyer's sixty newest purchase logs, matching the unchanged order page. */
    public Page buyerOrders(String token) {
        StaffAccess.Actor actor = access.requireUser(token);
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_userlog WHERE uid=? AND type='buy'",
                Integer.class, actor.getUid());
        List<Map<String, Object>> rows = jdbc.queryForList(orderSelect(
                        "WHERE l.uid=? AND l.type='buy' ORDER BY l.created DESC,l.id DESC LIMIT 60"),
                actor.getUid());
        return new Page(toOrders(rows, false), total == null ? 0 : total);
    }

    /**
     * Returns purchase logs credited to the authenticated merchant.
     * Address and email belong to the buyer and are exposed only on this token-bound seller route.
     */
    public Page sellerOrders(String token, int requestedPage, int requestedLimit) {
        StaffAccess.Actor actor = access.requireUser(token);
        int page = Math.max(1, requestedPage);
        int limit = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedLimit));
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_userlog WHERE toid=? AND type='buy'",
                Integer.class, actor.getUid());
        List<Map<String, Object>> rows = jdbc.queryForList(orderSelect(
                        "WHERE l.toid=? AND l.type='buy' ORDER BY l.created DESC,l.id DESC LIMIT ?,?"),
                actor.getUid(), (page - 1) * limit, limit);
        return new Page(toOrders(rows, true), total == null ? 0 : total);
    }

    /**
     * Executes one legacy cleanup selector and returns its affected row count.
     *
     * <p>Selectors: 1 old clock logs; 2 old paylogs; 3 old buy logs; 4 used recharge codes;
     * 5 used invitations; 6 dormant empty accounts; 7 unpaid paylogs; 8 old ad-reward logs.
     * Thirty-day and one-year cutoffs use epoch seconds. Selector 6 deliberately excludes staff,
     * balances, valid VIP, active sessions, authored data and pending withdrawals, and is capped at
     * 500 accounts per invocation.
     */
    public int clean(String token, final int selector) {
        access.requireAdministrator(token);
        if (selector < 1 || selector > 8) {
            throw new IllegalArgumentException("参数错误");
        }
        final long now = Instant.now().getEpochSecond();
        final long thirtyDaysAgo = now - 30L * 86400;
        final long oneYearAgo = now - 365L * 86400;
        CleanupResult result = economyLock.execute(connection -> {
            switch (selector) {
                case 1:
                    return rows(jdbc.update(
                            "DELETE FROM starfree_userlog WHERE type='clock' AND created<?",
                            thirtyDaysAgo));
                case 2:
                    return rows(jdbc.update(
                            "DELETE FROM starfree_paylog WHERE created<?", thirtyDaysAgo));
                case 3:
                    return rows(jdbc.update(
                            "DELETE FROM starfree_userlog WHERE type='buy' AND created<?",
                            thirtyDaysAgo));
                case 4:
                    return rows(jdbc.update("DELETE FROM starfree_paykey WHERE status=1"));
                case 5:
                    return rows(jdbc.update("DELETE FROM starfree_invitation WHERE status=1"));
                case 6:
                    return cleanDormantUsers(oneYearAgo, now);
                case 7:
                    return rows(jdbc.update("DELETE FROM starfree_paylog WHERE status=0"));
                case 8:
                    return rows(jdbc.update(
                            "DELETE FROM starfree_userlog WHERE type='adsGift' AND created<?",
                            thirtyDaysAgo));
                default:
                    throw new IllegalArgumentException("参数错误");
            }
        });
        for (Map<String, Object> user : result.deletedUsers) {
            sessions.removeAccounts(text(user, "name"), text(user, "mail"), text(user, "phone"));
            String authCode = text(user, "authCode");
            if (!authCode.isEmpty()) {
                sessions.remove(authCode);
            }
            caches.afterUserWrite(number(get(user, "uid")), text(user, "name"));
        }
        if (selector == 5) {
            caches.afterInvitationWrite();
        }
        if (selector == 1 || selector == 3 || selector == 8) {
            caches.afterUserlogCleanup();
        }
        caches.afterDashboardCountWrite();
        return result.rows;
    }

    private CleanupResult cleanDormantUsers(long oneYearAgo, long now) {
        String eligible = " FROM starfree_users u WHERE u.`group` NOT IN ('administrator','editor') "
                + "AND COALESCE(u.assets,0)=0 AND COALESCE(u.points,0)=0 "
                + "AND (COALESCE(u.vip,0)=0 OR u.vip<?) "
                + "AND (u.authCode IS NULL OR u.authCode='') "
                + "AND ((u.logged>0 AND u.logged<?) OR (u.logged=0 AND u.created<?)) "
                + "AND NOT EXISTS(SELECT 1 FROM starfree_contents c WHERE c.authorId=u.uid) "
                + "AND NOT EXISTS(SELECT 1 FROM starfree_comments c WHERE c.authorId=u.uid) "
                + "AND NOT EXISTS(SELECT 1 FROM starfree_space s WHERE s.uid=u.uid) "
                + "AND NOT EXISTS(SELECT 1 FROM starfree_shop s WHERE s.uid=u.uid) "
                + "AND NOT EXISTS(SELECT 1 FROM starfree_userlog l "
                + "WHERE l.uid=u.uid AND l.type='withdraw' AND l.cid=-1) "
                + "ORDER BY u.uid LIMIT " + MAX_STALE_USERS_PER_RUN;
        List<Map<String, Object>> candidates = jdbc.queryForList(
                "SELECT u.uid,u.name,u.mail,u.phone,u.authCode" + eligible,
                now, oneYearAgo, oneYearAgo);
        int removed = 0;
        List<Map<String, Object>> deleted = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            long uid = number(get(candidate, "uid"));
            int changed = jdbc.update(
                    "DELETE u,a FROM starfree_users u LEFT JOIN starfree_userapi a ON a.uid=u.uid "
                            + "WHERE u.uid=? AND u.`group` NOT IN ('administrator','editor') "
                            + "AND COALESCE(u.assets,0)=0 AND COALESCE(u.points,0)=0 "
                            + "AND (COALESCE(u.vip,0)=0 OR u.vip<?) "
                            + "AND (u.authCode IS NULL OR u.authCode='') "
                            + "AND ((u.logged>0 AND u.logged<?) OR (u.logged=0 AND u.created<?)) "
                            + "AND NOT EXISTS(SELECT 1 FROM starfree_contents c WHERE c.authorId=u.uid) "
                            + "AND NOT EXISTS(SELECT 1 FROM starfree_comments c WHERE c.authorId=u.uid) "
                            + "AND NOT EXISTS(SELECT 1 FROM starfree_space s WHERE s.uid=u.uid) "
                            + "AND NOT EXISTS(SELECT 1 FROM starfree_shop s WHERE s.uid=u.uid) "
                            + "AND NOT EXISTS(SELECT 1 FROM starfree_userlog l "
                            + "WHERE l.uid=u.uid AND l.type='withdraw' AND l.cid=-1)",
                    uid, now, oneYearAgo, oneYearAgo);
            if (changed > 0) {
                removed++;
                deleted.add(candidate);
            }
        }
        return new CleanupResult(removed, deleted);
    }

    private String orderSelect(String tail) {
        return "SELECT l.id,l.uid,l.cid,l.type AS log_type,l.num,l.created,l.toid,"
                + "s.id AS shop_id,s.title AS shop_title,s.imgurl AS shop_imgurl,"
                + "s.text AS shop_text,s.price AS shop_price,s.integral AS shop_integral,"
                + "s.num AS shop_num,s.type AS shop_type,s.value AS shop_value,s.cid AS shop_cid,"
                + "s.uid AS shop_uid,s.vipDiscount AS shop_vipDiscount,s.created AS shop_created,"
                + "s.status AS shop_status,s.sellNum AS shop_sellNum,s.isMd AS shop_isMd,"
                + "s.sort AS shop_sort,s.subtype AS shop_subtype,s.isView AS shop_isView,"
                + "merchant.mail AS merchant_email,buyer.mail AS buyer_email,buyer.address AS buyer_address "
                + "FROM starfree_userlog l LEFT JOIN starfree_shop s ON s.id=l.cid "
                + "LEFT JOIN starfree_users merchant ON merchant.uid=s.uid "
                + "LEFT JOIN starfree_users buyer ON buyer.uid=l.uid " + tail;
    }

    private List<Map<String, Object>> toOrders(List<Map<String, Object>> rows, boolean seller) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> order = new LinkedHashMap<>();
            put(order, "id", get(row, "id"));
            put(order, "uid", get(row, "uid"));
            put(order, "cid", get(row, "cid"));
            put(order, "type", get(row, "log_type"));
            put(order, "num", get(row, "num"));
            put(order, "created", get(row, "created"));
            put(order, "toid", get(row, "toid"));
            if (get(row, "shop_id") != null) {
                order.put("shopInfo", shop(row));
            }
            if (seller) {
                order.put("address", get(row, "buyer_address"));
                order.put("userEmail", get(row, "buyer_email"));
            } else {
                order.put("merchantEmail", get(row, "merchant_email"));
            }
            result.add(order);
        }
        return result;
    }

    private Map<String, Object> shop(Map<String, Object> row) {
        Map<String, Object> shop = new LinkedHashMap<>();
        String[] names = {"id", "title", "imgurl", "text", "price", "integral", "num", "type",
                "value", "cid", "uid", "vipDiscount", "created", "status", "sellNum", "isMd",
                "sort", "subtype", "isView"};
        for (String name : names) {
            put(shop, name, get(row, "shop_" + name));
        }
        return shop;
    }

    private CleanupResult rows(int count) {
        return new CleanupResult(count, Collections.<Map<String, Object>>emptyList());
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Object get(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
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
            return Long.parseLong(value == null ? "" : String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Map<String, Object> row, String key) {
        Object value = get(row, key);
        return value == null ? "" : String.valueOf(value);
    }

    /** Standard legacy order page contract. */
    public static final class Page {
        private final List<Map<String, Object>> data;
        private final int total;

        Page(List<Map<String, Object>> data, int total) {
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

    private static final class CleanupResult {
        private final int rows;
        private final List<Map<String, Object>> deletedUsers;

        private CleanupResult(int rows, List<Map<String, Object>> deletedUsers) {
            this.rows = rows;
            this.deletedUsers = deletedUsers;
        }
    }
}
