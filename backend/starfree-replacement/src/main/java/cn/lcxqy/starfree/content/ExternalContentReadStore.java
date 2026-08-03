package cn.lcxqy.starfree.content;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * Shared Redis cache and quota guard for public third-party content feeds.
 *
 * <p>The retained API already stores JDK-serialized keys and values, so this component deliberately
 * uses the configured {@link RedisTemplate} rather than StringRedisTemplate. Cache keys contain only
 * hashes of search/client material. When legacy Redis is disabled, reads miss and writes/limits are
 * no-ops for local development; production must enable it before routing Pexels traffic here.
 */
@Service
public class ExternalContentReadStore {
    private final RedisTemplate<Object, Object> redis;
    private final boolean enabled;
    private final String prefix;

    public ExternalContentReadStore(
            RedisTemplate<Object, Object> redis,
            @Value("${legacy.redis.enabled:false}") boolean enabled,
            @Value("${legacy.redis.prefix:starfree}") String prefix) {
        this.redis = redis;
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.trim().isEmpty() ? "starfree" : prefix.trim();
    }

    /** Rejects repeated Pexels requests from the same IP/User-Agent fingerprint for three seconds. */
    public void claimPexels(String clientFingerprint) {
        if (!enabled) {
            return;
        }
        String key = prefix + "_ImagePexels_rate_" + sha256(clientFingerprint);
        Boolean claimed = redis.opsForValue().setIfAbsent(key, "1", 3, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(claimed)) {
            throw new IllegalArgumentException("你的操作太频繁了");
        }
    }

    /** Returns one cached serializable response, or null when missing/disabled. */
    public Object get(String namespace, String material) {
        if (!enabled) {
            return null;
        }
        return redis.opsForValue().get(cacheKey(namespace, material));
    }

    /** Stores one third-party response for a bounded number of seconds. */
    public void put(String namespace, String material, Object value, long seconds) {
        if (!enabled || value == null) {
            return;
        }
        redis.opsForValue().set(cacheKey(namespace, material), value,
                Math.max(1, seconds), TimeUnit.SECONDS);
    }

    private String cacheKey(String namespace, String material) {
        return prefix + "_external_" + namespace + "_" + sha256(material);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
