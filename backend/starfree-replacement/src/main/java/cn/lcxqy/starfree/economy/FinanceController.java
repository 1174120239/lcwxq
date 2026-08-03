package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.api.ApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 钱包流水读取接口；第三方支付下单、卡密和支付通知不在本控制器。 */
@RestController
@RequestMapping("/pay")
public class FinanceController {
    private final EconomyAccountService accounts;

    public FinanceController(EconomyAccountService accounts) {
        this.accounts = accounts;
    }

    /**
     * ANY {@code /pay/payorderList}：查询当前用户最近 30 条资产流水。
     *
     * <p>必填 {@code token}。成功返回历史兼容的顶层 {@code paydata}，不是普通 data 包络；
     * 同时带 count（本页条数）和 total（全部条数）。只按 token 所属 uid 查询，不能传 uid
     * 查看他人。不要把该列表当成支付网关的实时订单状态，数据来自本地 paylog 投影。
     */
    @RequestMapping("/payorderList")
    public Map<String, Object> payOrders(@RequestParam Map<String, String> params) {
        // The wallet page reads the historical top-level paydata property.
        return accounts.payOrderList(params);
    }

    /**
     * ANY {@code /pay/financeList}：管理端分页查询全站流水。
     *
     * <p>仅 administrator token；参数 {@code searchParams} JSON 支持 uid/status/paytype，另有
     * {@code page=1/limit=15}。返回标准分页包络。该查询不改变订单，不可用来补单或退款。
     */
    @RequestMapping("/financeList")
    public ApiResponse financeList(@RequestParam Map<String, String> params) {
        EconomyAccountService.Page page = accounts.financeList(params);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * ANY {@code /pay/financeTotal}：管理端按业务类型汇总财务数据。
     *
     * <p>仅 administrator token。返回 recharge/trade/withdraw/income 四项，由已完成 paylog
     * 的 paytype、subject 和正负金额分类计算。新增 paytype 时必须同步更新分类规则，否则
     * 总额会遗漏；该统计不是会计总账，也不替代第三方支付平台对账。
     */
    @RequestMapping("/financeTotal")
    public ApiResponse financeTotal(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", accounts.financeTotal(params));
    }
}
