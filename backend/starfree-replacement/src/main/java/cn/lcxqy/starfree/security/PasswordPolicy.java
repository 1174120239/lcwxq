package cn.lcxqy.starfree.security;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Shared policy for every operation that sets a new user password. */
public final class PasswordPolicy {
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;
    private static final Set<String> COMMON_PASSWORDS = new HashSet<>(Arrays.asList(
            "password1", "password123", "qwerty123", "abc12345", "admin123",
            "welcome1", "iloveyou1", "letmein1", "11111111a", "12345678a"));

    private PasswordPolicy() {
    }

    public static void requireStrong(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw invalid();
        }
        boolean letter = false;
        boolean digit = false;
        for (int index = 0; index < password.length(); index++) {
            char value = password.charAt(index);
            if (Character.isISOControl(value)) {
                throw invalid();
            }
            letter |= Character.isLetter(value);
            digit |= Character.isDigit(value);
        }
        if (!letter || !digit || COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("密码至少8位，且必须同时包含字母和数字");
    }
}
