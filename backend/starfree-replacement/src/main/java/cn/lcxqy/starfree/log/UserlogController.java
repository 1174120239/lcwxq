package cn.lcxqy.starfree.log;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户互动日志接口，覆盖收藏、文章点赞、打赏和每日打卡。
 *
 * <p>{@code reward} 和 {@code clock} 会改变经济数据，使用全局锁及持久化操作日志；
 * {@code mark}/{@code likes} 是普通互动。广告奖励虽然共享同一 URL 前缀，但由
 * AdsRewardController 独立负责。
 */
@RestController
@RequestMapping("/SFreeUserlog")
public class UserlogController {
    private final UserlogService logs;

    public UserlogController(UserlogService logs) {
        this.logs = logs;
    }

    /**
     * ANY {@code /SFreeUserlog/markList}：查询当前用户收藏的文章。
     *
     * <p>必填：{@code token}；分页参数 {@code page=1}、{@code limit=15}，limit 最大 50。
     * 服务只返回仍存在、已发布且类型为 post 的文章，并额外附加用于取消收藏的
     * {@code logid}。total 按收藏日志计数，若历史收藏对应文章已删除，可能大于实际 data 数量。
     */
    @RequestMapping("/markList")
    public ApiResponse markList(@RequestParam Map<String, String> params) {
        UserlogService.MarkPage page = logs.markList(params);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * ANY {@code /SFreeUserlog/isMark}：查询文章收藏状态。
     *
     * <p>必填：{@code token}、内容 id {@code cid}。返回 data 包含 {@code isMark=0/1}
     * 和 {@code logid}；未收藏时 logid 为 -1。该接口只检查 mark，不代表点赞或购买状态。
     */
    @RequestMapping("/isMark")
    public ApiResponse isMark(@RequestParam Map<String, String> params) {
        return ApiResponse.success(logs.isMark(params));
    }

    /**
     * ANY {@code /SFreeUserlog/addLog}：新增互动或执行经济动作。
     *
     * <p>必填：{@code token} 和 JSON 字符串 {@code params}。params.type 仅允许
     * {@code mark/reward/likes/clock}；非 clock 需 {@code cid}，reward 还需正数
     * {@code num}。经济动作应提供顶层或 params 内的 {@code requestId}，重试必须复用同一
     * requestId。clock 使用特殊 {@code ApiResponse.clock} 包络；不要按普通 data 结构解析。
     */
    @RequestMapping("/addLog")
    public ApiResponse add(@RequestParam Map<String, String> params) {
        Map<String, Object> result = logs.add(params);
        if (result.containsKey("clockData")) {
            return ApiResponse.clock(result.get("clockData"));
        }
        return ApiResponse.success("操作成功", result);
    }

    /**
     * ANY {@code /SFreeUserlog/removeLog}：删除互动日志。
     *
     * <p>必填：{@code token} 和日志 id {@code key}。普通用户只能删除自己的收藏；管理员可
     * 删除允许的互动记录。删除 likes 时会下调文章点赞计数。该接口不能用于撤销打赏或签到，
     * 经济流水必须通过对账/补偿流程处理，不能直接删日志冒充退款。
     */
    @RequestMapping("/removeLog")
    public ApiResponse remove(@RequestParam Map<String, String> params) {
        logs.remove(params);
        return ApiResponse.success("操作成功", null);
    }
}
