package cn.lcxqy.starfree.space;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Shares the closed backend's Space abuse-control keys while both backends are live.
 *
 * <p>The legacy RedisTemplate stores keys and values with Java serialization. Using the same
 * RedisTemplate here is intentional: StringRedisTemplate would create visually similar but
 * completely different raw Redis keys.</p>
 */
@Service
public class LegacySpaceAbuseGuard {
    private static final int ROBOT_FIRST_TTL_SECONDS = 4;
    private static final int ROBOT_REPEAT_TTL_SECONDS = 5;
    private static final int INTERCEPT_COUNTER_TTL_SECONDS = 600;
    private static final int POST_COUNTER_TTL_SECONDS = 86400;

    private final RedisTemplate<Object, Object> redis;
    private final boolean enabled;
    private final String prefix;

    public LegacySpaceAbuseGuard(
            RedisTemplate<Object, Object> redis,
            @Value("${legacy.redis.enabled:false}") boolean enabled,
            @Value("${legacy.redis.prefix:starfree}") String prefix) {
        this.redis = redis;
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.trim().isEmpty() ? "starfree" : prefix.trim();
    }

    public void requireNotSilenced(long uid) {
        if (enabled && value(silenceKey(uid)) != null) {
            throw new IllegalArgumentException(
                    "\u4f60\u7684\u64cd\u4f5c\u592a\u9891\u7e41\u4e86\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5");
        }
    }

    /**
     * Reproduces the old burst rule: the first request gets four seconds, subsequent requests
     * refresh a five-second window, and the fourth request creates the configured silence key.
     */
    public synchronized void checkRobotBurst(long uid, boolean configured, int silenceSeconds) {
        if (!enabled || !configured) {
            return;
        }
        String key = robotKey(uid);
        int current = counter(key);
        if (current == 0) {
            setCounter(key, 1, ROBOT_FIRST_TTL_SECONDS);
            return;
        }

        int next = current + 1;
        if (next >= 4) {
            setCounter(silenceKey(uid), 1, positive(silenceSeconds, 600));
            throw new IllegalArgumentException(
                    "\u4f60\u7684\u64cd\u4f5c\u8fc7\u4e8e\u9891\u7e41\uff0c\u5df2\u88ab\u7981\u8a00");
        }
        setCounter(key, next, ROBOT_REPEAT_TTL_SECONDS);
        throw new IllegalArgumentException("\u4f60\u7684\u64cd\u4f5c\u592a\u9891\u7e41\u4e86");
    }

    /** Records one forbidden-word strike and reports whether this strike caused a silence. */
    public synchronized boolean recordForbidden(long uid, int silenceSeconds) {
        if (!enabled) {
            return false;
        }
        String key = interceptKey(uid);
        int next = counter(key) + 1;
        if (next >= 4) {
            setCounter(silenceKey(uid), 1, positive(silenceSeconds, 3600));
            return true;
        }
        setCounter(key, next, INTERCEPT_COUNTER_TTL_SECONDS);
        return false;
    }

    /**
     * Reserves one legacy daily-post slot immediately before the MyISAM insert.
     *
     * <p>The database count remains authoritative in the replacement. The Redis value is raised
     * to at least that count so a request routed back to the old backend cannot bypass posts made
     * through this backend. A failed insert must call {@link PostReservation#cancel()}.</p>
     */
    public synchronized PostReservation reservePost(long uid, boolean staff, int postMax,
                                                     int persistedCount) {
        if (!enabled || staff || postMax <= 0) {
            return PostReservation.noop();
        }
        String key = postCountKey(uid);
        int previous = counter(key);
        int baseline = Math.max(previous, Math.max(0, persistedCount));
        if (baseline >= postMax) {
            throw new IllegalArgumentException(
                    "\u4f60\u5df2\u8d85\u8fc7\u6700\u5927\u53d1\u5e03\u6570\u91cf\u9650\u5236\uff0c\u8bf7\u60a824\u5c0f\u65f6\u540e\u518d\u64cd\u4f5c");
        }
        setCounter(key, baseline + 1, POST_COUNTER_TTL_SECONDS);
        return new PostReservation(this, key);
    }

    private synchronized void cancel(String key) {
        int current = counter(key);
        if (current <= 1) {
            redis.delete(key);
            return;
        }
        setCounter(key, current - 1, POST_COUNTER_TTL_SECONDS);
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
            // Do not overwrite an unknown legacy value and silently disable a protection.
            throw new IllegalStateException("Invalid legacy Redis counter at " + key, error);
        }
    }

    private void setCounter(String key, int value, int ttlSeconds) {
        ValueOperations<Object, Object> values = redis.opsForValue();
        values.set(key, String.valueOf(value), ttlSeconds, TimeUnit.SECONDS);
    }

    private int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private String silenceKey(long uid) {
        return prefix + "_" + uid + "_silence";
    }

    private String robotKey(long uid) {
        return prefix + "_" + uid + "_isAddSpace";
    }

    private String interceptKey(long uid) {
        return prefix + "_" + uid + "_isIntercept";
    }

    private String postCountKey(long uid) {
        return prefix + "_" + uid + "_spaceNum";
    }

    public static class PostReservation {
        private final LegacySpaceAbuseGuard owner;
        private final String key;
        private boolean active;

        private PostReservation(LegacySpaceAbuseGuard owner, String key) {
            this.owner = owner;
            this.key = key;
            this.active = owner != null;
        }

        static PostReservation noop() {
            return new PostReservation(null, null);
        }

        public void cancel() {
            if (active) {
                active = false;
                owner.cancel(key);
            }
        }
    }
}
