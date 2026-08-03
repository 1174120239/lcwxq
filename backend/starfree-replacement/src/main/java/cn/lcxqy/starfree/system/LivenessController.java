package cn.lcxqy.starfree.system;

import cn.lcxqy.starfree.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 进程存活检查；刻意与会访问数据库的 {@link HealthController} 分开。 */
@RestController
public class LivenessController {

    /**
     * GET {@code /health/live}：轻量存活探针。
     *
     * <p>参数：无。鉴权：无。返回统一 {@code ApiResponse}；不访问 MySQL、Redis 或旧 API。
     * 该端点只表示 JVM 和 Spring MVC 仍能处理请求，不能证明业务依赖可用。
     */
    @GetMapping("/health/live")
    public ApiResponse live() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "starfree-replacement");
        return ApiResponse.success(data);
    }
}
