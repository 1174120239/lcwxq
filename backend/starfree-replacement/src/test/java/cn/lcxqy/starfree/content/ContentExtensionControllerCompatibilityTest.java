package cn.lcxqy.starfree.content;

import cn.lcxqy.starfree.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentExtensionControllerCompatibilityTest {
    @Test
    void allNineExtensionRoutesKeepLegacyGetAndPostCompatibility() throws Exception {
        assertGetAndPost(ContentExtensionController.class, "isComment", Map.class);
        assertGetAndPost(ContentExtensionController.class, "recommend", Map.class);
        assertGetAndPost(ContentExtensionController.class, "top", Map.class);
        assertGetAndPost(ContentExtensionController.class, "swiper", Map.class);
        assertGetAndPost(ContentExtensionController.class, "setField", Map.class);
        assertGetAndPost(ContentExtensionController.class, "contentConfig");
        assertGetAndPost(ContentExtensionController.class, "allData", Map.class);
        assertGetAndPost(ExternalContentFeedController.class,
                "pexels", Map.class, HttpServletRequest.class);
        assertGetAndPost(ExternalContentFeedController.class, "foreverBlog", Map.class);
    }

    @Test
    void isCommentUsesCodeAsTheLegacyBoolean() {
        ContentExtensionService service = mock(ContentExtensionService.class);
        when(service.hasCommentedOrAuthored("token", 7)).thenReturn(false);
        ContentExtensionController controller = new ContentExtensionController(service);

        ApiResponse result = controller.isComment(
                map("token", "token", "key", "7"));

        assertThat(result.getCode()).isZero();
        assertThat(result.getMsg()).isEmpty();
        assertThat(result.getData()).isNull();
    }

    @Test
    void externalControllerReturnsProviderObjectWithoutAnApiEnvelope() {
        ExternalContentFeedService feeds = mock(ExternalContentFeedService.class);
        Map<String, Object> provider = Collections.<String, Object>singletonMap("data", "native");
        when(feeds.foreverBlog(1)).thenReturn(provider);

        Object result = new ExternalContentFeedController(feeds)
                .foreverBlog(Collections.<String, String>emptyMap());

        assertThat(result).isSameAs(provider).isNotInstanceOf(ApiResponse.class);
    }

    private void assertGetAndPost(Class<?> type, String method,
                                  Class<?>... parameterTypes) throws Exception {
        RequestMapping mapping = type.getMethod(method, parameterTypes)
                .getAnnotation(RequestMapping.class);
        assertThat(mapping.method()).containsExactlyInAnyOrder(
                RequestMethod.GET, RequestMethod.POST);
    }

    private Map<String, String> map(String... values) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }
}
