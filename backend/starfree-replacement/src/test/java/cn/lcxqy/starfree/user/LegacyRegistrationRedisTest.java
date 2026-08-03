package cn.lcxqy.starfree.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LegacyRegistrationRedisTest {
    private RedisTemplate<Object, Object> redis;
    private ValueOperations<Object, Object> values;
    private LegacyRegistrationRedis registration;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        registration = new LegacyRegistrationRedis(redis, true, "starfree");
    }

    @Test
    void readsAndConsumesTheLegacyVerificationCodeKey() {
        when(values.get("starfree_sendCodeuser@example.com")).thenReturn("123456");

        assertThat(registration.verificationCode("user@example.com")).isEqualTo("123456");
        registration.consumeVerificationCode("user@example.com");

        verify(redis).delete("starfree_sendCodeuser@example.com");
    }

    @Test
    void readsAndConsumesTheLegacyPhoneVerificationCodeKey() {
        when(values.get("starfree_sendSMS13800138000")).thenReturn("654321");

        assertThat(registration.phoneVerificationCode("13800138000")).isEqualTo("654321");
        registration.consumePhoneVerificationCode("13800138000");

        verify(redis).delete("starfree_sendSMS13800138000");
    }

    @Test
    void thirdBurstRequestCreatesTheLegacyIpSilenceKey() {
        String repeated = "203.0.113.7_isOperation";
        when(values.get("203.0.113.7_silence")).thenReturn(null);
        when(values.get(repeated)).thenReturn(null, "1", "2");

        registration.checkBurst("203.0.113.7", true, 900);
        assertThrows(IllegalArgumentException.class,
                () -> registration.checkBurst("203.0.113.7", true, 900));
        assertThrows(IllegalArgumentException.class,
                () -> registration.checkBurst("203.0.113.7", true, 900));

        verify(values).set("203.0.113.7_silence", "1", 900, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    @Test
    void disabledBridgeNeverTouchesRedis() {
        RedisTemplate<Object, Object> disabledRedis = mock(RedisTemplate.class);
        LegacyRegistrationRedis disabled =
                new LegacyRegistrationRedis(disabledRedis, false, "starfree");

        assertThat(disabled.verificationCode("user@example.com")).isNull();
        disabled.consumeVerificationCode("user@example.com");
        disabled.checkBurst("203.0.113.7", true, 600);

        verifyNoInteractions(disabledRedis);
    }
}
