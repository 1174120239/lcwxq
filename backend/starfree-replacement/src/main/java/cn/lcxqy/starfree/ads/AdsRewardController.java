package cn.lcxqy.starfree.ads;

import cn.lcxqy.starfree.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 激励视频奖励接口；奖励记入 {@code assets}。
 *
 * <p>客户端回调与广告平台服务端回调由配置 {@code adsVideoType} 二选一。官方充值和支付回调
 * 不属于这里，仍保留旧 API。所有实际加资产动作使用全局锁、持久化 journal 和 MyISAM 补偿。
 */
@RestController
@RequestMapping("/SFreeUserlog")
public class AdsRewardController {
    private static final Logger LOG = LoggerFactory.getLogger(AdsRewardController.class);

    private final AdsRewardService rewards;

    public AdsRewardController(AdsRewardService rewards) {
        this.rewards = rewards;
    }

    /**
     * ANY {@code /SFreeUserlog/adsGift}：发起一次激励视频任务。
     *
     * <p>必填 {@code token/appkey}。服务检查 Redis 防刷、当日奖励次数和 appkey 对应的 adpid，
     * 先写一条 cid=0 的待完成 adsGift 日志，再返回 {@code adpid/logid}。只发起不会加资产；
     * 客户端必须保留 logid 给对应回调，跨用户使用会被拒绝。
     */
    @RequestMapping("/adsGift")
    public ApiResponse start(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", rewards.start(params));
    }

    /**
     * ANY {@code /SFreeUserlog/adsGiftNotify}：客户端模式确认广告完成。
     *
     * <p>必填 {@code token/logid}，仅在 {@code adsVideoType=0} 启用。logid 必须属于当前用户、
     * 当天且仍为 cid=0；成功把日志标为完成、增加配置的 assets 奖励并写 paylog。相同已提交
     * logid 会返回相同结果而不二次加钱；其他重复/过期状态拒绝。
     */
    @RequestMapping("/adsGiftNotify")
    public ApiResponse clientNotify(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", rewards.clientNotify(params));
    }

    /**
     * ANY {@code /SFreeUserlog/adsServerNotify}：广告平台服务端奖励回调。
     *
     * <p>无用户 token；至少要求 {@code trans_id/user_id/sign}，其他厂商字段原样参与兼容处理。
     * 仅在服务端回调模式启用，使用数据库配置密钥校验签名；密钥为空时必须 fail closed。
     * trans_id 是全局幂等键，重放只确认已处理，不重复加 assets。无论内部异常还是签名失败，
     * HTTP body 都保持厂商要求的裸 {@code {isValid:false}}，不能改为 ApiResponse 包络。
     */
    @RequestMapping("/adsServerNotify")
    public Map<String, Object> serverNotify(@RequestParam Map<String, String> params) {
        boolean valid;
        try {
            valid = rewards.serverNotify(params);
        } catch (RuntimeException error) {
            // The advertising provider requires this exact body even when an internal step fails.
            LOG.error("Advertising reward callback failed", error);
            valid = false;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("isValid", valid);
        return response;
    }
}
