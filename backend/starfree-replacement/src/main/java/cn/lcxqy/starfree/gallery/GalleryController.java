package cn.lcxqy.starfree.gallery;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Standalone image gallery API. Gallery records are not forum dynamics. */
@RestController
@RequestMapping("/SFreeGallery")
public class GalleryController {
    private final GalleryService service;
    private final ObjectMapper mapper;

    public GalleryController(GalleryService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @RequestMapping("/galleryList")
    public ApiResponse galleryList(@RequestParam Map<String, String> params) {
        GalleryService.Page page = service.galleryList(params);
        return ApiResponse.paged(page.data, page.data.size(), page.total);
    }

    @RequestMapping("/galleryTags")
    public ApiResponse galleryTags() {
        return ApiResponse.success("", service.galleryTags());
    }

    @RequestMapping("/galleryInfo")
    public ApiResponse galleryInfo(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", service.galleryInfo(RequestValues.integer(params, "id", 0),
                RequestValues.text(params, "token")));
    }

    @RequestMapping("/gallerySubmit")
    public ApiResponse gallerySubmit(@RequestParam Map<String, String> params) {
        return ApiResponse.success("图集已提交审核", service.gallerySubmit(RequestValues.text(params, "token"), body(params)));
    }

    @RequestMapping("/galleryManage")
    public ApiResponse galleryManage(@RequestParam Map<String, String> params) {
        GalleryService.Page page = service.galleryManage(params);
        return ApiResponse.paged(page.data, page.data.size(), page.total);
    }

    @RequestMapping("/galleryReview")
    public ApiResponse galleryReview(@RequestParam Map<String, String> params) {
        return ApiResponse.success("图集状态已更新", service.galleryReview(RequestValues.text(params, "token"),
                RequestValues.integer(params, "id", 0), RequestValues.text(params, "status"), RequestValues.text(params, "reason")));
    }

    @RequestMapping("/galleryDelete")
    public ApiResponse galleryDelete(@RequestParam Map<String, String> params) {
        return ApiResponse.success("图集已删除", service.galleryDelete(RequestValues.text(params, "token"),
                RequestValues.integer(params, "id", 0), RequestValues.text(params, "reason")));
    }

    private Map<String, Object> body(Map<String, String> params) {
        return RequestValues.jsonObject(mapper, params.get("params"));
    }
}
