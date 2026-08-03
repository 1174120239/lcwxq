package cn.lcxqy.starfree.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LegacyProjectionCacheInvalidatorTest {
    @Test
    void disabledInvalidatorNeverContactsRedis() {
        RedisConnectionFactory connections = mock(RedisConnectionFactory.class);

        new LegacyProjectionCacheInvalidator(connections, false, "starfree")
                .afterUserWrite(7, "alice");

        verifyNoInteractions(connections);
    }

    @Test
    void decoderAcceptsOnlyCompleteLegacySerializedStrings() {
        byte[] encoded = encoded("starfree_userInfo_7");

        assertThat(LegacyProjectionCacheInvalidator.javaSerializedString(encoded))
                .isEqualTo("starfree_userInfo_7");
        assertThat(LegacyProjectionCacheInvalidator.javaSerializedString(
                "starfree_userInfo_7".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(LegacyProjectionCacheInvalidator.javaSerializedString(new byte[]{
                (byte) 0xac, (byte) 0xed, 0, 5, 0x74, 0, 9, 's'
        })).isNull();
    }

    @Test
    void exactUserKeysDoNotMatchLongerUserIds() {
        assertThat(LegacyProjectionCacheInvalidator.matches(
                "starfree_userInfo_7",
                Collections.singletonList("starfree_userInfo_7"),
                Collections.singletonList("starfree_userList_"))).isTrue();
        assertThat(LegacyProjectionCacheInvalidator.matches(
                "starfree_userInfo_70",
                Collections.singletonList("starfree_userInfo_7"),
                Collections.singletonList("starfree_userList_"))).isFalse();
        assertThat(LegacyProjectionCacheInvalidator.matches(
                "starfree_userList_1_15_null",
                Collections.<String>emptyList(),
                Collections.singletonList("starfree_userList_"))).isTrue();
    }

    @Test
    void shopInvalidationKeepsExactDetailIdsAndListPrefixesSeparate() {
        assertThat(LegacyProjectionCacheInvalidator.matches(
                "starfree_shopInfo7",
                Collections.singletonList("starfree_shopInfo7"),
                Collections.singletonList("starfree_shopList_"))).isTrue();
        assertThat(LegacyProjectionCacheInvalidator.matches(
                "starfree_shopInfo70",
                Collections.singletonList("starfree_shopInfo7"),
                Collections.singletonList("starfree_shopList_"))).isFalse();
        assertThat(LegacyProjectionCacheInvalidator.matches(
                "starfree_shopList_1_15_null",
                Collections.<String>emptyList(),
                Collections.singletonList("starfree_shopList_"))).isTrue();
    }

    private byte[] encoded(String value) {
        byte[] text = value.getBytes(StandardCharsets.UTF_8);
        byte[] raw = new byte[text.length + 7];
        raw[0] = (byte) 0xac;
        raw[1] = (byte) 0xed;
        raw[2] = 0;
        raw[3] = 5;
        raw[4] = 0x74;
        raw[5] = (byte) (text.length >>> 8);
        raw[6] = (byte) text.length;
        System.arraycopy(text, 0, raw, 7, text.length);
        return raw;
    }
}
