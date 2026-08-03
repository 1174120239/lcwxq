package cn.lcxqy.starfree.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Invalidates read projections created by the retained Java API after replacement writes.
 *
 * <p>The old application uses JDK-serialized strings as Redis keys. A normal UTF-8 key pattern
 * therefore cannot find those entries, and {@code KEYS *} would block Redis in production. This
 * component performs a cursor-based {@code SCAN}, decodes only the known serialized-string wire
 * format, and deletes the original raw key bytes. Database writes remain authoritative: Redis
 * failures are logged but are not rethrown after a successful MyISAM write.
 *
 * <p>Call a domain-specific method only after its MySQL operation succeeds. Exact matches are used
 * for identifiers such as {@code userInfo_12}; prefix matching there would also evict user 120.
 */
@Service
public class LegacyProjectionCacheInvalidator {
    private static final Logger LOG = LoggerFactory.getLogger(LegacyProjectionCacheInvalidator.class);

    private final RedisConnectionFactory connections;
    private final boolean enabled;
    private final String prefix;

    public LegacyProjectionCacheInvalidator(
            RedisConnectionFactory connections,
            @Value("${legacy.redis.enabled:false}") boolean enabled,
            @Value("${legacy.redis.prefix:starfree}") String prefix) {
        this.connections = connections;
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.trim().isEmpty() ? "starfree" : prefix.trim();
    }

    /**
     * Clears all public user pages plus the exact profile/statistics projections for one account.
     *
     * @param uid authoritative database uid; values below one are ignored for exact uid keys
     * @param name current login name, used because userInfo accepts either uid or name as its key
     */
    public void afterUserWrite(long uid, String name) {
        List<String> exact = new ArrayList<>();
        if (uid > 0) {
            exact.add(prefix + "_userInfo_" + uid);
            exact.add(prefix + "_userData_" + uid);
        }
        if (name != null && !name.trim().isEmpty()) {
            exact.add(prefix + "_userInfo_" + name.trim());
        }
        invalidate("user write", exact,
                Collections.singletonList(prefix + "_userList_"));
    }

    /** Clears old invitation pages after generating, consuming, or removing invite codes. */
    public void afterInvitationWrite() {
        invalidate("invitation write", Collections.<String>emptyList(),
                Collections.singletonList(prefix + "_invitationList_"));
    }

    /**
     * Clears category and content projections after category metadata changes.
     *
     * <p>Article detail contains category objects, while category list and category-content pages
     * have independent caches. All three families must be evicted together.
     */
    public void afterMetaWrite() {
        invalidate("meta write", Collections.<String>emptyList(), Arrays.asList(
                prefix + "_metasList_",
                prefix + "_metaInfo_",
                prefix + "_selectContents_",
                prefix + "_contentsInfo_",
                prefix + "_contentsList_"));
    }

    /** Clears the old administrator dashboard projection after count-changing writes. */
    public void afterDashboardCountWrite() {
        invalidate("dashboard count write",
                Collections.singletonList(prefix + "_allData"),
                Collections.<String>emptyList());
    }

    /** Clears old order, reward and user-statistics pages after bulk log maintenance. */
    public void afterUserlogCleanup() {
        invalidate("userlog cleanup", Collections.<String>emptyList(), Arrays.asList(
                prefix + "_orderList_",
                prefix + "_orderSellList_",
                prefix + "_rewardList_",
                prefix + "_markList_",
                prefix + "_userData_"));
    }

    /**
     * Clears legacy shop projections after a product is created, edited, moderated, mounted, or
     * deleted.
     *
     * <p>The retained API used two different exact keys for product detail because of a historical
     * typo: {@code _shopInfo<sid>} is read, while some anonymous writes used
     * {@code _spaceInfo_<sid>}. Both are removed. Product data is also embedded in content detail,
     * content lists, dynamic entries, order pages, and the administrator count dashboard, so those
     * prefix families must be evicted together. A non-positive id is ignored for exact keys.
     */
    public void afterShopWrite(long shopId) {
        List<String> exact = new ArrayList<>();
        if (shopId > 0) {
            exact.add(prefix + "_shopInfo" + shopId);
            exact.add(prefix + "_spaceInfo_" + shopId);
        }
        invalidate("shop write", exact, Arrays.asList(
                prefix + "_shopList_",
                prefix + "_contentsInfo_",
                prefix + "_contentsList_",
                prefix + "_spaceList_",
                prefix + "_spaceInfo_",
                prefix + "_orderList_",
                prefix + "_orderSellList_"));
        afterDashboardCountWrite();
    }

    /**
     * Clears the ten-minute legacy VIP package projection after package administration changes.
     * The current replacement only reads packages, but this method is the required invalidation
     * hook for future local package-management writes and PHP-admin interoperability.
     */
    public void afterVipPackageWrite() {
        invalidate("VIP package write",
                Collections.singletonList(prefix + "_vipTypeList"),
                Collections.<String>emptyList());
    }

    private void invalidate(String operation, List<String> exact, List<String> startsWith) {
        if (!enabled || connections == null || (exact.isEmpty() && startsWith.isEmpty())) {
            return;
        }
        RedisConnection connection = null;
        try {
            connection = connections.getConnection();
            List<byte[]> matches = new ArrayList<>();
            try (Cursor<byte[]> cursor = connection.scan(
                    ScanOptions.scanOptions().match("*").count(500).build())) {
                while (cursor.hasNext()) {
                    byte[] rawKey = cursor.next();
                    String logicalKey = javaSerializedString(rawKey);
                    if (matches(logicalKey, exact, startsWith)) {
                        matches.add(rawKey);
                    }
                }
            }
            if (!matches.isEmpty()) {
                connection.del(matches.toArray(new byte[matches.size()][]));
            }
        } catch (RuntimeException cacheFailure) {
            LOG.error("Legacy Redis cache cleanup failed after {}", operation, cacheFailure);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    static boolean matches(String logicalKey, List<String> exact, List<String> startsWith) {
        if (logicalKey == null) {
            return false;
        }
        if (exact.contains(logicalKey)) {
            return true;
        }
        for (String prefix : startsWith) {
            if (logicalKey.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decodes the Java serialization short-string form {@code AC ED 00 05 74 <len> <utf8>}.
     * Long strings, objects, malformed lengths, and ordinary UTF-8 keys deliberately return null.
     */
    static String javaSerializedString(byte[] raw) {
        if (raw == null || raw.length < 7
                || (raw[0] & 0xff) != 0xac || (raw[1] & 0xff) != 0xed
                || raw[2] != 0 || raw[3] != 5 || raw[4] != 0x74) {
            return null;
        }
        int size = ((raw[5] & 0xff) << 8) | (raw[6] & 0xff);
        if (raw.length != 7 + size) {
            return null;
        }
        return new String(raw, 7, size, StandardCharsets.UTF_8);
    }
}
