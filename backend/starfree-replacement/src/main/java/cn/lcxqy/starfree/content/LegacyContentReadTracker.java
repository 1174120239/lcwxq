package cn.lcxqy.starfree.content;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 复用旧后端 Java 序列化的 15 分钟文章浏览去重 key。
 *
 * <p>去重维度是 cid + 可信客户端 IP + User-Agent。重复读取仍刷新 TTL，完全匹配旧行为。
 * Redis 故障时返回 false（不增加浏览量），宁可少计也不在故障期间让每次刷新都加 views。
 */
@Service
public class LegacyContentReadTracker {
    private static final long READ_TTL_SECONDS = 900;

    private final RedisTemplate<Object, Object> redis;
    private final boolean enabled;
    private final String prefix;

    public LegacyContentReadTracker(
            RedisTemplate<Object, Object> redis,
            @Value("${legacy.redis.enabled:false}") boolean enabled,
            @Value("${legacy.redis.prefix:starfree}") String prefix) {
        this.redis = redis;
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.trim().isEmpty() ? "starfree" : prefix.trim();
    }

    /**
     * 首次读取返回 true，并写 900 秒 key；重复或 Redis 异常返回 false。桥接关闭的本地环境
     * 每次都返回 true，故本地连续刷新会持续增加 views，这是配置差异而不是生产规则。
     */
    public boolean firstRead(long cid, String ip, String userAgent) {
        if (!enabled) {
            return true;
        }
        String key = prefix + "_isRead_" + value(ip) + "_" + value(userAgent) + "_" + cid;
        try {
            Object existing = redis.opsForValue().get(key);
            // The old backend refreshes this TTL on duplicate reads too.
            redis.opsForValue().set(key, "yes", READ_TTL_SECONDS, TimeUnit.SECONDS);
            return existing == null;
        } catch (RuntimeException redisFailure) {
            // Keep articles readable during Redis incidents without inflating every request.
            return false;
        }
    }

    private String value(String input) {
        return input == null ? "null" : input;
    }
}
