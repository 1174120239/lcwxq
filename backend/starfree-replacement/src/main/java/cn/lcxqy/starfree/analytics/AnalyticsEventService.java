package cn.lcxqy.starfree.analytics;

import cn.lcxqy.starfree.security.LegacyTokenService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Privacy-minimized, allow-listed analytics events. */
@Service
public class AnalyticsEventService {
    private static final Set<String> ALLOWED_EVENTS = new HashSet<>(Arrays.asList(
            "app_launch", "app_foreground", "app_update_check", "app_update_download",
            "app_update_install", "app_first_launch", "app_download_click"));
    private static final Set<String> ALLOWED_PLATFORMS = new HashSet<>(Arrays.asList(
            "", "app", "android", "ios"));

    private final JdbcTemplate jdbc;
    private final LegacyTokenService tokens;

    public AnalyticsEventService(JdbcTemplate jdbc, LegacyTokenService tokens) {
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    /**
     * Records one event. Invalid or unavailable analytics must not break the caller's main flow.
     * The endpoint accepts an opaque random visitor id, never a raw device id or token.
     */
    public int record(Map<String, String> request) {
        String eventType = clean(request.get("eventType"), 40);
        if (!ALLOWED_EVENTS.contains(eventType)) {
            throw new IllegalArgumentException("不支持的分析事件");
        }
        String platform = clean(request.get("platform"), 16).toLowerCase();
        if (!ALLOWED_PLATFORMS.contains(platform)) {
            throw new IllegalArgumentException("平台参数不正确");
        }
        String appVersion = clean(request.get("appVersion"), 32);
        String source = clean(request.get("source"), 64);
        String visitorId = clean(request.get("visitorId"), 64);
        Long uid = null;
        try {
            uid = tokens.userId(clean(request.get("token"), 128));
        } catch (RuntimeException ignored) {
            // A missing legacy user table/session store must not make analytics noisy.
        }
        try {
            return jdbc.update("INSERT INTO starfree_activity_events "
                            + "(uid,event_type,platform,app_version,event_source,visitor_id,space_id,created) "
                            + "VALUES (?,?,?,?,?,?,NULL,?)",
                    uid, eventType, platform, appVersion, source, visitorId,
                    System.currentTimeMillis() / 1000L);
        } catch (DataAccessException unavailable) {
            // Analytics is optional during a rolling migration; do not fail normal app requests.
            return 0;
        }
    }

    private String clean(String value, int max) {
        if (value == null) return "";
        String result = value.trim();
        if (result.length() > max) result = result.substring(0, max);
        for (int i = 0; i < result.length(); i++) {
            if (Character.isISOControl(result.charAt(i))) {
                throw new IllegalArgumentException("分析参数不正确");
            }
        }
        return result;
    }
}
