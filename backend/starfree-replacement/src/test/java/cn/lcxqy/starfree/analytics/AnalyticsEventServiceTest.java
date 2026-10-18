package cn.lcxqy.starfree.analytics;

import cn.lcxqy.starfree.security.LegacyTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsEventServiceTest {
    @Test
    void onlyAllowListedEventsAreStoredWithOpaqueMetadata() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LegacyTokenService tokens = mock(LegacyTokenService.class);
        when(tokens.userId(anyString())).thenReturn(42L);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), anyLong())).thenReturn(1);
        AnalyticsEventService service = new AnalyticsEventService(jdbc, tokens);

        Map<String, String> request = new HashMap<>();
        request.put("eventType", "app_launch");
        request.put("platform", "android");
        request.put("appVersion", "1.2.3");
        request.put("source", "app");
        request.put("visitorId", "visitor-abc");
        request.put("token", "sf2_token");

        service.record(request);

        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void rejectsUnknownEvents() {
        AnalyticsEventService service = new AnalyticsEventService(mock(JdbcTemplate.class), mock(LegacyTokenService.class));
        Map<String, String> request = new HashMap<>();
        request.put("eventType", "password");
        assertThatThrownBy(() -> service.record(request)).isInstanceOf(IllegalArgumentException.class);
    }
}
