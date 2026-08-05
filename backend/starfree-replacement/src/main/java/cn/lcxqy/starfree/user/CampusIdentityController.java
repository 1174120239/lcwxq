package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/SFreeUsers")
public class CampusIdentityController {
    private final CampusIdentityService service;
    private final ObjectMapper mapper;

    public CampusIdentityController(CampusIdentityService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Public enabled campus and admission-year options for ordinary registration. */
    @RequestMapping("/campusIdentityOptions")
    public ApiResponse options() {
        return ApiResponse.success("", service.registrationOptions());
    }

    /** Staff list including disabled options and historical usage counts. */
    @RequestMapping("/campusIdentityManage")
    public ApiResponse manage(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", service.manageOptions(RequestValues.text(params, "token")));
    }

    /** Staff create, rename, reorder, enable or disable. Hard deletion is intentionally absent. */
    @RequestMapping("/campusIdentitySave")
    public ApiResponse save(@RequestParam Map<String, String> params) {
        Map<String, Object> body = RequestValues.jsonObject(mapper, params.get("params"));
        return ApiResponse.success("保存成功",
                service.save(RequestValues.text(params, "token"), body));
    }
}
