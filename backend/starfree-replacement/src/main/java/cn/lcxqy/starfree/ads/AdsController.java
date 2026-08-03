package cn.lcxqy.starfree.ads;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 广告配置、展示和自助购买/管理接口。
 *
 * <p>广告购买消耗 {@code assets}，不消耗 points 或 experience。官方充值只负责先获得
 * assets，仍由旧支付后端完成；本控制器不会创建第三方支付订单。
 */
@RestController
@RequestMapping("/SFreeAds")
public class AdsController {
    private final AdsService ads;

    public AdsController(AdsService ads) {
        this.ads = ads;
    }

    /**
     * ANY {@code /SFreeAds/adsConfig}：公开读取三种广告位的价格和容量。
     *
     * <p>无参数、无鉴权。data 包含 start/push/banner 对应的 Price/Num 配置。返回 msg
     * 刻意为空以兼容旧前端；这里只展示配置，不保证当前还有空余广告位，购买时会在锁内重查。
     */
    @RequestMapping("/adsConfig")
    public ApiResponse config() {
        // Legacy returns an empty message for this endpoint; keep that envelope for the frontend.
        return ApiResponse.success("", ads.config());
    }

    /**
     * ANY {@code /SFreeAds/adsList}：分页查询广告。
     *
     * <p>参数：{@code searchParams} JSON、{@code searchKey/page=1/limit=10/token}，limit 最大
     * 50。匿名只看 status=1 且未过期；普通登录用户默认只看自己的广告，也不能伪造 filters.uid
     * 查看他人；staff 可管理筛选 status/type/aid/uid。返回标准分页包络。生产带 token 请求是否
     * 进入新服务受 Nginx 灰度控制，应检查 X-Starfree-Backend。
     */
    @RequestMapping("/adsList")
    public ApiResponse list(@RequestParam Map<String, String> params) {
        AdsService.AdsPage page = ads.page(
                RequestValues.text(params, "searchParams"),
                RequestValues.integer(params, "limit", 10),
                RequestValues.integer(params, "page", 1),
                RequestValues.text(params, "searchKey"),
                RequestValues.text(params, "token"));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * ANY {@code /SFreeAds/adsInfo}：读取单个广告。
     *
     * <p>必填 {@code id}，token 可选。公开只能看 status=1；广告所有者和 staff 可看待审/过期
     * 管理记录。成功直接返回广告对象，不使用统一 API 包络，因为旧页面读取的是
     * {@code res.data.name}；任何包络改造都会造成前端字段整体错位。
     */
    @RequestMapping("/adsInfo")
    public Map<String, Object> info(@RequestParam Map<String, String> params) {
        // The old frontend reads res.data.name directly, so this endpoint intentionally returns
        // the ad object itself instead of the normal {code,msg,data} envelope.
        return ads.info(RequestValues.integer(params, "id", 0), RequestValues.text(params, "token"));
    }

    /**
     * ANY {@code /SFreeAds/addAds}：用资产余额购买广告位。
     *
     * <p>必填 {@code token/day/requestId} 和 {@code params} JSON；params 含 name/type/img/
     * intro/url/urltype，type 为 0..2，day 为 1..3650。锁内按最新单价、容量和余额计算，扣减
     * {@code assets}、插入广告及 paylog；普通用户初始待审，staff 可直接生效。重试必须复用同一
     * requestId，变更参数要生成新 id。MyISAM 投影失败会反向补偿并写 InnoDB journal。
     */
    @RequestMapping("/addAds")
    public ApiResponse add(@RequestParam Map<String, String> params) {
        return ApiResponse.success("操作成功", ads.add(params));
    }

    /**
     * ANY {@code /SFreeAds/editAds}：编辑广告资料。
     *
     * <p>必填 {@code token} 和 params.aid/name/type/img/intro/url；仅所有者或 staff。普通用户
     * 修改后 status 重置为 0 等待复审，staff 编辑保留当前状态。此操作不续期、不退款，也不
     * 再扣 assets；价格和到期时间只能由购买/续期流程改变。
     */
    @RequestMapping("/editAds")
    public ApiResponse edit(@RequestParam Map<String, String> params) {
        return ApiResponse.success("操作成功", ads.edit(params));
    }

    /**
     * ANY {@code /SFreeAds/deleteAds}：删除广告。
     *
     * <p>必填 {@code token/id}，仅所有者或 staff。保持旧行为：无论广告是否待审或剩余多少天，
     * 删除均不退还 assets。成功 data 为 null，客户端必须在删除确认框中明确这是不可退款操作。
     */
    @RequestMapping("/deleteAds")
    public ApiResponse delete(@RequestParam Map<String, String> params) {
        ads.delete(params);
        return ApiResponse.success("操作成功", null);
    }

    /**
     * ANY {@code /SFreeAds/auditAds}：审核通过广告。
     *
     * <p>仅 administrator/editor token，参数 {@code id}。当前动作只设 status=1，不处理拒绝/
     * 退款，也不延长 close。审核时服务会确认广告存在；重复通过结果仍为已通过状态。
     */
    @RequestMapping("/auditAds")
    public ApiResponse audit(@RequestParam Map<String, String> params) {
        return ApiResponse.success("操作成功", ads.audit(params));
    }

    /**
     * ANY {@code /SFreeAds/renewalAds}：管理员赠送广告展示天数。
     *
     * <p>仅 administrator token；必填 {@code id/day/requestId}，day 为 1..3650。该接口按照
     * 当前广告类型价格增加累计 price 和 close，并写“系统赠送广告位时间”流水，但不从管理员
     * 或广告主扣 assets。重试必须复用 requestId；普通用户自助续费不是本接口职责。
     */
    @RequestMapping("/renewalAds")
    public ApiResponse renewal(@RequestParam Map<String, String> params) {
        return ApiResponse.success("操作成功", ads.renewal(params));
    }
}
