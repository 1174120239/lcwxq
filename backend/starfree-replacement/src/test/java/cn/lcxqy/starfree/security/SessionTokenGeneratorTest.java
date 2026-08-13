package cn.lcxqy.starfree.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenGeneratorTest {
    @Test
    void tokenHasCurrentVersionAndRandomPayloadWithoutUsername() {
        SessionTokenGenerator generator = new SessionTokenGenerator();

        String first = generator.generate("public-account-name");
        String second = generator.generate("public-account-name");

        assertThat(first).matches("sf2_[0-9a-f]{60}")
                .hasSize(64)
                .doesNotContain("public-account-name")
                .isNotEqualTo(second);
        assertThat(SessionTokenGenerator.isCurrentFormat(first)).isTrue();
        assertThat(SessionTokenGenerator.isCurrentFormat("legacy-token")).isFalse();
    }
}
