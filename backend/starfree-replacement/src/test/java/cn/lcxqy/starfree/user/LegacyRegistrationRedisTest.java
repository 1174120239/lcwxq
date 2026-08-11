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
    void storesVerificationCodeWithLegacyKeyAndTtl() {
        registration.storeVerificationCode("user@example.com", "123456", 1800);

        verify(values).set("starfree_sendCodeuser@example.com", "123456",
                1800, TimeUnit.SECONDS);
    }

    @Test
    void claimsRecipientAndIpCooldowns() {
        when(values.setIfAbsent("starfree_emailCodeRecipient_user@example.com",
                "1", 60, TimeUnit.SECONDS)).thenReturn(true);
        when(values.setIfAbsent("starfree_emailCodeIp_203.0.113.7",
                "1", 3, TimeUnit.SECONDS)).thenReturn(true);

        registration.claimEmailSend("user@example.com", "203.0.113.7", 60, 3);

        verify(values).setIfAbsent("starfree_emailCodeRecipient_user@example.com",
                "1", 60, TimeUnit.SECONDS);
        verify(values).setIfAbsent("starfree_emailCodeIp_203.0.113.7",
                "1", 3, TimeUnit.SECONDS);
    }

    @Test
    void recipientCooldownRejectsRepeatedSend() {
        when(values.setIfAbsent("starfree_emailCodeRecipient_user@example.com",
                "1", 60, TimeUnit.SECONDS)).thenReturn(false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registration.claimEmailSend(
                        "user@example.com", "203.0.113.7", 60, 3));

        assertThat(error.getMessage()).contains("发送过于频繁");
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

    @SuppressWarnings("unchecked")
    @Test
    void disabledBridgeRejectsCodeStorage() {
        RedisTemplate<Object, Object> disabledRedis = mock(RedisTemplate.class);
        LegacyRegistrationRedis disabled =
                new LegacyRegistrationRedis(disabledRedis, false, "starfree");

        assertThrows(IllegalStateException.class,
                () -> disabled.storeVerificationCode("user@example.com", "123456", 1800));
        verifyNoInteractions(disabledRedis);
    }
}
