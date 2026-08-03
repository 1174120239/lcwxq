package cn.lcxqy.starfree.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

@Component
public class PhpassPasswordVerifier {
    private static final String ITOA64 = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int LEGACY_ITERATION_LOG2 = 8;

    private final SecureRandom random;

    public PhpassPasswordVerifier() {
        this(new SecureRandom());
    }

    PhpassPasswordVerifier(SecureRandom random) {
        this.random = random;
    }

    /** Generates the portable PHPass format used by the closed Typecho backend. */
    public String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        byte[] saltBytes = new byte[6];
        random.nextBytes(saltBytes);
        String salt = encode64(saltBytes, saltBytes.length);
        String setting = "$P$" + ITOA64.charAt(LEGACY_ITERATION_LOG2 + 5) + salt;
        String result = cryptPrivate(password, setting);
        if (result == null) {
            throw new IllegalStateException("Could not generate a PHPass hash");
        }
        return result;
    }

    public boolean matches(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }
        String calculated = cryptPrivate(password, storedHash);
        if (calculated == null) {
            return false;
        }
        return MessageDigest.isEqual(
                calculated.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private String cryptPrivate(String password, String setting) {
        if (setting.length() < 12
                || !(setting.startsWith("$P$") || setting.startsWith("$H$"))) {
            return null;
        }

        int countLog2 = ITOA64.indexOf(setting.charAt(3));
        if (countLog2 < 7 || countLog2 > 30) {
            return null;
        }

        String salt = setting.substring(4, 12);
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        MessageDigest md5 = md5();
        md5.update(salt.getBytes(StandardCharsets.UTF_8));
        byte[] hash = md5.digest(passwordBytes);

        int count = 1 << countLog2;
        do {
            md5.reset();
            md5.update(hash);
            hash = md5.digest(passwordBytes);
        } while (--count > 0);

        return setting.substring(0, 12) + encode64(hash, 16);
    }

    private String encode64(byte[] input, int count) {
        StringBuilder output = new StringBuilder();
        int index = 0;
        do {
            int value = input[index++] & 0xff;
            output.append(ITOA64.charAt(value & 0x3f));
            if (index < count) {
                value |= (input[index] & 0xff) << 8;
            }
            output.append(ITOA64.charAt((value >> 6) & 0x3f));
            if (index++ >= count) {
                break;
            }
            if (index < count) {
                value |= (input[index] & 0xff) << 16;
            }
            output.append(ITOA64.charAt((value >> 12) & 0x3f));
            if (index++ >= count) {
                break;
            }
            output.append(ITOA64.charAt((value >> 18) & 0x3f));
        } while (index < count);
        return output.toString();
    }

    private MessageDigest md5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 is unavailable", exception);
        }
    }
}
