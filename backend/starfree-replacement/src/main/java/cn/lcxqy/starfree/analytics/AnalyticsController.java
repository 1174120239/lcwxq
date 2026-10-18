package cn.lcxqy.starfree.analytics;

import cn.lcxqy.starfree.api.ApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public, allow-listed event ingestion for lifecycle and download attribution. */
@RestController
@RequestMapping("/SFreeAnalytics")
public class AnalyticsController {
    private final AnalyticsEventService events;

    public AnalyticsController(AnalyticsEventService events) {
        this.events = events;
    }

    @RequestMapping(value = "/event", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse event(@RequestParam Map<String, String> request) {
        events.record(request);
        return ApiResponse.success("", 1);
    }
}
