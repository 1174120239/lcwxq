package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.api.ApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 商城购买与 VIP 消费接口；商品信息/管理等未命中路径仍由旧 API 处理。 */
@RestController
@RequestMapping("/SFreeShop")
public class ShopEconomyController {
    private final ShopEconomyService shop;

    public ShopEconomyController(ShopEconomyService shop) {
        this.shop = shop;
    }

    /**
     * ANY {@code /SFreeShop/buyShop}：购买商城商品。
     *
     * <p>必填 {@code token/sid/isIntegral/requestId}，可选 {@code fid} 关联内容。isIntegral=0
     * 以 assets 结算，=1 按商品配置使用 points 抵扣；服务检查上架、库存、不能买自己的商品、
     * 非重复型商品的购买记录及 VIP 折扣，随后更新买卖双方余额/积分、库存、日志和流水。
     * 重试必须复用 requestId；assets、points 绝不能互相代用。
     */
    @RequestMapping("/buyShop")
    public ApiResponse buy(@RequestParam Map<String, String> params) {
        shop.buy(params);
        return ApiResponse.success("\u64cd\u4f5c\u6210\u529f", null);
    }

    /**
     * ANY {@code /SFreeShop/isBuyShop}：判断当前用户是否买过商品。
     *
     * <p>必填 {@code token/sid}。已购买返回 code=1，未购买或非法 sid 返回 code=0；沿用旧状态
     * 式包络，不返回布尔 data。这里只查持久化 buy 日志，不检查订单是否可再次购买。
     */
    @RequestMapping("/isBuyShop")
    public ApiResponse isBought(@RequestParam Map<String, String> params) {
        return shop.isBought(params)
                ? ApiResponse.success("\u5df2\u8d2d\u4e70", null)
                : ApiResponse.failure("\u672a\u8d2d\u4e70");
    }

    /**
     * ANY {@code /SFreeShop/buyVIP}：按天购买 VIP。
     *
     * <p>必填 {@code token/day/requestId}，价格按当前 vipPrice*day 从 assets 扣除；在已有 VIP
     * 到期时间上顺延，并按 vipDay 规则处理永久状态。写用户、paylog 和 journal，重试复用
     * requestId。此接口不是充值：余额不足时应先走旧官方支付取得 assets。
     */
    @RequestMapping("/buyVIP")
    public ApiResponse buyVip(@RequestParam Map<String, String> params) {
        Map<String, Object> result = shop.buyVipDays(params);
        return ApiResponse.success("\u5f00\u901aVIP\u6210\u529f", result.get("rows"));
    }

    /**
     * ANY {@code /SFreeShop/buyVIPpackage}：购买配置化 VIP 套餐。
     *
     * <p>必填 {@code token/id/requestId}。服务从 starfree_vips 读取价格、基础天数和赠送天数，
     * 从 assets 扣款并更新 VIP 到期时间。客户端价格字段不会被信任；套餐配置变化后新的请求
     * 必须使用新 requestId，旧请求重放返回原结果。
     */
    @RequestMapping("/buyVIPpackage")
    public ApiResponse buyVipPackage(@RequestParam Map<String, String> params) {
        Map<String, Object> result = shop.buyVipPackage(params);
        return ApiResponse.success("\u5f00\u901aVIP\u6210\u529f", result.get("rows"));
    }

    /**
     * ANY {@code /SFreeShop/vipInfo}：公开读取 VIP 单价、折扣和永久天数配置。
     *
     * <p>无参数、无鉴权；返回标准包络，data 含 vipDiscount/vipPrice/scale/vipDay。该接口不
     * 返回套餐列表，套餐列表仍由既有 StarFreeSystem/vipTypeList 或旧后端提供。
     */
    @RequestMapping("/vipInfo")
    public ApiResponse vipInfo() {
        return ApiResponse.success("", shop.vipInfo());
    }
}
