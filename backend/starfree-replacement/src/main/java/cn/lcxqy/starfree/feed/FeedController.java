package cn.lcxqy.starfree.feed;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Unified public community feed: ordinary posts, questions and mutual-aid tasks. */
@RestController
@RequestMapping("/SFreeFeed")
public class FeedController {
    private final FeedService service;

    public FeedController(FeedService service) {
        this.service = service;
    }

    @RequestMapping("/feedList")
    public ApiResponse feedList(@RequestParam Map<String, String> params) {
        FeedService.Page page = service.feedList(
                RequestValues.integer(params, "page", 1),
                RequestValues.integer(params, "limit", 12),
                RequestValues.text(params, "type"));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }
}
