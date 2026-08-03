package cn.lcxqy.starfree.content;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LegacyContentReadTrackerTest {

    @Test
    void sharesAndRefreshesTheExactLegacyReadKey() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        ValueOperations<Object, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        String key = "starfree_isRead_203.0.113.7_agent_11";
        when(values.get(key)).thenReturn(null, "yes");
        LegacyContentReadTracker tracker = new LegacyContentReadTracker(redis, true, "starfree");

        assertThat(tracker.firstRead(11, "203.0.113.7", "agent")).isTrue();
        assertThat(tracker.firstRead(11, "203.0.113.7", "agent")).isFalse();
        verify(values, org.mockito.Mockito.times(2))
                .set(key, "yes", 900L, TimeUnit.SECONDS);
    }

    @Test
    void disabledLocalModeDoesNotRequireRedis() {
        RedisTemplate<Object, Object> redis = mock(RedisTemplate.class);
        LegacyContentReadTracker tracker = new LegacyContentReadTracker(redis, false, "starfree");

        assertThat(tracker.firstRead(11, "127.0.0.1", null)).isTrue();
        verifyNoInteractions(redis);
    }
}
