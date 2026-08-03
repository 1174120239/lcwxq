package cn.lcxqy.starfree.proxy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyProxyControllerTest {
    @Test
    void officialPaymentCreationAndCallbacksUseTheEconomyLock() {
        assertThat(LegacyProxyController.requiresEconomyLock("/pay/notify")).isTrue();
        assertThat(LegacyProxyController.requiresEconomyLock("/pay/wxPayNotify")).isTrue();
        assertThat(LegacyProxyController.requiresEconomyLock("/pay/EPayNotify")).isTrue();
        assertThat(LegacyProxyController.requiresEconomyLock("/pay/tokenPayStar")).isTrue();
    }

    @Test
    void unrelatedLegacyRoutesRemainOrdinaryPassThroughRequests() {
        assertThat(LegacyProxyController.requiresEconomyLock("/SFreeChat/sendMsg")).isFalse();
        assertThat(LegacyProxyController.requiresEconomyLock("/pay/payorderList")).isFalse();
        assertThat(LegacyProxyController.requiresEconomyLock("/SFreeAds/adsList")).isFalse();
        assertThat(LegacyProxyController.requiresEconomyLock(
                "/SFreeUserlog/adsGift")).isFalse();
        assertThat(LegacyProxyController.requiresEconomyLock(
                "/SFreeUserlog/adsGiftNotify")).isFalse();
        assertThat(LegacyProxyController.requiresEconomyLock(
                "/SFreeUserlog/adsServerNotify")).isFalse();
    }
}
