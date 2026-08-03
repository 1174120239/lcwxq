package cn.lcxqy.starfree.content;

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
import java.util.List;

/**
 * 内容写入后的旧 Redis 缓存失效器。
 *
 * <p>Redis key 是 Java 序列化字符串，无法用普通 UTF-8 pattern 直接精确匹配，所以使用 SCAN
 * 读取原始 key，安全解析字符串头后再删除详情 cid、全部内容列表和分类内容列表。禁止改成
 * KEYS *；生产数据量增大时 KEYS 会阻塞 Redis。缓存是次要投影，失败只记日志，不把已成功的
 * MyISAM 写入报失败。
 */
@Service
public class LegacyContentCacheInvalidator {
    private static final Logger LOG = LoggerFactory.getLogger(LegacyContentCacheInvalidator.class);

    private final RedisConnectionFactory connections;
    private final boolean enabled;
    private final String prefix;

    public LegacyContentCacheInvalidator(
            RedisConnectionFactory connections,
            @Value("${legacy.redis.enabled:false}") boolean enabled,
            @Value("${legacy.redis.prefix:starfree}") String prefix) {
        this.connections = connections;
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.trim().isEmpty() ? "starfree" : prefix.trim();
    }

    static LegacyContentCacheInvalidator disabled() {
        return new LegacyContentCacheInvalidator(null, false, "starfree");
    }

    /** 写入后清理该 cid 的详情，以及所有页码/筛选条件对应的内容列表投影。 */
    public void afterContentWrite(long cid) {
        if (!enabled || connections == null) {
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
                    String key = javaSerializedString(rawKey);
                    if (matchesContentProjection(key, prefix, cid)) {
                        matches.add(rawKey);
                    }
                }
            }
            if (!matches.isEmpty()) {
                connection.del(matches.toArray(new byte[matches.size()][]));
            }
        } catch (RuntimeException cacheFailure) {
            // The MyISAM article row is authoritative; stale cache cleanup is best effort.
            LOG.error("Content {} was written but legacy Redis cache cleanup failed", cid, cacheFailure);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    static boolean matchesContentProjection(String key, String prefix, long cid) {
        if (key == null || prefix == null) {
            return false;
        }
        return key.startsWith(prefix + "_contentsInfo_" + cid + "_")
                || key.startsWith(prefix + "_contentsList_")
                || key.startsWith(prefix + "_selectContents_");
    }

    /**
     * 只接受标准 Java serialized short String (`AC ED 00 05 74`)；其他对象或畸形字节返回 null，
     * 防止扫描时把未知二进制 key 误判并删除。
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
