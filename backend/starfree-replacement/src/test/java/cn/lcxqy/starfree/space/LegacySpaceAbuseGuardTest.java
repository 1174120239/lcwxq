package cn.lcxqy.starfree.space;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacySpaceAbuseGuardTest {
    private RedisTemplate<Object, Object> redis;
    private ValueOperations<Object, Object> values;
    private LegacySpaceAbuseGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        guard = new LegacySpaceAbuseGuard(redis, true, "starfree");
    }

    @Test
    void firstRobotRequestCreatesLegacyFourSecondCounter() {
        when(values.get("starfree_7_isAddSpace")).thenReturn(null);

        guard.checkRobotBurst(7L, true, 600);

        verify(values).set("starfree_7_isAddSpace", "1", 4, TimeUnit.SECONDS);
        verify(redis, never()).delete("starfree_7_isAddSpace");
    }

    @Test
    void repeatedRobotRequestRefreshesFiveSecondWindowAndRejects() {
        when(values.get("starfree_7_isAddSpace")).thenReturn("1");

        assertThatThrownBy(() -> guard.checkRobotBurst(7L, true, 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u4f60\u7684\u64cd\u4f5c\u592a\u9891\u7e41\u4e86");
        verify(values).set("starfree_7_isAddSpace", "2", 5, TimeUnit.SECONDS);
    }

    @Test
    void fourthRobotRequestCreatesConfiguredSilence() {
        when(values.get("starfree_7_isAddSpace")).thenReturn("3");

        assertThatThrownBy(() -> guard.checkRobotBurst(7L, true, 900))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("\u4f60\u7684\u64cd\u4f5c\u8fc7\u4e8e\u9891\u7e41\uff0c\u5df2\u88ab\u7981\u8a00");
        verify(values).set("starfree_7_silence", "1", 900, TimeUnit.SECONDS);
    }

    @Test
    void fourthForbiddenStrikeCreatesInterceptSilence() {
        when(values.get("starfree_7_isIntercept")).thenReturn("3");

        assertThat(guard.recordForbidden(7L, 3600)).isTrue();

        verify(values).set("starfree_7_silence", "1", 3600, TimeUnit.SECONDS);
    }

    @Test
    void failedPostReservationRestoresSharedLegacyCounter() {
        when(values.get("starfree_7_spaceNum")).thenReturn("2", "5");

        LegacySpaceAbuseGuard.PostReservation reservation =
                guard.reservePost(7L, false, 10, 4);
        reservation.cancel();

        verify(values).set("starfree_7_spaceNum", "5", 86400, TimeUnit.SECONDS);
        verify(values).set("starfree_7_spaceNum", "4", 86400, TimeUnit.SECONDS);
    }

    @Test
    void legacyCounterCanBlockEvenWhenDatabaseCountIsLower() {
        when(values.get("starfree_7_spaceNum")).thenReturn("10");

        assertThatThrownBy(() -> guard.reservePost(7L, false, 10, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24\u5c0f\u65f6");
        verify(values, never()).set("starfree_7_spaceNum", "11", 86400, TimeUnit.SECONDS);
    }
}
