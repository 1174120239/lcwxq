package cn.lcxqy.starfree.ads;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdsRewardServiceTest {
    private static final String SIGNATURE =
            "7572d82f3b43bedac54edfc2480ea49da30836c2b9efda8bf84c377cb9707975";

    @Test
    void providerSignatureUsesTheLegacySha256Contract() {
        assertThat(AdsRewardService.validSignature("secret", "tx-1", SIGNATURE)).isTrue();
        assertThat(AdsRewardService.validSignature(
                "secret", "tx-1", SIGNATURE.toUpperCase())).isTrue();
        assertThat(AdsRewardService.validSignature("secret", "tx-2", SIGNATURE)).isFalse();
    }

    @Test
    void emptySecurityKeyIsNeverAccepted() {
        assertThat(AdsRewardService.validSignature("", "tx-1", SIGNATURE)).isFalse();
        assertThat(AdsRewardService.validSignature("   ", "tx-1", SIGNATURE)).isFalse();
    }
}
