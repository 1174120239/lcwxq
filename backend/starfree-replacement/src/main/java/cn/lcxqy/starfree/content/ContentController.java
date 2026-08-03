package cn.lcxqy.starfree.content;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 普通文章/视频的读取、发布、编辑、删除和审核接口。
 *
 * <p>发布与编辑是“受控混合路由”：仅确定为普通 post/video 的请求由新服务写入；付费、
 * 草稿、商品/动态关联及其他封闭功能会保留原始表单并转发旧后端。不要把这个控制器理解为
 * 已经重建所有 SFreeContents 功能。
 */
@RestController
@RequestMapping("/SFreeContents")
public class ContentController {
    private final ContentService contents;
    private final LegacyContentReadTracker reads;
    private final ContentAddRoutingPolicy addRouting;
    private final LegacyContentWriteForwarder legacyWrite;

    @Autowired
    public ContentController(ContentService contents, LegacyContentReadTracker reads,
                             ContentAddRoutingPolicy addRouting,
                             LegacyContentWriteForwarder legacyWrite) {
        this.contents = contents;
        this.reads = reads;
        this.addRouting = addRouting;
        this.legacyWrite = legacyWrite;
    }

    ContentController(ContentService contents, LegacyContentReadTracker reads) {
        this(contents, reads, new ContentAddRoutingPolicy(new ObjectMapper()), null);
    }

    /**
     * ANY {@code /SFreeContents/contentsList}：分页查询内容列表。
     *
     * <p>参数：{@code searchParams} JSON 支持 type/status/authorId/mid/关键词及推荐状态等筛选，
     * 另有 {@code searchKey/order/page=1/limit=15/random=0/token}；limit 最大 50。
     * 普通访客始终只查 publish，只有 administrator/editor token 可使用 status 管理筛选。
     * random=1 会使用 RAND()，大表上代价高。返回标准分页包络；生产带 token 请求当前可能
     * 仍由 Nginx 路由到旧 API，修改前应先查响应头 {@code X-Starfree-Backend}。
     */
    @RequestMapping("/contentsList")
    public ApiResponse list(@RequestParam Map<String, String> params) {
        ContentService.ContentPage page = contents.page(RequestValues.text(params, "searchParams"),
                RequestValues.integer(params, "limit", 15), RequestValues.integer(params, "page", 1),
                RequestValues.text(params, "order"), RequestValues.text(params, "searchKey"),
                RequestValues.integer(params, "random", 0), RequestValues.text(params, "token"));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * ANY {@code /SFreeContents/contentsInfo}：读取完整内容详情。
     *
     * <p>内容 id 优先取 {@code key}，兼容 {@code cid}；{@code isMd=1} 请求 Markdown 形态，
     * 可选 token 仅用于待审核内容的管理可见性。成功直接返回文章对象，不是
     * {@code {code,msg,data}}；不存在才返回失败包络，这是前端强依赖的例外结构。
     * 首次读取按“内容 id + 客户端 IP + User-Agent”在共享 Java 序列化 Redis 中去重 900 秒；
     * 本次请求实际计数成功时，响应里的 views 同步为自增后的值。Nginx 必须可信地传 X-Real-IP，
     * 否则所有用户可能共用 IP。
     */
    @RequestMapping("/contentsInfo")
    public Object info(@RequestParam Map<String, String> params, HttpServletRequest request) {
        long cid = RequestValues.integer(params, "key", RequestValues.integer(params, "cid", 0));
        Map<String, Object> data = contents.detail(cid,
                RequestValues.integer(params, "isMd", 0), RequestValues.text(params, "token"));
        if (data == null) {
            return ApiResponse.failure("\u8be5\u6587\u7ae0\u4e0d\u5b58\u5728");
        }

        // Keep the raw article response shape, but expose the value that is authoritative after
        // this request. The old API returned the pre-increment value, which made a newly opened
        // article continue to display 0 even though the database had already recorded the read.
        if (reads.firstRead(cid, clientIp(request), request.getHeader("User-Agent"))) {
            contents.incrementViews(cid);
            Object currentViews = data.get("views");
            if (currentViews instanceof Number) {
                data.put("views", ((Number) currentViews).longValue() + 1L);
            }
        }
        return data;
    }

    /**
     * POST {@code /SFreeContents/contentsAdd}：发布内容或透明转发旧写接口。
     *
     * <p>必填普通表单：{@code token}、JSON {@code params}（title/type/category/tag 等）及
     * 顶层 {@code text}；可选 {@code isMd}。仅结构明确的普通 post/video 进入新实现；
     * isDraft、isPaid、isSpace、商品 sid、插件/未知类型、重复或歧义表单均原样转发 8081。
     * 新实现校验 200 字标题、60000 字正文、防刷/敏感代码和分类关系；Markdown 会添加标记并
     * 把 {@code ||rn||} 转为换行。文章行写入后才做尽力而为的用户活动/经验更新，客户端超时
     * 不可盲重试。为兼容旧控制器，成功 data 固定返回插入行数 1，而不是 cid。
     */
    @PostMapping("/contentsAdd")
    public Object add(@RequestParam MultiValueMap<String, String> form, HttpServletRequest request) {
        Map<String, String> params = form.toSingleValueMap();
        if (!addRouting.useReplacement(params)) {
            if (legacyWrite == null) {
                throw new IllegalStateException("Legacy contentsAdd forwarder is unavailable");
            }
            return legacyWrite.forwardAdd(form, request);
        }
        contents.add(params, clientIp(request));
        // The closed controller returns the insert row count, while cid/status are persisted.
        return ApiResponse.success("发布成功", 1);
    }

    Object add(Map<String, String> params, HttpServletRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.setAll(params);
        return add(form, request);
    }

    /**
     * POST {@code /SFreeContents/contentsUpdate}：修改普通内容或透明转发旧接口。
     *
     * <p>必填 {@code token} 和 params.cid/title，正文使用顶层或 params.text。路由策略先排除
     * 草稿、付费、商品、动态关联及未知形态，再确认数据库原类型为 post/video；其余请求保持
     * 原始 body/header 转发旧后端。新实现只允许作者或管理角色，保留原文章类型和省略时的
     * Markdown 模式，校验关系 id 后以可补偿顺序更新 MyISAM 内容/关系表，并清理详情及列表缓存。
     * 成功 data 固定为 1。请求重试前应先读取详情确认，避免把已成功的更新再次覆盖。
     */
    @PostMapping("/contentsUpdate")
    public Object update(@RequestParam MultiValueMap<String, String> form,
                         HttpServletRequest request) {
        Map<String, String> params = form.toSingleValueMap();
        if (!addRouting.useReplacementUpdate(params) || !contents.isOrdinaryUpdate(params)) {
            if (legacyWrite == null) {
                throw new IllegalStateException("Legacy contentsUpdate forwarder is unavailable");
            }
            return legacyWrite.forwardUpdate(form, request);
        }
        contents.updateOrdinary(params);
        return ApiResponse.success("修改成功", 1);
    }

    /**
     * ANY {@code /SFreeContents/contentsDelete}：删除内容。
     *
     * <p>必填 {@code token}，内容 id 由服务兼容解析 {@code key/cid}。仅作者或相应管理角色可
     * 删除；服务同步处理关系、配置化删除经验以及相关缓存。成功 data 为 null。历史表是 MyISAM，
     * 多步中断时必须用对账脚本检查文章、关系、评论/日志孤儿和经验，不能依赖 @Transactional
     * 自动回滚的假设。
     */
    @RequestMapping("/contentsDelete")
    public ApiResponse delete(@RequestParam Map<String, String> params) {
        contents.delete(params);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * ANY {@code /SFreeContents/contentsAudit}：审核内容。
     *
     * <p>必填 administrator/editor {@code token}、内容 id 和审核动作（服务兼容旧参数名）。
     * 审核会改变 publish/waiting 等状态，并按配置处理审核经验和通知；重复或非法状态返回
     * {@code code=0}。该接口只负责普通内容审核，不替代商城、动态或插件的审核流程。
     */
    @RequestMapping("/contentsAudit")
    public ApiResponse audit(@RequestParam Map<String, String> params) {
        return ApiResponse.success("操作成功", contents.audit(params));
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
