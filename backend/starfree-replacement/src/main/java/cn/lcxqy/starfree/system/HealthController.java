package cn.lcxqy.starfree.system;

import cn.lcxqy.starfree.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库就绪检查和服务根路径。
 *
 * <p>这两个端点不做登录鉴权，只应通过本机、监控系统或受控的 Nginx 路由访问。
 * {@code /health} 会真实查询数据库，适合判断服务是否“可接流量”；它不是单纯的进程存活检查。
 */
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * GET {@code /health}：数据库就绪检查。
     *
     * <p>参数：无。鉴权：无。返回统一 {@code ApiResponse}，data 中包含 {@code status}
     * 和当前 schema 名 {@code database}。方法会执行 {@code SELECT 1} 和
     * {@code SELECT DATABASE()}；数据库不可用时请求会进入全局异常处理，而不会伪报 UP。
     * 注意：响应会暴露 schema 名，公网若不需要该信息应由 Nginx 限制访问。
     */
    @GetMapping("/health")
    public ApiResponse health() {
        Integer dual = jdbc.queryForObject("SELECT 1", Integer.class);
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", dual != null && dual == 1 ? "UP" : "DOWN");
        data.put("database", database);
        return ApiResponse.success(data);
    }

    /**
     * GET {@code /}：返回替代后端的固定服务标识。
     *
     * <p>参数和鉴权均无，也不访问 MySQL/Redis，所以只能证明 Spring MVC 可以响应，
     * 不能用于判断数据库是否就绪；数据库探测请使用 {@code /health}。
     */
    @GetMapping("/")
    public ApiResponse root() {
        return ApiResponse.success(Collections.singletonMap("service", "starfree-replacement"));
    }
}
