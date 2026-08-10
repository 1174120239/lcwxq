package cn.lcxqy.starfree.bot;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/SFreeBot")
public class BotController {
    private static final Logger LOG = LoggerFactory.getLogger(BotController.class);

    private final BotService bot;

    public BotController(BotService bot) {
        this.bot = bot;
    }

    @RequestMapping(value = "/config", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse config(@RequestParam Map<String, String> params) {
        return ApiResponse.success(bot.config(params));
    }

    @RequestMapping(value = "/bindChallenge", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse bindChallenge(@RequestParam Map<String, String> params,
                                     HttpServletRequest request) {
        Map<String, String> enriched = new LinkedHashMap<>(params);
        enriched.put("ip", clientAddress(request));
        enriched.put("userAgent", request.getHeader("User-Agent"));
        return ApiResponse.success(bot.bindChallenge(enriched, externalBase(request)));
    }

    @RequestMapping(value = "/bindPage", method = RequestMethod.GET,
            produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> bindPage(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(bot.bindPage(RequestValues.text(params, "token")));
    }

    @RequestMapping(value = "/bindLogin", method = RequestMethod.POST,
            produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> bindLogin(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(bot.bindLogin(params));
    }

    @RequestMapping(value = "/meStatus", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse meStatus(@RequestParam Map<String, String> params) {
        return ApiResponse.success(bot.meStatus(params));
    }

    @RequestMapping(value = "/signin", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse signin(@RequestParam Map<String, String> params) {
        return ApiResponse.success("签到成功", bot.signin(params));
    }

    @RequestMapping(value = "/addSpace", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse addSpace(@RequestParam Map<String, String> params,
                                HttpServletRequest request) {
        Map<String, Object> result = bot.addSpace(params, clientAddress(request));
        return ApiResponse.success(String.valueOf(result.get("msg")), result);
    }

    @RequestMapping(value = "/updateProfile", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse updateProfile(@RequestParam Map<String, String> params) {
        return ApiResponse.success("操作成功", bot.updateProfile(params));
    }

    @RequestMapping(value = "/latestSpaces", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse latestSpaces(@RequestParam Map<String, String> params) {
        return ApiResponse.success(bot.latestSpaces(params));
    }

    @RequestMapping(value = "/registerGroup", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse registerGroup(@RequestParam Map<String, String> params) {
        return ApiResponse.success("群同步已启用", bot.registerGroup(params));
    }

    @RequestMapping(value = "/delivery", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse delivery(@RequestParam Map<String, String> params) {
        return ApiResponse.success(bot.delivery(params));
    }

    @RequestMapping(value = "/qzoneBatch", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse qzoneBatch(@RequestParam Map<String, String> params) {
        return ApiResponse.success(bot.qzoneBatch(params));
    }

    @RequestMapping(value = "/qzoneDelivery", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse qzoneDelivery(@RequestParam Map<String, String> params) {
        return ApiResponse.success(bot.qzoneDelivery(params));
    }

    @RequestMapping(value = "/chat", method = RequestMethod.POST)
    public ApiResponse chat(@RequestParam Map<String, String> params) {
        try {
            return ApiResponse.success(bot.chat(params));
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            LOG.error("QQ bot chat proxy failed", error);
            return ApiResponse.failure("AI 回复失败，请稍后再试");
        }
    }

    private String externalBase(HttpServletRequest request) {
        String proto = header(request, "X-Forwarded-Proto");
        if (proto.isEmpty()) {
            proto = request.getScheme();
        }
        String host = header(request, "X-Forwarded-Host");
        if (host.isEmpty()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.trim().isEmpty()) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && port != 80 && port != 443) {
                host = host + ":" + port;
            }
        }
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        return proto + "://" + host + context;
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = header(request, "X-Forwarded-For");
        if (!forwarded.isEmpty()) {
            int comma = forwarded.indexOf(',');
            return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded;
        }
        String realIp = header(request, "X-Real-IP");
        return realIp.isEmpty() ? request.getRemoteAddr() : realIp;
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }
}
