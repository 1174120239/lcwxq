package cn.lcxqy.starfree.space;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 圈子动态（Space）完整核心接口，不包含插件动态类型。
 *
 * <p>读取会统一执行 publish/待审/私密/锁定可见性规则；写入会兼容旧 Java Redis 的禁言、
 * 防刷和每日发布计数。类型 6 是历史插件附件，按项目边界明确拒绝。
 */
@RestController
@RequestMapping("/SFreeSpace")
public class SpaceController {
    private final SpaceService spaces;

    public SpaceController(SpaceService spaces) {
        this.spaces = spaces;
    }

    /**
     * ANY {@code /SFreeSpace/addSpace}：发布动态、回复或转发。
     *
     * <p>必填 {@code token/type}；type=0 的正文或图片至少提供一种，type=4 仍需视频和正文；可选
     * {@code text/pic/toid/onlyMe}。type 允许 0..5，除 0/4 外必须提供目标 toid；onlyMe 只能 0/1；
     * 正文最大 1500 字，type=6 插件动态拒绝。
     * 服务检查禁言、防机器人时间窗、24 小时发布上限、最低经验、违禁词和目标可见/锁定状态。
     * spaceAudit 开启时写 status=0 并返回待审提示；公开成功后经验最多按日记三次。主动态写入
     * 后，用户活动字段和经验是尽力更新，失败不会让客户端误重试产生重复 MyISAM 记录。
     */
    @RequestMapping("/addSpace")
    public ApiResponse add(@RequestParam Map<String, String> params,
                           HttpServletRequest request) {
        boolean pending = spaces.add(params, clientIp(request));
        return ApiResponse.success(pending
                ? "\u53d1\u5e03\u6210\u529f\uff0c\u8bf7\u7b49\u5f85\u7ba1\u7406\u5458\u5ba1\u6838"
                : "\u53d1\u5e03\u6210\u529f", null);
    }

    /**
     * ANY {@code /SFreeSpace/editSpace}：编辑已有动态。
     *
     * <p>必填 {@code token/id}；type=0 的正文或图片至少提供一种，type=4 仍需视频和正文；可选
     * text/pic/toid/onlyMe/type。仅原作者或 staff 可编辑；类型
     * 必须与原记录一致，插件记录拒绝，回复目标必须已公开且调用者可见。实现刻意保留原作者，
     * 修复旧后端“管理员编辑后把 uid 改成管理员”的所有权转移问题。编辑不重新发放经验。
     */
    @RequestMapping("/editSpace")
    public ApiResponse edit(@RequestParam Map<String, String> params) {
        int changed = spaces.edit(params);
        return ApiResponse.success("\u4fdd\u5b58\u6210\u529f", changed);
    }

    /**
     * ANY {@code /SFreeSpace/spaceReview}：审核待审动态。
     *
     * <p>仅 administrator/editor token；必填 {@code id/type}，type=1 通过并设 status=1，
     * type=0 拒绝并删除该行。重复通过或非法动作返回业务失败。状态变更完成后写系统通知；
     * 通知是次要投影，主状态一旦成功就不应因通知失败被客户端再次提交。
     */
    @RequestMapping("/spaceReview")
    public ApiResponse review(@RequestParam Map<String, String> params) {
        int changed = spaces.review(params);
        return ApiResponse.success("\u64cd\u4f5c\u6210\u529f", changed);
    }

    /**
     * ANY {@code /SFreeSpace/spaceLock}：锁定或解锁动态。
     *
     * <p>仅 administrator/editor token；{@code id} 必填，{@code type=2} 锁定、type=1 解锁。
     * 待审核 status=0 不允许直接锁定，重复状态变更会失败。锁定后的动态不可继续回复/转发；
     * 成功后向作者写系统通知。不要把 type=0 当作“解锁”，0 属于待审核状态。
     */
    @RequestMapping("/spaceLock")
    public ApiResponse lock(@RequestParam Map<String, String> params) {
        int changed = spaces.lock(params);
        return ApiResponse.success("\u64cd\u4f5c\u6210\u529f", changed);
    }

    /**
     * ANY {@code /SFreeSpace/spaceInfo}：读取单条动态详情。
     *
     * <p>必填 {@code id}，token 可选。匿名只能看公开且非私密记录；作者可看自己的 onlyMe/待审，
     * staff 可按管理规则查看。无权查看和不存在使用相同业务错误，避免泄漏私密记录是否存在。
     * 成功读取会将 views 加 1，返回标准包络，data 中会附作者、图片、目标动态和当前用户相关状态。
     */
    @RequestMapping("/spaceInfo")
    public ApiResponse info(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", spaces.info(
                RequestValues.integer(params, "id", 0),
                RequestValues.text(params, "token")));
    }

    /**
     * ANY {@code /SFreeSpace/spaceList}：分页查询动态。
     *
     * <p>{@code searchParams} JSON 支持 id/uid/type/toid/status/onlyMe 等筛选，另有
     * {@code searchKey/order/page=1/limit=15/token/isManage=0}，limit 最大 50。isManage=1
     * 只有 staff token 才生效；普通列表默认排除回复 type=3，并严格过滤他人的私密/待审记录。
     * 返回标准分页包络，count 为当前页、total 为相同可见性条件下的总数，不能跨权限缓存。
     */
    @RequestMapping("/spaceList")
    public ApiResponse list(@RequestParam Map<String, String> params) {
        SpaceService.SpacePage page = spaces.page(
                RequestValues.text(params, "searchParams"),
                RequestValues.integer(params, "page", 1),
                RequestValues.integer(params, "limit", 15),
                RequestValues.text(params, "searchKey"),
                RequestValues.text(params, "order"),
                RequestValues.integer(params, "isManage", 0),
                RequestValues.text(params, "token"));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * ANY {@code /SFreeSpace/spaceDelete}：删除单条动态。
     *
     * <p>必填 {@code token/id}。作者可自删，staff 可删任意记录并通知作者；这修复了旧 AOP
     * 误拦作者分支的问题。为保持旧数据契约，本接口只删除主行，不级联回复、转发或
     * {@code starfree_userlog(type=spaceLike)}，也不扣经验；清理孤儿数据必须另做可回滚迁移。
     */
    @RequestMapping("/spaceDelete")
    public ApiResponse delete(@RequestParam Map<String, String> params) {
        int changed = spaces.delete(params);
        return ApiResponse.success("\u64cd\u4f5c\u6210\u529f", changed);
    }

    /**
     * ANY {@code /SFreeSpace/spaceLikes}：为动态点赞。
     *
     * <p>必填 {@code token/id}，目标必须对当前用户可见。去重键是持久化的
     * {@code uid + spaceId + type='spaceLike'}，没有 24 小时过期，也不使用 IP/User-Agent；
     * 因而新旧后端可以共享点赞历史。服务用 MySQL named lock 串行执行“查重、写日志、加计数”，
     * 计数失败时补偿删除日志。重复点赞返回业务失败，当前没有取消点赞接口。
     */
    @RequestMapping("/spaceLikes")
    public ApiResponse like(@RequestParam Map<String, String> params) {
        int changed = spaces.like(params);
        return ApiResponse.success("\u70b9\u8d5e\u6210\u529f", changed);
    }

    /**
     * ANY {@code /SFreeSpace/followSpace} 和 {@code /myFollowSpace}：关注用户动态流。
     *
     * <p>必填 {@code token}；分页 {@code page=1/limit=15}，limit 最大 50。只返回已关注用户的
     * status=1、onlyMe=0、type!=3 记录，明确修复旧接口泄露私密动态和回复行的问题。
     * 为兼容旧接口，返回包络只设置当前页 count，不提供数据库 total；两个路径行为完全一致。
     */
    @RequestMapping({"/followSpace", "/myFollowSpace"})
    public ApiResponse followed(@RequestParam Map<String, String> params) {
        SpaceService.SpacePage page = spaces.followed(
                RequestValues.integer(params, "page", 1),
                RequestValues.integer(params, "limit", 15),
                RequestValues.text(params, "token"));
        ApiResponse response = ApiResponse.success("", page.getData());
        // Legacy followSpace only returned the current row count, not the database total.
        response.setCount(page.getData().size());
        return response;
    }

    /**
     * ANY {@code /SFreeSpace/topicList}：动态话题中心。
     *
     * <p>token 可选；返回 {@code official} 官方话题和 {@code followed} 当前用户关注的话题。
     * 后台通过现有 {@code SFreeMetas/addMeta} 创建 {@code type=tag} 即可增加官方话题；
     * 用户创建的话题只会进入自己的关注列表，管理员在原有话题管理中可继续编辑或推荐。
     */
    @RequestMapping("/topicList")
    public ApiResponse topicList(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", spaces.topicCenter(
                RequestValues.text(params, "token"),
                RequestValues.text(params, "searchKey")));
    }

    /**
     * ANY {@code /SFreeSpace/topicCreate}：创建或复用一个用户话题。
     *
     * <p>必填 token/name。名称会去掉两侧 {@code #}，限制为 1 到 24 个中英文、数字、
     * 下划线或短横线；创建成功后自动关注，重复名称直接复用已有 tag。
     */
    @RequestMapping("/topicCreate")
    public ApiResponse topicCreate(@RequestParam Map<String, String> params) {
        return ApiResponse.success("话题已添加", spaces.createTopic(params));
    }

    /**
     * ANY {@code /SFreeSpace/topicFollow}：关注或取消关注话题。
     *
     * <p>必填 token/mid/type；type=1 关注，type=0 取消关注。接口可重复调用，关注使用
     * INSERT IGNORE，取消使用 DELETE，因此前端重试不会产生重复关系。
     */
    @RequestMapping("/topicFollow")
    public ApiResponse topicFollow(@RequestParam Map<String, String> params) {
        return ApiResponse.success(
                RequestValues.integer(params, "type", -1) == 1 ? "已关注话题" : "已取消关注",
                spaces.followTopic(params));
    }

    private String clientIp(HttpServletRequest request) {
        String value = request.getHeader("X-Real-IP");
        if (value == null || value.trim().isEmpty()) {
            value = request.getHeader("X-Forwarded-For");
        }
        if (value != null && !value.trim().isEmpty()) {
            int comma = value.indexOf(',');
            return (comma >= 0 ? value.substring(0, comma) : value).trim();
        }
        return request.getRemoteAddr();
    }
}
