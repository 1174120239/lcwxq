package cn.lcxqy.starfree.content;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Legacy-compatible content helper and administration routes.
 *
 * <p>All mappings retain GET/form-POST support used by the existing uni-app management pages.
 * Authentication and ownership are enforced again inside the service; client uid/group values are
 * ignored. Business errors keep HTTP 200 with {@code code=0} through the shared exception handler.
 */
@RestController
@RequestMapping("/SFreeContents")
public class ContentExtensionController {
    private final ContentExtensionService service;

    public ContentExtensionController(ContentExtensionService service) {
        this.service = service;
    }

    /**
     * GET/POST {@code /SFreeContents/isCommnet}: reply-hidden visibility check.
     *
     * <p>Requires token and content id key. code=1 means the user authored or commented on the
     * content; code=0 with an empty message means neither. This intentionally preserves the typo in
     * the legacy URL and does not treat a bookmark, like, reward, or administrator role as a reply.
     */
    @RequestMapping(value = "/isCommnet", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse isComment(@RequestParam Map<String, String> request) {
        boolean matched = service.hasCommentedOrAuthored(
                RequestValues.text(request, "token"), RequestValues.integer(request, "key", 0));
        return new ApiResponse(matched ? 1 : 0, "", null);
    }

    /**
     * GET/POST {@code /SFreeContents/toRecommend}: staff recommendation flag.
     * Requires token, key=cid and recommend=0/1. It updates modified time and invalidates detail/list
     * caches; it never changes status, author, score, category, or article body.
     */
    @RequestMapping(value = "/toRecommend", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse recommend(@RequestParam Map<String, String> request) {
        return writeResult(service.setRecommended(RequestValues.text(request, "token"),
                RequestValues.integer(request, "key", 0),
                RequestValues.integer(request, "recommend", -1)));
    }

    /**
     * GET/POST {@code /SFreeContents/addTop}: staff pin flag.
     * Requires token, key=cid and istop=0/1. Pinning affects list ordering only; the row must already
     * exist and no new content is created.
     */
    @RequestMapping(value = "/addTop", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse top(@RequestParam Map<String, String> request) {
        return writeResult(service.setTop(RequestValues.text(request, "token"),
                RequestValues.integer(request, "key", 0),
                RequestValues.integer(request, "istop", -1)));
    }

    /**
     * GET/POST {@code /SFreeContents/addSwiper}: staff carousel flag.
     * Requires token, key=cid and isswiper=0/1. The endpoint only marks eligibility; image presence
     * and front-page rendering remain frontend/content concerns.
     */
    @RequestMapping(value = "/addSwiper", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse swiper(@RequestParam Map<String, String> request) {
        return writeResult(service.setSwiper(RequestValues.text(request, "token"),
                RequestValues.integer(request, "key", 0),
                RequestValues.integer(request, "isswiper", -1)));
    }

    /**
     * GET/POST {@code /SFreeContents/setFields}: upsert one string custom field.
     *
     * <p>Requires token, cid, identifier-like name and strvalue. Only the article owner or staff may
     * write. The primary key is (cid,name), so retries replace the same value instead of duplicating
     * it. Configured reserved names are rejected; int/float field types are not exposed here.
     */
    @RequestMapping(value = "/setFields", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse setField(@RequestParam Map<String, String> request) {
        return writeResult(service.setStringField(RequestValues.text(request, "token"),
                RequestValues.integer(request, "cid", 0), RequestValues.text(request, "name"),
                request.get("strvalue")));
    }

    /**
     * GET/POST {@code /SFreeContents/contentConfig}: public deletion policy.
     * Returns only allowDelete. No other apiconfig fields or provider secrets may be added to this
     * public response even if future frontend code asks for them.
     */
    @RequestMapping(value = "/contentConfig", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse contentConfig() {
        return ApiResponse.success("", service.publicConfig());
    }

    /**
     * GET/POST {@code /SFreeContents/allData}: staff management dashboard counts.
     *
     * <p>Requires administrator/editor token. Counts include public totals plus waiting moderation,
     * self-delete requests and pending withdrawals. It is a live read and performs no cache writes;
     * callers should not poll it continuously on large production tables.
     */
    @RequestMapping(value = "/allData", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse allData(@RequestParam Map<String, String> request) {
        return ApiResponse.success("", service.dashboard(RequestValues.text(request, "token")));
    }

    private ApiResponse writeResult(int changed) {
        return changed > 0
                ? ApiResponse.success("操作成功", changed)
                : ApiResponse.failure("操作失败");
    }
}
