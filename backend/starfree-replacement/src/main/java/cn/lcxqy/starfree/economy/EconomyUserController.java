package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.api.ApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 钱包人工调整和提现接口，与个人资料修改严格分离。
 *
 * <p>{@code assets} 是可充值/消费的钱包余额，{@code points} 是任务或商城抵扣积分，
 * {@code experience} 只用于等级；三者严禁互换。官方支付充值创建与通知仍走旧 API。
 */
@RestController
@RequestMapping("/SFreeUsers")
public class EconomyUserController {
    private final EconomyAccountService accounts;

    public EconomyUserController(EconomyAccountService accounts) {
        this.accounts = accounts;
    }

    /**
     * ANY {@code /SFreeUsers/userRecharge}：管理人员人工加减余额。
     *
     * <p>必填 staff {@code token}、目标用户 {@code key}、正数 {@code num}、动作 {@code type}
     * 和 {@code requestId}。type=0 增加、type=1 扣减；rechargeType=0 操作 assets，=1 操作
     * points，永不修改 experience。余额不能变负或溢出。写用户、paylog 和 journal，重试必须
     * 复用 requestId。本接口名虽叫 Recharge，但不是第三方充值入口。
     */
    @RequestMapping("/userRecharge")
    public ApiResponse adjust(@RequestParam Map<String, String> params) {
        Map<String, Object> result = accounts.adjust(params);
        return ApiResponse.success("\u64cd\u4f5c\u6210\u529f", result.get("rows"));
    }

    /**
     * ANY {@code /SFreeUsers/userWithdraw}：用户申请提现。
     *
     * <p>必填 {@code token/num/requestId}，num 为正数 assets 金额。这里只创建
     * {@code starfree_userlog(type=withdraw,cid=-1)} 待审记录，不立即扣款；同一用户已有待审
     * 申请时拒绝。审核通过时才在锁内检查并扣 assets。客户端重试必须复用 requestId。
     */
    @RequestMapping("/userWithdraw")
    public ApiResponse withdraw(@RequestParam Map<String, String> params) {
        Map<String, Object> result = accounts.requestWithdrawal(params);
        return ApiResponse.success("\u64cd\u4f5c\u6210\u529f", result.get("rows"));
    }

    /**
     * ANY {@code /SFreeUsers/withdrawList}：分页查询提现申请。
     *
     * <p>必填 token；{@code page=1/limit=15/searchParams}，普通用户只能看到自己，administrator
     * 可按 searchParams.uid/cid 筛选全部。cid=-1 待审、0 已通过、-2 已拒绝。每行附用户 pay
     * 收款资料；这是敏感管理数据，不能开放为匿名查询。
     */
    @RequestMapping("/withdrawList")
    public ApiResponse withdrawalList(@RequestParam Map<String, String> params) {
        EconomyAccountService.Page page = accounts.withdrawalList(params);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * ANY {@code /SFreeUsers/withdrawStatus}：管理员审核提现。
     *
     * <p>仅 administrator token；{@code key} 为提现日志 id，type=1 通过、type=0 拒绝。
     * 通过时再次检查用户 assets 后扣款、更新状态并写 paylog；拒绝不扣款。动作使用由日志 id
     * 和动作构成的固定幂等键，重复/相反动作不会二次扣款。该接口不负责线下实际打款。
     */
    @RequestMapping("/withdrawStatus")
    public ApiResponse reviewWithdrawal(@RequestParam Map<String, String> params) {
        Map<String, Object> result = accounts.reviewWithdrawal(params);
        return ApiResponse.success("\u64cd\u4f5c\u6210\u529f", result.get("rows"));
    }
}
