package cn.lcxqy.starfree.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/** Reads the Java-serialized verification keys written by the legacy endpoints. */
@Service
public class LegacyRegistrationRedis {
    private static final int BURST_SECONDS = 3;

    private final RedisTemplate<Object, Object> redis;
    private final boolean enabled;
    private final String prefix;

    public LegacyRegistrationRedis(
            RedisTemplate<Object, Object> redis,
            @Value("${legacy.redis.enabled:false}") boolean enabled,
            @Value("${legacy.redis.prefix:starfree}") String prefix) {
        this.redis = redis;
        this.enabled = enabled;
        this.prefix = prefix == null || prefix.trim().isEmpty() ? "starfree" : prefix.trim();
    }

    public String verificationCode(String email) {
        if (!enabled) {
            return null;
        }
        Object value = redis.opsForValue().get(codeKey(email));
        return value == null ? null : String.valueOf(value);
    }

    public void consumeVerificationCode(String email) {
        if (enabled) {
            redis.delete(codeKey(email));
        }
    }

    public void storeVerificationCode(String keyPart, String code, int seconds) {
        requireEnabled();
        set(codeKey(keyPart), code, seconds);
    }

    public void claimEmailSend(String recipientKey, String remoteAddress,
                               int recipientCooldownSeconds, int ipCooldownSeconds) {
        requireEnabled();
        String recipient = normalizeKeyPart(recipientKey);
        String address = normalizeAddress(remoteAddress);
        ValueOperations<Object, Object> values = redis.opsForValue();
        String recipientKeyName = prefix + "_emailCodeRecipient_" + recipient;
        Boolean recipientClaimed = values.setIfAbsent(
                recipientKeyName, "1", recipientCooldownSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(recipientClaimed)) {
            throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        }
        String ipKeyName = prefix + "_emailCodeIp_" + address;
        Boolean ipClaimed = values.setIfAbsent(
                ipKeyName, "1", ipCooldownSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(ipClaimed)) {
            redis.delete(recipientKeyName);
            throw new IllegalArgumentException("请求过于频繁，请稍后再试");
        }
    }

    public void releaseEmailSend(String recipientKey, String remoteAddress) {
        if (!enabled) {
            return;
        }
        redis.delete(prefix + "_emailCodeRecipient_" + normalizeKeyPart(recipientKey));
        redis.delete(prefix + "_emailCodeIp_" + normalizeAddress(remoteAddress));
    }

    public String phoneVerificationCode(String phone) {
        if (!enabled) {
            return null;
        }
        Object value = redis.opsForValue().get(phoneCodeKey(phone));
        return value == null ? null : String.valueOf(value);
    }

    public void consumePhoneVerificationCode(String phone) {
        if (enabled) {
            redis.delete(phoneCodeKey(phone));
        }
    }

    /** Preserves the old IP keys so requests cannot alternate between both backends. */
    public synchronized void checkBurst(String remoteAddress, boolean configured,
                                        int silenceSeconds) {
        if (!enabled || !configured) {
            return;
        }
        String address = remoteAddress == null ? "" : remoteAddress.trim();
        if (address.isEmpty()) {
            address = "unknown";
        }
        if (value(address + "_silence") != null) {
            throw new IllegalArgumentException(
                    "\u4f60\u5df2\u88ab\u7981\u6b62\u8bf7\u6c42\uff0c\u8bf7\u8010\u5fc3\u7b49\u5f85");
        }
        String repeatedKey = address + "_isOperation";
        int current = counter(repeatedKey);
        if (current == 0) {
            set(repeatedKey, "1", BURST_SECONDS);
            return;
        }
        int next = current + 1;
        if (next >= 3) {
            set(address + "_silence", "1", silenceSeconds > 0 ? silenceSeconds : 600);
            throw new IllegalArgumentException(
                    "\u4f60\u7684\u8bf7\u6c42\u5b58\u5728\u6076\u610f\u884c\u4e3a\uff0c10\u5206\u949f\u5185\u7981\u6b62\u64cd\u4f5c\uff01");
        }
        set(repeatedKey, String.valueOf(next), BURST_SECONDS);
        throw new IllegalArgumentException("\u4f60\u7684\u64cd\u4f5c\u592a\u9891\u7e41\u4e86");
    }

    private Object value(String key) {
        return redis.opsForValue().get(key);
    }

    private int counter(String key) {
        Object value = value(key);
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException error) {
            throw new IllegalStateException("Invalid legacy Redis counter at " + key, error);
        }
    }

    private void set(String key, String value, int seconds) {
        ValueOperations<Object, Object> values = redis.opsForValue();
        values.set(key, value, seconds, TimeUnit.SECONDS);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Legacy Redis verification storage is disabled");
        }
    }

    private String normalizeKeyPart(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("验证码接收账号不正确");
        }
        return normalized;
    }

    private String normalizeAddress(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    private String codeKey(String email) {
        return prefix + "_sendCode" + email;
    }

    private String phoneCodeKey(String phone) {
        return prefix + "_sendSMS" + phone;
    }
}
