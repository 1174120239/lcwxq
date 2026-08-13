package cn.lcxqy.starfree.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SessionTokenGenerator {
    static final String TOKEN_PREFIX = "sf2_";
    private static final String TOKEN_PATTERN = "^sf2_[0-9a-f]{64}$";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int TOKEN_BYTES = 32;
    private final SecureRandom random;

    public SessionTokenGenerator() {
        this(new SecureRandom());
    }

    SessionTokenGenerator(SecureRandom random) {
        this.random = random;
    }

    public String generate(String username) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        random.nextBytes(randomBytes);

        StringBuilder token = new StringBuilder(TOKEN_PREFIX.length() + TOKEN_BYTES * 2);
        token.append(TOKEN_PREFIX);
        for (byte value : randomBytes) {
            token.append(HEX[(value >>> 4) & 0x0f]);
            token.append(HEX[value & 0x0f]);
        }
        return token.toString();
    }

    public static boolean isCurrentFormat(String token) {
        return token != null && token.matches(TOKEN_PATTERN);
    }
}
