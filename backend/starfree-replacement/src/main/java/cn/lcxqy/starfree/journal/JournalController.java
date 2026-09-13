package cn.lcxqy.starfree.journal;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Standalone Campus Editions API. This module never reads or writes forum posts. */
@RestController
@RequestMapping("/SFreeJournal")
public class JournalController {
    private final JournalService service;
    private final ObjectMapper mapper;

    public JournalController(JournalService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @RequestMapping("/journalList")
    public ApiResponse journalList(@RequestParam Map<String, String> params) {
        JournalService.Page page = service.journalList(params);
        return ApiResponse.paged(page.data, page.data.size(), page.total);
    }

    @RequestMapping("/journalInfo")
    public ApiResponse journalInfo(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", service.journalInfo(RequestValues.integer(params, "id", 0),
                RequestValues.text(params, "token")));
    }

    @RequestMapping("/articleList")
    public ApiResponse articleList(@RequestParam Map<String, String> params) {
        JournalService.Page page = service.articleList(params);
        return ApiResponse.paged(page.data, page.data.size(), page.total);
    }

    @RequestMapping("/articleInfo")
    public ApiResponse articleInfo(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", service.articleInfo(RequestValues.integer(params, "id", 0),
                RequestValues.text(params, "token")));
    }

    @RequestMapping("/articleSubmit")
    public ApiResponse articleSubmit(@RequestParam Map<String, String> params) {
        return ApiResponse.success("投稿已提交", service.articleSubmit(RequestValues.text(params, "token"), body(params)));
    }

    @RequestMapping("/articleVote")
    public ApiResponse articleVote(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", service.articleVote(RequestValues.text(params, "token"),
                RequestValues.integer(params, "articleId", 0), RequestValues.integer(params, "vote", 0)));
    }

    @RequestMapping("/journalSave")
    public ApiResponse journalSave(@RequestParam Map<String, String> params) {
        return ApiResponse.success("刊物已保存", service.journalSave(RequestValues.text(params, "token"), body(params)));
    }

    @RequestMapping("/journalStatus")
    public ApiResponse journalStatus(@RequestParam Map<String, String> params) {
        return ApiResponse.success("刊物状态已更新", service.journalStatus(RequestValues.text(params, "token"),
                RequestValues.integer(params, "id", 0), RequestValues.integer(params, "status", -1)));
    }

    @RequestMapping("/articleManage")
    public ApiResponse articleManage(@RequestParam Map<String, String> params) {
        JournalService.Page page = service.articleManage(params);
        return ApiResponse.paged(page.data, page.data.size(), page.total);
    }

    @RequestMapping("/myArticles")
    public ApiResponse myArticles(@RequestParam Map<String, String> params) {
        JournalService.Page page = service.myArticles(params);
        return ApiResponse.paged(page.data, page.data.size(), page.total);
    }

    @RequestMapping("/articleReview")
    public ApiResponse articleReview(@RequestParam Map<String, String> params) {
        return ApiResponse.success("文章审核状态已更新", service.articleReview(RequestValues.text(params, "token"),
                RequestValues.integer(params, "id", 0), RequestValues.text(params, "status"),
                RequestValues.text(params, "reason")));
    }

    private Map<String, Object> body(Map<String, String> params) {
        return RequestValues.jsonObject(mapper, params.get("params"));
    }
}
