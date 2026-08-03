package cn.lcxqy.starfree.meta;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.content.ContentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 分类（meta）读取和管理接口。
 *
 * <p>公开读取无需 token；新增、编辑、删除和推荐必须使用当前管理员 token。管理写入使用
 * 字段白名单并同步清理旧 Java 后端的 Redis 投影，不能信任前端传入的 type/count 等派生字段。
 */
@RestController
@RequestMapping("/SFreeMetas")
public class MetaController {
    private final MetaService metas;
    private final ObjectMapper mapper;

    public MetaController(MetaService metas, ObjectMapper mapper) {
        this.metas = metas;
        this.mapper = mapper;
    }

    /**
     * ANY {@code /SFreeMetas/metasList}：分页查询分类。
     *
     * <p>参数：{@code searchParams} 为 JSON 过滤对象，{@code searchKey} 为关键词，
     * {@code order} 为兼容排序字段，{@code page} 默认 1，{@code limit} 默认 15。
     * 鉴权：无需 token。返回统一包络，但兼容旧接口只设置当前页 {@code count}，不设置
     * 数据库总数；前端不可把这里的 count 当作完整分页 total。
     */
    @RequestMapping("/metasList")
    public ApiResponse list(@RequestParam Map<String, String> params) {
        MetaService.MetaPage page = metas.page(RequestValues.text(params, "searchParams"),
                RequestValues.integer(params, "limit", 15), RequestValues.integer(params, "page", 1),
                RequestValues.text(params, "order"), RequestValues.text(params, "searchKey"));
        ApiResponse response = ApiResponse.success("", page.getData());
        response.setCount(page.getData().size());
        return response;
    }

    /**
     * ANY {@code /SFreeMetas/metaInfo}：读取单个分类。
     *
     * <p>参数：优先使用数值 {@code key}，缺失时使用 {@code mid}；也可通过
     * {@code slug} 定位。鉴权：无。成功返回统一包络，找不到分类时返回业务失败
     * {@code code=0}，不是 HTTP 404。不要同时传入互相冲突的 id 和 slug。
     */
    @RequestMapping("/metaInfo")
    public ApiResponse info(@RequestParam Map<String, String> params) {
        Map<String, Object> data = metas.info(RequestValues.integer(params, "key", RequestValues.integer(params, "mid", 0)), RequestValues.text(params, "slug"));
        return data == null ? ApiResponse.failure("分类不存在") : ApiResponse.success(data);
    }

    /**
     * ANY {@code /SFreeMetas/selectContents}：查询分类关联的内容。
     *
     * <p>参数：{@code searchParams} JSON 中携带分类/状态等筛选，另有 {@code searchKey}、
     * {@code order}、{@code page=1}、{@code limit=15} 和可选 {@code token}。token 会影响
     * 待审核内容等可见性。返回统一包络，但为兼容旧前端，{@code count} 是当前页行数，
     * 不是数据库总行数；带 token 的生产流量是否进入新后端还受 Nginx 灰度规则控制。
     */
    @RequestMapping("/selectContents")
    public ApiResponse contents(@RequestParam Map<String, String> params) {
        ContentService.ContentPage page = metas.contentsPage(RequestValues.text(params, "searchParams"),
                RequestValues.integer(params, "limit", 15), RequestValues.integer(params, "page", 1),
                RequestValues.text(params, "order"), RequestValues.text(params, "searchKey"),
                RequestValues.text(params, "token"));
        ApiResponse response = ApiResponse.success("", page.getData());
        response.setCount(page.getData().size());
        return response;
    }

    /**
     * GET/POST {@code /SFreeMetas/addMeta}：新增分类或标签。
     *
     * <p>必填：管理员 {@code token}，JSON {@code params} 中的 name、slug、type；type 只能是
     * category/tag。可选 description、imgurl、orderKey、parent、isrecommend。name/slug 按
     * type 唯一，count 固定从 0 开始。成功 data 是受影响行数；该接口不接受客户端指定 mid。
     */
    @RequestMapping(value = "/addMeta", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse add(@RequestParam Map<String, String> params) {
        int changed = metas.add(RequestValues.text(params, "token"),
                RequestValues.jsonObject(mapper, params.get("params")));
        return changed > 0 ? ApiResponse.success("操作成功", changed) : ApiResponse.failure("操作失败");
    }

    /**
     * GET/POST {@code /SFreeMetas/editMeta}：编辑一个已有分类或标签。
     *
     * <p>必填：管理员 token，params.mid。name/slug 若传入不可为空；description/imgurl 可用空串
     * 清除；orderKey、parent、isrecommend 会做范围校验。type、count、mid 之外的任意列均不可
     * 修改。父分类不能是自身、后代、标签或不存在的数据。成功后旧分类和文章缓存立即失效。
     */
    @RequestMapping(value = "/editMeta", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse edit(@RequestParam Map<String, String> params) {
        int changed = metas.edit(RequestValues.text(params, "token"),
                RequestValues.jsonObject(mapper, params.get("params")));
        return changed > 0 ? ApiResponse.success("操作成功", changed) : ApiResponse.failure("操作失败");
    }

    /**
     * GET/POST {@code /SFreeMetas/deleteMeta}：删除分类/标签及其内容关系。
     *
     * <p>必填：管理员 token、数值 id。只删除 meta 和 starfree_relationships 关联，不删除文章
     * 本身。由于数据库表为 MyISAM，服务使用一条多表 DELETE 避免先后两条 SQL 留下悬空关系；
     * 对前端始终把成功结果归一为 data=1。
     */
    @RequestMapping(value = "/deleteMeta", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse delete(@RequestParam Map<String, String> params) {
        int changed = metas.delete(RequestValues.text(params, "token"),
                RequestValues.integer(params, "id", 0));
        return changed > 0 ? ApiResponse.success("操作成功", changed) : ApiResponse.failure("操作失败");
    }

    /**
     * GET/POST {@code /SFreeMetas/toRecommend}：切换分类推荐状态。
     *
     * <p>必填：管理员 token、meta id {@code key}、recommend=0/1。该接口只改 isrecommend，
     * 不等于文章的推荐状态；前端不得把分类 mid 当作文章 cid 调用。
     */
    @RequestMapping(value = "/toRecommend", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse recommend(@RequestParam Map<String, String> params) {
        int changed = metas.recommend(RequestValues.text(params, "token"),
                RequestValues.integer(params, "key", 0),
                RequestValues.integer(params, "recommend", 1));
        return changed > 0 ? ApiResponse.success("操作成功", changed) : ApiResponse.failure("操作失败");
    }
}
