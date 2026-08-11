package cn.lcxqy.starfree.invitation;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationControllerCompatibilityTest {
    @Test
    void exposesPublicConfigAndAuthenticatedDashboard() throws Exception {
        assertRoute("config");
        assertRoute("me");
    }

    private void assertRoute(String route) throws Exception {
        Method method = InvitationController.class.getMethod(route, Map.class);
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(Arrays.asList(mapping.value())).contains("/" + route);
    }
}
