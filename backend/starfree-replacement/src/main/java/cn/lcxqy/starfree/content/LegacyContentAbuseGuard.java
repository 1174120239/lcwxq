package cn.lcxqy.starfree.content;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/** Shares the closed contentsAdd burst, silence, and daily-post keys during migration. */
@Service
public class LegacyContentAbuseGuard {
    private static final int BURST_TTL_SECONDS = 3;
    private static final int POST_TTL_SECONDS = 86400;

    private final RedisTemplate<Object, Object> redis;
    private final boolean enabled;
    private final String prefix;

    public LegacyContentAbuseGuard(
            RedisTemplate<Object, Object> redis,
            @Value("${legacy.redis.enabled:false}") boolean enabled,
            @Value("${legacy.redis.prefix:starfree}") String prefix) {
        this.redis = redis;
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.trim().isEmpty() ? "starfree" : prefix.trim();
    }

    static LegacyContentAbuseGuard disabled() {
        return new LegacyContentAbuseGuard(null, false, "starfree");
    }

    public synchronized void checkBurst(long uid, boolean configured, int silenceSeconds) {
        if (!enabled || !configured) {
            return;
        }
        if (value(silenceKey(uid)) != null) {
            throw new IllegalArgumentException("\u4f60\u5df2\u88ab\u7981\u8a00\uff0c\u8bf7\u8010\u5fc3\u7b49\u5f85");
        }
        int current = counter(repeatedKey(uid));
        if (current == 0) {
            set(repeatedKey(uid), 1, BURST_TTL_SECONDS);
            return;
        }
        int next = current + 1;
        if (next >= 3) {
            set(silenceKey(uid), 1, positive(silenceSeconds, 600));
            throw new IllegalArgumentException("\u4f60\u7684\u8bf7\u6c42\u5b58\u5728\u6076\u610f\u884c\u4e3a\uff0c10\u5206\u949f\u5185\u7981\u6b62\u64cd\u4f5c\uff01");
        }
        set(repeatedKey(uid), next, BURST_TTL_SECONDS);
        throw new IllegalArgumentException("\u4f60\u7684\u64cd\u4f5c\u592a\u9891\u7e41\u4e86");
    }

    public synchronized Reservation reservePost(long uid, boolean staff, int postMax,
                                                 int persistedCount) {
        if (!enabled || staff || postMax <= 0) {
            return Reservation.noop();
        }
        String key = postKey(uid);
        int previous = counter(key);
        int baseline = Math.max(previous, Math.max(0, persistedCount));
        if (baseline >= postMax) {
            throw new IllegalArgumentException("\u4f60\u5df2\u8d85\u8fc7\u6700\u5927\u53d1\u5e03\u6570\u91cf\u9650\u5236\uff0c\u8bf7\u60a824\u5c0f\u65f6\u540e\u518d\u64cd\u4f5c");
        }
        set(key, baseline + 1, POST_TTL_SECONDS);
        return new Reservation(this, key);
    }

    private synchronized void cancel(String key) {
        int current = counter(key);
        if (current <= 1) {
            redis.delete(key);
        } else {
            set(key, current - 1, POST_TTL_SECONDS);
        }
    }

    private int counter(String key) {
        Object raw = value(key);
        if (raw == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(raw)));
        } catch (NumberFormatException error) {
            throw new IllegalStateException("Invalid legacy Redis counter at " + key, error);
        }
    }

    private Object value(String key) {
        return redis.opsForValue().get(key);
    }

    private void set(String key, int value, int seconds) {
        ValueOperations<Object, Object> values = redis.opsForValue();
        values.set(key, String.valueOf(value), seconds, TimeUnit.SECONDS);
    }

    private int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private String repeatedKey(long uid) { return prefix + "_" + uid + "_isRepeated"; }
    private String silenceKey(long uid) { return prefix + "_" + uid + "_silence"; }
    private String postKey(long uid) { return prefix + "_" + uid + "_postNum"; }

    public static final class Reservation {
        private final LegacyContentAbuseGuard owner;
        private final String key;
        private boolean active;

        private Reservation(LegacyContentAbuseGuard owner, String key) {
            this.owner = owner;
            this.key = key;
            this.active = owner != null;
        }

        static Reservation noop() { return new Reservation(null, null); }

        public void cancel() {
            if (active) {
                active = false;
                owner.cancel(key);
            }
        }
    }
}
