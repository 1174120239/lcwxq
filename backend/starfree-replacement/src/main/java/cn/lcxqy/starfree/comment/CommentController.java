package cn.lcxqy.starfree.comment;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 评论读取、发布、删除和审核接口。
 *
 * <p>评论写入会同时维护文章评论数、用户经验和通知数据；旧表多数为 MyISAM，相关服务
 * 已做权限校验及可补偿处理，但不能把多表写入理解为数据库原子事务。
 */
@RestController
@RequestMapping("/SFreeComments")
public class CommentController {
    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    /**
     * ANY {@code /SFreeComments/commentsList}：分页查询评论。
     *
     * <p>参数：{@code searchParams} JSON 过滤对象、{@code searchKey}、{@code order}、
     * {@code page=1}、{@code limit=15}，以及可选 {@code token}。公开请求只应看到允许
     * 展示的评论；作者或管理角色可因 token 获得额外可见性。返回标准分页包络，
     * {@code count} 为当前页数量，{@code total} 为匹配总数。
     */
    @RequestMapping("/commentsList")
    public ApiResponse list(@RequestParam Map<String, String> params) {
        CommentService.CommentPage page = comments.page(RequestValues.text(params, "searchParams"),
                RequestValues.integer(params, "limit", 15), RequestValues.integer(params, "page", 1),
                RequestValues.text(params, "searchKey"), RequestValues.text(params, "order"),
                RequestValues.text(params, "token"));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * ANY {@code /SFreeComments/commentsAdd}：发布评论或回复。
     *
     * <p>必填：{@code token}；评论关系字段放在 JSON 字符串 {@code params} 中，正文和图片
     * 使用顶层 {@code text}/{@code pic}。服务会校验目标文章/父评论、禁言和审核配置，
     * 写入评论并维护计数、经验及站内信。返回数据中的 {@code status=waiting} 表示待审核；
     * 客户端超时后不要盲目重复提交，应先查询评论列表，避免产生重复评论。
     */
    @RequestMapping("/commentsAdd")
    public ApiResponse add(@RequestParam Map<String, String> params) {
        Map<String, Object> data = comments.add(params);
        String msg = "waiting".equals(data.get("status")) ? "发布成功，将在审核通过后显示！" : "评论成功";
        return ApiResponse.success(msg, data);
    }

    /**
     * ANY {@code /SFreeComments/commentsDelete}：删除评论。
     *
     * <p>必填：{@code token}；评论 id 使用 {@code key}，兼容 {@code coid}。仅评论作者或
     * 允许的管理角色可删除。删除会同步修正文章评论计数并按配置处理经验；接口成功 data
     * 固定为 null。旧表为 MyISAM，故发生进程级中断时需检查评论、计数和经验是否一致。
     */
    @RequestMapping("/commentsDelete")
    public ApiResponse delete(@RequestParam Map<String, String> params) {
        comments.delete(params);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * ANY {@code /SFreeComments/commentsAudit}：管理端审核评论。
     *
     * <p>必填：管理角色 {@code token}、评论 {@code key} 和审核动作 {@code type}。
     * 服务拒绝普通用户和重复/非法状态变更，并在审核通过时处理可见性、计数和经验。
     * 返回统一包络；业务拒绝仍使用 HTTP 200 + {@code code=0}，调用方必须检查 code。
     */
    @RequestMapping("/commentsAudit")
    public ApiResponse audit(@RequestParam Map<String, String> params) {
        return ApiResponse.success("操作成功", comments.audit(params));
    }
}
