package cn.lcxqy.starfree.economy;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Legacy-compatible product catalog and product-management HTTP boundary.
 *
 * <p>All endpoints accept the existing form/query protocol used by uni-app. Complex product input
 * remains JSON in the {@code params} form field; this controller does not accept an arbitrary JSON
 * request body. Product ownership, moderation status, inventory counters, and mounted content id
 * are controlled by the service and cannot be assigned through extra params fields.
 */
@RestController
@RequestMapping("/SFreeShop")
public class ShopCatalogController {
    private final ShopCatalogService shops;
    private final ObjectMapper mapper;

    public ShopCatalogController(ShopCatalogService shops, ObjectMapper mapper) {
        this.shops = shops;
        this.mapper = mapper;
    }

    /**
     * GET/POST {@code /SFreeShop/shopList}: paginated product catalog.
     *
     * <p>Inputs: optional JSON {@code searchParams}; {@code searchKey}; {@code order};
     * {@code page=1}; {@code limit=15}, capped at 50; optional token. Supported filters are id,
     * type, cid, uid, status, isMd, sort, subtype, and isView. Supported order values are created,
     * id, price, sellNum, and sort; unknown values fall back to created, never raw SQL.
     *
     * <p>Response uses {@code code/msg/data/count/total}; count is the current page length and total
     * is the full filtered count. Each row contains a sanitized {@code userJson}. The paid
     * {@code value} field is removed unless a valid token belongs to the seller or staff. Existing
     * frontend pages omit token for some uid/status views, so non-sensitive draft metadata remains
     * query-compatible; callers must not interpret that compatibility as access to paid content.
     */
    @RequestMapping(value = "/shopList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse list(@RequestParam Map<String, String> params) {
        ShopCatalogService.ShopPage page = shops.page(
                RequestValues.jsonObject(mapper, params.get("searchParams")),
                RequestValues.text(params, "searchKey"),
                RequestValues.text(params, "order"),
                RequestValues.integer(params, "page", 1),
                RequestValues.integer(params, "limit", 15),
                RequestValues.text(params, "token"));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * GET/POST {@code /SFreeShop/shopInfo}: reads one product as a naked object.
     *
     * <p>Input: numeric {@code key} (legacy alias {@code id} is also accepted) and optional token.
     * This is a deliberate non-standard response: success is the product object itself, with no
     * code/data envelope; missing or unauthorized records return {@code {}}. Published description
     * data is public. The paid value is returned only to seller, staff, or a token-bound buyer with
     * a persisted buy log. A client-supplied uid never grants paid-value access.
     */
    @RequestMapping(value = "/shopInfo", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> info(@RequestParam Map<String, String> params) {
        long id = RequestValues.integer(params, "key", RequestValues.integer(params, "id", 0));
        return shops.info(id, RequestValues.text(params, "token"));
    }

    /**
     * GET/POST {@code /SFreeShop/addShop}: creates a product for the logged-in user.
     *
     * <p>Required: token; params.title; params.type 1..4; description in top-level {@code text} or
     * params.text. Optional allowlisted fields: imgurl, price, integral, num, value, vipDiscount,
     * subtype, isView, and staff-only sort. {@code isMd=1} converts legacy {@code ||rn||} markers
     * back to line breaks. {@code isSpace=1} requests a best-effort type-5 dynamic after the product
     * row exists. uid, status, cid, created, and sellNum supplied by the client are ignored.
     *
     * <p>Status is derived from contentAuditlevel, forbidden words, and current staff role. The
     * authoritative product insert is MyISAM; a secondary dynamic/cache failure is logged and does
     * not turn a successful insert into a retryable failure. Success keeps the legacy shape and
     * message without exposing the generated id.
     */
    @RequestMapping(value = "/addShop", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse add(@RequestParam Map<String, String> params) {
        int isSpace = RequestValues.integer(params, "isSpace", 0);
        if (isSpace != 0 && isSpace != 1) {
            throw new IllegalArgumentException("isSpace参数错误");
        }
        shops.add(RequestValues.text(params, "token"),
                RequestValues.jsonObject(mapper, params.get("params")), params.get("text"),
                RequestValues.integer(params, "isMd", 1), isSpace == 1);
        return ApiResponse.success("添加成功", null);
    }

    /**
     * GET/POST {@code /SFreeShop/editShop}: edits an owned product or a product managed by staff.
     *
     * <p>Required: token and params.id. The same content/product field limits as addShop apply.
     * Missing allowlisted fields retain their database values. top-level text, when present, takes
     * precedence over params.text. uid/cid/created/sellNum/status are immutable here; cid belongs to
     * mountShop and explicit status changes belong to auditShop. Non-staff edits are re-moderated
     * and may return an approved product to pending. MyISAM means this row update and Redis cache
     * eviction are not one transaction; MySQL remains authoritative if cache cleanup fails.
     */
    @RequestMapping(value = "/editShop", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse edit(@RequestParam Map<String, String> params) {
        Integer isMd = params.containsKey("isMd")
                ? RequestValues.integer(params, "isMd", -1) : null;
        int changed = shops.edit(RequestValues.text(params, "token"),
                RequestValues.jsonObject(mapper, params.get("params")), params.get("text"), isMd);
        return changed > 0 ? ApiResponse.success("修改成功", null)
                : ApiResponse.failure("修改失败");
    }

    /**
     * GET/POST {@code /SFreeShop/deleteShop}: deletes one owned product; staff may delete any.
     *
     * <p>Inputs: token and numeric {@code key} (id alias accepted). The operation deletes only the
     * starfree_shop row. Legacy buy logs, mounted article references, and payment history are not
     * cascaded because the source MyISAM schema has no reliable transaction/foreign-key boundary.
     * A staff deletion sends the seller a best-effort system notice. Callers should archive rather
     * than delete sold products when historical paid access must remain resolvable.
     */
    @RequestMapping(value = "/deleteShop", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse delete(@RequestParam Map<String, String> params) {
        long id = RequestValues.integer(params, "key", RequestValues.integer(params, "id", 0));
        int changed = shops.delete(RequestValues.text(params, "token"), id);
        return changed > 0 ? ApiResponse.success("操作成功", null)
                : ApiResponse.failure("操作失败");
    }

    /**
     * GET/POST {@code /SFreeShop/auditShop}: staff moderation for one product.
     *
     * <p>Required: staff token and product {@code key}. Legacy {@code type=0} approves; type=1
     * rejects and requires {@code reason}. Missing type defaults to approval for compatibility with
     * pages/manage/shop.vue. Repeating the same target state is idempotent and does not emit a
     * duplicate inbox notice. Provider email/push delivery is deliberately not attempted; the
     * authoritative notification is a local starfree_inbox system row.
     */
    @RequestMapping(value = "/auditShop", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse audit(@RequestParam Map<String, String> params) {
        int changed = shops.audit(RequestValues.text(params, "token"),
                RequestValues.integer(params, "key", 0),
                RequestValues.integer(params, "type", 0), params.get("reason"));
        return changed > 0 ? ApiResponse.success("操作成功", null)
                : ApiResponse.failure("操作失败");
    }

    /**
     * GET/POST {@code /SFreeShop/mountShop}: attaches a product to post/video content.
     *
     * <p>Required: token, positive {@code sid}, and {@code cid}; cid=-1 unmounts. The caller must
     * own the product. A positive content id must exist, be post/video, and have the same owner;
     * staff may perform corrective cross-owner administration. This stricter check intentionally
     * closes the old arbitrary-cid mounting flaw. The endpoint changes only shop.cid and never
     * edits the article body or creates a purchase.
     */
    @RequestMapping(value = "/mountShop", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse mount(@RequestParam Map<String, String> params) {
        int changed = shops.mount(RequestValues.text(params, "token"),
                RequestValues.integer(params, "sid", 0),
                RequestValues.integer(params, "cid", 0));
        return changed > 0 ? ApiResponse.success("操作成功", null)
                : ApiResponse.failure("操作失败");
    }
}
