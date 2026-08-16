package cn.lcxqy.starfree.lostfound;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Form-compatible HTTP boundary for the campus lost-and-found area. */
@RestController
@RequestMapping("/SFreeLostFound")
public class LostFoundController {
    private final LostFoundService service;
    private final LostFoundCommentService comments;
    private final LostFoundConfigService config;
    private final ObjectMapper mapper;

    public LostFoundController(LostFoundService service, LostFoundCommentService comments,
                               LostFoundConfigService config, ObjectMapper mapper) {
        this.service = service;
        this.comments = comments;
        this.config = config;
        this.mapper = mapper;
    }

    @RequestMapping(value = "/config", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse config(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", config.publicConfig(RequestValues.text(params, "token")));
    }

    @RequestMapping(value = "/configManage", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse configManage(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", config.manage(RequestValues.text(params, "token")));
    }

    @RequestMapping(value = "/configSave", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse configSave(@RequestParam Map<String, String> params) {
        return ApiResponse.success("设置已保存", config.save(
                RequestValues.text(params, "token"), body(params)));
    }

    @RequestMapping(value = "/itemList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse itemList(@RequestParam Map<String, String> params) {
        LostFoundService.Page page = service.itemList(params);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    @RequestMapping(value = "/itemInfo", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse itemInfo(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", service.itemInfo(
                RequestValues.integer(params, "id", 0), RequestValues.text(params, "token")));
    }

    @RequestMapping(value = "/itemAdd", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse itemAdd(@RequestParam Map<String, String> params) {
        Map<String, Object> item = service.itemAdd(RequestValues.text(params, "token"), body(params));
        String message = number(item.get("status")) == 1
                ? "信息已发布" : "信息已提交，等待审核";
        return ApiResponse.success(message, item);
    }

    @RequestMapping(value = "/itemEdit", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse itemEdit(@RequestParam Map<String, String> params) {
        return ApiResponse.success("修改成功", service.itemEdit(
                RequestValues.text(params, "token"), body(params)));
    }

    @RequestMapping(value = "/itemStatus", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse itemStatus(@RequestParam Map<String, String> params) {
        return ApiResponse.success("状态已更新", service.itemStatus(
                RequestValues.text(params, "token"), RequestValues.integer(params, "id", 0),
                RequestValues.text(params, "action")));
    }

    @RequestMapping(value = "/itemDelete", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse itemDelete(@RequestParam Map<String, String> params) {
        return ApiResponse.success("信息已关闭", service.itemDelete(
                RequestValues.text(params, "token"), RequestValues.integer(params, "id", 0)));
    }

    @RequestMapping(value = "/itemManage", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse itemManage(@RequestParam Map<String, String> params) {
        LostFoundService.Page page = service.itemManage(params);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    @RequestMapping(value = "/itemAudit", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse itemAudit(@RequestParam Map<String, String> params) {
        return ApiResponse.success("审核状态已更新", service.itemAudit(
                RequestValues.text(params, "token"), RequestValues.integer(params, "id", 0),
                RequestValues.text(params, "action"), RequestValues.text(params, "reason")));
    }

    @RequestMapping(value = "/commentList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse commentList(@RequestParam Map<String, String> params) {
        java.util.List<Map<String, Object>> data = comments.comments(
                RequestValues.integer(params, "itemId", 0));
        return ApiResponse.paged(data, data.size(), data.size());
    }

    @RequestMapping(value = "/commentAdd", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse commentAdd(@RequestParam Map<String, String> params) {
        return ApiResponse.success("评论已发布", comments.add(
                RequestValues.text(params, "token"),
                RequestValues.integer(params, "itemId", 0),
                RequestValues.integer(params, "parentId", 0),
                RequestValues.text(params, "text")));
    }

    @RequestMapping(value = "/commentDelete", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse commentDelete(@RequestParam Map<String, String> params) {
        comments.delete(RequestValues.text(params, "token"),
                RequestValues.integer(params, "commentId", 0));
        return ApiResponse.success("评论已删除", null);
    }

    @RequestMapping(value = "/contactShare", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse contactShare(@RequestParam Map<String, String> params) {
        return ApiResponse.success("联系方式已定向发送", comments.shareContact(
                RequestValues.text(params, "token"),
                RequestValues.integer(params, "itemId", 0),
                RequestValues.integer(params, "commentId", 0)));
    }

    @RequestMapping(value = "/contactAccess", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse contactAccess(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", comments.contactAccess(
                RequestValues.text(params, "token"),
                RequestValues.integer(params, "itemId", 0)));
    }

    private Map<String, Object> body(Map<String, String> params) {
        return RequestValues.jsonObject(mapper, params.get("params"));
    }

    private long number(Object value) {
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
