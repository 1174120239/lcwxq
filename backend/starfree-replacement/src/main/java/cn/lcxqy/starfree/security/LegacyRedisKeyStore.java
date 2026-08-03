package cn.lcxqy.starfree.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Minimal access to non-session keys shared with the closed Java API.
 *
 * <p>The injected RedisTemplate deliberately keeps Java serialization. Using a string template
 * would create visually identical but byte-incompatible keys. This class exposes named operations
 * instead of arbitrary public key/value access so request parameters cannot become Redis keys.
 */
@Component
public class LegacyRedisKeyStore {
    private final RedisTemplate<Object, Object> redis;
    private final boolean enabled;
    private final String prefix;

    public LegacyRedisKeyStore(
            RedisTemplate<Object, Object> redis,
            @Value("${legacy.redis.enabled:false}") boolean enabled,
            @Value("${legacy.redis.prefix:starfree}") String prefix) {
        this.redis = redis;
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.trim().isEmpty() ? "starfree" : prefix.trim();
    }

    /** Confirms a server-generated scan nonce and replaces its value with a login token for 90s. */
    public void approveScan(String nonce, String token) {
        if (!enabled) {
            throw new IllegalArgumentException("扫码登录未启用");
        }
        String key = required(nonce, "codeContent");
        String normalizedToken = required(token, "token");
        if (redis.opsForValue().get(key) == null) {
            throw new IllegalArgumentException("二维码已过期");
        }
        redis.opsForValue().set(key, normalizedToken, 90, TimeUnit.SECONDS);
    }

    /** Creates or removes the exact legacy per-user silence key used by post and Space guards. */
    public boolean setSilenced(long uid, boolean silenced, int seconds) {
        if (!enabled) {
            throw new IllegalArgumentException("共享Redis未启用");
        }
        String key = prefix + "_" + uid + "_silence";
        if (silenced) {
            redis.opsForValue().set(key, "1", Math.max(1, seconds), TimeUnit.SECONDS);
            return true;
        }
        return Boolean.TRUE.equals(redis.delete(key));
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}
