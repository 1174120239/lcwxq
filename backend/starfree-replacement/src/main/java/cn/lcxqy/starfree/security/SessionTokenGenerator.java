package cn.lcxqy.starfree.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SessionTokenGenerator {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final SecureRandom random;

    public SessionTokenGenerator() {
        this(new SecureRandom());
    }

    SessionTokenGenerator(SecureRandom random) {
        this.random = random;
    }

    public String generate(String username) {
        byte[] randomBytes = new byte[16];
        random.nextBytes(randomBytes);

        StringBuilder token = new StringBuilder(username.length() + 32);
        token.append(username);
        for (byte value : randomBytes) {
            token.append(HEX[(value >>> 4) & 0x0f]);
            token.append(HEX[value & 0x0f]);
        }
        return token.toString();
    }
}
