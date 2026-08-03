package cn.lcxqy.starfree.content;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyContentAbuseGuardTest {
    private RedisTemplate<Object, Object> redis;
    private ValueOperations<Object, Object> values;
    private LegacyContentAbuseGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        guard = new LegacyContentAbuseGuard(redis, true, "starfree");
    }

    @Test
    void firstRequestCreatesTheThreeSecondLegacyCounter() {
        when(values.get("starfree_7_silence")).thenReturn(null);
        when(values.get("starfree_7_isRepeated")).thenReturn(null);

        guard.checkBurst(7, true, 600);

        verify(values).set("starfree_7_isRepeated", "1", 3, TimeUnit.SECONDS);
    }

    @Test
    void secondRequestIsRejectedAndRefreshesTheCounter() {
        when(values.get("starfree_7_silence")).thenReturn(null);
        when(values.get("starfree_7_isRepeated")).thenReturn("1");

        assertThatThrownBy(() -> guard.checkBurst(7, true, 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("你的操作太频繁了");
        verify(values).set("starfree_7_isRepeated", "2", 3, TimeUnit.SECONDS);
    }

    @Test
    void thirdRequestCreatesTheSharedSilenceKey() {
        when(values.get("starfree_7_silence")).thenReturn(null);
        when(values.get("starfree_7_isRepeated")).thenReturn("2");

        assertThatThrownBy(() -> guard.checkBurst(7, true, 900))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("恶意行为");
        verify(values).set("starfree_7_silence", "1", 900, TimeUnit.SECONDS);
    }

    @Test
    void failedPostReservationRestoresTheSharedCount() {
        when(values.get("starfree_7_postNum")).thenReturn("4", "5");

        LegacyContentAbuseGuard.Reservation reservation = guard.reservePost(7, false, 10, 3);
        reservation.cancel();

        verify(values).set("starfree_7_postNum", "5", 86400, TimeUnit.SECONDS);
        verify(values).set("starfree_7_postNum", "4", 86400, TimeUnit.SECONDS);
    }

    @Test
    void staffDoesNotTouchTheDailyCounter() {
        guard.reservePost(7, true, 10, 9);

        verify(values, never()).get("starfree_7_postNum");
    }
}
