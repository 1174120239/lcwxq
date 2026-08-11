package cn.lcxqy.starfree.qa;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/SFreeQa")
public class QaController {
    private final QaService service;
    private final ObjectMapper mapper;

    public QaController(QaService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @RequestMapping("/questionList")
    public ApiResponse questionList(@RequestParam Map<String, String> params) {
        QaService.Page page = service.questionList(params);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    @RequestMapping("/questionInfo")
    public ApiResponse questionInfo(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", service.questionInfo(params));
    }

    @RequestMapping("/questionAdd")
    public ApiResponse questionAdd(@RequestParam Map<String, String> params) {
        return ApiResponse.success("问题已提交，等待审核", service.questionAdd(
                RequestValues.text(params, "token"), body(params)));
    }

    @RequestMapping("/answerList")
    public ApiResponse answerList(@RequestParam Map<String, String> params) {
        QaService.Page page = service.answerList(params);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    @RequestMapping("/answerAdd")
    public ApiResponse answerAdd(@RequestParam Map<String, String> params) {
        return ApiResponse.success("回答发布成功", service.answerAdd(
                RequestValues.text(params, "token"), body(params)));
    }

    @RequestMapping("/answerEdit")
    public ApiResponse answerEdit(@RequestParam Map<String, String> params) {
        return ApiResponse.success("回答修改成功", service.answerEdit(
                RequestValues.text(params, "token"), body(params)));
    }

    @RequestMapping("/answerDelete")
    public ApiResponse answerDelete(@RequestParam Map<String, String> params) {
        return ApiResponse.success("回答已删除", service.answerDelete(
                RequestValues.text(params, "token"), longValue(params.get("id"))));
    }

    @RequestMapping("/answerLike")
    public ApiResponse answerLike(@RequestParam Map<String, String> params) {
        return ApiResponse.success("", service.answerLike(
                RequestValues.text(params, "token"), longValue(params.get("answerId"))));
    }

    @RequestMapping("/commentList")
    public ApiResponse commentList(@RequestParam Map<String, String> params) {
        QaService.Page page = service.commentList(params);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    @RequestMapping("/commentAdd")
    public ApiResponse commentAdd(@RequestParam Map<String, String> params) {
        return ApiResponse.success("评论成功", service.commentAdd(
                RequestValues.text(params, "token"), body(params)));
    }

    @RequestMapping("/commentDelete")
    public ApiResponse commentDelete(@RequestParam Map<String, String> params) {
        return ApiResponse.success("评论已删除", service.commentDelete(
                RequestValues.text(params, "token"), longValue(params.get("id"))));
    }

    @RequestMapping("/questionManage")
    public ApiResponse questionManage(@RequestParam Map<String, String> params) {
        QaService.Page page = service.questionManage(params);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    @RequestMapping("/questionSave")
    public ApiResponse questionSave(@RequestParam Map<String, String> params) {
        return ApiResponse.success("问题保存成功", service.questionSave(
                RequestValues.text(params, "token"), body(params)));
    }

    @RequestMapping("/questionStatus")
    public ApiResponse questionStatus(@RequestParam Map<String, String> params) {
        return ApiResponse.success("问题状态已更新", service.questionStatus(
                RequestValues.text(params, "token"), longValue(params.get("id")),
                RequestValues.integer(params, "status", -1)));
    }

    private Map<String, Object> body(Map<String, String> params) {
        return RequestValues.jsonObject(mapper, params.get("params"));
    }

    private long longValue(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
