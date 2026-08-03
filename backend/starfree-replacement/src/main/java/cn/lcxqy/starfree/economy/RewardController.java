package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 文章打赏记录的只读接口；实际打赏由 SFreeUserlog/addLog 的 reward 类型完成。 */
@RestController
@RequestMapping("/SFreeContents")
public class RewardController {
    private final EconomyReadService reads;

    public RewardController(EconomyReadService reads) {
        this.reads = reads;
    }

    /**
     * ANY {@code /SFreeContents/rewardList}：分页查询文章打赏记录。
     *
     * <p>参数：内容 id 使用历史名 {@code id}，另有 {@code page=1/limit=15}，limit 最大 50；
     * 无需 token。只读取 {@code starfree_userlog(type=reward)}，每条附打赏者脱敏 userJson 和
     * VIP 状态。返回标准分页包络。该接口不验证文章是否仍存在，因此删除文章后可能仍有流水。
     */
    @RequestMapping("/rewardList")
    public ApiResponse rewards(@RequestParam Map<String, String> params) {
        EconomyAccountService.Page page = reads.rewards(
                RequestValues.integer(params, "id", 0),
                RequestValues.integer(params, "page", 1),
                RequestValues.integer(params, "limit", 15));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }
}
