package cn.lcxqy.starfree.content;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyContentCacheInvalidatorTest {
    @Test
    void decodesOnlyTheLegacyJavaStringWireFormat() {
        byte[] value = "starfree_contentsInfo_17_0".getBytes(StandardCharsets.UTF_8);
        byte[] raw = new byte[value.length + 7];
        raw[0] = (byte) 0xac;
        raw[1] = (byte) 0xed;
        raw[2] = 0;
        raw[3] = 5;
        raw[4] = 0x74;
        raw[5] = (byte) (value.length >>> 8);
        raw[6] = (byte) value.length;
        System.arraycopy(value, 0, raw, 7, value.length);

        assertThat(LegacyContentCacheInvalidator.javaSerializedString(raw))
                .isEqualTo("starfree_contentsInfo_17_0");
        assertThat(LegacyContentCacheInvalidator.javaSerializedString(value)).isNull();
        assertThat(LegacyContentCacheInvalidator.javaSerializedString(new byte[]{
                (byte) 0xac, (byte) 0xed, 0, 5, 0x74, 0, 9, 's'
        })).isNull();
        byte[] withTrailingByte = new byte[raw.length + 1];
        System.arraycopy(raw, 0, withTrailingByte, 0, raw.length);
        assertThat(LegacyContentCacheInvalidator.javaSerializedString(withTrailingByte)).isNull();
    }

    @Test
    void matchesDetailGlobalAndCategoryListProjections() {
        assertThat(LegacyContentCacheInvalidator.matchesContentProjection(
                "starfree_contentsInfo_17_0", "starfree", 17L)).isTrue();
        assertThat(LegacyContentCacheInvalidator.matchesContentProjection(
                "starfree_contentsList_2_created", "starfree", 17L)).isTrue();
        assertThat(LegacyContentCacheInvalidator.matchesContentProjection(
                "starfree_selectContents_9_1_created", "starfree", 17L)).isTrue();
        assertThat(LegacyContentCacheInvalidator.matchesContentProjection(
                "starfree_contentsInfo_18_0", "starfree", 17L)).isFalse();
        assertThat(LegacyContentCacheInvalidator.matchesContentProjection(
                "starfree_userInfo_17", "starfree", 17L)).isFalse();
    }
}
