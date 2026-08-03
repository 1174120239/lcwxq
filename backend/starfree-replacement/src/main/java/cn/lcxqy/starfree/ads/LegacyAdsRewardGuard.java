package cn.lcxqy.starfree.ads;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/** Shares the legacy advertising cooldown and anti-burst Redis keys. */
@Service
public class LegacyAdsRewardGuard {
    private static final int BURST_TTL_SECONDS = 3;
    private static final int VIDEO_COOLDOWN_SECONDS = 20;

    private final RedisTemplate<Object, Object> redis;
    private final boolean enabled;
    private final String prefix;

    public LegacyAdsRewardGuard(
            RedisTemplate<Object, Object> redis,
            @Value("${legacy.redis.enabled:false}") boolean enabled,
            @Value("${legacy.redis.prefix:starfree}") String prefix) {
        this.redis = redis;
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.trim().isEmpty() ? "starfree" : prefix.trim();
    }

    /** Reproduces the old shared three-second request burst rule. */
    public synchronized void checkBurst(long uid, boolean configured, int silenceSeconds) {
        if (!enabled || !configured) {
            return;
        }
        if (value(silenceKey(uid)) != null) {
            throw new IllegalArgumentException(
                    "\u4f60\u5df2\u88ab\u7981\u8a00\uff0c\u8bf7\u8010\u5fc3\u7b49\u5f85");
        }
        int current = counter(repeatedKey(uid));
        if (current == 0) {
            set(repeatedKey(uid), "1", BURST_TTL_SECONDS);
            return;
        }
        int next = current + 1;
        if (next >= 3) {
            set(silenceKey(uid), "1", silenceSeconds > 0 ? silenceSeconds : 600);
            throw new IllegalArgumentException(
                    "\u4f60\u7684\u8bf7\u6c42\u5b58\u5728\u6076\u610f\u884c\u4e3a\uff0c\u5df2\u6682\u65f6\u7981\u6b62\u64cd\u4f5c\uff01");
        }
        set(repeatedKey(uid), String.valueOf(next), BURST_TTL_SECONDS);
        throw new IllegalArgumentException("\u4f60\u7684\u64cd\u4f5c\u592a\u9891\u7e41\u4e86");
    }

    /** Atomically reserves the old 20-second video-start cooldown. */
    public Reservation reserveVideoStart(long uid) {
        if (!enabled) {
            return Reservation.noop();
        }
        String key = videoKey(uid);
        Boolean acquired = redis.opsForValue().setIfAbsent(
                key, "data", VIDEO_COOLDOWN_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalArgumentException(
                    "\u4e0d\u8981\u6076\u610f\u8df3\u8fc7\u6fc0\u52b1\u89c6\u9891\u54e6\uff01");
        }
        return new Reservation(this, key);
    }

    private synchronized void cancel(String key) {
        redis.delete(key);
    }

    private Object value(String key) {
        return redis.opsForValue().get(key);
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

    private void set(String key, String value, int seconds) {
        ValueOperations<Object, Object> values = redis.opsForValue();
        values.set(key, value, seconds, TimeUnit.SECONDS);
    }

    private String repeatedKey(long uid) {
        return prefix + "_" + uid + "_isRepeated";
    }

    private String silenceKey(long uid) {
        return prefix + "_" + uid + "_silence";
    }

    private String videoKey(long uid) {
        return prefix + "_adsGift_" + uid;
    }

    public static final class Reservation {
        private final LegacyAdsRewardGuard owner;
        private final String key;
        private boolean active;

        private Reservation(LegacyAdsRewardGuard owner, String key) {
            this.owner = owner;
            this.key = key;
            this.active = owner != null;
        }

        static Reservation noop() {
            return new Reservation(null, null);
        }

        /** Releases only failed starts; successful starts keep the cooldown until its TTL. */
        public void cancel() {
            if (active) {
                active = false;
                owner.cancel(key);
            }
        }
    }
}
