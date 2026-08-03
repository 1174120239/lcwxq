package cn.lcxqy.starfree.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContentAddRoutingPolicyTest {
    private ContentAddRoutingPolicy routing;
    private Map<String, String> request;

    @BeforeEach
    void setUp() {
        routing = new ContentAddRoutingPolicy(new ObjectMapper());
        request = new HashMap<>();
        request.put("params", "{\"title\":\"ordinary\",\"sid\":-1}");
    }

    @Test
    void ordinaryPostsAndVideosUseTheReplacement() {
        assertThat(routing.useReplacement(request)).isTrue();

        request.put("params", "{\"title\":\"video\",\"type\":\"video\"}");
        assertThat(routing.useReplacement(request)).isTrue();
    }

    @Test
    void legacyFeaturesAreDelegated() {
        for (String flag : new String[]{"isPaid", "isDraft", "isSpace"}) {
            request.put(flag, "1");
            assertThat(routing.useReplacement(request)).isFalse();
            request.remove(flag);
        }

        request.put("params", "{\"title\":\"shop\",\"sid\":7}");
        assertThat(routing.useReplacement(request)).isFalse();

        request.put("params", "{\"title\":\"plugin\",\"type\":\"plugin\"}");
        assertThat(routing.useReplacement(request)).isFalse();
    }

    @Test
    void AmbiguousRequestsAreDelegatedConservatively() {
        request.put("isPaid", "unexpected");
        assertThat(routing.useReplacement(request)).isFalse();

        request.clear();
        request.put("params", "not-json");
        assertThat(routing.useReplacement(request)).isFalse();

        request.put("params", "{\"sid\":\"not-a-number\"}");
        assertThat(routing.useReplacement(request)).isFalse();
    }

    @Test
    void ordinaryUpdatesStayLocalWhileClosedUpdateFeaturesAreDelegated() {
        request.put("params", "{\"cid\":9,\"title\":\"ordinary\",\"sid\":-1}");
        assertThat(routing.useReplacementUpdate(request)).isTrue();

        request.put("isDraft", "1");
        assertThat(routing.useReplacementUpdate(request)).isFalse();
        request.remove("isDraft");

        request.put("isPaid", "1");
        assertThat(routing.useReplacementUpdate(request)).isFalse();
        request.remove("isPaid");

        request.put("params", "{\"cid\":9,\"title\":\"shop\",\"sid\":3}");
        assertThat(routing.useReplacementUpdate(request)).isFalse();

        request.put("params", "{\"cid\":9,\"title\":\"unknown\",\"type\":\"plugin\"}");
        assertThat(routing.useReplacementUpdate(request)).isFalse();
    }
}
