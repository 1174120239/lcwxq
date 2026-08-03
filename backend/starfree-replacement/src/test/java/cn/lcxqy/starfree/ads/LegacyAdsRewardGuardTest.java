package cn.lcxqy.starfree.ads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyAdsRewardGuardTest {
    private RedisTemplate<Object, Object> redis;
    private ValueOperations<Object, Object> values;
    private LegacyAdsRewardGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        guard = new LegacyAdsRewardGuard(redis, true, "starfree");
    }

    @Test
    void videoStartUsesTheJavaSerializedLegacyKeyAndTwentySecondTtl() {
        when(values.setIfAbsent(
                "starfree_adsGift_7", "data", 20, TimeUnit.SECONDS)).thenReturn(true);

        guard.reserveVideoStart(7);

        verify(values).setIfAbsent(
                "starfree_adsGift_7", "data", 20, TimeUnit.SECONDS);
    }

    @Test
    void duplicateVideoStartIsRejected() {
        when(values.setIfAbsent(
                "starfree_adsGift_7", "data", 20, TimeUnit.SECONDS)).thenReturn(false);

        assertThatThrownBy(() -> guard.reserveVideoStart(7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\u6fc0\u52b1\u89c6\u9891");
    }

    @Test
    void failedStartCanReleaseItsCooldownReservation() {
        when(values.setIfAbsent(
                "starfree_adsGift_7", "data", 20, TimeUnit.SECONDS)).thenReturn(true);

        LegacyAdsRewardGuard.Reservation reservation = guard.reserveVideoStart(7);
        reservation.cancel();

        verify(redis).delete("starfree_adsGift_7");
    }

    @Test
    void thirdBurstRequestCreatesTheSharedSilenceKey() {
        when(values.get("starfree_7_silence")).thenReturn(null);
        when(values.get("starfree_7_isRepeated")).thenReturn("2");

        assertThatThrownBy(() -> guard.checkBurst(7, true, 900))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\u6076\u610f\u884c\u4e3a");
        verify(values).set("starfree_7_silence", "1", 900, TimeUnit.SECONDS);
    }
}
