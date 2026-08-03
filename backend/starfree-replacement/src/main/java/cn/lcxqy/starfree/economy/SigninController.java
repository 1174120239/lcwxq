package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.api.ApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 七日签到经济接口，使用独立签到表并分别奖励 assets 与 experience。 */
@RestController
@RequestMapping("/SFreeEconomy")
public class SigninController {
    private final SigninService signin;

    public SigninController(SigninService signin) {
        this.signin = signin;
    }

    /**
     * ANY {@code /SFreeEconomy/signinConfig}：公开读取第 1 至 7 天奖励配置。
     *
     * <p>无参数、无鉴权。直接返回含 {@code assets_1day..7day} 和
     * {@code experience_1day..7day} 的裸对象，不使用 ApiResponse；旧 userexp.vue 直接读取
     * 顶层字段。这里只展示配置，不表示用户今天一定可签到。
     */
    @RequestMapping("/signinConfig")
    public Map<String, Object> config() {
        // userexp.vue reads assets_1day directly, without an API envelope.
        return signin.config();
    }

    /**
     * ANY {@code /SFreeEconomy/signinStreak}：读取当前用户连续签到天数。
     *
     * <p>必填 {@code token}。直接返回裸 {@code {leiji:n}}，不使用统一包络。值来自该用户最近
     * 一条签到日志；从未签到返回 0。它不触发签到，也不发奖励。
     */
    @RequestMapping("/signinStreak")
    public Map<String, Object> streak(@RequestParam Map<String, String> params) {
        // userexp.vue likewise reads the historical top-level leiji field.
        return signin.streak(params);
    }

    /**
     * ANY {@code /SFreeEconomy/signin}：执行当天七日签到。
     *
     * <p>必填 token。按 JVM 默认时区计算连续天数和 1..7 日奖励，生产服务器必须保持
     * Asia/Shanghai；分别增加 assets 与 experience，并写签到日志、paylog 和 InnoDB journal。
     * 幂等键固定为“日期+uid”，同日重复不会二次奖励；不要把 experience 奖励写入 points。
     * 超过第 7 天按服务规则循环。
     */
    @RequestMapping("/signin")
    public ApiResponse signin(@RequestParam Map<String, String> params) {
        return ApiResponse.success("\u7b7e\u5230\u6210\u529f", signin.signin(params));
    }
}
