package cn.lcxqy.starfree.anonymous;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 匿名发帖接口（原 ng_music 插件功能的本土化实现）。
 *
 * <p>公开端：{@code config} 只返回是否开放与分类方式；{@code post} 与 contentsAdd 表单结构
 * 兼容，帖子以专用匿名账号发布；{@code owner} 仅帖主或 staff 可查询真实发布者。管理端：
 * {@code admin/config} 仅 administrator 可读写匿名账号、审核开关、分类方式与固定分类。
 */
@RestController
@RequestMapping("/SFreeAnonymous")
public class AnonymousController {
    private final AnonymousPostService service;

    @Autowired
    public AnonymousController(AnonymousPostService service) {
        this.service = service;
    }

    /** ANY {@code /SFreeAnonymous/config}：匿名功能公开配置。 */
    @RequestMapping("/config")
    public ApiResponse config() {
        return ApiResponse.success("请求成功", service.publicConfig());
    }

    /** ANY {@code /SFreeAnonymous/post}：匿名发布动态（兼容旧前端的 GET 表单）。 */
    @RequestMapping("/post")
    public ApiResponse post(@RequestParam MultiValueMap<String, String> form,
                            HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        form.forEach((key, values) -> params.put(key, values == null || values.isEmpty()
                ? "" : values.get(0)));
        return ApiResponse.success("匿名动态发布成功", service.post(params, clientIp(request)));
    }

    /** ANY {@code /SFreeAnonymous/owner}：查询匿名动态真实发布者（仅动态主人或 staff）。 */
    @RequestMapping("/owner")
    public ApiResponse owner(@RequestParam Map<String, String> params) {
        long sid = RequestValues.integer(params, "sid",
                RequestValues.integer(params, "id", RequestValues.integer(params, "cid", 0)));
        long uid = service.owner(RequestValues.text(params, "token"), sid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uid", uid);
        return ApiResponse.success("查询成功", data);
    }

    /** ANY {@code /SFreeAnonymous/admin/config}：管理员读取匿名配置。 */
    @RequestMapping("/admin/config")
    public ApiResponse adminConfig(@RequestParam Map<String, String> params) {
        return ApiResponse.success("请求成功",
                service.adminConfig(RequestValues.text(params, "token")));
    }

    /** POST {@code /SFreeAnonymous/admin/config}：管理员更新匿名配置。 */
    @PostMapping("/admin/config")
    public ApiResponse updateAdminConfig(@RequestParam MultiValueMap<String, String> form) {
        Map<String, String> params = new LinkedHashMap<>();
        form.forEach((key, values) -> params.put(key, values == null || values.isEmpty()
                ? "" : values.get(0)));
        service.updateAdminConfig(RequestValues.text(params, "token"), params);
        return ApiResponse.success("保存成功", null);
    }

    private String clientIp(HttpServletRequest request) {
        String value = request.getHeader("X-Real-IP");
        if (value == null || value.trim().isEmpty()) {
            value = request.getHeader("X-Forwarded-For");
        }
        if (value != null && !value.trim().isEmpty()) {
            int comma = value.indexOf(',');
            return (comma >= 0 ? value.substring(0, comma) : value).trim();
        }
        return request.getRemoteAddr();
    }
}
