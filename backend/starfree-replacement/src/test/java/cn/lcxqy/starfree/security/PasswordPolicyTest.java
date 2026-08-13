package cn.lcxqy.starfree.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {
    @Test
    void acceptsMixedLetterAndDigitPassword() {
        assertThatCode(() -> PasswordPolicy.requireStrong("NewSecret123"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortSingleClassAndCommonPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.requireStrong("123456"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordPolicy.requireStrong("abcdefgh"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordPolicy.requireStrong("Password123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
