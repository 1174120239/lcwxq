package cn.lcxqy.starfree.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsControllerCompatibilityTest {
    @Test
    void exposesEventIngestionRoute() throws Exception {
        Method method = AnalyticsController.class.getMethod("event", Map.class);
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).contains("/event");
    }
}
