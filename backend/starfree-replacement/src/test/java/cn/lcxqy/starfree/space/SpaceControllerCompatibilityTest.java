package cn.lcxqy.starfree.space;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceControllerCompatibilityTest {
    @Test
    void followEndpointKeepsLegacyAndFrontendAlias() throws Exception {
        Method method = SpaceController.class.getMethod("followed", java.util.Map.class);
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);

        assertThat(mapping.value()).containsExactlyInAnyOrder("/followSpace", "/myFollowSpace");
    }

    @Test
    void controllerKeepsLegacyBasePath() {
        RequestMapping mapping = SpaceController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/SFreeSpace");
    }

    @Test
    void dynamicReplyHistoryHasDedicatedRoute() throws Exception {
        Method method = SpaceController.class.getMethod("userReplies", java.util.Map.class);
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/userReplies");
    }

    @Test
    void dynamicReportsExposeSubmitListAndReviewRoutes() throws Exception {
        assertRoute("reportAdd", "/reportAdd");
        assertRoute("reportList", "/reportList");
        assertRoute("reportReview", "/reportReview");
    }

    private void assertRoute(String methodName, String route) throws Exception {
        Method method = SpaceController.class.getMethod(methodName, java.util.Map.class);
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly(route);
    }
}
