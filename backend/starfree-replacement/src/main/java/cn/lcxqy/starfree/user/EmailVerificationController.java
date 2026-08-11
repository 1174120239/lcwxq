package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/** Legacy-compatible form endpoints for registration and password-recovery email codes. */
@RestController
@RequestMapping("/SFreeUsers")
class EmailVerificationController {
    private final EmailVerificationService service;
    private final ObjectMapper mapper;

    EmailVerificationController(EmailVerificationService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Accepts params={"mail":"..."}; success never includes the generated code. */
    @RequestMapping(value = "/RegSendCode", method = {RequestMethod.GET, RequestMethod.POST})
    ApiResponse sendRegistrationCode(@RequestParam Map<String, String> params,
                                     HttpServletRequest request) {
        Map<String, Object> body = RequestValues.jsonObject(mapper, params.get("params"));
        service.sendRegistrationCode(RequestValues.objectText(body, "mail"),
                clientAddress(request));
        return ApiResponse.success("邮件发送成功", null);
    }

    /** Accepts params={"name":"username-or-email"} and sends to the bound mailbox. */
    @RequestMapping(value = "/SendCode", method = {RequestMethod.GET, RequestMethod.POST})
    ApiResponse sendRecoveryCode(@RequestParam Map<String, String> params,
                                 HttpServletRequest request) {
        Map<String, Object> body = RequestValues.jsonObject(mapper, params.get("params"));
        service.sendRecoveryCode(RequestValues.objectText(body, "name"),
                clientAddress(request));
        return ApiResponse.success("邮件发送成功", null);
    }

    private String clientAddress(HttpServletRequest request) {
        String value = request.getHeader("X-Real-IP");
        if (value == null || value.trim().isEmpty()) {
            value = request.getHeader("X-Forwarded-For");
            if (value != null && value.contains(",")) {
                value = value.substring(0, value.indexOf(','));
            }
        }
        if (value == null || value.trim().isEmpty()) {
            value = request.getRemoteAddr();
        }
        return value == null ? "" : value.trim();
    }
}
